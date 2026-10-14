package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Device measurements only; no schedule insertion/deletion or server calls. */
final class DeviceUiChecks {
    static String run(Instrumentation test, Bundle arguments) throws Exception {
        if ("reminder_state".equals(arguments.getString("phase")))
            return AgendaReminder.WORKER.submit(() -> reminderState(test)).get();
        if ("week_widget_render".equals(arguments.getString("phase"))) return weekWidgets(test);
        if ("restore_occurrence".equals(arguments.getString("phase"))) {
            String key = arguments.getString("key", "");
            if (!key.matches("(system|chrona):[0-9]+:[0-9]+")) throw new IllegalArgumentException("Explicit occurrence key required");
            AgendaReminderPreferences.prefs(test.getTargetContext()).edit().remove("hidden:" + key).commit();
            return AgendaReminder.WORKER.submit(() -> reminderState(test)).get();
        }
        if ("progress_cards".equals(arguments.getString("phase")))
            return AgendaReminder.WORKER.submit(() -> progressCards(test)).get();
        if ("enable_registered_scene".equals(arguments.getString("phase"))) {
            boolean enabled;
            try { enabled = setAtomicScene(test, true); }
            catch (Exception failure) {
                try { setAtomicScene(test, false); }
                catch (Exception cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
            if (!enabled) setAtomicScene(test, false);
            return new JSONObject().put("scene", "CHRONA_AGENDA").put("enabled", enabled).toString();
        }
        if ("registered_atomic".equals(arguments.getString("phase"))) return atomic(test, "CHRONA_AGENDA", 60000);
        if ("disable_registered_scene".equals(arguments.getString("phase")))
            return new JSONObject().put("scene", "CHRONA_AGENDA").put("enabled", setAtomicScene(test, false)).toString();
        if ("register_atomic".equals(arguments.getString("phase"))) {
            Context context = test.getTargetContext();
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Method status = NotificationManager.class.getDeclaredMethod("getSceneStatus", String.class, String.class);
            String scene = "CHRONA_AGENDA";
            JSONObject registration = new JSONObject().put("scene", scene).put("before", status.invoke(manager, context.getPackageName(), scene));
            try {
                registration.put("after", setAtomicScene(test, true));
            } catch (Exception failure) { setAtomicScene(test, false); throw failure; }
            Bundle progress = new Bundle(); progress.putString("stream", "atomic registration=" + registration + "\n"); test.sendStatus(0, progress);
            if (!registration.getBoolean("after")) { setAtomicScene(test, false); return registration.toString(); }
            try { return registration.put("notification", new JSONObject(atomic(test, scene))).toString(); }
            catch (Exception failure) { setAtomicScene(test, false); throw failure; }
        }
        if ("capability".equals(arguments.getString("phase"))) {
            JSONArray methods = new JSONArray();
            for (Method method : NotificationManager.class.getDeclaredMethods())
                if (method.getName().toLowerCase(java.util.Locale.ROOT).matches(".*(scene|superx|island).*")) methods.put(method.toGenericString());
            return new JSONObject().put("methods", methods).toString();
        }
        String phase = arguments.getString("phase");
        if ("atomic".equals(phase)) return atomic(test);
        if (phase == null || "calendar".equals(phase) || "gestures".equals(phase)) return calendar(test, arguments);
        throw new IllegalArgumentException("Unknown device check phase: " + phase);
    }
    private static String reminderState(Instrumentation test) throws Exception {
        Context context = test.getTargetContext(); long now = System.currentTimeMillis();
        JSONObject report = new JSONObject().put("now", now)
                .put("enabled", AgendaReminderPreferences.enabled(context))
                .put("system", AgendaReminderPreferences.includesSystemCalendar(context))
                .put("atomicChrona", AgendaReminderPreferences.atomicChrona(context))
                .put("atomicSystem", AgendaReminderPreferences.atomicSystem(context))
                .put("atomicAvailable", VivoAtomicNotification.available(context));
        java.util.Set<String> hidden = new java.util.HashSet<>();
        JSONObject state = new JSONObject();
        for (java.util.Map.Entry<String, ?> item : AgendaReminderPreferences.prefs(context).getAll().entrySet()) {
            state.put(item.getKey(), item.getValue());
            if (item.getKey().startsWith("hidden:") && item.getValue() instanceof Long && (Long)item.getValue() > now)
                hidden.add(item.getKey().substring(7));
        }
        report.put("state", state); JSONArray local = new JSONArray(), system = new JSONArray();
        try (com.donglan.chrona.data.TaskStore store = new com.donglan.chrona.data.TaskStore(context)) {
            for (com.donglan.chrona.data.EventCandidate e : store.nextReminderCandidates(now, hidden))
                local.put(new JSONObject().put("id", e.id).put("title", e.title).put("start", e.startAtMillis)
                        .put("end", e.endAtMillis).put("category", e.category));
            com.donglan.chrona.calendar.CalendarStore calendars = new com.donglan.chrona.calendar.CalendarStore(context);
            if (calendars.hasReadPermission())
                for (com.donglan.chrona.calendar.CalendarOccurrence e : calendars.reminderInstances(now, now+86400000L,
                        new java.util.HashSet<>(store.linkedCalendarIds()), hidden))
                    system.put(new JSONObject().put("id", e.eventId).put("title", e.title).put("start", e.startMillis).put("end", e.endMillis));
        }
        report.put("local", local).put("systemCandidates", system);
        AgendaReminder.refreshSafely(context, null); JSONArray notifications = new JSONArray();
        for (android.service.notification.StatusBarNotification n : context.getSystemService(NotificationManager.class).getActiveNotifications())
            notifications.put(new JSONObject().put("id", n.getId()).put("tag", n.getTag()).put("title", n.getNotification().extras.getCharSequence("android.title"))
                    .put("operation", n.getNotification().extras.getInt("notification.superx.operation", -1)));
        return report.put("notifications", notifications).toString();
    }
    private static String weekWidgets(Instrumentation test) throws Exception {
        Context context = test.getTargetContext(); float density = context.getResources().getDisplayMetrics().density;
        java.lang.reflect.Method render = WeekCalendarWidgetProvider.class.getDeclaredMethod("render", Context.class, int.class, int.class);
        render.setAccessible(true); JSONObject report = new JSONObject();
        test.runOnMainSync(() -> {
            try {
                android.graphics.Bitmap matrix = android.graphics.Bitmap.createBitmap(Math.round(310*density),Math.round(330*density),android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(matrix); canvas.drawColor(UiStyle.colors(context).background);
                int index=0;
                for (int width : new int[]{64,140}) for (int height : new int[]{64,140}) {
                    android.widget.RemoteViews remote=(android.widget.RemoteViews)render.invoke(null,context,width,height);
                    View view=remote.apply(context,new android.widget.FrameLayout(context));
                    int w=Math.round(width*density),h=Math.round(height*density);
                    view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
                    View icon=view.findViewById(R.id.week_widget_icon),title=view.findViewById(R.id.week_widget_title);
                    android.graphics.Rect bounds=new android.graphics.Rect();icon.getDrawingRect(bounds);((android.view.ViewGroup)view).offsetDescendantRectToMyCoords(icon,bounds);
                    if(bounds.left<0||bounds.top<0||bounds.right>w||bounds.bottom>h)throw new AssertionError("Clipped widget icon "+width+"x"+height);
                    if((title.getVisibility()==View.GONE)!=(width<100&&height<100))throw new AssertionError("Widget text visibility");
                    int x=Math.round((index%2*155+8)*density),y=Math.round((index/2*165+8)*density);canvas.save();canvas.translate(x,y);view.draw(canvas);canvas.restore();index++;
                }
                java.io.File file=new java.io.File(context.getExternalFilesDir(null),"week-widget-matrix.png");
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)){matrix.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
                report.put("layouts",4).put("iconClipping",false).put("image",file.getAbsolutePath());
            }catch(Exception failure){throw new RuntimeException(failure);}
        });return report.toString();
    }
    /** Explicitly user-approved device configuration; never part of the production APK. */
    private static boolean setAtomicScene(Instrumentation test, boolean enabled) throws Exception {
        Context context = test.getTargetContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String value = Boolean.toString(enabled), scene = "CHRONA_AGENDA";
        test.getUiAutomation().adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
        try {
            if (context.checkSelfPermission("android.permission.STATUS_BAR_SERVICE") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("ADB did not grant the temporary scene configuration permission");
            Method register = NotificationManager.class.getDeclaredMethod("setSuperXInfosSceneList", List.class, List.class, List.class, List.class);
            register.invoke(manager, List.of(scene), List.of(value), List.of(context.getPackageName()), List.of(value));
        } finally { test.getUiAutomation().dropShellPermissionIdentity(); }
        return (Boolean) NotificationManager.class.getDeclaredMethod("getSceneStatus", String.class, String.class)
                .invoke(manager, context.getPackageName(), scene);
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void mode(Activity activity, int value) throws Exception {
        Method m = activity.getClass().getDeclaredMethod("changeMode", int.class); m.setAccessible(true); m.invoke(activity, value);
    }
    private static void ready(Instrumentation test, Activity activity) throws Exception {
        long until = SystemClock.uptimeMillis() + 25000; int stable = 0;
        while (SystemClock.uptimeMillis() < until) {
            boolean[] idle = {false}; Throwable[] failure = {null};
            test.runOnMainSync(() -> {
                try { idle[0] = !(boolean) field(activity, "loading") && !(boolean) field(activity, "bodyTransitionRunning")
                        && field(activity, "calendarBuildSteps") == null; }
                catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new IllegalStateException(failure[0]);
            stable = idle[0] ? stable + 1 : 0;
            if (stable >= 5) return;
            SystemClock.sleep(25);
        }
        String[] state = {""};
        test.runOnMainSync(() -> {
            try { state[0] = "loading=" + field(activity, "loading") + ", transition=" + field(activity, "bodyTransitionRunning")
                    + ", steps=" + field(activity, "calendarBuildSteps") + ", preDraw=" + field(activity, "bodyPreDraw")
                    + ", resumed=" + field(activity, "resumed") + ", layout=" + ((View) field(activity, "body")).isLayoutRequested(); }
            catch (Exception e) { state[0] = e.toString(); }
        });
        throw new IllegalStateException("Calendar did not settle within 25 seconds: " + state[0]);
    }
    private static String calendar(Instrumentation test, Bundle arguments) throws Exception {
        Context context = test.getTargetContext();
        Activity activity;
        test.getUiAutomation().adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND");
        try { activity = test.startActivitySync(new Intent(context, CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        finally { test.getUiAutomation().dropShellPermissionIdentity(); }
        int[] original = {-1};
        test.runOnMainSync(() -> { try { original[0] = (int) field(activity, "mode"); } catch (Exception e) { throw new IllegalStateException(e); } });
        HandlerThread thread = new HandlerThread("ChronaFrameMeasurements"); thread.start();
        List<long[]> frames = Collections.synchronizedList(new ArrayList<>());
        Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
            long deadline = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : 16_666_667L;
            frames.add(new long[]{metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    metrics.getMetric(FrameMetrics.DRAW_DURATION), metrics.getMetric(FrameMetrics.SYNC_DURATION), deadline, dropped,
                    metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION), metrics.getMetric(FrameMetrics.ANIMATION_DURATION),
                    metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION), metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                    metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                    Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.GPU_DURATION) : -1});
        };
        JSONArray cases = new JSONArray();
        try {
            ready(test, activity);
            test.runOnMainSync(() -> activity.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(thread.getLooper())));
            int[] modes = "gestures".equals(arguments.getString("phase")) ? new int[]{1} : new int[]{1, 2, 3, 4, 0, 1, 4, 2, 3, 1};
            for (int selected : modes) {
                foreground(test, activity);
                frames.clear(); long started = SystemClock.uptimeMillis();
                test.runOnMainSync(() -> { try { mode(activity, selected); } catch (Exception e) { throw new IllegalStateException(e); } });
                ready(test, activity); SystemClock.sleep(80);
                JSONObject result = stats(frames).put("mode", selected).put("settleMs", SystemClock.uptimeMillis() - started);
                int[] count = {0};
                test.runOnMainSync(() -> { try { count[0] = ((List<?>) field(field(activity, "snapshot"), "entries")).size(); }
                    catch (Exception e) { throw new IllegalStateException(e); } });
                result.put("events", count[0]); cases.put(result);
                Bundle progress = new Bundle(); progress.putString("stream", "calendar: " + result + "\n"); test.sendStatus(0, progress);
            }
            for (boolean forward : new boolean[]{true, false, true, false, true, false}) {
                frames.clear(); long started = SystemClock.uptimeMillis();
                gesture(test, activity, forward); ready(test, activity); SystemClock.sleep(80);
                JSONObject result = stats(frames).put("gesture", forward ? "right" : "left").put("settleMs", SystemClock.uptimeMillis() - started);
                cases.put(result); Bundle progress = new Bundle(); progress.putString("stream", "calendar gesture: " + result + "\n"); test.sendStatus(0, progress);
            }
            return new JSONObject().put("version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName)
                    .put("manufacturer", Build.MANUFACTURER).put("cases", cases).toString();
        } finally {
            test.runOnMainSync(() -> {
                activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
                if (original[0] >= 0) activity.getPreferences(Context.MODE_PRIVATE).edit().putInt("mode", original[0]).apply();
                activity.finish();
            });
            thread.quitSafely();
        }
    }
    private static JSONObject stats(List<long[]> frames) throws Exception {
        List<long[]> copy; synchronized (frames) { copy = new ArrayList<>(frames); }
        List<Long> times = new ArrayList<>(); long layout = 0, draw = 0, sync = 0, missed = 0, longFrames = 0, lostCallbacks = 0;
        for (long[] f : copy) {
            times.add(f[0]); layout = Math.max(layout, f[1]); draw = Math.max(draw, f[2]); sync = Math.max(sync, f[3]);
            if (f[4] > 0 && f[0] > f[4]) missed++;
            if (f[0] > 32_000_000L) longFrames++;
            lostCallbacks += f[5];
        }
        Collections.sort(times);
        List<long[]> longest = new ArrayList<>(copy); longest.sort((a, b) -> Long.compare(b[0], a[0]));
        JSONArray details = new JSONArray();
        for (int i = 0; i < Math.min(3, longest.size()); i++) {
            long[] f = longest.get(i);
            details.put(new JSONObject().put("totalMs", f[0] / 1e6).put("layoutMs", f[1] / 1e6)
                    .put("drawMs", f[2] / 1e6).put("syncMs", f[3] / 1e6).put("deadlineMs", f[4] / 1e6)
                    .put("unknownDelayMs", f[6] / 1e6).put("animationMs", f[7] / 1e6).put("inputMs", f[8] / 1e6)
                    .put("commandMs", f[9] / 1e6).put("swapMs", f[10] / 1e6).put("gpuMs", f[11] / 1e6));
        }
        return new JSONObject().put("frames", times.size()).put("p95Ms", percentile(times, .95)).put("maxMs", percentile(times, 1))
                .put("maxLayoutMs", layout / 1e6).put("maxDrawMs", draw / 1e6).put("maxSyncMs", sync / 1e6)
                .put("missedDeadline", missed).put("over32ms", longFrames).put("lostMetricCallbacks", lostCallbacks).put("longestFrames", details);
    }
    private static double percentile(List<Long> values, double quantile) {
        return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * quantile) - 1)) / 1e6;
    }
    private static void gesture(Instrumentation test, Activity activity, boolean forward) throws Exception {
        foreground(test, activity);
        float[] points = new float[4];
        test.runOnMainSync(() -> {
            try {
                DockNavigationLayout dock = (DockNavigationLayout) field(activity, "tabs");
                int[] origin = new int[2]; dock.getLocationOnScreen(origin);
                View from = dock.itemAt(forward ? 0 : 4), to = dock.itemAt(forward ? 4 : 0);
                int[] first = new int[2], last = new int[2]; from.getLocationOnScreen(first); to.getLocationOnScreen(last);
                points[0] = first[0] + from.getWidth() / 2f; points[1] = first[1] + from.getHeight() / 2f;
                points[2] = last[0] + to.getWidth() / 2f; points[3] = origin[1] + dock.getHeight() + 30;
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        long down = SystemClock.uptimeMillis();
        send(test, down, MotionEvent.ACTION_DOWN, points[0], points[1]);
        for (int i = 1; i <= 24; i++) {
            if (i % 4 == 0) foreground(test, activity);
            SystemClock.sleep(8); float fraction = i / 24f;
            send(test, down, MotionEvent.ACTION_MOVE, points[0] + (points[2] - points[0]) * fraction,
                    points[1] + (points[3] - points[1]) * fraction);
        }
        send(test, down, MotionEvent.ACTION_UP, points[2], points[3]);
    }
    private static void foreground(Instrumentation test, Activity activity) {
        boolean[] focused = {false}; test.runOnMainSync(() -> focused[0] = activity.hasWindowFocus());
        if (!focused[0]) throw new IllegalStateException("Calendar lost foreground focus; no pointer events injected");
    }
    private static void send(Instrumentation test, long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try { test.sendPointerSync(event); } finally { event.recycle(); }
    }
    private static String atomic(Instrumentation test) throws Exception {
        return atomic(test, "CHRONA_AGENDA");
    }
    /** Temporary cards use the three real slots, then restore the real reminders. No task data is written. */
    private static String progressCards(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Method factory = AgendaReminder.class.getDeclaredMethod("notification", Context.class,
                AgendaEntry.class, long.class, boolean.class, int.class);
        factory.setAccessible(true);
        Method alarm = AgendaReminder.class.getDeclaredMethod("schedule", Context.class, long.class);
        alarm.setAccessible(true);
        manager.createNotificationChannel(new NotificationChannel(AgendaReminder.CHANNEL,
                "即将开始的日程", NotificationManager.IMPORTANCE_HIGH));
        long now = System.currentTimeMillis();
        AgendaEntry[] entries = new AgendaEntry[3];
        JSONObject report = new JSONObject();
        try {
            // Pause the same alarm while the worker is occupied by UI measurements;
            // otherwise its goAsync receiver could wait behind this test and time out.
            alarm.invoke(null, context, 0L);
            for (int i = 0; i < 3; i++) {
                int id = AgendaReminder.NOTIFICATION_ID + i;
                VivoAtomicNotification.end(context, manager, id);
                long start = now + (i + 1) * 5 * 60_000L;
                entries[i] = new AgendaEntry(-501 - i, 0, start, start + 20 * 60_000L,
                        "进度测试 " + (i + 1), "", "", "测试", false, false, true);
                manager.notify(id, (Notification)factory.invoke(null, context, entries[i], now, false, id));
            }
            SystemClock.sleep(1000);
            report.put("created", testCardCount(manager));
            Bundle first = new Bundle(); first.putString("stream", "progress cards=" + report + "\n"); test.sendStatus(0, first);
            SystemClock.sleep(24000);
            for (int i = 0; i < 3; i++) manager.notify(AgendaReminder.NOTIFICATION_ID + i,
                    (Notification)factory.invoke(null, context, entries[i], now + 60_000L, true, AgendaReminder.NOTIFICATION_ID + i));
            report.put("updated", testCardCount(manager));
            Bundle second = new Bundle(); second.putString("stream", "progress updated=" + report + "\n"); test.sendStatus(0, second);
            SystemClock.sleep(24000);
        } finally {
            for (int i = 0; i < 3; i++) VivoAtomicNotification.end(context, manager, AgendaReminder.NOTIFICATION_ID + i);
            SystemClock.sleep(500);
            report.put("afterEnd", testCardCount(manager));
            AgendaReminder.refreshSafely(context, null);
        }
        if (report.optInt("created") != 3 || report.optInt("updated") != 3 || report.optInt("afterEnd") != 0)
            throw new IllegalStateException("Progress card lifecycle failed: " + report);
        return report.toString();
    }
    private static long testCardCount(NotificationManager manager) {
        return java.util.Arrays.stream(manager.getActiveNotifications())
                .filter(n -> n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, "").toString().startsWith("进度测试 ")).count();
    }
    private static String atomic(Instrumentation test, String scene) throws Exception {
        return atomic(test, scene, 12000);
    }
    private static String atomic(Instrumentation test, String scene, long firstHoldMs) throws Exception {
        Context context = test.getTargetContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Class<?> adapter = Class.forName("com.donglan.chrona.VivoAtomicNotification");
        Method payload = adapter.getDeclaredMethod("extras", Context.class, String.class, String.class, String.class,
                String.class, String.class, String.class, int.class, String.class, PendingIntent.class, boolean.class); payload.setAccessible(true);
        Method end = adapter.getDeclaredMethod("end", Context.class, NotificationManager.class, int.class); end.setAccessible(true);
        String channel = "chrona_atomic_validation"; int id = 0x4348f0;
        manager.createNotificationChannel(new NotificationChannel(channel, "原子岛验证", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent details = PendingIntent.getActivity(context, id, new Intent(context, DashboardActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Method queryScene = NotificationManager.class.getDeclaredMethod("getSceneStatus", String.class, String.class);
        JSONObject result = new JSONObject().put("manufacturer", Build.MANUFACTURER).put("scene", scene)
                .put("sceneEnabled", queryScene.invoke(manager, context.getPackageName(), scene));
        try {
            for (boolean update : new boolean[]{false, true}) {
                Bundle data = (Bundle) payload.invoke(null, context, "Chrona原子岛验证", update ? "更新验证" : "创建验证", "10分钟后开始", "本机测试", "拾时", "10分钟", update ? 25 : 75, update ? "距开始 8分钟" : "距开始 23分钟", details, update);
                data.putString("notification.superx.scene", scene);
                manager.notify(id, new Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle("Chrona原子岛验证").setContentText(update ? "更新验证" : "创建验证")
                        .setContentIntent(details).setOngoing(true).setOnlyAlertOnce(update).addExtras(data).build());
                Bundle progress = new Bundle(); progress.putString("stream", "atomic operation=" + (update ? 1 : 0) + " scene=" + result + "\n"); test.sendStatus(0, progress);
                SystemClock.sleep(update ? 12000 : firstHoldMs);
            }
            result.put("ownNotificationActive", java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n -> n.getId() == id));
        } finally { end.invoke(null, context, manager, id); manager.deleteNotificationChannel(channel); }
        return result.toString();
    }
}
