package com.botoni.avaliacaodepreco.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.DirectionsListener;
import com.botoni.avaliacaodepreco.di.PermissionsListener;
import com.botoni.avaliacaodepreco.di.ResultListener;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
import com.botoni.avaliacaodepreco.ui.views.SearchWatcher;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainFragment extends Fragment implements DirectionsListener, PermissionsListener {
    private final String DEFAULT_LOCATION = "Cuiabá";
    private static final int BACKGROUND_THREAD_POOL_SIZE = 4;
    private static final int MAXIMUM_SEARCH_RESULTS = 10;
    private FragmentManager manager;
    private ExecutorService executor;
    private Geocoder geocoder;
    private FusedLocationProviderClient locationClient;
    private TextInputEditText inputOrigin;
    private TextInputEditText inputDestination;
    private TextInputLayout layoutInputOrigin;
    private MaterialCardView locationCard;
    private RecyclerView recyclerView;
    private LocationAdapter adapter;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private final List<Address> addresses = new ArrayList<>();
    private Address originAddress;
    private Address destinationAddress;
    private String countryCode;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeServices();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupComponents();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    @Override
    public String load(@NonNull Context context) {
        return DirectionsListener.super.load(context);
    }

    @Override
    public String build(LatLng origin, LatLng destination, Context context) {
        return DirectionsListener.super.build(origin, destination, context);
    }

    @Override
    public String fetch(String curl) throws IOException {
        return DirectionsListener.super.fetch(curl);
    }

    @Override
    public List<LatLng> decode(String code) {
        return DirectionsListener.super.decode(code);
    }

    @Override
    public void parse(@NonNull String json, @NonNull ResultListener<Directions> resultListener) {
        DirectionsListener.super.parse(json, resultListener);
    }

    private void initializeServices() {
        executor = Executors.newFixedThreadPool(BACKGROUND_THREAD_POOL_SIZE);
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoder = new Geocoder(requireContext(), new Locale("pt", "BR"));
        load(requireContext());
    }

    private void bindViews(View view) {
        locationCard = view.findViewById(R.id.adicionar_localizacao_card);
        inputOrigin = view.findViewById(R.id.origem_input);
        inputDestination = view.findViewById(R.id.destino_input);
        layoutInputOrigin = view.findViewById(R.id.origem_input_layout);
        recyclerView = view.findViewById(R.id.localizacoes_recycler_view);
    }


    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupComponents() {
        ask(permissionLauncher, requireContext());
        setupBottomSheet();
        setupRecyclerView();
        setUpCard();
        setupOriginInput();
        loadDefaultDestination();
    }

    private void setupBottomSheet() {
        FrameLayout container = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        bottomSheet = BottomSheetBehavior.from(container);
        bottomSheet.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupRecyclerView() {
        adapter = new LocationAdapter(addresses, this::onAddressSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setUpCard() {
        locationCard.setOnClickListener(v ->
                bottomSheet.setState(BottomSheetBehavior.STATE_HALF_EXPANDED));
    }

    private void setupOriginInput() {
        setupOriginFocus();
        setupOriginWatcher();
        setupOriginClearIcon();
    }

    private void setupOriginFocus() {
        inputOrigin.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED);
                inputOrigin.setCursorVisible(true);
            } else {
                inputOrigin.setCursorVisible(false);
            }
        });
    }

    @SuppressLint("PrivateResource")
    private void setupOriginClearIcon() {
        layoutInputOrigin.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layoutInputOrigin.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
        layoutInputOrigin.setEndIconVisible(false);
        layoutInputOrigin.setEndIconOnClickListener(v -> clearOrigin());
    }

    private void setupOriginWatcher() {
        inputOrigin.addTextChangedListener(new SearchWatcher(this::searchAddress));
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void onAddressSelected(Address address) {
        Bundle bundle = new Bundle();
        bundle.putParcelable("origin", address);
        getParentFragmentManager().setFragmentResult("originKey", bundle);
        originAddress = address;
        updateOriginUI(address);
        bottomSheet.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void updateOriginUI(Address address) {
        inputOrigin.setText("");
        inputOrigin.setHint(address.getAddressLine(0));
        inputOrigin.setEnabled(false);
        inputOrigin.setFocusable(false);
        inputOrigin.setFocusableInTouchMode(false);
        inputOrigin.setCursorVisible(false);
        layoutInputOrigin.setEndIconVisible(true);
        inputOrigin.clearFocus();
    }

    private void loadDefaultDestination() {
        inputDestination.setHint(DEFAULT_LOCATION);
        inputDestination.setEnabled(false);
        inputDestination.setCursorVisible(false);

        searchAddresses(DEFAULT_LOCATION, null, new ResultListener<>() {
            @Override
            public void onSuccess(List<Address> results) {
                if (!results.isEmpty() && isAdded()) {
                    destinationAddress = results.get(0);
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("destination", destinationAddress);
                    getParentFragmentManager().setFragmentResult("destinationKey", bundle);
                }
            }

            @Override
            public void onError(int errorResId) {
                showSnackBar(errorResId);
            }
        });
    }

    private void searchAddress(String query) {
        layoutInputOrigin.setError(null);
        searchAddresses(query, countryCode, new ResultListener<>() {
            @Override
            public void onSuccess(List<Address> results) {
                updateSearchResults(results);
            }

            @Override
            public void onError(int errorResId) {
                updateSearchResults(Collections.emptyList());
                showSnackBar(errorResId);
            }
        });
    }

    private void searchAddresses(String query, String country, ResultListener<List<Address>> resultListener) {
        executor.execute(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, MAXIMUM_SEARCH_RESULTS);
                if (results == null) results = Collections.emptyList();
                List<Address> filtered = filterByCountry(results, country);
                runOnUiThread(() -> resultListener.onSuccess(filtered));
            } catch (IOException e) {
                runOnUiThread(() -> resultListener.onError(R.string.error_locations));
            }
        });
    }

    private void getAddressFromCoordinates(double lat, double lng, ResultListener<List<Address>> resultListener) {
        executor.execute(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results == null) results = Collections.emptyList();
                List<Address> finalResults = results;
                runOnUiThread(() -> resultListener.onSuccess(finalResults));
            } catch (IOException e) {
                runOnUiThread(() -> resultListener.onError(R.string.error_locations));
            }
        });
    }

    private List<Address> filterByCountry(List<Address> list, String country) {
        if (country == null || list.isEmpty()) return list;
        return list.stream()
                .filter(a -> country.equals(a.getCountryCode()))
                .collect(Collectors.toList());
    }

    private void handlePermissionResult(Map<String, Boolean> results) {
        onResult(results, this::requestUserLocation,
                () -> showDialog(R.string.error_permission_denied_title, R.string.error_permission_denied)
        );
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void requestUserLocation() {
        if (isGranted(requireContext())) return;
        locationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        extractCountryCode(location);
                    }
                });
    }

    private void extractCountryCode(Location location) {
        getAddressFromCoordinates(location.getLatitude(), location.getLongitude(), new ResultListener<>() {
            @Override
            public void onSuccess(List<Address> results) {
                if (!results.isEmpty()) {
                    countryCode = results.get(0).getCountryCode();
                }
            }

            @Override
            public void onError(int errorResId) {
                showSnackBar(errorResId);
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateSearchResults(List<Address> results) {
        addresses.clear();
        addresses.addAll(results);
        adapter.notifyDataSetChanged();
    }

    private void clearOrigin() {
        originAddress = null;
        resetOriginUI();
    }

    private void resetOriginUI() {
        inputOrigin.setText("");
        inputOrigin.setHint(R.string.label_endereco_origem);
        inputOrigin.setEnabled(true);
        inputOrigin.setFocusable(true);
        inputOrigin.setFocusableInTouchMode(true);
        layoutInputOrigin.setEndIconVisible(false);
    }

    private void runOnUiThread(Runnable action) {
        requireActivity().runOnUiThread(action);
    }

    private void showDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.submit, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSnackBar(int messageResId) {
        Snackbar.make(requireView(), messageResId, Snackbar.LENGTH_SHORT).show();
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}