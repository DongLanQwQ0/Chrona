package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** One first-launch request shared by launcher and share-entry activities. */
final class StartupPermissions {
    static final int REQUEST_CODE = 4100;

    static String[] required(int sdk) {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.READ_CALENDAR);
        permissions.add(Manifest.permission.WRITE_CALENDAR);
        if (sdk >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (sdk <= 28) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissions.toArray(new String[0]);
    }

    static void requestFirstLaunch(Activity activity) {
        requestFirstLaunch(activity, null);
    }

    static void requestFirstLaunch(Activity activity, String alreadyRequested) {
        SharedPreferences preferences = activity.getSharedPreferences(
                "startup_permissions", Context.MODE_PRIVATE);
        if (preferences.getBoolean("attempted", false)) return;
        List<String> missing = new ArrayList<>();
        for (String permission : required(Build.VERSION.SDK_INT)) {
            if (!permission.equals(alreadyRequested)
                    && activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                missing.add(permission);
        }
        // Persist before opening the system dialog to avoid duplicate requests after rotation.
        preferences.edit().putBoolean("attempted", true).apply();
        if (!missing.isEmpty()) activity.requestPermissions(
                missing.toArray(new String[0]), REQUEST_CODE);
    }

    private StartupPermissions() { }
}
