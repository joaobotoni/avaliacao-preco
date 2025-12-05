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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.DirectionsListener;
import com.botoni.avaliacaodepreco.di.PermissionsListener;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainFragment extends Fragment implements DirectionsListener, PermissionsListener {
    private static final String DEFAULT_LOCATION = "Cuiabá";
    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private FragmentManager manager;
    private Address originAddress;
    private Address destinationAddress;
    private String userCountryCode;
    private final List<Address> searchResults = new ArrayList<>();
    private ExecutorService executorService;
    private Geocoder geocoder;
    private FusedLocationProviderClient locationClient;
    private TextInputEditText originInput;
    private TextInputEditText destinationInput;
    private TextInputLayout originInputLayout;
    private MaterialCardView addLocationCard;
    private CardView partidaCardView;
    private TextView partidaEnderecoTextView;
    private EditText partidaKmAdicionalInput;
    private Button partidaAdicionar5KmButton;
    private Button partidaAdicionar10KmButton;
    private Button partidaAdicionar20KmButton;
    private CardView destinoCardView;
    private TextView destinoEnderecoTextView;
    private EditText destinoKmAdicionalInput;
    private Button destinoAdicionar5KmButton;
    private Button destinoAdicionar10KmButton;
    private Button destinoAdicionar20KmButton;
    private Button location;
    private RecyclerView recyclerView;
    private LocationAdapter adapter;
    private BottomSheetBehavior<FrameLayout> bottomSheet;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult
            );

    private void onPermissionsResult(Map<String, Boolean> results) {
        onResult(results, this::fetchUserLocation, this::showPermissionDeniedDialog);
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDependencies();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false); // This inflates the XML you provided
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupUI();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdown();
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void fetchUserLocation() {
        if (isGranted(requireContext())) return;

        locationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        processUserLocation(location);
                    }
                });
    }

    private void processUserLocation(Location location) {
        reverseGeocode(location.getLatitude(), location.getLongitude(),
                addresses -> {
                    if (!addresses.isEmpty()) {
                        updateUserCountryCode(addresses.get(0).getCountryCode());
                    }
                }, this::showError);
    }

    private void showPermissionDeniedDialog() {
        showDialog(
                R.string.error_permission_denied_title,
                R.string.error_permission_denied
        );
    }


    private void initializeDependencies() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoder = new Geocoder(requireContext(), new Locale("pt", "BR"));
        load(requireContext());
    }

    private void initializeViews(View root) {

        partidaCardView = root.findViewById(R.id.local_partida_card);
        partidaEnderecoTextView = root.findViewById(R.id.local_partida_endereco_text);
        partidaKmAdicionalInput = root.findViewById(R.id.local_partida_km_adicional_input);
        partidaAdicionar5KmButton = root.findViewById(R.id.local_partida_adicionar_5km_button);
        partidaAdicionar10KmButton = root.findViewById(R.id.local_partida_adicionar_10km_button);
        partidaAdicionar20KmButton = root.findViewById(R.id.local_partida_adicionar_20km_button);

        destinoCardView = root.findViewById(R.id.local_destino_card);
        destinoEnderecoTextView = root.findViewById(R.id.local_destino_endereco_text);
        destinoKmAdicionalInput = root.findViewById(R.id.local_destino_km_adicional_input);
        destinoAdicionar5KmButton = root.findViewById(R.id.local_destino_adicionar_5km_button);
        destinoAdicionar10KmButton = root.findViewById(R.id.local_destino_adicionar_10km_button);
        destinoAdicionar20KmButton = root.findViewById(R.id.local_destino_adicionar_20km_button);

        location = root.findViewById(R.id.button_fragment_location);
        addLocationCard = root.findViewById(R.id.adicionar_localizacao_card);
        originInput = root.findViewById(R.id.origem_input);
        destinationInput = root.findViewById(R.id.destino_input);
        originInputLayout = root.findViewById(R.id.origem_input_layout);
        recyclerView = root.findViewById(R.id.localizacoes_recycler_view);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupUI() {
        requestPermissions();
        setupBottomSheet();
        setupRecyclerView();
        setupCard();
        setupOriginInput();
        setupDestinationInput();
        transition();
    }

    private void setupBottomSheet() {
        FrameLayout container = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        bottomSheet = BottomSheetBehavior.from(container);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupRecyclerView() {
        adapter = new LocationAdapter(searchResults, this::onAddressSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupCard() {
        addLocationCard.setOnClickListener(v ->
                setBottomSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED)
        );
    }

    private void transition() {
        manager = getParentFragmentManager();
        location.setOnClickListener(v -> manager.beginTransaction()
                .replace(R.id.fragment_container_view, LocationFragment.class, null)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit());
    }

    private void setupOriginInput() {
        configureOriginFocusListener();
        configureOriginTextWatcher();
        configureOriginClearButton();
    }

    private void setupDestinationInput() {
        setDestinationHint(DEFAULT_LOCATION);
        setDestinationEnabled(false);
        setDestinationCursorVisible(false);
        loadDefaultDestination();
    }

    private void configureOriginFocusListener() {
        originInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED);
                setOriginCursorVisible(true);
            } else {
                setOriginCursorVisible(false);
            }
        });
    }

    @SuppressLint("PrivateResource")
    private void configureOriginClearButton() {
        originInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        originInputLayout.setEndIconDrawable(
                com.google.android.material.R.drawable.mtrl_ic_cancel
        );
        setOriginClearButtonVisible(false);
        originInputLayout.setEndIconOnClickListener(v -> clearOrigin());
    }

    private void configureOriginTextWatcher() {
        originInput.addTextChangedListener(new SearchWatcher(this::searchAddress));
    }


    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void requestPermissions() {
        ask(permissionLauncher, requireContext());
    }

    private void searchAddress(String query) {
        clearError();
        geocodeByName(query, userCountryCode, this::updateSearchResults,
                error -> {
                    updateSearchResults(Collections.emptyList());
                    runOnMainThread(() -> showError(error));
                }
        );
    }

    private void geocodeByName(String query, String countryCode, Consumer<List<Address>> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
                List<Address> filtered = filterByCountry(
                        results != null ? results : Collections.emptyList(),
                        countryCode
                );
                runOnMainThread(() -> onSuccess.accept(filtered));
            } catch (IOException e) {
                onError.accept(R.string.error_locations);
            }
        });
    }

    private void reverseGeocode(double lat, double lng, Consumer<List<Address>> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                List<Address> finalResults = results != null ? results : Collections.emptyList();
                runOnMainThread(() -> onSuccess.accept(finalResults));
            } catch (IOException e) {
                onError.accept(R.string.error_locations);
            }
        });
    }

    private List<Address> filterByCountry(List<Address> addresses, String countryCode) {
        if (countryCode == null || addresses.isEmpty()) {
            return addresses;
        }
        return addresses.stream()
                .filter(address -> countryCode.equals(address.getCountryCode()))
                .collect(Collectors.toList());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void onAddressSelected(Address address) {
        updateOriginAddress(address);
        notifyOriginSelected(address);
        updateOriginUI(address);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void loadDefaultDestination() {
        geocodeByName(
                DEFAULT_LOCATION,
                null,
                addresses -> {
                    if (!addresses.isEmpty() && isAdded()) {
                        updateDestinationAddress(addresses.get(0));
                        notifyDestinationSelected(addresses.get(0));
                    }
                },
                error -> runOnMainThread(() -> showError(error))
        );
    }


    private void updateOriginAddress(Address address) {
        this.originAddress = address;
    }

    private void updateDestinationAddress(Address address) {
        this.destinationAddress = address;
    }

    private void updateUserCountryCode(String countryCode) {
        this.userCountryCode = countryCode;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateSearchResults(List<Address> results) {
        searchResults.clear();
        searchResults.addAll(results);
        adapter.notifyDataSetChanged();
    }

    private void clearOrigin() {
        updateOriginAddress(null);
        resetOriginUI();
    }

    private void notifyOriginSelected(Address address) {
        sendFragmentResult(RESULT_KEY_ORIGIN, KEY_ORIGIN, address);
    }

    private void notifyDestinationSelected(Address address) {
        sendFragmentResult(RESULT_KEY_DESTINATION, KEY_DESTINATION, address);
    }

    private void sendFragmentResult(String resultKey, String bundleKey, Address address) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(bundleKey, address);
        getParentFragmentManager().setFragmentResult(resultKey, bundle);
    }

    private void updateOriginUI(Address address) {
        setOriginText("");
        setOriginHint(address.getAddressLine(0));
        setOriginEnabled(false);
        setOriginFocusable(false);
        setOriginCursorVisible(false);
        setOriginClearButtonVisible(true);
        clearOriginFocus();
    }

    private void resetOriginUI() {
        setOriginText("");
        setOriginHint(getString(R.string.label_endereco_origem));
        setOriginEnabled(true);
        setOriginFocusable(true);
        setOriginClearButtonVisible(false);
    }

    private void setBottomSheetState(int state) {
        bottomSheet.setState(state);
    }

    private void setOriginText(String text) {
        originInput.setText(text);
    }

    private void setOriginHint(String hint) {
        originInput.setHint(hint);
    }

    private void setOriginEnabled(boolean enabled) {
        originInput.setEnabled(enabled);
    }

    private void setOriginFocusable(boolean focusable) {
        originInput.setFocusable(focusable);
        originInput.setFocusableInTouchMode(focusable);
    }

    private void setOriginCursorVisible(boolean visible) {
        originInput.setCursorVisible(visible);
    }

    private void setOriginClearButtonVisible(boolean visible) {
        originInputLayout.setEndIconVisible(visible);
    }

    private void clearOriginFocus() {
        originInput.clearFocus();
    }

    private void clearError() {
        originInputLayout.setError(null);
    }

    private void setDestinationHint(String hint) {
        destinationInput.setHint(hint);
    }

    private void setDestinationEnabled(boolean enabled) {
        destinationInput.setEnabled(enabled);
    }

    private void setDestinationCursorVisible(boolean visible) {
        destinationInput.setCursorVisible(visible);
    }

    private void showError(int messageResId) {
        showSnackbar(messageResId);
    }

    private void showSnackbar(int messageResId) {
        Snackbar.make(requireView(), messageResId, Snackbar.LENGTH_SHORT).show();
    }

    private void showDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.submit, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeAsync(Runnable task) {
        executorService.execute(task);
    }

    private void runOnMainThread(Runnable action) {
        requireActivity().runOnUiThread(action);
    }

    private void shutdown() {
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
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
    public void parse(@NonNull String json, @NonNull Consumer<Directions> success, @NonNull Consumer<Integer> failure) {
        DirectionsListener.super.parse(json, success, failure);
    }
}
