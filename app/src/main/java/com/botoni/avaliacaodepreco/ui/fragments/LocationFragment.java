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
import com.botoni.avaliacaodepreco.di.ResultListener;
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

public class LocationFragment extends Fragment implements OnMapReadyCallback, DirectionsListener {
    private static final int CAMERA_BOUNDS_PADDING = 150;
    private static final int BACKGROUND_THREAD_POOL_SIZE = 2;
    private static final float ROUTE_POLYLINE_WIDTH = 10f;
    private ExecutorService executor;
    private GoogleMap map;
    private Marker originMarker;
    private Marker destinationMarker;
    private Polyline polyline;
    private TextView textDistance;
    private TextView textDuration;
    private Address originAddress;
    private Address destinationAddress;

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener("originKey", this, (key, bundle) -> {
            originAddress = bundle.getParcelable("origin");
            checkAndRoute();
        });

        getParentFragmentManager().setFragmentResultListener("destinationKey", this, (key, bundle) -> {
            destinationAddress = bundle.getParcelable("destination");
            checkAndRoute();
        });

        initializeServices();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_fragment, container, false);
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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.map = googleMap;
        setupMap();
        checkAndRoute();
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

    private void bindViews(View view) {
        textDistance = view.findViewById(R.id.text_distance_summary);
        textDuration = view.findViewById(R.id.text_duration_summary);
        view.findViewById(R.id.button_edit).setOnClickListener(v -> onEditRoute());
        view.findViewById(R.id.button_finalizar).setOnClickListener(v -> onFinalizeRoute());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void setupComponents() {
        setupBottomSheet();
        setupMapFragment();
    }

    private void setupMapFragment() {
        SupportMapFragment fragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (fragment != null) {
            fragment.getMapAsync(this);
        }
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

    private void setupBottomSheet() {
        FrameLayout container = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        BottomSheetBehavior<FrameLayout> bottomSheet = BottomSheetBehavior.from(container);
        bottomSheet.setPeekHeight(650);
        bottomSheet.setHideable(false);
        bottomSheet.setDraggable(false);
        bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED);

        View layoutHalfExpanded = requireView().findViewById(R.id.layout_half_expanded);
        View layoutExpanded = requireView().findViewById(R.id.layout_expanded);

        layoutHalfExpanded.setVisibility(View.VISIBLE);
        layoutExpanded.setVisibility(View.GONE);
    }

    private void initializeServices() {
        executor = Executors.newFixedThreadPool(BACKGROUND_THREAD_POOL_SIZE);
    }


    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void checkAndRoute() {
        if (originAddress != null && destinationAddress != null && map != null) {
            route();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void route() {
        LatLng origin = new LatLng(originAddress.getLatitude(), originAddress.getLongitude());
        LatLng destination = new LatLng(destinationAddress.getLatitude(), destinationAddress.getLongitude());

        addOriginMarker(origin);
        addDestinationMarker(destination);

        directions(origin, destination, new ResultListener<>() {
            @Override
            public void onSuccess(Directions directions) {
                runOnUiThread(() -> {
                    drawPolyline(directions.getRoutePoints());
                    adjustCamera(origin, destination);
                    textDistance.setText(directions.getDistance());
                    textDuration.setText(directions.getDuration());
                });
            }

            @Override
            public void onError(int errorResId) {
                runOnUiThread(() -> showSnackBar(errorResId));
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void directions(LatLng origin, LatLng destination, ResultListener<Directions> resultListener) {
        executor.execute(() -> {
            try {
                String url = build(origin, destination, requireContext());
                String json = fetch(url);
                parse(json, resultListener);
            } catch (Exception e) {
                runOnUiThread(() -> resultListener.onError(R.string.error_network_directions));
            }
        });
    }

    private void drawPolyline(List<LatLng> points) {
        clearPolyline();
        polyline = map.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(ROUTE_POLYLINE_WIDTH)
                .color(0xFF2196F3)
                .geodesic(true));
    }

    private void adjustCamera(LatLng origin, LatLng destination) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(destination);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), CAMERA_BOUNDS_PADDING));
    }

    private void clearPolyline() {
        if (polyline != null) {
            polyline.remove();
            polyline = null;
        }
    }

    private void addOriginMarker(LatLng position) {
        if (map == null) return;
        removeOriginMarker();
        originMarker = map.addMarker(new MarkerOptions()
                .position(position)
                .title("Origem")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void addDestinationMarker(LatLng position) {
        if (map == null) return;
        removeDestinationMarker();
        destinationMarker = map.addMarker(new MarkerOptions()
                .position(position)
                .title("Destino")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
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

    private void onEditRoute() {
        getParentFragmentManager().popBackStack();
    }

    private void onFinalizeRoute() {
        showSnackBar();
    }

    private void runOnUiThread(Runnable action) {
        if (isAdded()) {
            requireActivity().runOnUiThread(action);
        }
    }

    private void showSnackBar(int messageResId) {
        Snackbar.make(requireView(), messageResId, Snackbar.LENGTH_SHORT).show();
    }

    private void showSnackBar() {
        Snackbar.make(requireView(), "Rota finalizada!", Snackbar.LENGTH_SHORT).show();
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}