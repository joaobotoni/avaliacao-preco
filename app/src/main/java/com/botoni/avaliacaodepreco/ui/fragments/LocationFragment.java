package com.botoni.avaliacaodepreco.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.di.Callback;
import com.botoni.avaliacaodepreco.domain.Directions;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
import com.botoni.avaliacaodepreco.ui.views.SearchWatcher;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LocationFragment extends Fragment implements OnMapReadyCallback {
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private static final String DEFAULT_DESTINATION_ADDRESS = "Cuiabá";
    private static final float DEFAULT_MAP_ZOOM_LEVEL = 15f;
    private static final int MAXIMUM_SEARCH_RESULTS = 10;
    private static final String DIRECTIONS_API_ENDPOINT = "https://maps.googleapis.com/maps/api/directions/json";
    private static final int CAMERA_BOUNDS_PADDING = 150;
    private static final int BACKGROUND_THREAD_POOL_SIZE = 4;
    private static final float ROUTE_POLYLINE_WIDTH = 10f;
    private GoogleMap googleMapInstance;
    private Geocoder geocoderService;
    private ExecutorService backgroundTaskExecutor;
    private FusedLocationProviderClient fusedLocationClient;
    private View layoutSearch;
    private View layoutRouteSummary;
    private TextView textDistance;
    private TextView textDuration;
    private TextInputEditText originAddressInputField;
    private TextInputEditText destinationAddressInputField;
    private TextInputLayout originAddressInputLayout;
    private RecyclerView searchResultsRecyclerView;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private LocationAdapter searchResultsAdapter;
    private final List<Address> searchResultAddresses = new ArrayList<>();
    private Marker originMarker;
    private Marker destinationMarker;
    private Polyline routePolyline;
    private Address selectedOriginAddress;
    private Address selectedDestinationAddress;
    private String userCountryCode;
    private String googleMapsApiKey;

    private final ActivityResultLauncher<String[]> locationPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handleLocationPermissionsResult);

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeBackgroundServices();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_fragment, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewReferences(view);
        configureAllComponents();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownBackgroundExecutor();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMapInstance = googleMap;
        configureMapAppearanceAndControls();
        loadDefaultDestinationAddress();
    }

    private void bindViewReferences(View root) {
        textDistance = root.findViewById(R.id.text_distance_summary);
        textDuration = root.findViewById(R.id.text_duration_summary);
        layoutSearch = root.findViewById(R.id.layout_half_expanded);
        layoutRouteSummary = root.findViewById(R.id.layout_expanded);
        originAddressInputField = root.findViewById(R.id.origem_input);
        destinationAddressInputField = root.findViewById(R.id.destino_input);
        originAddressInputLayout = root.findViewById(R.id.origem_input_layout);
        searchResultsRecyclerView = root.findViewById(R.id.localizacoes_recycler_view);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void configureAllComponents() {
        requestLocationPermissionsIfNeeded();
        configureOriginInputBehavior();
        configureSearchResultsRecyclerView();
        configureBottomSheet();
        attachMapFragment();
        requestCurrentDeviceLocation();
    }

    private void attachMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void configureOriginInputBehavior() {
        configureOriginFocusHandling();
        configureOriginTextWatcher();
        configureOriginClearIcon();
    }


    private void configureOriginFocusHandling() {
        originAddressInputField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                originAddressInputField.setCursorVisible(true);
                layoutSearch.setVisibility(View.GONE);
                layoutRouteSummary.setVisibility(View.VISIBLE);
            } else {
                originAddressInputField.setCursorVisible(false);
            }
        });
    }

    private void configureOriginTextWatcher() {
        originAddressInputField.addTextChangedListener(new SearchWatcher(this::executeAddressSearch));
    }

    @SuppressLint("PrivateResource")
    private void configureOriginClearIcon() {
        originAddressInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        originAddressInputLayout.setEndIconDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel);
        originAddressInputLayout.setEndIconVisible(false);
        originAddressInputLayout.setEndIconOnClickListener(v -> clearOriginSelection());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void configureSearchResultsRecyclerView() {
        searchResultsAdapter = new LocationAdapter(searchResultAddresses, this::handleAddressSelected);
        searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchResultsRecyclerView.setAdapter(searchResultsAdapter);
    }

    private void configureBottomSheet() {
        FrameLayout bottomSheetView = requireView().findViewById(R.id.localizacao_bottom_sheet_container);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
        bottomSheetBehavior.setPeekHeight(450);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    private void configureMapAppearanceAndControls() {
        applyCustomMapStyle();
        disableUnnecessaryMapControls();
    }

    private void applyCustomMapStyle() {
        try {
            googleMapInstance.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));
        } catch (Resources.NotFoundException ignored) {
        }
    }

    private void disableUnnecessaryMapControls() {
        googleMapInstance.getUiSettings().setZoomControlsEnabled(false);
        googleMapInstance.getUiSettings().setCompassEnabled(false);
        googleMapInstance.getUiSettings().setMyLocationButtonEnabled(false);
        googleMapInstance.getUiSettings().setMapToolbarEnabled(false);
    }

    private void loadDefaultDestinationAddress() {
        destinationAddressInputField.setHint(DEFAULT_DESTINATION_ADDRESS);
        destinationAddressInputField.setEnabled(false);
        destinationAddressInputField.setCursorVisible(false);

        searchAddresses(DEFAULT_DESTINATION_ADDRESS, null, new Callback<>() {
            @Override
            public void onSuccess(List<Address> results) {
                if (!results.isEmpty() && isAdded()) {
                    selectedDestinationAddress = results.get(0);
                    LatLng position = new LatLng(selectedDestinationAddress.getLatitude(),
                            selectedDestinationAddress.getLongitude());
                    googleMapInstance.moveCamera(CameraUpdateFactory.newLatLngZoom(position, DEFAULT_MAP_ZOOM_LEVEL));
                    placeDestinationMarker(selectedDestinationAddress);
                }
            }

            @Override
            public void onError(int errorResId) {
            }
        });
    }

    private void placeOriginMarker(Address address) {
        if (googleMapInstance == null) return;
        removeOriginMarker();
        LatLng position = new LatLng(address.getLatitude(), address.getLongitude());
        originMarker = googleMapInstance.addMarker(new MarkerOptions()
                .position(position)
                .title(getString(R.string.origin_title))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void placeDestinationMarker(Address address) {
        if (googleMapInstance == null) return;
        removeDestinationMarker();
        LatLng position = new LatLng(address.getLatitude(), address.getLongitude());
        destinationMarker = googleMapInstance.addMarker(new MarkerOptions()
                .position(position)
                .title(getString(R.string.destination_title))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void handleAddressSelected(Address address) {
        selectedOriginAddress = address;
        originAddressInputField.setText("");
        originAddressInputField.setHint(address.getAddressLine(0));
        originAddressInputField.setEnabled(false);
        originAddressInputField.setFocusable(false);
        originAddressInputField.setFocusableInTouchMode(false);
        originAddressInputField.setCursorVisible(false);
        originAddressInputLayout.setEndIconVisible(true);
        originAddressInputField.clearFocus();

        layoutSearch.setVisibility(View.VISIBLE);
        layoutRouteSummary.setVisibility(View.GONE);

        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        clearRoutePolyline();
        placeOriginMarker(address);
        requestDirections();
    }

    private void clearOriginSelection() {
        selectedOriginAddress = null;
        originAddressInputField.setText("");
        originAddressInputField.setHint(R.string.label_endereco_origem);
        originAddressInputField.setEnabled(true);
        originAddressInputField.setFocusable(true);
        originAddressInputField.setFocusableInTouchMode(true);
        originAddressInputLayout.setEndIconVisible(false);

        layoutSearch.setVisibility(View.GONE);
        layoutRouteSummary.setVisibility(View.VISIBLE);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);

        clearRoutePolyline();
        removeOriginMarker();
        if (selectedDestinationAddress != null && googleMapInstance != null) {
            LatLng dest = new LatLng(selectedDestinationAddress.getLatitude(),
                    selectedDestinationAddress.getLongitude());
            googleMapInstance.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, DEFAULT_MAP_ZOOM_LEVEL));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void requestDirections() {
        if (selectedOriginAddress == null || selectedDestinationAddress == null) return;

        LatLng origin = new LatLng(selectedOriginAddress.getLatitude(), selectedOriginAddress.getLongitude());
        LatLng destination = new LatLng(selectedDestinationAddress.getLatitude(), selectedDestinationAddress.getLongitude());

        fetchDirectionsFromGoogle(origin, destination, new Callback<>() {
            @Override
            public void onSuccess(Directions directions) {
                drawRoutePolyline(directions.getRoutePoints());
                adjustCameraToShowBothPoints(origin, destination);
                textDistance.setText(directions.getDistance());
                textDuration.setText(directions.getDuration());

                runOnUiThread(() -> {
                    bottomSheetBehavior.setPeekHeight(650);
                    bottomSheetBehavior.setHideable(false);
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                });
            }

            @Override
            public void onError(int errorResId) {
                showSnackBarMessage(errorResId);
            }
        });
    }

    private void drawRoutePolyline(List<LatLng> points) {
        clearRoutePolyline();
        routePolyline = googleMapInstance.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(ROUTE_POLYLINE_WIDTH)
                .color(ContextCompat.getColor(requireContext(), R.color.route_color))
                .geodesic(true));
    }

    private void adjustCameraToShowBothPoints(LatLng origin, LatLng destination) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(destination);
        googleMapInstance.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), CAMERA_BOUNDS_PADDING));
    }

    private void clearRoutePolyline() {
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
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

    @SuppressLint("NotifyDataSetChanged")
    private void updateSearchResultsList(List<Address> addresses) {
        searchResultAddresses.clear();
        searchResultAddresses.addAll(addresses);
        searchResultsAdapter.notifyDataSetChanged();
    }

    private void initializeBackgroundServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoderService = new Geocoder(requireContext(), new Locale("pt", "BR"));
        backgroundTaskExecutor = Executors.newFixedThreadPool(BACKGROUND_THREAD_POOL_SIZE);
        loadGoogleMapsApiKeyFromManifest();
    }

    private void loadGoogleMapsApiKeyFromManifest() {
        try {
            ApplicationInfo appInfo = requireContext().getPackageManager()
                    .getApplicationInfo(requireContext().getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                googleMapsApiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private void requestLocationPermissionsIfNeeded() {
        if (areLocationPermissionsMissing()) {
            locationPermissionsLauncher.launch(LOCATION_PERMISSIONS);
        }
    }

    private boolean areLocationPermissionsMissing() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED;
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void handleLocationPermissionsResult(Map<String, Boolean> results) {
        boolean anyGranted = Boolean.TRUE.equals(results.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) ||
                Boolean.TRUE.equals(results.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
        if (anyGranted) {
            requestCurrentDeviceLocation();
        } else {
            showPermissionDeniedDialog();
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    private void requestCurrentDeviceLocation() {
        if (areLocationPermissionsMissing()) return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        determineCountryCodeFromLocation(location);
                    }
                });
    }

    private void determineCountryCodeFromLocation(Location location) {
        retrieveAddressesFromCoordinates(location.getLatitude(), location.getLongitude(), new Callback<>() {
            @Override
            public void onSuccess(List<Address> results) {
                if (!results.isEmpty()) {
                    userCountryCode = results.get(0).getCountryCode();
                }
            }

            @Override
            public void onError(int errorResId) {
            }
        });
    }

    private void executeAddressSearch(String query) {
        originAddressInputLayout.setError(null);
        searchAddresses(query, userCountryCode, new Callback<>() {
            @Override
            public void onSuccess(List<Address> addresses) {
                updateSearchResultsList(addresses);
            }

            @Override
            public void onError(int errorResId) {
                updateSearchResultsList(Collections.emptyList());
            }
        });
    }

    private void searchAddresses(String query, String countryCode, Callback<List<Address>> callback) {
        backgroundTaskExecutor.execute(() -> {
            try {
                List<Address> results = geocoderService.getFromLocationName(query, MAXIMUM_SEARCH_RESULTS);
                if (results == null) results = Collections.emptyList();
                List<Address> filtered = filterAddressesByCountry(results, countryCode);
                runOnUiThread(() -> callback.onSuccess(filtered));
            } catch (IOException e) {
                runOnUiThread(() -> callback.onError(R.string.error_locations));
            }
        });
    }

    private void retrieveAddressesFromCoordinates(double latitude, double longitude, Callback<List<Address>> callback) {
        backgroundTaskExecutor.execute(() -> {
            try {
                List<Address> results = geocoderService.getFromLocation(latitude, longitude, 1);
                if (results == null) results = Collections.emptyList();
                List<Address> finalResults = results;
                runOnUiThread(() -> callback.onSuccess(finalResults));
            } catch (IOException e) {
                runOnUiThread(() -> callback.onError(R.string.error_locations));
            }
        });
    }

    private List<Address> filterAddressesByCountry(List<Address> addresses, String countryCode) {
        if (countryCode == null || addresses.isEmpty()) return addresses;
        return addresses.stream()
                .filter(a -> countryCode.equals(a.getCountryCode()))
                .collect(Collectors.toList());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void fetchDirectionsFromGoogle(LatLng origin, LatLng destination, Callback<Directions> callback) {
        backgroundTaskExecutor.execute(() -> {
            try {
                String url = buildDirectionsRequestUrl(origin, destination);
                String json = downloadJsonFromUrl(url);
                parseDirectionsJson(json, callback);
            } catch (Exception e) {
                runOnUiThread(() -> callback.onError(R.string.error_network_directions));
            }
        });
    }

    private String buildDirectionsRequestUrl(LatLng origin, LatLng destination) {
        String originStr = origin.latitude + "," + origin.longitude;
        String destStr = destination.latitude + "," + destination.longitude;
        String params = String.format(Locale.US, "origin=%s&destination=%s&key=%s",
                URLEncoder.encode(originStr, StandardCharsets.UTF_8),
                URLEncoder.encode(destStr, StandardCharsets.UTF_8),
                googleMapsApiKey);
        return DIRECTIONS_API_ENDPOINT + "?" + params;
    }

    private String downloadJsonFromUrl(String urlString) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            if (reader != null) reader.close();
            if (conn != null) conn.disconnect();
        }
    }

    private void parseDirectionsJson(String json, Callback<Directions> callback) {
        try {
            JSONObject root = new JSONObject(json);
            String status = root.getString("status");
            if (!"OK".equals(status)) {
                runOnUiThread(() -> callback.onError("ZERO_RESULTS".equals(status) ?
                        R.string.error_no_route : R.string.error_unknown_directions));
                return;
            }
            JSONArray routes = root.getJSONArray("routes");
            JSONObject leg = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0);

            String distance = leg.getJSONObject("distance").getString("text");
            String duration = leg.getJSONObject("duration").getString("text");
            String encodedPolyline = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points");

            List<LatLng> points = decodePolyline(encodedPolyline);
            Directions directions = new Directions(points, distance, duration);
            runOnUiThread(() -> callback.onSuccess(directions));
        } catch (Exception e) {
            runOnUiThread(() -> callback.onError(R.string.error_json_directions));
        }
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> points = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            points.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return points;
    }

    private void runOnUiThread(Runnable action) {
        requireActivity().runOnUiThread(action);
    }

    private void shutdownBackgroundExecutor() {
        if (backgroundTaskExecutor != null && !backgroundTaskExecutor.isShutdown()) {
            backgroundTaskExecutor.shutdown();
        }
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error_permission_denied_title)
                .setMessage(R.string.error_permission_denied)
                .setPositiveButton(R.string.submit, (d, w) -> d.dismiss())
                .show();
    }

    private void showSnackBarMessage(int resId) {
        Snackbar.make(requireView(), resId, Snackbar.LENGTH_SHORT).show();
    }
}