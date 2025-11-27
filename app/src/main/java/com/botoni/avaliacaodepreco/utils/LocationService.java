package com.botoni.avaliacaodepreco.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
public class LocationService {

    private Geocoder geocoder;
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    public LocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.geocoder = new Geocoder(context, new Locale("pt", "BR"));
    }

    public List<Address> getAddressWithQuery(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Address> addresses = geocoder.getFromLocationName(query.trim(), 5);
            return addresses == null ? Collections.emptyList() : addresses;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public void getLastLocation(OnSuccessListener<Location> listener, Runnable runnable){
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runnable.run();
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(listener);
    }
}
