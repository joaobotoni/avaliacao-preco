package com.botoni.avaliacaodepreco.ui;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.botoni.avaliacaodepreco.R;
import com.botoni.avaliacaodepreco.ui.adapter.LocationAdapter;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

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

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private static final String DEFAULT_DESTINATION = "Cuiabá";
    private static final float DEFAULT_ZOOM = 15f;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int GEOCODER_MAX_RESULTS = 10;
    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private GoogleMap mMap;
    private Geocoder geocoder;
    private ExecutorService executor;
    private FusedLocationProviderClient locationClient;
    private TextInputEditText origin;
    private TextInputEditText destination;
    private TextInputLayout inputLayoutOrigin;
    private RecyclerView recyclerView;
    private FloatingActionButton floatingActionButton;
    private BottomSheetBehavior<FrameLayout> bottomSheet;
    private LocationAdapter adapter;
    private final List<Address> addresses = new ArrayList<>();
    private Marker originMarker;
    private Marker destinationMarker;
    private String countryCode;
    private Polyline currentPolyline;
    private Address selectedOrigin;
    private Address selectedDestination;
    private String apiKey;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionsResult
            );

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeServices();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupAllComponents();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownExecutor();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.mMap = map;
        configureMap();
        setupDestinationLocation(DEFAULT_DESTINATION);
    }

    private void initializeServices() {
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoder = new Geocoder(requireContext(), new Locale("pt", "BR"));
        executor = Executors.newFixedThreadPool(4);
        loadApiKey();
    }

    private void loadApiKey() {
        try {
            ApplicationInfo appInfo = requireContext().getPackageManager()
                    .getApplicationInfo(requireContext().getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            showSnackBar(R.string.error_api_key);
        }
    }

    private void initializeViews(View view) {
        origin = view.findViewById(R.id.origem_input);
        destination = view.findViewById(R.id.destino_input);
        inputLayoutOrigin = view.findViewById(R.id.origem_input_layout);
        recyclerView = view.findViewById(R.id.localizacoes_recycler_view);
        floatingActionButton = view.findViewById(R.id.fabLocation);
    }

    private void setupAllComponents() {
        requestPermissionsIfNeeded();
        setupInputComponents();
        setupRecyclerView();
        setupBottomSheet(requireView().findViewById(R.id.localizacao_bottom_sheet_container));
        setupMapFragment();
        fetchLastLocation();
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void configureMap() {
        applyMapStyle();
        configureMapUI();
    }

    private void applyMapStyle() {
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));
        } catch (Resources.NotFoundException e) {
            showSnackBar(R.string.error_maps);
        }
    }

    private void configureMapUI() {
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
    }

    private void setupDestinationLocation(String query) {
        destination.setHint(query);
        destination.setEnabled(false);
        destination.setCursorVisible(false);

        executor.execute(() -> {
            List<Address> result = fetchLocationFromQuery(query);
            if (result != null && !result.isEmpty()) {
                selectedDestination = result.get(0);
                LatLng location = new LatLng(selectedDestination.getLatitude(), selectedDestination.getLongitude());
                runOnUiThread(() -> {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM));
                addDestinationMarker(selectedDestination);
                });
            }
        });
    }

    private void addOriginMarker(Address address) {
        if (mMap == null) return;

        if (originMarker != null) {
            originMarker.remove();
        }

        LatLng position = new LatLng(address.getLatitude(), address.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title("Origem")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        originMarker = mMap.addMarker(markerOptions);
    }


    private void addDestinationMarker(Address address) {
        if (mMap == null) return;

        if (destinationMarker != null) {
            destinationMarker.remove();
        }
        LatLng position = new LatLng(address.getLatitude(), address.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .position(position)
                .title("Destino")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        destinationMarker = mMap.addMarker(markerOptions);
    }
        private void setupInputComponents() {
        setupOriginFocusListener();
        setupOriginTextWatcher();
    }

    private void setupOriginFocusListener() {
        origin.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
    }

    private void setupOriginTextWatcher() {
        origin.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > MIN_SEARCH_LENGTH) {
                    searchLocation(s.toString());
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new LocationAdapter(addresses, this::handleLocationSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupBottomSheet(FrameLayout sheet) {
        bottomSheet = BottomSheetBehavior.from(sheet);
        configureBottomSheetBehavior();
        setupBottomSheetCallback();
        setupFloatingActionButton();
    }

    private void configureBottomSheetBehavior() {
        bottomSheet.setHideable(true);
        bottomSheet.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        updateFabVisibility(bottomSheet.getState());
    }

    private void setupBottomSheetCallback() {
        bottomSheet.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int state) {
                updateFabVisibility(state);
            }

            @Override
            public void onSlide(@NonNull View sheet, float offset) {
                if (offset > 0) {
                    floatingActionButton.setAlpha(1 - offset);
                }
            }
        });
    }

    private void setupFloatingActionButton() {
        floatingActionButton.setOnClickListener(v ->
                bottomSheet.setState(BottomSheetBehavior.STATE_HALF_EXPANDED));
    }

    private void updateFabVisibility(int state) {
        if (state == BottomSheetBehavior.STATE_HALF_EXPANDED ||
                state == BottomSheetBehavior.STATE_EXPANDED) {
            floatingActionButton.hide();
        } else if (state == BottomSheetBehavior.STATE_HIDDEN) {
            floatingActionButton.show();
        }
    }

    private void requestPermissionsIfNeeded() {
        if (needsPermissions()) {
            permissionLauncher.launch(PERMISSIONS);
        }
    }

    private boolean needsPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    private void handlePermissionsResult(Map<String, Boolean> result) {
        boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION)) &&
                Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

        if (!granted) {
            showPermissionDeniedDialog();
        }
    }

    private void handleLocationSelected(Address address) {
        selectedOrigin = address;
        updateOriginInput(address);
        configureOriginAsReadOnly();
        setupClearButton();
        hideBottomSheet();
        addOriginMarker(address);
        if (selectedDestination != null) {
            drawRoute(selectedOrigin, selectedDestination);
        }
    }

    private void updateOriginInput(Address address) {
        String formatAddress = String.format("%s - %s", address.getLocality(), address.getAdminArea());
        origin.setText(formatAddress);
    }

    private void configureOriginAsReadOnly() {
        origin.setEnabled(false);
        origin.setCursorVisible(false);
    }

    private void setupClearButton() {
        inputLayoutOrigin.setEndIconDrawable(
                getResources().getDrawable(com.google.android.material.R.drawable.mtrl_ic_cancel, null));
        inputLayoutOrigin.setEndIconVisible(true);
        inputLayoutOrigin.setEndIconOnClickListener(v -> clearSelection());
    }

    private void hideBottomSheet() {
        if (origin.getText() != null && !origin.getText().toString().trim().isEmpty()) {
            bottomSheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void clearSelection() {
        origin.setText("");
        origin.setEnabled(true);
        origin.setCursorVisible(true);
        inputLayoutOrigin.setEndIconMode(TextInputLayout.END_ICON_NONE);
        origin.requestFocus();
        selectedOrigin = null;
        clearRoute();
    }

    private void drawRoute(Address origin, Address destination) {
        LatLng originLatLng = new LatLng(origin.getLatitude(), origin.getLongitude());
        LatLng destLatLng = new LatLng(destination.getLatitude(), destination.getLongitude());

        executor.execute(() -> {
            String jsonResponse = getDirectionsJson(originLatLng, destLatLng);
            if (jsonResponse != null) {
                List<LatLng> points = parseDirectionsJson(jsonResponse);
                runOnUiThread(() -> {
                    if (!points.isEmpty()) {
                        drawPolyline(points);
                        animateCameraToRoute(originLatLng, destLatLng);
                    } else {
                        showSnackBar(R.string.error_route);
                    }
                });
            } else {
                runOnUiThread(() -> showSnackBar(R.string.error_route));
            }
        });
    }

    private String getDirectionsJson(LatLng origin, LatLng destination) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        try {
            String originParam = origin.latitude + "," + origin.longitude;
            String destParam = destination.latitude + "," + destination.longitude;

            String urlString = DIRECTIONS_API_URL +
                    "?origin=" + URLEncoder.encode(originParam, StandardCharsets.UTF_8) +
                    "&destination=" + URLEncoder.encode(destParam, StandardCharsets.UTF_8) +
                    "&key=" + apiKey;

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            if (connection.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                StringBuilder response = new StringBuilder();
                int data;
                while ((data = reader.read()) != -1) {
                    response.append((char) data);
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            showSnackBar(R.string.error_api_key);
        }
        return null;
    }

    private List<LatLng> parseDirectionsJson(String jsonString) {
        List<LatLng> points = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(jsonString);
            String status = json.getString("status");

            if (!"OK".equals(status)) {
                return points;
            }

            JSONArray routes = json.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.getString("points");
                points = decodePolyline(encodedPolyline);
            }
        } catch (Exception e) {
            showSnackBar(R.string.error_route);
        }
        return points;
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
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

            LatLng position = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(position);
        }
        return poly;
    }

    private void drawPolyline(List<LatLng> points) {
        clearRoute();
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .width(10f)
                .color(ContextCompat.getColor(requireContext(), R.color.route_color))
                .geodesic(true);

        currentPolyline = mMap.addPolyline(polylineOptions);
    }

    private void animateCameraToRoute(LatLng origin, LatLng destination) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(destination);

        LatLngBounds bounds = builder.build();
        int padding = 150;

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    private void clearRoute() {
        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }
    }

    private void searchLocation(String query) {
        executor.execute(() -> {
            List<Address> results = fetchLocationFromQuery(query);
            runOnUiThread(() -> updateAddressList(results));
        });
    }

    private List<Address> fetchLocationFromQuery(String query) {
        List<Address> addressList = getAddressesFromGeocoder(() ->
                geocoder.getFromLocationName(query, GEOCODER_MAX_RESULTS)
        );
        return filterAddressesByCountryCode(addressList);
    }

    private List<Address> filterAddressesByCountryCode(List<Address> addressList) {
        if (countryCode == null) {
            return addressList;
        }
        return addressList.stream()
                .filter(address -> countryCode.equals(address.getCountryCode()))
                .collect(Collectors.toList());
    }

    private void updateAddressList(List<Address> newAddresses) {
        addresses.clear();
        addresses.addAll(newAddresses);
        adapter.notifyDataSetChanged();
    }

    private void fetchLastLocation() {
        if (needsPermissions()) {
            return;
        }

        locationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        updateCountryCode(location);
                    }
                });
    }

    private void updateCountryCode(Location location) {
        List<Address> results = getAddressesFromGeocoder(() ->
                geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1)
        );

        if (!results.isEmpty()) {
            countryCode = results.get(0).getCountryCode();
        }
    }

    private List<Address> getAddressesFromGeocoder(GeocoderQuery query) {
        try {
            List<Address> results = query.execute();
            return results != null ? results : Collections.emptyList();
        } catch (IOException e) {
            showSnackBar(R.string.error_locations);
            return Collections.emptyList();
        }
    }

    @FunctionalInterface
    private interface GeocoderQuery {
        List<Address> execute() throws IOException;
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.permission_denied_title)
                .setMessage(R.string.permission_denied)
                .setPositiveButton(R.string.submit, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSnackBar(int messageRes) {
        Snackbar.make(requireView(), messageRes, Snackbar.LENGTH_SHORT).show();
    }

    private void runOnUiThread(Runnable runnable) {
        requireActivity().runOnUiThread(runnable);
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}