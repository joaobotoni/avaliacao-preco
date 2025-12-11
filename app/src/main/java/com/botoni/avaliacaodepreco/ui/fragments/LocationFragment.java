package com.botoni.avaliacaodepreco.ui.fragments;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Address;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.DirectionsProvider;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class LocationFragment extends Fragment implements OnMapReadyCallback, DirectionsProvider {
    private static final int CAMERA_PADDING = 150;
    private static final int THREAD_POOL_SIZE = 2;
    private static final float POLYLINE_WIDTH = 10f;
    private static final int POLYLINE_COLOR = 0xFF2196F3;
    private static final int BOTTOM_SHEET_PEEK_HEIGHT = 600;
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_UP_ORIGIN = "updateOrigin";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private static final String RESULT_KEY_UP_ORIGIN = "updateOriginKey";
    private static final String MARKER_TITLE_ORIGIN = "Origem";
    private static final String MARKER_TITLE_DESTINATION = "Destino";
    private static final String STATE_ORIGIN_ADDRESS = "originNew";
    private static final String STATE_DESTINATION_ADDRESS = "destinationNew";
    private static final String ARG_ORIGIN = "arg_origin";
    private static final String ARG_DESTINATION = "arg_destination";

    private Address originAddress;
    private Address destinationAddress;
    private ExecutorService executorService;
    private GoogleMap map;
    private Marker originMarker;
    private Marker destinationMarker;
    private Polyline routePolyline;
    private View cardInfo;
    private TextView originText;
    private TextView destinationText;
    private TextView distanceText;
    private TextView durationText;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;

    public static LocationFragment newInstance(Address origin, Address destination) {
        LocationFragment fragment = new LocationFragment();
        Bundle args = new Bundle();
        if (origin != null) {
            args.putParcelable(ARG_ORIGIN, origin);
        }
        if (destination != null) {
            args.putParcelable(ARG_DESTINATION, destination);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDependencies();
        loadArguments();
        setupFragmentResultListeners();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupUI();
        restoreState(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Limpar referências para evitar memory leaks
        if (originMarker != null) {
            originMarker.remove();
            originMarker = null;
        }
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
        map = null;
        cardInfo = null;
        originText = null;
        destinationText = null;
        distanceText = null;
        durationText = null;
        bottomSheetBehavior = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (!isAdded()) return;

        this.map = googleMap;
        setupMap();
        attemptRouteCalculation();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (originAddress != null) {
            outState.putParcelable(STATE_ORIGIN_ADDRESS, originAddress);
        }
        if (destinationAddress != null) {
            outState.putParcelable(STATE_DESTINATION_ADDRESS, destinationAddress);
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
    }

    private void loadArguments() {
        Bundle args = getArguments();
        if (args != null) {
            // Compatibilidade com todas as versões do Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                originAddress = args.getParcelable(ARG_ORIGIN, Address.class);
                destinationAddress = args.getParcelable(ARG_DESTINATION, Address.class);
            } else {
                originAddress = args.getParcelable(ARG_ORIGIN);
                destinationAddress = args.getParcelable(ARG_DESTINATION);
            }
        }
    }

    private void setupFragmentResultListeners() {
        setupOriginListener();
        setupDestinationListener();
    }

    private void setupOriginListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_ORIGIN, this,
                (key, bundle) -> {
                    if (!isAdded()) return;

                    Address address = getAddressFromBundle(bundle, KEY_ORIGIN);
                    if (address != null) {
                        updateOriginAddress(address);
                        attemptRouteCalculation();
                    }
                }
        );
    }

    private void setupDestinationListener() {
        getParentFragmentManager().setFragmentResultListener(RESULT_KEY_DESTINATION, this,
                (key, bundle) -> {
                    if (!isAdded()) return;

                    Address address = getAddressFromBundle(bundle, KEY_DESTINATION);
                    if (address != null) {
                        updateDestinationAddress(address);
                        attemptRouteCalculation();
                    }
                }
        );
    }

    private Address getAddressFromBundle(Bundle bundle, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelable(key, Address.class);
        } else {
            return bundle.getParcelable(key);
        }
    }

    private void initializeViews(View root) {
        cardInfo = root.findViewById(R.id.origem_destino_layout);
        originText = root.findViewById(R.id.text_origin);
        destinationText = root.findViewById(R.id.text_destination);
        distanceText = root.findViewById(R.id.text_distance_summary);
        durationText = root.findViewById(R.id.text_duration_summary);

        root.findViewById(R.id.button_edit).setOnClickListener(v -> navigateBackFromEdit());
        root.findViewById(R.id.button_finalizar).setOnClickListener(v -> navigateBack());
    }

    private void setupUI() {
        setupBottomSheet();
        setupMapFragment();
        hideCardInfo();
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Address savedOrigin = getAddressFromBundle(savedInstanceState, STATE_ORIGIN_ADDRESS);
            Address savedDestination = getAddressFromBundle(savedInstanceState, STATE_DESTINATION_ADDRESS);

            if (savedOrigin != null) {
                originAddress = savedOrigin;
            }
            if (savedDestination != null) {
                destinationAddress = savedDestination;
            }
        }

        if (hasOrigin() && hasDestination()) {
            updateCardInfo();
        }
    }

    private void setupBottomSheet() {
        View view = getView();
        if (view == null) return;

        FrameLayout container = view.findViewById(R.id.localizacao_bottom_sheet_container);
        if (container == null) return;

        bottomSheetBehavior = BottomSheetBehavior.from(container);
        configureBottomSheetBehavior();
        configureBottomSheetLayouts();
    }

    private void configureBottomSheetBehavior() {
        if (bottomSheetBehavior == null) return;

        bottomSheetBehavior.setPeekHeight(BOTTOM_SHEET_PEEK_HEIGHT);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setDraggable(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void configureBottomSheetLayouts() {
        View view = getView();
        if (view == null) return;

        View halfExpandedLayout = view.findViewById(R.id.layout_half_expanded);
        View expandedLayout = view.findViewById(R.id.layout_expanded);

        setViewVisibility(halfExpandedLayout, View.VISIBLE);
        setViewVisibility(expandedLayout, View.GONE);
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = findMapFragment();
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Nullable
    private SupportMapFragment findMapFragment() {
        return (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
    }

    private void setupMap() {
        if (map == null || !isAdded()) return;

        applyMapStyle();
        configureMapUI();
    }

    private void applyMapStyle() {
        if (map == null || !isAdded()) return;

        try {
            boolean isDarkMode = isDarkModeEnabled();
            int styleRes = isDarkMode ? R.raw.map_style_dark : R.raw.map_style_light;
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), styleRes));
        } catch (Resources.NotFoundException | IllegalStateException ignored) {
        }
    }

    private boolean isDarkModeEnabled() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureMapUI() {
        if (map == null) return;

        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
    }

    private void attemptRouteCalculation() {
        if (canCalculateRoute()) {
            calculateRoute();
        }
    }

    private boolean canCalculateRoute() {
        return hasOrigin() && hasDestination() && hasMap();
    }

    private boolean hasOrigin() {
        return originAddress != null;
    }

    private boolean hasDestination() {
        return destinationAddress != null;
    }

    private boolean hasMap() {
        return map != null;
    }

    private void calculateRoute() {
        if (!isAdded() || !canCalculateRoute()) return;

        LatLng origin = extractCoordinates(originAddress);
        LatLng destination = extractCoordinates(destinationAddress);

        placeOriginMarker(origin);
        placeDestinationMarker(destination);

        fetchDirections(origin, destination,
                this::handleDirectionsSuccess,
                this::handleDirectionsError
        );
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
                        onError.accept(R.string.erro_rede_rotas);
                    }
                });
            }
        });
    }

    private void handleDirectionsSuccess(Directions directions) {
        runOnMainThread(() -> {
            if (!isAdded() || map == null) return;

            drawRoute(directions.getRoutePoints());
            adjustCameraToRoute(extractCoordinates(originAddress), extractCoordinates(destinationAddress));
            updateRouteInfo(directions);
            updateCardInfo();
            showBottomSheet();
        });
    }

    private void handleDirectionsError(Integer errorResId) {
        runOnMainThread(() -> {
            if (isAdded()) {
                showError(errorResId);
            }
        });
    }

    private void drawRoute(List<LatLng> points) {
        if (map == null) return;

        removePolyline();
        createPolyline(points);
    }

    private void createPolyline(List<LatLng> points) {
        if (map == null) return;

        routePolyline = map.addPolyline(
                new PolylineOptions()
                        .addAll(points)
                        .width(POLYLINE_WIDTH)
                        .color(POLYLINE_COLOR)
                        .geodesic(true)
        );
    }

    private void adjustCameraToRoute(LatLng origin, LatLng destination) {
        if (map == null) return;

        LatLngBounds bounds = buildRouteBounds(origin, destination);
        animateCamera(bounds);
    }

    private LatLngBounds buildRouteBounds(LatLng origin, LatLng destination) {
        return new LatLngBounds.Builder()
                .include(origin)
                .include(destination)
                .build();
    }

    private void animateCamera(LatLngBounds bounds) {
        if (map == null) return;

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, CAMERA_PADDING));
    }

    private void placeOriginMarker(LatLng position) {
        if (!hasMap()) return;
        removeOriginMarker();
        createOriginMarker(position);
    }

    private void placeDestinationMarker(LatLng position) {
        if (!hasMap()) return;
        removeDestinationMarker();
        createDestinationMarker(position);
    }

    private void createOriginMarker(LatLng position) {
        if (map == null) return;
        originMarker = map.addMarker(createMarkerOptions(position, MARKER_TITLE_ORIGIN));
    }

    private void createDestinationMarker(LatLng position) {
        if (map == null) return;
        destinationMarker = map.addMarker(createMarkerOptions(position, MARKER_TITLE_DESTINATION));
    }

    private MarkerOptions createMarkerOptions(LatLng position, String title) {
        return new MarkerOptions()
                .position(position)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
    }

    private void removeOriginMarker() {
        if (originMarker != null) {
            originMarker.remove();
            originMarker = null;
        }
    }

    private void removeDestinationMarker() {
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }
    }

    private void removePolyline() {
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }

    private void updateOriginAddress(Address address) {
        this.originAddress = address;
    }

    private void updateDestinationAddress(Address address) {
        this.destinationAddress = address;
    }

    private void updateRouteInfo(Directions directions) {
        setDistanceText(directions.getDistance());
        setDurationText(directions.getDuration());
    }

    private void updateCardInfo() {
        if (hasOrigin() && hasDestination()) {
            setOriginText(formatAddressText(originAddress));
            setDestinationText(formatAddressText(destinationAddress));
            showCardInfo();
        }
    }

    private String formatAddressText(Address address) {
        return String.format("%s, %s", address.getLocality(), address.getAdminArea());
    }

    private void setOriginText(String text) {
        if (originText != null) {
            originText.setText(text);
        }
    }

    private void setDestinationText(String text) {
        if (destinationText != null) {
            destinationText.setText(text);
        }
    }

    private void setDistanceText(String text) {
        if (distanceText != null) {
            distanceText.setText(text);
        }
    }

    private void setDurationText(String text) {
        if (durationText != null) {
            durationText.setText(text);
        }
    }

    private void showCardInfo() {
        setCardInfoVisibility(View.VISIBLE);
    }

    private void hideCardInfo() {
        setCardInfoVisibility(View.GONE);
    }

    private void setCardInfoVisibility(int visibility) {
        if (cardInfo != null) {
            cardInfo.setVisibility(visibility);
        }
    }

    private void setViewVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void showBottomSheet() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void navigateBack() {
        if (isAdded()) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void navigateBackFromEdit() {
        if (!isAdded()) return;

        Bundle result = new Bundle();
        result.putBoolean(KEY_UP_ORIGIN, true);
        getParentFragmentManager().setFragmentResult(RESULT_KEY_UP_ORIGIN, result);

        getParentFragmentManager().popBackStack();
    }

    private void showError(int messageResId) {
        View view = getView();
        if (view != null && isAdded()) {
            Snackbar.make(view, messageResId, Snackbar.LENGTH_SHORT).show();
        }
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
        }
    }
}