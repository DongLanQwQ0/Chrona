package com.donglan.chrona;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.text.BreakIterator;
import java.util.Locale;

/** Presentation deadlines only. Never changes reminder expiry, completion or unread state. */
public final class AtomicCapsuleController extends BroadcastReceiver {
    static final long WAKE_MILLIS = BuildConfig.CAPSULE_DESKTOP_MILLIS;
    private static final String PREFS = "atomic_capsule", PREFIX = "notification.superx.";
    private static final String META = "chrona.atomic.", EXPIRE = "capsule_expire";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable boundary;

    private static synchronized SharedPreferences prefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (prefs.getInt("boot", -2) != boot) prefs.edit().clear().putInt("boot", boot).commit();
        return prefs;
    }

    static void initialize(Context context) {
        if (!VivoAtomicNotification.supportedManufacturer()) return;
        SharedPreferences prefs = prefs(context);
        long now = SystemClock.elapsedRealtime();
        boolean pendingWindow = prefs.getAll().entrySet().stream()
                .anyMatch(item -> item.getKey().startsWith("until:") && (Long) item.getValue() > now);
        // A recovered process cannot receive a screen-on broadcast sent while it was absent.
        // Restore one reading window, but never extend an already-running window.
        if (interactive(context)
                && prefs.getLong("wake_until", 0) <= now && !pendingWindow) {
            prefs.edit().putLong("wake_until", now + WAKE_MILLIS).commit();
            refresh(context);
        }
        schedule(context);
    }

    static void screenOn(Context context, long receivedAt) {
        if (!VivoAtomicNotification.supportedManufacturer()) return;
        showReadingWindow(context, receivedAt);
    }

    /** Our own last activity stopped; no other app or launcher is inspected. */
    static void appExited(Context context, long receivedAt) {
        if (!VivoAtomicNotification.supportedManufacturer() || !interactive(context)) return;
        showReadingWindow(context, receivedAt);
    }

    private static void showReadingWindow(Context context, long receivedAt) {
        prefs(context).edit().putLong("wake_until", receivedAt + WAKE_MILLIS).commit();
        refresh(context);
        schedule(context);
    }

    static void screenOff(Context context) {
        if (!VivoAtomicNotification.supportedManufacturer()) return;
        HANDLER.post(() -> {
            if (boundary != null) HANDLER.removeCallbacks(boundary);
            boundary = null;
            context.getSystemService(AlarmManager.class).cancel(alarm(context));
        });
    }

    private static boolean interactive(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    static Bundle decorate(Context context, int id, String identity, Bundle data) {
        SharedPreferences prefs = prefs(context);
        if (!identity.equals(prefs.getString("identity:" + id, ""))) {
            prefs.edit().putString("identity:" + id, identity)
                    .putLong("until:" + id, SystemClock.elapsedRealtime() + WAKE_MILLIS).commit();
        }
        data.putInt(META + "id", id);
        data.putString(META + "identity", identity);
        Bundle island = data.getBundle(PREFIX + "island"), capsule = data.getBundle(PREFIX + "capsule");
        data.putString(META + "title", island.getBundle("island.superx.rightInfo")
                .getCharSequence("island.superx.rightInfo.content").toString());
        data.putString(META + "left", island.getBundle("island.superx.leftInfo")
                .getCharSequence("island.superx.leftInfo.content").toString());
        data.putString(META + "capsule", capsule.getString(PREFIX + "capsule.content", ""));
        // Preserve the native expand/dismiss gesture and the existing detail action.
        // Force-show blocks immediate outside-tap dismissal on vivo; never use it here.
        apply(context, data);
        schedule(context);
        return data;
    }

    static boolean compact(long now, long wakeUntil, long itemUntil) {
        return now >= Math.max(wakeUntil, itemUntil);
    }

    static String shortTitle(String title) {
        BreakIterator letters = BreakIterator.getCharacterInstance(Locale.ROOT);
        letters.setText(title);
        int end = letters.first();
        for (int i = 0; i < 2; i++) {
            int next = letters.next();
            if (next == BreakIterator.DONE) return title;
            end = next;
        }
        return title.substring(0, end);
    }

    /** Reapply after every countdown update so it cannot expand an expired capsule. */
    static void apply(Context context, Bundle data) {
        String identity = data.getString(META + "identity");
        if (identity == null) return; // Non-capsule notifications do not participate.
        int id = data.getInt(META + "id");
        SharedPreferences prefs = prefs(context);
        boolean small = compact(SystemClock.elapsedRealtime(), prefs.getLong("wake_until", 0), prefs.getLong("until:" + id, 0));
        Bundle island = new Bundle(data.getBundle(PREFIX + "island"));
        Bundle left = new Bundle(island.getBundle("island.superx.leftInfo"));
        Bundle right = new Bundle(island.getBundle("island.superx.rightInfo"));
        Bundle capsule = new Bundle(data.getBundle(PREFIX + "capsule"));
        right.putCharSequence("island.superx.rightInfo.content", small ? shortTitle(data.getString(META + "title", "")) : data.getString(META + "title", ""));
        left.putCharSequence("island.superx.leftInfo.content", small ? "" : data.getString(META + "left", ""));
        capsule.putString(PREFIX + "capsule.content", small ? shortTitle(data.getString(META + "title", "")) : data.getString(META + "capsule", ""));
        island.putBundle("island.superx.leftInfo", left);island.putBundle("island.superx.rightInfo", right);
        data.putBundle(PREFIX + "island", island);data.putBundle(PREFIX + "capsule", capsule);
    }

    static void countdown(Bundle data, String text) {
        if (!data.containsKey(META + "identity")) return;
        data.putString(META + "left", text);
        data.putString(META + "capsule", text + " " + data.getString(META + "title", ""));
    }

    static void clear(Context context, int id) {
        if (!VivoAtomicNotification.supportedManufacturer()) return;
        prefs(context).edit().remove("identity:" + id).remove("until:" + id).commit();
        schedule(context);
    }

    private static PendingIntent alarm(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, AtomicCapsuleController.class).setAction(EXPIRE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context context) {
        Context app = context.getApplicationContext();
        HANDLER.post(() -> scheduleOnMain(app));
    }

    private static void scheduleOnMain(Context context) {
        if (boundary != null) HANDLER.removeCallbacks(boundary);
        boundary = null;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent alarm = alarm(context);
        alarms.cancel(alarm);
        if (!interactive(context)) return;
        SharedPreferences prefs = prefs(context);
        long now = SystemClock.elapsedRealtime(), at = Long.MAX_VALUE, wake = prefs.getLong("wake_until", 0);
        for (String key : prefs.getAll().keySet()) if (key.startsWith("identity:")) {
            long until = Math.max(wake, prefs.getLong("until:" + key.substring(9), 0));
            if (until > now) at = Math.min(at, until);
        }
        if (at == Long.MAX_VALUE) return;
        boundary = () -> refresh(context);
        HANDLER.postDelayed(boundary, at - now);
        // One durable boundary, not a polling service. The handler covers in-process timing
        // when exact-alarm permission is absent; Android may defer the alarm after process exit.
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm);return;
            }
        } catch (SecurityException failure) {
            android.util.Log.w("ChronaAtomic", "Exact capsule alarm unavailable", failure);
        }
        alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm);
    }

    private static void refresh(Context context) {
        HomeworkNotice.refreshCapsule(context);
        AgendaReminder.requestRefresh(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (!EXPIRE.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        AgendaReminder.WORKER.execute(() -> {
            try {
                HomeworkNotice.refreshCapsule(context);
                AgendaReminder.refreshSafely(context.getApplicationContext(), null);
                schedule(context);
            } finally { pending.finish(); }
        });
    }
}
