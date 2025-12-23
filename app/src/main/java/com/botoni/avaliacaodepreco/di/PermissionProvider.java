package com.botoni.avaliacaodepreco.di;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import java.util.Map;

public interface PermissionProvider {

    String[] getPermissions();

    default boolean isGranted(Context context) {
        String[] permissions = getPermissions();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    default void request(ActivityResultLauncher<String[]> launcher, Context context) {
        if (!isGranted(context)) {
            launcher.launch(getPermissions());
        }
    }

    default void onResult(Map<String, Boolean> results, boolean requireAll, Runnable onGranted, Runnable onDenied) {
        String[] permissions = getPermissions();

        if (requireAll) {
            boolean allGranted = true;
            for (String permission : permissions) {
                if (!Boolean.TRUE.equals(results.get(permission))) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                onGranted.run();
            } else {
                onDenied.run();
            }
        } else {
            boolean anyGranted = false;
            for (String permission : permissions) {
                if (Boolean.TRUE.equals(results.get(permission))) {
                    anyGranted = true;
                    break;
                }
            }

            if (anyGranted) {
                onGranted.run();
            } else {
                onDenied.run();
            }
        }
    }

    default void onResult(Map<String, Boolean> results, Runnable onGranted, Runnable onDenied) {
        onResult(results, true, onGranted, onDenied);
    }
}