package com.botoni.avaliacaodepreco.ui.fragments;

import android.content.Context;
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
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.DirectionsListener;
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

public class LocationFragment extends Fragment implements OnMapReadyCallback, DirectionsListener {
    private static final int CAMERA_PADDING = 150;
    private static final int THREAD_POOL_SIZE = 2;
    private static final float POLYLINE_WIDTH = 10f;
    private static final int POLYLINE_COLOR = 0xFF2196F3;
    private static final int BOTTOM_SHEET_PEEK_HEIGHT = 650;
    private static final String KEY_ORIGIN = "origin";
    private static final String KEY_DESTINATION = "destination";
    private static final String RESULT_KEY_ORIGIN = "originKey";
    private static final String RESULT_KEY_DESTINATION = "destinationKey";
    private static final String MARKER_TITLE_ORIGIN = "Origem";
    private static final String MARKER_TITLE_DESTINATION = "Destino";
    private Address originAddress;
    private Address destinationAddress;
    private ExecutorService executorService;
    private GoogleMap map;
    private Marker originMarker;
    private Marker destinationMarker;
    private Polyline routePolyline;
    private TextView distanceText;
    private TextView durationText;

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerFragmentListeners();
        initializeDependencies();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_fragment, container, false);
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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.map = googleMap;
        setupMap();
        attemptRouteCalculation();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void registerFragmentListeners() {
        registerOriginListener();
        registerDestinationListener();
    }
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void registerOriginListener() {
        getParentFragmentManager().setFragmentResultListener(
                RESULT_KEY_ORIGIN,
                this,
                (key, bundle) -> {
                    updateOriginAddress(bundle.getParcelable(KEY_ORIGIN));
                    attemptRouteCalculation();
                }
        );
    }
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void registerDestinationListener() {
        getParentFragmentManager().setFragmentResultListener(
                RESULT_KEY_DESTINATION,
                this,
                (key, bundle) -> {
                    updateDestinationAddress(bundle.getParcelable(KEY_DESTINATION));
                    attemptRouteCalculation();
                }
        );
    }

    private void initializeDependencies() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    private void initializeViews(View root) {
        distanceText = root.findViewById(R.id.text_distance_summary);
        durationText = root.findViewById(R.id.text_duration_summary);

        root.findViewById(R.id.button_edit).setOnClickListener(v -> handleEditAction());
        root.findViewById(R.id.button_finalizar).setOnClickListener(v -> handleFinalizeAction());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupUI() {
        setupBottomSheet();
        setupMapFragment();
    }

    private void setupBottomSheet() {
        FrameLayout container = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(container);

        configureBottomSheetBehavior(behavior);
        configureBottomSheetLayouts();
    }

    private void configureBottomSheetBehavior(BottomSheetBehavior<FrameLayout> behavior) {
        behavior.setPeekHeight(BOTTOM_SHEET_PEEK_HEIGHT);
        behavior.setHideable(true);
        behavior.setDraggable(false);
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void configureBottomSheetLayouts() {
        View halfExpandedLayout = requireView().findViewById(R.id.layout_half_expanded);
        View expandedLayout = requireView().findViewById(R.id.layout_expanded);

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
        applyMapStyle();
        configureMapUI();
    }

    private void applyMapStyle() {
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));
        } catch (Resources.NotFoundException ignored) {}
    }

    private void configureMapUI() {
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void calculateRoute() {
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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void fetchDirections(LatLng origin, LatLng destination, Consumer<Directions> onSuccess, Consumer<Integer> onError) {
        executeAsync(() -> {
            try {
                String url = buildDirectionsUrl(origin, destination);
                String json = fetchDirectionsJson(url);
                parseDirections(json, onSuccess, onError);
            } catch (Exception e) {
                runOnMainThread(() -> onError.accept(R.string.error_network_directions));
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private String buildDirectionsUrl(LatLng origin, LatLng destination) {
        return build(origin, destination, requireContext());
    }

    private String fetchDirectionsJson(String url) throws IOException {
        return fetch(url);
    }

    private void parseDirections(String json, Consumer<Directions> onSuccess, Consumer<Integer> onError) {
        parse(json, onSuccess, onError);
    }

    private void handleDirectionsSuccess(Directions directions) {
        runOnMainThread(() -> {
            drawRoute(directions.getRoutePoints());
            adjustCameraToRoute(extractCoordinates(originAddress), extractCoordinates(destinationAddress));
            displayRouteInfo(directions);
            showBottomSheet(); // Mostrar o BottomSheet após calcular a rota
        });
    }

    private void handleDirectionsError(Integer errorResId) {
        runOnMainThread(() -> showError(errorResId));
    }

    private void drawRoute(List<LatLng> points) {
        removePolyline();
        createPolyline(points);
    }

    private void createPolyline(List<LatLng> points) {
        routePolyline = map.addPolyline(
                new PolylineOptions()
                        .addAll(points)
                        .width(POLYLINE_WIDTH)
                        .color(POLYLINE_COLOR)
                        .geodesic(true)
        );
    }

    private void adjustCameraToRoute(LatLng origin, LatLng destination) {
        LatLngBounds bounds = buildRouteBounds(origin, destination);
        animateCamera(bounds);
    }

    private LatLngBounds buildRouteBounds(LatLng origin, LatLng destination) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(destination);
        return builder.build();
    }

    private void animateCamera(LatLngBounds bounds) {
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
        originMarker = map.addMarker(
                createMarkerOptions(position, MARKER_TITLE_ORIGIN)
        );
    }

    private void createDestinationMarker(LatLng position) {
        destinationMarker = map.addMarker(
                createMarkerOptions(position, MARKER_TITLE_DESTINATION)
        );
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

    private void displayRouteInfo(Directions directions) {
        setDistanceText(directions.getDistance());
        setDurationText(directions.getDuration());
    }

    private void setDistanceText(String distance) {
        distanceText.setText(distance);
    }

    private void setDurationText(String duration) {
        durationText.setText(duration);
    }

    private void setViewVisibility(View view, int visibility) {
        view.setVisibility(visibility);
    }

    private void showBottomSheet() {
        FrameLayout container = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(container);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void handleEditAction() {
        navigateBack();
    }

    private void handleFinalizeAction() {
        navigateBack();
    }

    private void navigateBack() {
        getParentFragmentManager().popBackStack();
    }

    private void showError(int messageResId) {
        showSnackbar(messageResId);
    }


    private void showSnackbar(int messageResId) {
        Snackbar.make(requireView(), messageResId, Snackbar.LENGTH_SHORT).show();
    }

    private void executeAsync(Runnable task) {
        executorService.execute(task);
    }

    private void runOnMainThread(Runnable action) {
        if (isAdded()) {
            requireActivity().runOnUiThread(action);
        }
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