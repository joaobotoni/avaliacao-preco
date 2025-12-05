package com.botoni.avaliacaodepreco.di;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import java.util.Map;

public interface LocationPermissionProvider {
    String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    default boolean isGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }
    default void request(ActivityResultLauncher<String[]> launcher, Context context) {
        if (isGranted(context)) {
            launcher.launch(LOCATION_PERMISSIONS);
        }
    }
    default void onResult(Map<String, Boolean> results, Runnable onGranted, Runnable onDenied) {
        boolean granted = Boolean.TRUE.equals(results.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) ||
                Boolean.TRUE.equals(results.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false));
        if (granted) {
            onGranted.run();
        } else {
            onDenied.run();
        }
    }
}
