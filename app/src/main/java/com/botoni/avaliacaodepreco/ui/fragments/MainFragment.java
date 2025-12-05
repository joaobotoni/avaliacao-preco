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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.AddressProvider;
import com.botoni.avaliacaodepreco.di.DirectionsProvider;
import com.botoni.avaliacaodepreco.di.LocationPermissionProvider;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MainFragment extends Fragment implements DirectionsProvider,
        LocationPermissionProvider, AddressProvider {
    private static final String DEFAULT_LOCATION = "Cuiabá";
    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private static final String STATE_ORIGIN = "newOrigin";
    private static final String STATE_DESTINATION = "newDestination";
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
    private CardView ajusteCardView;
    private EditText kmAdicionalInput;
    private Button adicionar5KmButton;
    private Button adicionar10KmButton;
    private Button adicionar20KmButton;
    private Button location;
    private RecyclerView recyclerView;
    private LocationAdapter adapter;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private volatile double distance;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::onPermissionsResult
    );

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDependencies();
        setupFragmentResultListeners();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupUI();
        restoreState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUIAfterReturn();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isNavigating = false;
        clearViewReferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (originAddress != null) {
            outState.putParcelable(STATE_ORIGIN, originAddress);
        }
        if (destinationAddress != null) {
            outState.putParcelable(STATE_DESTINATION, destinationAddress);
        }
    }

    @Override
    public List<Address> search(String query) {
        try {
            List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
            return results != null ? results : emptyList();
        } catch (IOException e) {
            return emptyList();
        }
    }

    @Override
    public String load(@NonNull Context context) {
        return DirectionsProvider.super.load(context);
    }

    @Override
    public String build(LatLng origin, LatLng destination, Context context) {
        return DirectionsProvider.super.build(origin, destination, context);
    }

    @Override
    public String fetch(String curl) throws IOException {
        return DirectionsProvider.super.fetch(curl);
    }

    @Override
    public List<LatLng> decode(String code) {
        return DirectionsProvider.super.decode(code);
    }

    @Override
    public void parse(@NonNull String json, @NonNull Consumer<Directions> success, @NonNull Consumer<Integer> failure) {
        DirectionsProvider.super.parse(json, success, failure);
    }

    private void initializeDependencies() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Context context = getContext();
        if (context != null) {
            locationClient = LocationServices.getFusedLocationProviderClient(context);
            geocoder = new Geocoder(context, new Locale("pt", "BR"));
            load(context);
        }
    }

    private void setupFragmentResultListeners() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_ORIGIN, this,
                (requestKey, bundle) -> {
                    if (!isAdded()) return;
                    Address address = getAddressFromBundle(bundle, KEY_ORIGIN);
                    if (address != null) {
                        originAddress = address;
                        updateOriginUI(address);
                        setAjusteCardViewVisible(View.VISIBLE);
                        setButtonTransactionVisible(View.VISIBLE);
                        if (destinationAddress != null) {
                            calculateRoute();
                        }
                    }
                });

        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_DESTINATION, this,
                (requestKey, bundle) -> {
                    if (!isAdded()) return;
                    Address address = getAddressFromBundle(bundle, KEY_DESTINATION);
                    if (address != null) {
                        destinationAddress = address;
                        if (originAddress != null) {
                            calculateRoute();
                        }
                    }
                });
    }

    private Address getAddressFromBundle(Bundle bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, Address.class);
        } else {
            return bundle.getParcelable(key);
        }
    }

    private void initializeViews(View root) {
        ajusteCardView = root.findViewById(R.id.ajuste_km_card);
        kmAdicionalInput = root.findViewById(R.id.km_adicional_input);
        adicionar5KmButton = root.findViewById(R.id.adicionar_5km_button);
        adicionar10KmButton = root.findViewById(R.id.adicionar_10km_button);
        adicionar20KmButton = root.findViewById(R.id.local_partida_adicionar_20km_button);


        location = root.findViewById(R.id.button_fragment_location);
        addLocationCard = root.findViewById(R.id.adicionar_localizacao_card);
        originInput = root.findViewById(R.id.origem_input);
        destinationInput = root.findViewById(R.id.destino_input);
        originInputLayout = root.findViewById(R.id.origem_input_layout);
        recyclerView = root.findViewById(R.id.localizacoes_recycler_view);
    }

    private void setupUI() {
        if (!isAdded()) return;
        requestPermissions();
        setupBottomSheet();
        setupRecyclerView();
        setupCard();
        setupOriginInput();
        setupDestinationInput();
        setupTransition();
        setupAdditionalButtons();
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        Address savedOrigin = getAddressFromBundle(savedInstanceState, STATE_ORIGIN);
        if (savedOrigin != null) {
            originAddress = savedOrigin;
            updateOriginUI(savedOrigin);
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }

        Address savedDestination = getAddressFromBundle(savedInstanceState, STATE_DESTINATION);
        if (savedDestination != null) {
            destinationAddress = savedDestination;
        }

        if (originAddress != null && destinationAddress != null) {
            calculateRoute();
        }
    }

    private void updateUIAfterReturn() {
        if (!isAdded()) return;

        if (originAddress != null) {
            updateOriginUI(originAddress);
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }

        if (bottomSheet != null) {
            setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        }

        if (originInput != null) {
            originInput.clearFocus();
        }
        if (destinationInput != null) {
            destinationInput.clearFocus();
        }
    }

    private void clearViewReferences() {
        ajusteCardView = null;
        location = null;
        addLocationCard = null;
        originInput = null;
        destinationInput = null;
        originInputLayout = null;
        recyclerView = null;
        adapter = null;
        bottomSheet = null;
    }

    private void setupBottomSheet() {
        View view = getView();
        if (view == null) return;
        FrameLayout container = view.findViewById(R.id.localizacao_bottom_sheet_container);
        if (container == null) return;
        bottomSheet = BottomSheetBehavior.from(container);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;
        adapter = new LocationAdapter(searchResults, this::onAddressSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupCard() {
        if (addLocationCard != null) {
            addLocationCard.setOnClickListener(v -> setBottomSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED));
        }
    }

    private void setupTransition() {
        manager = getParentFragmentManager();
        if (location != null) {
            location.setOnClickListener(v -> navigateToLocationFragment());
        }
    }

    private void setupOriginInput() {
        if (originInput == null || originInputLayout == null) return;
        configureOriginFocusListener();
        configureOriginTextWatcher();
        configureOriginClearButton();
    }

    private void setupDestinationInput() {
        if (destinationInput == null) return;
        setDestinationHint(DEFAULT_LOCATION);
        setDestinationEnabled(false);
        setDestinationCursorVisible(false);
        if (destinationAddress == null) {
            loadDefaultDestination();
        }
    }

    private void setupAdditionalButtons() {
        if (adicionar5KmButton != null) {
            adicionar5KmButton.setOnClickListener(v -> {
                distance += 5;
                runOnMainThread(() -> showSnackbar(String.format("Distância atualizada! Agora: %.2f km", distance)));
            });
        }
        if (adicionar10KmButton != null) {
            adicionar10KmButton.setOnClickListener(v -> {
                distance += 10;
                runOnMainThread(() -> showSnackbar(String.format("Distância atualizada! Agora: %.2f km", distance)));
            });
        }
        if (adicionar20KmButton != null) {
            adicionar20KmButton.setOnClickListener(v -> {
                distance += 20;
                runOnMainThread(() -> showSnackbar(String.format("Distância atualizada! Agora: %.2f km", distance)));
            });
        }
    }

    private void configureOriginFocusListener() {
        if (originInput == null) return;
        originInput.setOnFocusChangeListener((v, hasFocus) -> {
            setBottomSheetState(hasFocus ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN);
            setOriginCursorVisible(hasFocus);
        });
    }

    @SuppressLint("PrivateResource")
    private void configureOriginClearButton() {
        if (originInputLayout == null) return;
        originInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        originInputLayout.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
        setOriginClearButtonVisible(false);
        originInputLayout.setEndIconOnClickListener(v -> clearOrigin());
    }

    private void configureOriginTextWatcher() {
        if (originInput == null) return;
        originInput.addTextChangedListener(new SearchWatcher(this::searchAddress));
    }

    private void requestPermissions() {
        Context context = getContext();
        if (context != null) {
            request(permissionLauncher, context);
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        onResult(results, this::fetchUserLocation, this::showPermissionDeniedDialog);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    private void fetchUserLocation() {
        Context context = getContext();
        if (context == null || isGranted(context)) return;
        locationClient.getLastLocation().addOnSuccessListener(requireActivity(), this::processUserLocation);
    }

    private void processUserLocation(Location location) {
        if (location == null || !isAdded()) return;
        reverseGeocode(location.getLatitude(), location.getLongitude(), this::handleUserLocationResult, this::showError);
    }

    private void handleUserLocationResult(List<Address> addresses) {
        Address firstAddress = first(addresses);
        if (firstAddress != null) {
            updateUserCountryCode(code(firstAddress));
        }
    }

    private void searchAddress(String query) {
        clearError();
        geocodeByNameWithFilter(query, userCountryCode, this::updateSearchResults, this::handleSearchError);
    }

    private void handleSearchError(Integer errorResId) {
        updateSearchResults(emptyList());
        showError(errorResId);
    }

    private void geocodeByNameWithFilter(String query, String countryCode, Consumer<List<Address>> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS);
                List<Address> filtered = filter(results != null ? results : emptyList(), countryCode);
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onSuccess.accept(filtered);
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.error_locations);
                    }
                });
            }
        });
    }

    private void reverseGeocode(double lat, double lng, Consumer<List<Address>> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onSuccess.accept(results != null ? results : emptyList());
                    }
                });
            } catch (IOException e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.error_locations);
                    }
                });
            }
        });
    }

    private void onAddressSelected(Address address) {
        if (!isAdded()) return;
        updateOriginAddress(address);
        notifyOriginSelected(address);
        updateOriginUI(address);
        setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        if (originAddress != null) {
            setAjusteCardViewVisible(View.VISIBLE);
            setButtonTransactionVisible(View.VISIBLE);
        }
    }

    private void loadDefaultDestination() {
        geocodeByNameWithFilter(DEFAULT_LOCATION, null, this::handleDefaultDestination, this::showError);
    }

    private void handleDefaultDestination(List<Address> addresses) {
        Address firstAddress = first(addresses);
        if (firstAddress != null && isAdded()) {
            updateDestinationAddress(firstAddress);
            notifyDestinationSelected(firstAddress);
        }
    }

    private void calculateRoute() {
        if (!isAdded() || originAddress == null || destinationAddress == null) return;
        LatLng origin = extractCoordinates(originAddress);
        LatLng destination = extractCoordinates(destinationAddress);
        fetchDirections(origin, destination, this::handleDirectionsSuccess, this::handleDirectionsError);
    }

    private LatLng extractCoordinates(Address address) {
        return new LatLng(address.getLatitude(), address.getLongitude());
    }

    private void fetchDirections(LatLng origin, LatLng destination, Consumer<Directions> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                Context context = getContext();
                if (context == null) return;
                String url = build(origin, destination, context);
                String json = fetch(url);
                parse(json, onSuccess, onError);
            } catch (Exception e) {
                runOnMainThread(() -> {
                    if (isAdded()) {
                        onError.accept(R.string.error_network_directions);
                    }
                });
            }
        });
    }

    private void handleDirectionsSuccess(Directions directions) {
        runOnMainThread(() -> {
            if (isAdded()) {
                updateRouteInfo(directions);
            }
        });
    }

    private void updateRouteInfo(Directions directions) {
        distance = formatDistance(directions.getDistance());
    }

    private double formatDistance(String distanceStr) {
        return Double.parseDouble(distanceStr.replaceAll("[^0-9.]", ""));
    }

    private void handleDirectionsError(Integer errorResId) {
        runOnMainThread(() -> {
            if (isAdded()) {
                showError(errorResId);
            }
        });
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void clearOrigin() {
        updateOriginAddress(null);
        resetOriginUI();
        setAjusteCardViewVisible(View.GONE);
        setButtonTransactionVisible(View.GONE);
    }

    private void notifyOriginSelected(Address address) {
        calculateRoute();
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
        if (!isAdded()) return;
        setOriginText("");
        setOriginHint(format(address));
        setOriginEnabled(false);
        setOriginFocusable(false);
        setOriginCursorVisible(false);
        setOriginClearButtonVisible(true);
        clearOriginFocus();
    }

    private void resetOriginUI() {
        if (!isAdded()) return;
        setOriginText("");
        setOriginHint(getString(R.string.label_endereco_origem));
        setOriginEnabled(true);
        setOriginFocusable(true);
        setOriginClearButtonVisible(false);
    }

    private volatile boolean isNavigating = false;

    private void navigateToLocationFragment() {
        if (!isAdded() || originAddress == null || destinationAddress == null || isNavigating)
            return;
        isNavigating = true;
        LocationFragment fragment = LocationFragment.newInstance(originAddress, destinationAddress);
        manager.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    private void setBottomSheetState(int state) {
        if (bottomSheet != null) {
            bottomSheet.setState(state);
        }
    }

    private void setOriginText(String text) {
        if (originInput != null) {
            originInput.setText(text);
        }
    }

    private void setOriginHint(String hint) {
        if (originInput != null) {
            originInput.setHint(hint);
        }
    }

    private void setOriginEnabled(boolean enabled) {
        if (originInput != null) {
            originInput.setEnabled(enabled);
        }
    }

    private void setOriginFocusable(boolean focusable) {
        if (originInput != null) {
            originInput.setFocusable(focusable);
            originInput.setFocusableInTouchMode(focusable);
        }
    }

    private void setButtonTransactionVisible(int visible) {
        if (location != null) {
            location.setVisibility(visible);
        }
    }

    private void setAjusteCardViewVisible(int visible) {
        if (ajusteCardView != null) {
            ajusteCardView.setVisibility(visible);
        }
    }


    private void setOriginCursorVisible(boolean visible) {
        if (originInput != null) {
            originInput.setCursorVisible(visible);
        }
    }

    private void setOriginClearButtonVisible(boolean visible) {
        if (originInputLayout != null) {
            originInputLayout.setEndIconVisible(visible);
        }
    }

    private void clearOriginFocus() {
        if (originInput != null) {
            originInput.clearFocus();
        }
    }

    private void clearError() {
        if (originInputLayout != null) {
            originInputLayout.setError(null);
        }
    }

    private void setDestinationHint(String hint) {
        if (destinationInput != null) {
            destinationInput.setHint(hint);
        }
    }

    private void setDestinationEnabled(boolean enabled) {
        if (destinationInput != null) {
            destinationInput.setEnabled(enabled);
        }
    }

    private void setDestinationCursorVisible(boolean visible) {
        if (destinationInput != null) {
            destinationInput.setCursorVisible(visible);
        }
    }

    private String formatAddressText(Address address) {
        if (address == null) return "";
        String locality = address.getLocality();
        String adminArea = address.getAdminArea();
        if (locality != null && adminArea != null) {
            return String.format("%s, %s", locality, adminArea);
        } else if (locality != null) {
            return locality;
        } else if (adminArea != null) {
            return adminArea;
        }
        return address.getFeatureName() != null ? address.getFeatureName() : "";
    }

    private void showError(int messageResId) {
        if (isAdded()) {
            showSnackbar(messageResId);
        }
    }


    private void showSnackbar(String message) {
        View view = getView();
        if (view != null && isAdded()) {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showSnackbar(int messageResId) {
        View view = getView();
        if (view != null && isAdded()) {
            Snackbar.make(view, messageResId, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showPermissionDeniedDialog() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error_permission_denied_title)
                .setMessage(R.string.error_permission_denied)
                .setPositiveButton(R.string.submit, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void executeAsync(Runnable task) {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(task);
        }
    }

    private void runOnMainThread(Runnable action) {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
}