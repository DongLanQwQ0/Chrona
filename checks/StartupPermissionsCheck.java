package com.donglan.chrona;

import java.util.Arrays;
import java.util.List;

/** Verify the permission set against supported Android version boundaries. */
public final class StartupPermissionsCheck {
    public static void main(String[] args) {
        for (int sdk : new int[]{26, 28, 29, 32, 33, 36}) {
            List<String> permissions = Arrays.asList(StartupPermissions.required(sdk));
            if (!permissions.contains("android.permission.READ_CALENDAR")
                    || !permissions.contains("android.permission.WRITE_CALENDAR")
                    || permissions.contains("android.permission.POST_NOTIFICATIONS") != (sdk >= 33)
                    || permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE") != (sdk <= 28)
                    || permissions.size() != (sdk >= 33 || sdk <= 28 ? 3 : 2))
                throw new AssertionError("Unexpected permissions for API " + sdk + ": " + permissions);
        }
        System.out.println("Startup permission checks passed: 6 Android version boundaries");
    }
}
