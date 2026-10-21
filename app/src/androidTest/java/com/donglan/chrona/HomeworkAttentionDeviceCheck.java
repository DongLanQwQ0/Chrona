package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Disposable native screen-on, persistent attention and real paginated navigation checks. */
final class HomeworkAttentionDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext(); FixtureIsolation.require(context); assertions = 0;
        var checkpoint = context.getSharedPreferences("homework_attention_check", 0);
        String scope = args.getString("scope", "ui");
        if ("cleanup".equals(scope)) { require(checkpoint.contains("capture"), "known fixture recovery only"); cleanup(context, checkpoint); return "PASS: isolated fixture recovered"; }
        if ("restart".equals(scope) || "expired".equals(scope)) {
            long expiry = checkpoint.getLong("expiry", 0), id = checkpoint.getLong("candidate", 0);
            require(expiry > 0 && id > 0, "checkpoint belongs to a retained real fixture");
            if ("restart".equals(scope)) {
                require(android.os.Process.myPid() != checkpoint.getInt("pid", 0), "actual process changed");
                require(context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0) == expiry, "first screen deadline survives process recreation");
                require(notice(context) != null && notice(context).getTimeoutAfter() > 0, "system notification remains live");
            } else {
                require(System.currentTimeMillis() >= expiry, "actual ten minutes elapsed since first wake");
                require(notice(context) == null, "real system notification expired");
            }
            try (TaskStore store = new TaskStore(context)) {
                var rows = store.getCandidatesByIds(List.of(id));
                require(rows.size() == 1 && !rows.get(0).completed(), "timeout never deletes or completes source homework");
            }
            require(new ZjuHomeworkStore(context).newestUnread() == 0, "hidden source does not expose attention");
            if ("expired".equals(scope)) cleanup(context, checkpoint);
            return new JSONObject().put("assertions", assertions).put("scope", scope).put("expiry", expiry).put("now", System.currentTimeMillis()).toString();
        }
        require(!checkpoint.contains("capture"), "previous fixture must finish before another starts");
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        boolean syntheticCredential = "synthetic-not-a-login".equals(settings.prefs.getString("credentials", ""));
        require(!settings.configured() || syntheticCredential, "screen fixture refuses any real login configuration");
        var before = new ZjuHomeworkStore(context).state();
        String account = "homework-attention-" + UUID.randomUUID();
        var guide = context.getSharedPreferences("usage_guide", 0);
        Field version = GuideActivity.class.getDeclaredField("GUIDE_VERSION"); version.setAccessible(true);
        checkpoint.edit().putBoolean("old_enabled", before.enabled).putString("old_account", before.account)
                .putLong("old_generation", before.generation).putLong("old_capture", before.captureId)
                .putInt("old_guide", guide.getInt("version", 0)).putBoolean("old_school", settings.enabled())
                .putBoolean("old_reminders", AgendaReminderPreferences.enabled(context))
                .putBoolean("old_synthetic", syntheticCredential)
                .putString("old_theme", ThemeStore.mode(context)).putString("account", account).commit();
        if (syntheticCredential) settings.prefs.edit().remove("credentials").commit();
        guide.edit().putInt("version", version.getInt(null)).commit();
        AgendaReminderPreferences.setEnabled(context, false);
        ThemeStore.setMode(context, args.getString("theme", "light"));
        test.getUiAutomation().grantRuntimePermission(context.getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
        Activity[] opened = {null}; boolean retained = false;
        try {
            ZjuHomeworkStore managed = new ZjuHomeworkStore(context); managed.configure(account, true);
            checkpoint.edit().putLong("capture", managed.state().captureId).commit();
            long generation = managed.state().generation;
            List<ZjuSnapshot.Homework> items = new ArrayList<>();
            int count = "timeout_start".equals(scope) ? 1 : Integer.parseInt(args.getString("count", "66"));
            for (int i = 0; i < count; i++) items.add(item("work-" + i, "合成课程 · 新作业 " + i));
            var result = managed.apply(account, generation, items);
            require(result.newHomeworkIds.size() == count, "real transaction inserted every new identity");
            long target = result.newHomeworkIds.get(count - 1);
            if ("notice_edges".equals(scope)) {
                settings.prefs.edit().putBoolean("enabled", true).commit();
                long first = result.newHomeworkIds.get(0), second = result.newHomeworkIds.get(1);
                var prefs = context.getSharedPreferences("homework_notice", 0);
                try {
                    shell(test, "input keyevent KEYCODE_SLEEP");
                    await(test, () -> !interactive(context), "real screen asleep for consecutive fetches");
                    HomeworkNotice.show(context, List.of(items.get(0)), List.of(first));
                    await(test, () -> notice(context) != null, "first asleep notification delivered");
                    long original = prefs.getLong("expiry", 0);
                    SystemClock.sleep(5);
                    HomeworkNotice.show(context, List.of(items.get(1)), List.of(second));
                    require(new org.json.JSONArray(prefs.getString("items", "[]")).length() == 2
                            && HomeworkNotice.awaitingScreen(context), "separate asleep fetches merge both unseen identities");
                    long merged = prefs.getLong("expiry", 0);
                    require(merged != original, "fixture has distinct batch identities");
                    new HomeworkNotice().onReceive(context, new Intent("close").putExtra("expiry", original));
                    require(notice(context) != null, "obsolete batch close cannot remove the merged batch");
                    require(TaskCompletion.set(context, first, managed.state().captureId, true) == null, "partial completion uses real completion entry");
                    await(test, () -> savedNoticeCount(context) == 1, "completed item removed while still asleep");
                    require(HomeworkNotice.awaitingScreen(context) && prefs.getLong("expiry", 0) == merged,
                            "partial completion preserves pending first-screen state and identity");
                    require(new org.json.JSONArray(prefs.getString("items", "[]")).getJSONObject(0).getLong("id") == second,
                            "remaining notification keeps correct homework identity");
                    PendingIntent oldClose = notice(context).deleteIntent;
                    shell(test, "input keyevent KEYCODE_WAKEUP");
                    await(test, () -> !HomeworkNotice.awaitingScreen(context), "real first wake starts the merged batch");
                    oldClose.send();
                    await(test, () -> notice(context) == null && prefs.getLong("expiry", 0) == 0,
                            "pre-wake close token remains usable after first-screen deadline changes");
                    require(managed.newestUnread() == target, "closing notification does not consume attention");
                    HomeworkNotice.show(context, List.of(items.get(1)), List.of(second));
                    require(TaskCompletion.set(context, second, managed.state().captureId, true) == null, "last completion saved");
                    await(test, () -> notice(context) == null, "all completed items remove the actual system notice");
                    HomeworkNotice.show(context, List.of(items.get(2)), List.of(target));
                    require(notice(context) != null, "separate unfinished work remains deliverable");
                    managed.configure(account, false);
                    HomeworkNotice.refresh(context);
                    await(test, () -> notice(context) == null, "disabling the source removes its notice");
                    require(managed.newestUnread() == 0, "disabled source does not expose an attention target");
                    try (TaskStore store = new TaskStore(context)) {
                        require(!store.getCandidatesByIds(List.of(target)).get(0).completed(), "source disable leaves unfinished homework unchanged");
                    }
                    return new JSONObject().put("assertions", assertions).put("scope", scope).toString();
                } finally { shell(test, "input keyevent KEYCODE_WAKEUP"); }
            }
            if ("timeout_start".equals(scope)) {
                shell(test, "input keyevent KEYCODE_SLEEP"); await(test, () -> !interactive(context), "real screen asleep");
                HomeworkNotice.show(context, List.of(items.get(0)), List.of(target));
                try {
                    // Android 16 replaces the app's zero timeout with its own three-day TTL.
                    await(test, () -> notice(context) != null && (notice(context).getTimeoutAfter() == 0
                            || notice(context).getTimeoutAfter() > HomeworkNotice.DURATION), "asleep notification has no app ten-minute timeout");
                } catch (AssertionError failure) {
                    throw new AssertionError("waiting=" + HomeworkNotice.awaitingScreen(context) + " interactive=" + interactive(context)
                            + " active=" + Arrays.toString(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications())
                            .map(n -> n.getId() + ":" + n.getNotification().getTimeoutAfter()).toArray()), failure);
                }
                require(HomeworkNotice.awaitingScreen(context), "asleep delivery retains first-screen state");
                var prefs = context.getSharedPreferences("homework_notice", 0);
                long provisional = System.currentTimeMillis() - 1000;
                prefs.edit().putLong("expiry", provisional).commit();
                new HomeworkNotice().onReceive(context, new Intent("expire").putExtra("expiry", provisional));
                HomeworkNotice.refresh(context);
                require(notice(context) != null && HomeworkNotice.awaitingScreen(context), "old send-time deadline cannot expire unseen work");
                long waking = System.currentTimeMillis(); shell(test, "input keyevent KEYCODE_WAKEUP");
                await(test, () -> interactive(context) && !HomeworkNotice.awaitingScreen(context), "actual SCREEN_ON starts timer");
                long expiry = prefs.getLong("expiry", 0);
                require(expiry >= waking + HomeworkNotice.DURATION && expiry <= System.currentTimeMillis() + HomeworkNotice.DURATION, "deadline is ten minutes from first screen broadcast");
                await(test, () -> notice(context) != null && notice(context).getTimeoutAfter() > 0
                        && notice(context).getTimeoutAfter() <= HomeworkNotice.DURATION
                        && !prefs.getBoolean("needs_post", true), "real ten-minute system timeout armed on wake");
                shell(test, "input keyevent KEYCODE_SLEEP"); shell(test, "input keyevent KEYCODE_WAKEUP");
                require(prefs.getLong("expiry", 0) == expiry, "second wake never extends timer");
                require(managed.newestUnread() == 0, "source hidden but local unread remains independent");
                checkpoint.edit().putLong("expiry", expiry).putLong("candidate", target).putInt("pid", android.os.Process.myPid()).commit();
                retained = true;
                return new JSONObject().put("assertions", assertions).put("scope", scope).put("expiry", expiry)
                        .put("pid", android.os.Process.myPid()).put("capture", managed.state().captureId).toString();
            }
            File snapshot = new File(context.getCacheDir(), "homework-attention-snapshot.db");
            try {
                try (TaskStore store = new TaskStore(context)) { store.createSnapshot(snapshot); }
                var inspect = ChronaDataBackup.class.getDeclaredMethod("inspectDatabase", File.class);
                inspect.setAccessible(true);
                long[] counts = (long[])inspect.invoke(null, snapshot);
                require(counts[1] >= count, "current-version snapshot passes actual backup import validation");
                try (TaskStore saved = TaskStore.openSnapshot(context, snapshot);
                     var unread = saved.getReadableDatabase().rawQuery("SELECT unread FROM zju_homework_map WHERE candidate_id=?", new String[]{Long.toString(target)})) {
                    require(unread.moveToFirst() && unread.getInt(0) == 1, "snapshot retains durable unread marker");
                }
            } finally { android.database.sqlite.SQLiteDatabase.deleteDatabase(snapshot); }
            settings.prefs.edit().putBoolean("enabled", true).commit();
            int unseen = Math.min(count, Integer.parseInt(args.getString("unseen", "1")));
            for (int i = 0; i < count - unseen; i++) managed.markSeen(result.newHomeworkIds.get(i));
            require(managed.newestUnread() == target, "only newest unseen work remains");
            managed.apply(account, generation, items);
            require(managed.newestUnread() == target, "duplicate fetch preserves unread instead of resetting it");
            opened[0] = open(test, context);
            await(test, () -> ((Number)field(opened[0], "unreadHomeworkId")).longValue() == target, "home data loads persistent attention");
            await(test, () -> Boolean.TRUE.equals(field(opened[0], "initialDataReady")), "initial home snapshot settled");
            require(Boolean.FALSE.equals(field(opened[0], "startupReadFailed")), "attention query preserves successful home snapshot");
            SystemClock.sleep(250);
            View homeButton = opened[0].getWindow().getDecorView().findViewWithTag("home-homework");
            require(homeButton != null && homeButton.getContentDescription().toString().contains("新作业"), "red-dot action exposes accessible new-work state");
            capture(test, context, "homework-red-dot-" + args.getString("theme", "light"));
            if (Boolean.parseBoolean(args.getString("covered", "false"))) {
                shell(test, "cmd statusbar expand-notifications");
                await(test, () -> !opened[0].hasWindowFocus(), "real notification shade covers target Activity");
                // Deliver the navigation callback only after the real window loses focus;
                // expanding after a touch can legitimately cover an already-seen frame.
                test.runOnMainSync(homeButton::performClick);
                SystemClock.sleep(1200);
                require(managed.newestUnread() == target, "covered navigation never consumes unseen attention");
                shell(test, "cmd statusbar collapse");
                await(test, opened[0]::hasWindowFocus, "target regains actual window focus");
            } else tap(test, homeButton);
            try {
                await(test, () -> ((Number)field(opened[0], "pendingHomeworkFocus")).longValue() == 0
                        && Integer.valueOf(DashboardActivity.SCHEDULE).equals(field(opened[0], "section"))
                        && ((Map<?, ?>)field(opened[0], "inboxRows")).containsKey(target), "real button reaches target row");
            } catch (AssertionError failure) {
                capture(test, context, "homework-navigation-failure");
                throw new AssertionError("section="+field(opened[0], "section")+" pending="+field(opened[0], "pendingHomeworkFocus")
                        +" page="+field(opened[0], "schedulePage")+" focus="+opened[0].hasWindowFocus()
                        +" buttonAttached="+homeButton.isAttachedToWindow()+" rows="+((Map<?, ?>)field(opened[0], "inboxRows")).keySet(), failure);
            }
            SystemClock.sleep(500); // Inspect committed frames, not coordinates before the next render.
            require(((Number)field(opened[0], "schedulePage")).intValue() == (count - 1) / ScheduleQuery.PAGE_SIZE, "navigation selects actual target page");
            Object row = ((Map<?, ?>)field(opened[0], "inboxRows")).get(target);
            View card = (View)field(row, "card"); ScrollView viewport = (ScrollView)field(opened[0], "scroll");
            int[] a = new int[2], b = new int[2]; test.runOnMainSync(() -> { card.getLocationOnScreen(a); viewport.getLocationOnScreen(b); });
            require(Math.abs(a[1] + card.getHeight()/2 - b[1] - viewport.getHeight()/2) <= 3, "target row is centered in actual viewport");
            require("新作业".contentEquals(card.getStateDescription()) && card.getForeground() != null, "centered row carries visible focus");
            capture(test, context, "homework-centered-" + args.getString("theme", "light"));
            if (Boolean.parseBoolean(args.getString("reduced", "false"))) {
                android.graphics.drawable.Drawable emphasized = card.getForeground();
                SystemClock.sleep(2200);
                test.runOnMainSync(() -> {});
                require(card.getStateDescription() == null && card.getForeground() != emphasized,
                        "reduced-motion focus clears without leaving an overlay");
            }
            long remaining = unseen > 1 ? result.newHomeworkIds.get(count - 2) : 0;
            await(test, () -> managed.newestUnread() == remaining, "only presented target becomes seen");
            managed.apply(account, generation, items);
            require(managed.newestUnread() == remaining, "later synchronization does not recreate consumed attention");
            Activity close = opened[0]; test.runOnMainSync(close::finish); opened[0] = null;
            opened[0] = open(test, context);
            await(test, () -> field(opened[0], "unreadHomeworkId").equals(remaining), "seen and remaining attention survive Activity recreation");
            if (remaining > 0) {
                View nextButton = opened[0].getWindow().getDecorView().findViewWithTag("home-homework");
                require(nextButton.getContentDescription().toString().contains("新作业"), "remaining work retains red-dot action");
                tap(test, nextButton);
                await(test, () -> ((Number)field(opened[0], "pendingHomeworkFocus")).longValue() == 0
                        && ((Map<?, ?>)field(opened[0], "inboxRows")).containsKey(remaining), "second click navigates to next unread work");
                await(test, () -> managed.newestUnread() == 0, "viewing last unread work clears attention");
            }
            return new JSONObject().put("assertions", assertions).put("theme", args.getString("theme", "light")).put("target", target).toString();
        } finally {
            if (opened[0] != null) { Activity close = opened[0]; test.runOnMainSync(close::finish); }
            settings.prefs.edit().putBoolean("enabled", checkpoint.getBoolean("old_school", false)).commit();
            if (!retained) cleanup(context, checkpoint);
        }
    }
    private static void cleanup(Context context, android.content.SharedPreferences checkpoint) {
        long expiry = context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0);
        new HomeworkNotice().onReceive(context, new Intent("close").putExtra("expiry", expiry));
        long capture = checkpoint.getLong("capture", 0); String account = checkpoint.getString("account", "");
        try (TaskStore store = new TaskStore(context)) {
            if (capture > 0) store.deleteTask(capture);
            if (!account.isEmpty()) for (String table : List.of("zju_homework_map", "zju_homework_history", "zju_homework_captures"))
                store.getWritableDatabase().delete(table, "account=?", new String[]{account});
            store.getWritableDatabase().execSQL("UPDATE managed_zju_state SET enabled=?,account=?,generation=?,capture_id=? WHERE id=1",
                    new Object[]{checkpoint.getBoolean("old_enabled", false) ? 1 : 0, checkpoint.getString("old_account", ""),
                            checkpoint.getLong("old_generation", 0), checkpoint.getLong("old_capture", 0) == 0 ? null : checkpoint.getLong("old_capture", 0)});
        }
        new ZjuSettingsStore(context).prefs.edit().putBoolean("enabled", checkpoint.getBoolean("old_school", false)).commit();
        if (checkpoint.getBoolean("old_synthetic", false)) new ZjuSettingsStore(context).prefs.edit().putString("credentials", "synthetic-not-a-login").commit();
        context.getSharedPreferences("usage_guide", 0).edit().putInt("version", checkpoint.getInt("old_guide", 0)).commit();
        AgendaReminderPreferences.setEnabled(context, checkpoint.getBoolean("old_reminders", false));
        ThemeStore.setMode(context, checkpoint.getString("old_theme", "light")); checkpoint.edit().clear().commit();
    }
    private static ZjuSnapshot.Homework item(String key, String title) { return new ZjuSnapshot.Homework(key, "synthetic", title, "合成验收，不联网", "", "", null, false); }
    private static boolean interactive(Context context) { return context.getSystemService(PowerManager.class).isInteractive(); }
    private static int savedNoticeCount(Context context) { try { return new org.json.JSONArray(context.getSharedPreferences("homework_notice", 0).getString("items", "[]")).length(); } catch (org.json.JSONException failure) { throw new IllegalStateException(failure); } }
    private static Notification notice(Context context) { return Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).filter(n -> n.getId() == HomeworkNotice.ID).map(n -> n.getNotification()).findFirst().orElse(null); }
    private static Activity open(Instrumentation test, Context context) {
        var monitor = test.addMonitor(DashboardActivity.class.getName(), null, false);
        try {
            test.runOnMainSync(() -> context.startActivity(new Intent(context, DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            Activity screen = monitor.waitForActivityWithTimeout(10000);
            if (screen == null) throw new AssertionError("home did not open"); await(test, screen::hasWindowFocus, "home focused"); return screen;
        } finally { test.removeMonitor(monitor); }
    }
    private static Object field(Object target, String name) { try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void tap(Instrumentation test, View target) { int[] xy = new int[2]; test.runOnMainSync(() -> target.getLocationOnScreen(xy)); long at = SystemClock.uptimeMillis(); for (int action : new int[]{0, 1}) { MotionEvent event = MotionEvent.obtain(at, SystemClock.uptimeMillis(), action, xy[0]+target.getWidth()/2f, xy[1]+target.getHeight()/2f, 0); test.sendPointerSync(event); event.recycle(); } }
    private static void shell(Instrumentation test, String command) throws Exception { try (var descriptor = test.getUiAutomation().executeShellCommand(command); var stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) { stream.readAllBytes(); } }
    private static void capture(Instrumentation test, Context context, String name) throws Exception { test.runOnMainSync(() -> {}); Bitmap image = test.getUiAutomation().takeScreenshot(); if (image == null) throw new AssertionError("No screenshot"); try (var out = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { image.recycle(); } }
    private static void await(Instrumentation test, BooleanSupplier ready, String message) { long until = SystemClock.uptimeMillis()+12000; do { test.runOnMainSync(() -> {}); if (ready.getAsBoolean()) return; SystemClock.sleep(20); } while (SystemClock.uptimeMillis()<until); throw new AssertionError(message); }
    private static void require(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
