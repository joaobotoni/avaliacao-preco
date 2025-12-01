package com.botoni.avaliacaodepreco.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.botoni.avaliacaodepreco.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends Fragment implements OnMapReadyCallback {
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    List<Address> addresses = new ArrayList<>();
    private Geocoder geocoder;
    private FusedLocationProviderClient fusedLocationClient;
    private ExecutorService executorService;
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionsResult
            );

    public static MainFragment newInstance() {
        return new MainFragment();
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
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requestLocationPermissionsIfNeeded();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private void initializeServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoder = new Geocoder(requireContext(), new Locale("pt", "BR"));
        executorService = Executors.newFixedThreadPool(4);
    }

    private void requestLocationPermissionsIfNeeded() {
        if (hasLocationPermissions()) {
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS);
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    private void handlePermissionsResult(java.util.Map<String, Boolean> result) {
        boolean allGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION)) &&
                Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

        if (!allGranted) {
            showPermissionDeniedDialog();
        }
    }

    public List<Address> getLocationWithName(String query) {
        try {
            List<Address> results = geocoder.getFromLocationName(query, 10);
            return results != null ? results : Collections.emptyList();
        } catch (IOException e) {
            showSnackBar(R.string.error_locations);
            return Collections.emptyList();
        }
    }

    public void getLastLocation() {
        if (hasLocationPermissions()) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location == null) return;
            try {
                List<Address> list = geocoder.getFromLocation(
                        location.getLatitude(),
                        location.getLongitude(),
                        20
                );
                if (list != null && !list.isEmpty()) {
                    addresses.addAll(list);
                }
            } catch (IOException e) {
                showSnackBar(R.string.error_locations);
            }
        });
    }

    private void showPermissionDeniedDialog() {
        showDialog(R.string.permission_denied_title, R.string.permission_denied);
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
}