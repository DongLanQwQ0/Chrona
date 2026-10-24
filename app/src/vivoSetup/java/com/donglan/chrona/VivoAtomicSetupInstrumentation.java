package com.donglan.chrona;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.List;
import org.json.JSONObject;

/** Separate setup APK: no task fixtures, account access, parsing or notification creation. */
public final class VivoAtomicSetupInstrumentation extends Instrumentation {
    private static final String PACKAGE = "com.donglan.chrona";
    private static final String SCENE = "CHRONA_AGENDA";
    private static final String PERMISSION = "android.permission.STATUS_BAR_SERVICE";
    private String mode;

    @Override public void onCreate(Bundle arguments) {
        mode = arguments == null ? "Status" : arguments.getString("mode", "Status");
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!PACKAGE.equals(context.getPackageName())) throw new SecurityException("Unexpected target package");
            if (!"vivo".equalsIgnoreCase(Build.MANUFACTURER)) throw new IllegalStateException("vivo / iQOO required");
            if (!List.of("Enable", "Disable", "Status").contains(mode)) throw new IllegalArgumentException("Invalid mode");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Method read = NotificationManager.class.getDeclaredMethod("getSceneStatus", String.class, String.class);
            read.setAccessible(true);
            // Read without shell identity, both before and after configuration.
            boolean before = (Boolean) read.invoke(manager, PACKAGE, SCENE);
            boolean expected = "Status".equals(mode) ? before : "Enable".equals(mode);
            boolean changed = expected != before;
            if (changed) {
                try {
                    setScene(context, manager, expected);
                    if ((Boolean) read.invoke(manager, PACKAGE, SCENE) != expected)
                        throw new IllegalStateException("System did not confirm the requested scene state");
                } catch (Exception failure) {
                    try {
                        setScene(context, manager, before);
                        if ((Boolean) read.invoke(manager, PACKAGE, SCENE) != before)
                            throw new IllegalStateException("Previous scene state could not be restored");
                    } catch (Exception recovery) { failure.addSuppressed(recovery); }
                    throw failure;
                }
            }
            boolean actual = (Boolean) read.invoke(manager, PACKAGE, SCENE);
            if (actual != expected) throw new IllegalStateException("Scene state changed during verification");
            result.putString("setup_report", new JSONObject().put("package", PACKAGE).put("scene", SCENE)
                    .put("mode", mode).put("before", before).put("enabled", actual).put("changed", changed)
                    .put("firmware", Build.DISPLAY).toString());
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
            finish(0, result);
        }
    }

    private void setScene(Context context, NotificationManager manager, boolean enabled) throws Exception {
        getUiAutomation().adoptShellPermissionIdentity(PERMISSION);
        try {
            if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("ADB shell does not have the scene configuration permission");
            Method write = NotificationManager.class.getDeclaredMethod("setSuperXInfosSceneList",
                    List.class, List.class, List.class, List.class);
            write.setAccessible(true);
            String value = Boolean.toString(enabled);
            write.invoke(manager, List.of(SCENE), List.of(value), List.of(PACKAGE), List.of(value));
        } finally { getUiAutomation().dropShellPermissionIdentity(); }
    }
}
