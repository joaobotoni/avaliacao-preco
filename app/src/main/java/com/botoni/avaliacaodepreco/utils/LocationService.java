package com.botoni.avaliacaodepreco.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
public class LocationService {
    private final Context context;
    private Geocoder geocoder;
    public LocationService(Context context) {
        this.context = context;
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
}
