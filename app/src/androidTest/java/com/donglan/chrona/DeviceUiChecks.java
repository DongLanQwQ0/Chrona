package com.donglan.chrona;

import android.app.Activity;
import java.io.File;
import com.donglan.chrona.data.TaskStore;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.app.Application;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Device measurements; only the explicit atomic fixture phase creates and removes its own test task. No server calls. */
final class DeviceUiChecks {
    static String run(Instrumentation test, Bundle arguments) throws Exception {
        if ("homework_attention".equals(arguments.getString("phase"))) return HomeworkAttentionDeviceCheck.run(test, arguments);
        if ("notification_layers".equals(arguments.getString("phase"))) return NotificationLayersDeviceCheck.run(test, arguments);
        if ("workspace_initial_state".equals(arguments.getString("phase"))) return WorkspaceInitialStateDeviceCheck.run(test, arguments);
        if ("classroom_history".equals(arguments.getString("phase"))) return ClassroomHistoryDeviceCheck.run(test, arguments);
        if ("lan_approval".equals(arguments.getString("phase"))) return LanApprovalDeviceCheck.run(test, arguments);
        if ("todo_due_default".equals(arguments.getString("phase"))) return TodoDueDefaultDeviceCheck.run(test, arguments);
        if ("ui_library".equals(arguments.getString("phase"))) return UiLibraryDeviceCheck.run(test, arguments);
        if ("homework_atomic_phone".equals(arguments.getString("phase"))) return HomeworkAtomicPhoneCheck.run(test,arguments);
        if ("homework_notice".equals(arguments.getString("phase"))) return HomeworkNoticeDeviceCheck.run(test,arguments);
        if ("detail_repeat_ui".equals(arguments.getString("phase"))) return DetailRepeatUiCheck.run(test,arguments);
        if ("toggle_reentry".equals(arguments.getString("phase"))) return WorkspaceDeviceCheck.toggleReentry(test);
        if ("compact_ui".equals(arguments.getString("phase"))) return CompactUiDeviceCheck.run(test, arguments);
        if ("compact_calendar".equals(arguments.getString("phase"))) return WorkspaceDeviceCheck.compactCalendar(test,arguments);
        if ("acrylic_motion".equals(arguments.getString("phase"))) return AcrylicMotionDeviceCheck.run(test);
        if ("history_acrylic".equals(arguments.getString("phase"))) return AcrylicMotionDeviceCheck.runHistory(test);
        if ("schedule_preferences".equals(arguments.getString("phase"))) return ScheduleSettingsDeviceCheck.run(test);
        if ("date_clip".equals(arguments.getString("phase"))) return WorkspaceDeviceCheck.dateClipping(test);
        if ("classroom_work".equals(arguments.getString("phase"))) return ClassroomWorkDeviceCheck.run(test);
        if ("classroom_notifications".equals(arguments.getString("phase"))) return ClassroomNotificationDeviceCheck.run(test);
        if ("workspace".equals(arguments.getString("phase"))) return WorkspaceDeviceCheck.run(test);
        if ("corners".equals(arguments.getString("phase"))) return CornerDeviceCheck.run(test);
        if ("browser".equals(arguments.getString("phase"))) return BrowserDeviceCheck.run(test);
        if ("repeat".equals(arguments.getString("phase"))) return RepeatDeviceCheck.run(test, arguments);
        if ("classroom".equals(arguments.getString("phase"))) return ClassroomDeviceCheck.run(test, arguments);
        if ("week_export_share".equals(arguments.getString("phase"))) return ExportShareDeviceCheck.week(test, arguments);
        if ("sync_roundtrip".equals(arguments.getString("phase"))) {
            Context context = test.getTargetContext();
            File recovery = new File(context.getFilesDir(), "sync-recovery-" + System.currentTimeMillis());
            synchronized (AndroidSync.LOCK) {
                if (!recovery.mkdirs()) throw new java.io.IOException("Cannot create private recovery directory");
                try (TaskStore store = new TaskStore(context)) {
                    android.database.sqlite.SQLiteDatabase database = store.getWritableDatabase();
                    database.beginTransaction();
                    try {
                        // Preserve the actual legacy schema and rows. The write transaction keeps
                        // the database and its WAL stable while taking this private recovery copy.
                        File source = new File(database.getPath());
                        java.nio.file.Files.copy(source.toPath(), new File(recovery, source.getName()).toPath());
                        File wal = new File(source.getPath() + "-wal");
                        if (wal.isFile()) java.nio.file.Files.copy(wal.toPath(), new File(recovery, source.getName() + "-wal").toPath());
                    } finally { database.endTransaction(); }
                }
                for (String name : new String[]{"sync-state.json", "course-timetable.json"}) {
                    File source = new File(context.getFilesDir(), name);
                    if (source.isFile()) java.nio.file.Files.copy(source.toPath(), new File(recovery, name).toPath());
                }
            }
            CountDownLatch done = new CountDownLatch(1);
            String[] message = new String[1];
            if (!AndroidSync.request(context, value -> { message[0] = value; done.countDown(); }))
                return new JSONObject().put("recoveryCreated", true).put("busy", true).toString();
            if (!done.await(180, TimeUnit.SECONDS)) throw new java.io.IOException("Sync wait timed out; request was not cancelled");
            return new JSONObject().put("recoveryCreated", true).put("result", message[0])
                    .put("success", message[0] != null && message[0].startsWith("上次同步：")).toString();
        }
        if ("sync_preflight".equals(arguments.getString("phase"))) {
            synchronized (AndroidSync.LOCK) {
                com.donglan.chrona.sync.SyncState state = AndroidSync.read(test.getTargetContext());
                int merges = 0;
                for (String id : state.records().keySet()) if (id.startsWith("merge_")) merges++;
                JSONObject result = new JSONObject().put("records", state.records().size()).put("mergeRecords", merges);
                Method validate = com.donglan.chrona.sync.SyncExchange.class.getDeclaredMethod("validate",
                        com.donglan.chrona.sync.SyncState.class, java.util.Set.class, Map.class);
                validate.setAccessible(true);
                try {
                    validate.invoke(null, state, new java.util.HashSet<String>(), new java.util.HashMap<String, Long>());
                    result.put("valid", true);
                } catch (java.lang.reflect.InvocationTargetException failed) {
                    // Return only known validation categories, never payload, IDs, paths or credentials.
                    Throwable cause = failed.getCause();
                    result.put("valid", false).put("errorType", cause.getClass().getSimpleName())
                            .put("unsupportedRecordIdentity", "云端记录身份不受支持".equals(cause.getMessage()));
                }
                if ("true".equals(arguments.getString("remote"))) {
                    String stage = "connect";
                    try {
                        WebDavSettingsStore settings = new WebDavSettingsStore(test.getTargetContext());
                        try (com.donglan.chrona.sync.WebDavClient client = new com.donglan.chrona.sync.WebDavClient(
                                settings.url(), settings.user(), settings.password())) {
                            client.testConnection(); stage = "list";
                            Map<String,String> heads = client.listHeads();
                            result.put("remoteHeads", heads.size());
                            for (String name : heads.keySet()) {
                                stage = "readHead";
                                com.donglan.chrona.sync.WebDavClient.Head head = client.readHead(name);
                                stage = "validateHead";
                                com.donglan.chrona.sync.SyncState remote = com.donglan.chrona.sync.SyncState.fromJson(
                                        new JSONObject(new String(head.bytes, java.nio.charset.StandardCharsets.UTF_8)));
                                validate.invoke(null, remote, new java.util.HashSet<String>(), new java.util.HashMap<String, Long>());
                            }
                            result.put("remoteValid", true);
                        }
                    } catch (Exception failure) {
                        Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException ? failure.getCause() : failure;
                        String message = cause.getMessage();
                        result.put("remoteValid", false).put("remoteStage", stage).put("remoteErrorType", cause.getClass().getSimpleName())
                                .put("remoteUnsupportedIdentity", "云端记录身份不受支持".equals(message));
                        if (cause instanceof com.donglan.chrona.sync.WebDavClient.Failure) result.put("remoteFailure", message);
                        if (message != null && message.matches("WebDAV request failed \\(HTTP [0-9]{3}\\)"))
                            result.put("remoteHttpError", message);
                        if (message != null && message.startsWith("WebDAV server did not provide")) result.put("remoteMissingEtag", true);
                    }
                }
                return result.toString();
            }
        }
        if ("acrylic_raster".equals(arguments.getString("phase"))) return AcrylicRasterDeviceCheck.run(test, arguments);
        if ("completion_card_motion".equals(arguments.getString("phase"))) return CompletionMotionDeviceCheck.run(test);
        if ("detail_carousel_restore".equals(arguments.getString("phase"))) return HomeworkDetailDeviceCheck.carouselRestore(test);
        if ("homework_controls".equals(arguments.getString("phase"))) return HomeworkDetailDeviceCheck.controls(test);
        if ("homework_detail".equals(arguments.getString("phase"))) return HomeworkDetailDeviceCheck.run(test);
        if ("source_read".equals(arguments.getString("phase"))) return HomeworkDetailDeviceCheck.sourceRead(test.getTargetContext());
        if ("homework_calendar_state".equals(arguments.getString("phase"))) {
            try (var store = new com.donglan.chrona.data.TaskStore(test.getTargetContext());
                 var cursor = store.getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN e.calendar_event_id IS NOT NULL THEN 1 ELSE 0 END) "
                         + "FROM event_candidates e JOIN managed_zju_state s ON s.capture_id=e.task_id "
                         + "WHERE s.enabled=1 AND e.completed_at_millis IS NULL AND (e.start_at_millis IS NOT NULL OR e.due_at_millis IS NOT NULL)", null)) {
                cursor.moveToFirst(); return new JSONObject().put("eligible", cursor.getInt(0)).put("linked", cursor.getInt(1))
                        .put("pending", ZjuHomeworkCalendar.pendingCount(test.getTargetContext())).toString();
            }
        }
        if ("reminder_state".equals(arguments.getString("phase")))
            return AgendaReminder.WORKER.submit(() -> reminderState(test)).get();
        if ("week_widget_render".equals(arguments.getString("phase"))) return weekWidgets(test);
        if ("week_image".equals(arguments.getString("phase"))) return weekImage(test);
        if ("interaction_acceptance".equals(arguments.getString("phase"))) return interactionAcceptance(test);
        if ("interaction_layout".equals(arguments.getString("phase"))) return interactionLayouts(test);
        if ("restore_occurrence".equals(arguments.getString("phase"))) {
            String key = arguments.getString("key", "");
            if (!key.matches("(system|chrona):[0-9]+:[0-9]+")) throw new IllegalArgumentException("Explicit occurrence key required");
            AgendaReminderPreferences.prefs(test.getTargetContext()).edit().remove("hidden:" + key).commit();
            return AgendaReminder.WORKER.submit(() -> reminderState(test)).get();
        }
        if ("progress_cards".equals(arguments.getString("phase")))
            return AgendaReminder.WORKER.submit(() -> progressCards(test)).get();
        if ("atomic_persistent_probe".equals(arguments.getString("phase"))) {
            Context context = test.getTargetContext();
            long[] ids = AgendaReminder.WORKER.submit(() -> {
                try (var store = new com.donglan.chrona.data.TaskStore(context)) {
                    long task = store.insertTask("原子岛点击回归测试（自动清理）", null, "device_check", System.currentTimeMillis());
                    try {
                        long start = System.currentTimeMillis() + Long.parseLong(arguments.getString("lead_ms", "300000"));
                        long candidate = store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0, task,
                                "原子岛点击回归测试", start, null, java.time.ZoneId.systemDefault().getId(), "", "", 30,
                                false, null, "task", false));
                        store.updateStatus(task, com.donglan.chrona.data.TaskRecord.READY, null);
                        AgendaReminder.refreshSafely(context, null);
                        return new long[]{task, candidate, start};
                    } catch (Exception failure) { store.deleteTask(task); throw failure; }
                }
            }).get();
            try {
                Bundle progress = new Bundle(); progress.putString("stream", "Atomic persistent probe ready\n"); test.sendStatus(0, progress);
                if ("true".equals(arguments.getString("auto_click"))) {
                    test.getUiAutomation().executeShellCommand("cmd statusbar expand-notifications").close();
                    SystemClock.sleep(3500);
                    android.view.accessibility.AccessibilityNodeInfo button = completionButton(test.getUiAutomation().getRootInActiveWindow());
                    if (button == null) throw new IllegalStateException("Temporary reminder completion button is not visible");
                    Rect bounds = new Rect(); button.getBoundsInScreen(bounds);
                    long down = SystemClock.uptimeMillis();
                    MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
                    MotionEvent release = MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
                    try { test.getUiAutomation().injectInputEvent(press, true); test.getUiAutomation().injectInputEvent(release, true); }
                    finally { press.recycle(); release.recycle(); }
                }
                if ("true".equals(arguments.getString("inspect_seconds"))) {
                    JSONObject state = new JSONObject();
                    state.put("initial", AgendaReminder.WORKER.submit(() -> secondsState(context, ids[2])).get());
                    SystemClock.sleep(2500);
                    state.put("automatic", AgendaReminder.WORKER.submit(() -> secondsState(context, ids[2])).get());
                    state.put("manual", AgendaReminder.WORKER.submit(() -> {
                        AtomicCountdownUpdates.tick(context); return secondsState(context, ids[2]);
                    }).get());
                    return state.toString();
                }
                SystemClock.sleep(Long.parseLong(arguments.getString("hold_ms", "15000")));
                return AgendaReminder.WORKER.submit(() -> {
                    try (var store = new com.donglan.chrona.data.TaskStore(context)) {
                        boolean complete = store.getCandidatesByIds(java.util.List.of(ids[1])).get(0).completed();
                        boolean visible = java.util.Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications())
                                .anyMatch(n -> "原子岛点击回归测试".contentEquals(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, "")));
                        if (!complete || visible) throw new IllegalStateException("Real click: completed=" + complete + ", visible=" + visible);
                        return "{\"realClickCompleted\":true,\"notificationRemoved\":true,\"temporaryFixtureOnly\":true}";
                    }
                }).get();
            } finally {
                AgendaReminder.WORKER.submit(() -> {
                    try (var store = new com.donglan.chrona.data.TaskStore(context)) { store.deleteTask(ids[0]); }
                    AgendaReminderPreferences.prefs(context).edit().remove("hidden:chrona:" + ids[1] + ":" + ids[2]).commit();
                    AgendaReminder.refreshSafely(context, null); return null;
                }).get();
            }
        }
        if ("atomic_click_probe".equals(arguments.getString("phase"))) {
            Context context = test.getTargetContext();
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            int id = AgendaReminder.NOTIFICATION_ID;
            long now = System.currentTimeMillis();
            AgendaEntry entry = new AgendaEntry(-99001, 0, now, now + 600000,
                    "勾按钮测试（不修改日程）", "", "", "测试", true, false, true);
            var prefs = AgendaReminderPreferences.prefs(context);
            for (var notice : manager.getActiveNotifications())
                if ("勾按钮测试（不修改日程）".contentEquals(notice.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, "")))
                    VivoAtomicNotification.end(context, manager, notice.getId());
            for (int attempt = 0; attempt < 20 && java.util.Arrays.stream(manager.getActiveNotifications())
                    .anyMatch(n -> n.getId() == id && "勾按钮测试（不修改日程）".contentEquals(
                            n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE, ""))); attempt++)
                SystemClock.sleep(50);
            if (!prefs.getString("slot_key:0", "").isEmpty()
                    || java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n -> n.getId() == id))
                throw new IllegalStateException("Probe needs an unused reminder slot");
            try {
                Method factory = AgendaReminder.class.getDeclaredMethod("notification", Context.class,
                        AgendaEntry.class, long.class, boolean.class, int.class, long.class, String.class, boolean.class);
                factory.setAccessible(true);
                AgendaReminder.WORKER.submit(() -> {
                    prefs.edit().putString("slot_key:0", entry.key()).putLong("slot_end:0", entry.finish()).commit();
                    manager.notify(id, (Notification) factory.invoke(null, context, entry, now, false, id, now, "", true));
                    return null;
                }).get();
                Bundle progress = new Bundle(); progress.putString("stream", "Atomic click probe ready; temporary ID=" + id + "\n");
                test.sendStatus(0, progress);
                if ("true".equals(arguments.getString("send_action")))
                    AgendaReminder.action(context, entry, "complete", 4).send();
                SystemClock.sleep("true".equals(arguments.getString("send_action")) ? 2000 : 30000);
                boolean hidden = prefs.contains("hidden:" + entry.key());
                boolean active = java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n -> n.getId() == id);
                if (!hidden || active) throw new IllegalStateException("Completion not applied: hidden=" + hidden + ", active=" + active
                        + ", slotStillMatches=" + entry.key().equals(prefs.getString("slot_key:0", "")));
                return "{\"temporaryCard\":true,\"taskWrites\":false,\"completionReceived\":true,\"notificationRemoved\":true}";
            } finally {
                AgendaReminder.WORKER.submit(() -> {
                    VivoAtomicNotification.end(context, manager, id);
                    prefs.edit().remove("hidden:" + entry.key()).remove("slot_key:0").remove("slot_end:0").commit();
                    AgendaReminder.refreshSafely(context, null);
                }).get();
            }
        }
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

    private static JSONObject secondsState(Context context, long deadline) throws Exception {
        JSONObject state = new JSONObject().put("deadline", deadline).put("now", System.currentTimeMillis())
                .put("nextDelay", AtomicCountdownUpdates.nextDelay(context));
        for (var item : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
            Notification notice = item.getNotification();
            if (!"原子岛点击回归测试".contentEquals(notice.extras.getCharSequence(Notification.EXTRA_TITLE, ""))) continue;
            state.put("when", notice.when);
            state.put("tag", String.valueOf(item.getTag())).put("keys", notice.extras.keySet().toString());
            Bundle island = notice.extras.getBundle("notification.superx.island");
            state.put("left", island == null ? "" : island.getBundle("island.superx.leftInfo")
                    .getCharSequence("island.superx.leftInfo.content", ""));
        }
        return state;
    }
    private static android.view.accessibility.AccessibilityNodeInfo completionButton(android.view.accessibility.AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("标记待办完成".equals(String.valueOf(node.getContentDescription())) && node.isVisibleToUser()) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            var found = completionButton(node.getChild(index)); if (found != null) return found;
        }
        return null;
    }
    private static String weekImage(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        java.time.LocalDate monday = java.time.LocalDate.of(2026, 10, 12);
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        long base = monday.atStartOfDay(zone).toInstant().toEpochMilli();
        List<AgendaEntry> entries = new ArrayList<>();
        entries.add(new AgendaEntry(1, 0, base, base + 86400000L * 2, "跨日全天安排", "", "", "系统日历", true, true, true));
        entries.add(new AgendaEntry(2, 0, base - 3600000L, base + 3600000L, "跨周活动", "教室", "", "拾时", false, false, true));
        entries.add(new AgendaEntry(3, 0, base + 86400000L * 6 + 86340000L, base + 86400000L * 7, "最后一分钟截止", "", "", "拾时", false, false, true));
        for (int i = 0; i < 3; i++) entries.add(new AgendaEntry(10+i, 0,
                base + 3600000L * 10, base + 3600000L * 12, "并行日程 " + i + " · 长标题完整保存在索引中", "东二教学楼", "", "拾时", false, false, i != 2));
        java.io.File image = WeekAgendaImage.write(context.getCacheDir(), monday, zone, entries, UiStyle.colors(context), "", true, true);
        android.graphics.BitmapFactory.Options size = new android.graphics.BitmapFactory.Options(); size.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(image.getPath(), size);
        if (size.outWidth != WeekAgendaImage.WIDTH || size.outHeight <= 2400) throw new AssertionError("Full week bounds missing");
        java.io.File output = new java.io.File(context.getExternalFilesDir(null), "week-export-edge-cases.png");
        java.nio.file.Files.copy(image.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); image.delete();
        java.io.File table = WeekAgendaImage.write(context.getCacheDir(), monday, zone, entries, UiStyle.colors(context), "", true, false);
        android.graphics.BitmapFactory.Options tableSize = new android.graphics.BitmapFactory.Options(); tableSize.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(table.getPath(), tableSize);
        require(tableSize.outWidth == size.outWidth && tableSize.outHeight < size.outHeight, "Table-only export retained the index height");
        String tablePreview, indexPreview;
        try { tablePreview = fixturePngPreview(table); indexPreview = fixturePngPreview(output); }
        finally { table.delete(); }
        java.io.File empty = WeekAgendaImage.write(context.getCacheDir(), monday, zone, List.of(), UiStyle.colors(context), "空筛选", false, false);
        if (empty.length() == 0) throw new AssertionError("Empty week PNG missing"); empty.delete();
        List<AgendaEntry> oversized = new ArrayList<>();
        for (int i=0;i<1000;i++) oversized.add(new AgendaEntry(100+i,0,base+36000000,base+39600000,"密集日程 "+i,"","","拾时",false,false,true));
        try { java.io.File bad = WeekAgendaImage.write(context.getCacheDir(),monday,zone,oversized,UiStyle.colors(context),"",false,true);
            bad.delete(); throw new AssertionError("Oversized export must be rejected before bitmap allocation");
        } catch (java.io.IOException expected) { }
        return new JSONObject().put("width",size.outWidth).put("height",size.outHeight).put("image",output.getPath())
                .put("tableHeight",tableSize.outHeight).put("tablePreviewPngBase64",tablePreview).put("indexPreviewPngBase64",indexPreview)
                .put("nativeGestures", nativeWeekGestures(test)).put("emptyWeek",true).put("sizeLimit",true).toString();
    }
    private static JSONObject nativeWeekGestures(Instrumentation test) throws Exception {
        JSONObject result = new JSONObject(); Throwable[] error = {null};
        test.runOnMainSync(() -> {
            try {
                Context context = test.getTargetContext();
                FrameLayout parent = new FrameLayout(context), content = new FrameLayout(context);
                FreeCalendarScrollView free = new FreeCalendarScrollView(context);
                ScrollView vertical = new ScrollView(context);
                vertical.addView(new View(context), new ScrollView.LayoutParams(2400, 2400));
                content.addView(vertical, new FrameLayout.LayoutParams(2400, 400));
                free.addView(content, new FrameLayout.LayoutParams(2400, 400));
                parent.addView(free, new FrameLayout.LayoutParams(1200, 400)); free.vertical(vertical);
                int[] switched = {0}; float[] zoom = {1f};
                free.gestures(direction -> switched[0] = direction, (factor, x, y) -> zoom[0] *= factor);
                parent.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)); parent.layout(0, 0, 1200, 400);
                long start = SystemClock.uptimeMillis();
                dispatchFixtureTouch(free, start, start, MotionEvent.ACTION_DOWN, new float[]{420});
                dispatchFixtureTouch(free, start, start+16, MotionEvent.ACTION_POINTER_DOWN | (1<<8), new float[]{420,780});
                for (int i=1;i<=6;i++) dispatchFixtureTouch(free, start, start+16+i*24, MotionEvent.ACTION_MOVE,
                        new float[]{420-i*50,780+i*50});
                dispatchFixtureTouch(free, start, start+200, MotionEvent.ACTION_POINTER_UP | (1<<8), new float[]{120,1080});
                dispatchFixtureTouch(free, start, start+216, MotionEvent.ACTION_UP, new float[]{120});
                require(zoom[0] > 1.1f && switched[0] == 0, "Native pinch did not zoom exclusively: factor="+zoom[0]
                        +", week="+switched[0]+", minimumSpan="+android.view.ViewConfiguration.get(context).getScaledMinimumScalingSpan());
                result.put("pinchFactor",zoom[0]).put("pinchDidNotChangeWeek",true);
                free.scrollTo(300,0); start += 300;
                dispatchFixtureTouch(free,start,start,MotionEvent.ACTION_DOWN,new float[]{100});
                dispatchFixtureTouch(free,start,start+24,MotionEvent.ACTION_MOVE,new float[]{600});
                dispatchFixtureTouch(free,start,start+48,MotionEvent.ACTION_UP,new float[]{600});
                require(switched[0] == -1, "Native continuous outward drag did not select previous week");
                result.put("continuousPreviousWeek",true); switched[0]=0;
                free.scrollTo(1200,0); start += 120;
                dispatchFixtureTouch(free,start,start,MotionEvent.ACTION_DOWN,new float[]{400});
                dispatchFixtureTouch(free,start,start+24,MotionEvent.ACTION_MOVE,new float[]{180});
                dispatchFixtureTouch(free,start,start+48,MotionEvent.ACTION_UP,new float[]{180});
                require(switched[0] == 1, "Native outward drag did not select next week");
                result.put("nextWeek",true).put("scope","native widgets and ScaleGestureDetector in a local fixture, not a full Activity");
            } catch (Throwable failure) { error[0] = failure; }
        });
        if (error[0] != null) throw new IllegalStateException("Native week gesture fixture failed",error[0]);
        return result;
    }
    private static void dispatchFixtureTouch(View view, long down, long now, int action, float[] xs) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[xs.length];
        for (int i=0;i<xs.length;i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords(); coordinates[i].x=xs[i]; coordinates[i].y=200; coordinates[i].pressure=1; coordinates[i].size=1;
        }
        MotionEvent event = MotionEvent.obtain(down,now,action,xs.length,properties,coordinates,0,0,1,1,0,0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    private static String fixturePngPreview(java.io.File image) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = 4;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(image.getPath(), options);
        require(bitmap != null, "Fixture PNG cannot be decoded");
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes), "Fixture PNG preview cannot be encoded");
            return android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP);
        } finally { bitmap.recycle(); }
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
                        new java.util.HashSet<>(store.linkedCalendarIds())))
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
    /** New Activity / empty instance cache, not a force-stopped or storage-cold process. */
    private static String interactionAcceptance(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        Application application = (Application) context.getApplicationContext();
        HandlerThread thread = new HandlerThread("ChronaInteractionFrames"); thread.start();
        Handler metricsHandler = new Handler(thread.getLooper());
        List<long[]> frames = Collections.synchronizedList(new ArrayList<>());
        Window.OnFrameMetricsAvailableListener listener = frameListener(frames);
        Activity[] launched = {null}; int[] originalMode = {-1}; boolean[] emptyCache = {false};
        Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (!(activity instanceof CalendarOverviewActivity)) return;
                launched[0] = activity;
                try {
                    originalMode[0] = (int) field(activity, "mode");
                    Map<?, ?> cache = (Map<?, ?>) field(activity, "snapshotCache");
                    synchronized (cache) { emptyCache[0] = cache.isEmpty(); }
                } catch (Exception failure) { throw new IllegalStateException(failure); }
                // onResume starts the worker: this listener also sees the opening frames.
                activity.getWindow().addOnFrameMetricsAvailableListener(listener, metricsHandler);
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        JSONArray cases = new JSONArray();
        test.runOnMainSync(() -> application.registerActivityLifecycleCallbacks(lifecycle));
        try {
            long openedAt = SystemClock.uptimeMillis();
            test.getUiAutomation().adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND");
            Activity activity;
            try { activity = test.startActivitySync(new Intent(context, CalendarOverviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            finally { test.getUiAutomation().dropShellPermissionIdentity(); }
            JSONObject first = observedLoad(test, activity, openedAt);
            drain(metricsHandler);
            first.put("case", "cold_activity_open").put("emptyInstanceCache", emptyCache[0])
                    .put("processCold", false).put("timingOrigin", "Activity launch request").put("frameMetrics", stats(frames));
            require(emptyCache[0], "Opening Activity did not have an empty instance cache");
            cases.put(first); progress(test, first);

            Object[] firstSnapshot = {null}; JSONObject[] initialCache = {null};
            test.runOnMainSync(() -> {
                try {
                    firstSnapshot[0] = field(activity, "snapshot");
                    initialCache[0] = cacheEvidence(activity, firstSnapshot[0]);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            int alternate = originalMode[0] == 1 ? 4 : 1;
            for (int selected : new int[]{alternate, originalMode[0]}) {
                drain(metricsHandler); frames.clear(); long started = SystemClock.uptimeMillis();
                test.runOnMainSync(() -> { try { mode(activity, selected); } catch (Exception failure) { throw new IllegalStateException(failure); } });
                JSONObject loaded = observedLoad(test, activity, started);
                boolean[] reused = {false}; JSONObject[] currentCache = {null};
                test.runOnMainSync(() -> { try {
                    Object current = field(activity, "snapshot"); reused[0] = current == firstSnapshot[0];
                    currentCache[0] = cacheEvidence(activity, current);
                }
                    catch (Exception failure) { throw new IllegalStateException(failure); } });
                drain(metricsHandler);
                boolean returning = selected == originalMode[0];
                String originalKey = initialCache[0].getString("cacheKey");
                String returnedKey = currentCache[0].getString("cacheKey");
                boolean sameInputs = !originalKey.isEmpty() && originalKey.equals(returnedKey)
                        && initialCache[0].getInt("providerRevision") == currentCache[0].getInt("providerRevision");
                String sceneStatus = !returning ? "measured" : originalKey.isEmpty() ? "unmeasured_initial_snapshot_not_cached"
                        : returnedKey.isEmpty() ? "unmeasured_returned_snapshot_not_cached"
                        : !sameInputs ? "unmeasured_inputs_changed" : "verified";
                loaded.put("case", selected == originalMode[0] ? "cached_mode_return" : "uncached_mode_switch")
                        .put("timingOrigin", "mode change request")
                        .put("mode", selected).put("sceneStatus",sceneStatus)
                        .put("initialCache",initialCache[0]).put("returnedCache",currentCache[0])
                        .put("snapshotObjectReused",reused[0]).put("cacheHitVerified",returning && sameInputs && reused[0])
                        .put("frameMetrics", stats(frames));
                if (returning && sameInputs) require(reused[0], "Same cache inputs did not reuse snapshot: " + loaded);
                cases.put(loaded); progress(test, loaded);
            }
            drain(metricsHandler); frames.clear();
            JSONObject press = heldPressAndDragOut(test, activity).put("case", "long_hold_and_drag_out");
            drain(metricsHandler); press.put("frameMetrics", stats(frames)); cases.put(press); progress(test, press);
            drain(metricsHandler); frames.clear();
            JSONObject reverse = rapidDockReversals(test, activity).put("case", "rapid_reverse_dock");
            drain(metricsHandler); reverse.put("frameMetrics", stats(frames)); cases.put(reverse); progress(test, reverse);
            return new JSONObject().put("phase", "interaction_acceptance").put("version", context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName).put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT).put("animationsEnabled", UiMotion.isEnabled())
                    .put("queryTiming", "worker Future completion observed at 10ms polling; includes revision/cache/repository work, not isolated SQL time")
                    .put("frameTiming", "Window FrameMetrics; no FPS inferred from code or zero-frame intervals")
                    .put("cases", cases).toString();
        } finally {
            test.runOnMainSync(() -> {
                application.unregisterActivityLifecycleCallbacks(lifecycle);
                if (launched[0] != null) {
                    launched[0].getWindow().removeOnFrameMetricsAvailableListener(listener);
                    if (originalMode[0] >= 0) launched[0].getPreferences(Context.MODE_PRIVATE).edit().putInt("mode", originalMode[0]).apply();
                    launched[0].finish();
                }
            });
            thread.quitSafely();
        }
    }
    /** Main-thread capture; cache keys include both local dataRevision and providerRevision. */
    private static JSONObject cacheEvidence(Activity activity, Object snapshot) throws Exception {
        Map<?, ?> cache = (Map<?, ?>) field(activity,"snapshotCache"); String key = ""; JSONArray keys = new JSONArray();
        synchronized (cache) {
            for (Map.Entry<?, ?> entry : cache.entrySet()) {
                keys.put(entry.getKey()); if (entry.getValue() == snapshot) key = entry.getKey().toString();
            }
        }
        return new JSONObject().put("cacheKey",key).put("cacheKeys",keys)
                .put("providerRevision",field(activity,"providerRevision")).put("includeSystem",field(activity,"includeSystem"))
                .put("systemUnavailable",field(snapshot,"systemUnavailable")).put("resumed",field(activity,"resumed"))
                .put("readCalendarGranted",new com.donglan.chrona.calendar.CalendarStore(activity).hasReadPermission())
                .put("windowFocused",activity.hasWindowFocus());
    }
    /** Worker completion and UI completion are separate observations, both relative to the action. */
    private static JSONObject observedLoad(Instrumentation test, Activity activity, long started) throws Exception {
        long until = started + 25000, workerMs = -1, uiMs = -1; int stable = 0;
        long observationStarted = SystemClock.uptimeMillis() - started; boolean firstObservation = true, workerAlreadyDone = false;
        while (SystemClock.uptimeMillis() < until) {
            boolean[] state = {false, false};
            test.runOnMainSync(() -> {
                try {
                    Future<?> read = (Future<?>) field(activity, "readFuture");
                    state[0] = read != null && read.isDone() && !read.isCancelled();
                    state[1] = !(boolean) field(activity, "loading") && !(boolean) field(activity, "bodyTransitionRunning")
                            && field(activity, "calendarBuildSteps") == null && !((DockNavigationLayout) field(activity, "tabs")).isAnimating();
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            long elapsed = SystemClock.uptimeMillis() - started;
            if (firstObservation) { workerAlreadyDone = state[0]; firstObservation = false; }
            if (state[0] && workerMs < 0) workerMs = elapsed;
            if (state[1]) { if (uiMs < 0) uiMs = elapsed; stable++; } else { uiMs = -1; stable = 0; }
            if (workerMs >= 0 && stable >= 5) {
                int[] count = {0}; String[] rendered = {""};
                test.runOnMainSync(() -> { try {
                    count[0] = ((List<?>) field(field(activity, "snapshot"), "entries")).size();
                    rendered[0] = (String) field(activity, "renderedKey");
                } catch (Exception failure) { throw new IllegalStateException(failure); } });
                require(!rendered[0].isEmpty(), "Calendar settled without a rendered snapshot");
                SystemClock.sleep(80);
                return new JSONObject().put("workerCompleteObservedMs", workerMs).put("uiCompleteObservedMs", uiMs)
                        .put("observationStartedMs", observationStarted).put("workerAlreadyDoneAtFirstObservation", workerAlreadyDone)
                        .put("observationIntervalMs", 10).put("events", count[0]);
            }
            SystemClock.sleep(10);
        }
        throw new AssertionError("Interaction load did not complete in 25 seconds");
    }
    private static JSONObject heldPressAndDragOut(Instrumentation test, Activity activity) throws Exception {
        foreground(test, activity); View[] control = {null}; Object[] before = {null};
        test.runOnMainSync(() -> { try {
            control[0] = description(activity.getWindow().getDecorView(), "上一段日期");
            before[0] = field(activity, "date");
        } catch (Exception failure) { throw new IllegalStateException(failure); } });
        require(control[0] != null, "Calendar previous-date control missing");
        float[] point = screenCenter(test, control[0]); long down = SystemClock.uptimeMillis();
        try {
            send(test, down, MotionEvent.ACTION_DOWN, point[0], point[1]);
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 350L);
            float first = heldScale(test, control[0]); SystemClock.sleep(350); float second = heldScale(test, control[0]);
            float[] outside = new float[2];
            test.runOnMainSync(() -> {
                Rect bounds = new Rect(); control[0].getGlobalVisibleRect(bounds);
                outside[0] = bounds.centerX(); outside[1] = bounds.bottom + 3 * android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
            });
            foreground(test, activity);
            send(test, down, MotionEvent.ACTION_MOVE, outside[0], outside[1]); SystemClock.sleep(80);
            send(test, down, MotionEvent.ACTION_UP, outside[0], outside[1]);
            waitReleased(test, control[0]);
            boolean[] unchanged = {false}; test.runOnMainSync(() -> { try { unchanged[0] = before[0].equals(field(activity, "date")); }
                catch (Exception failure) { throw new IllegalStateException(failure); } });
            require(unchanged[0], "Dragging a held control out and releasing triggered its action");
            // A subsequent normal tap must still reach the real listener.
            point = screenCenter(test, control[0]); long tap = SystemClock.uptimeMillis();
            send(test, tap, MotionEvent.ACTION_DOWN, point[0], point[1]); send(test, tap, MotionEvent.ACTION_UP, point[0], point[1]);
            ready(test, activity);
            boolean[] changed = {false}; test.runOnMainSync(() -> { try { changed[0] = !before[0].equals(field(activity, "date")); }
                catch (Exception failure) { throw new IllegalStateException(failure); } });
            require(changed[0], "Normal tap did not recover after drag-out release");
            test.runOnMainSync(() -> { try {
                Method restore = activity.getClass().getDeclaredMethod("navigateDate", java.time.LocalDate.class);
                restore.setAccessible(true); restore.invoke(activity, before[0]);
            } catch (Exception failure) { throw new IllegalStateException(failure); } }); ready(test, activity);
            return new JSONObject().put("holdMs", android.view.ViewConfiguration.getLongPressTimeout() + 700)
                    .put("heldScaleFirst", first).put("heldScaleSecond", second).put("dragOutDidNotClick", unchanged[0])
                    .put("releaseReset", true).put("nextTapWorked", changed[0]);
        } finally {
            if (activity.hasWindowFocus()) send(test, down, MotionEvent.ACTION_CANCEL, point[0], point[1]);
        }
    }
    private static float heldScale(Instrumentation test, View control) {
        float[] scale = {0}; boolean[] pressed = {false};
        test.runOnMainSync(() -> { pressed[0] = control.isPressed(); scale[0] = control.getScaleX();
            require(Math.abs(control.getScaleX() - control.getScaleY()) < .005f, "Held scale is asymmetric"); });
        require(pressed[0] && scale[0] >= 1.045f, "Long hold lost its pressed feedback"); return scale[0];
    }
    private static void waitReleased(Instrumentation test, View control) {
        long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            boolean[] released = {false}; test.runOnMainSync(() -> released[0] = !control.isPressed()
                    && Math.abs(control.getScaleX() - 1f) < .005f && Math.abs(control.getScaleY() - 1f) < .005f);
            if (released[0]) return; SystemClock.sleep(20);
        }
        throw new AssertionError("Released control retained pressed scale");
    }
    private static JSONObject rapidDockReversals(Instrumentation test, Activity activity) throws Exception {
        DockNavigationLayout[] dock = {null};
        test.runOnMainSync(() -> { try { dock[0] = (DockNavigationLayout) field(activity, "tabs"); }
            catch (Exception failure) { throw new IllegalStateException(failure); } });
        JSONArray acknowledged = new JSONArray(); long started = SystemClock.uptimeMillis();
        for (int selected : new int[]{4, 0, 4, 0, 4, 0}) {
            foreground(test, activity); float[] point = screenCenter(test, dock[0].itemAt(selected)); long down = SystemClock.uptimeMillis();
            send(test, down, MotionEvent.ACTION_DOWN, point[0], point[1]); send(test, down, MotionEvent.ACTION_UP, point[0], point[1]);
            // Intentionally reverse while the previous page/highlight is still settling.
            SystemClock.sleep(45); int[] actual = {-1};
            test.runOnMainSync(() -> { try { actual[0] = (int) field(activity, "mode"); }
                catch (Exception failure) { throw new IllegalStateException(failure); } });
            require(actual[0] == selected, "Dock lost a rapid reversal tap"); acknowledged.put(actual[0]);
        }
        JSONObject result = observedLoad(test, activity, started); long until = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < until) {
            boolean[] settled = {false}; test.runOnMainSync(() -> { try {
                float visual = (float) field(dock[0], "visualPosition");
                settled[0] = !dock[0].isTrackingTouch() && !dock[0].isAnimating()
                        && (int) field(dock[0], "selectedIndex") == 0 && Math.abs(visual) < .03f
                        && Math.abs(dock[0].getScaleX() - 1f) < .005f;
            } catch (Exception failure) { throw new IllegalStateException(failure); } });
            if (settled[0]) return result.put("timingOrigin", "first tap of six-tap burst").put("acknowledgedModes", acknowledged)
                    .put("finalMode", 0).put("highlightSettled", true);
            SystemClock.sleep(20);
        }
        throw new AssertionError("Dock failed to settle after rapid reversals");
    }
    private static View description(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = description(group.getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private static float[] screenCenter(Instrumentation test, View view) {
        float[] point = new float[2]; test.runOnMainSync(() -> {
            Rect bounds = new Rect(); require(view.getGlobalVisibleRect(bounds), "Interaction target is not visible");
            point[0] = bounds.exactCenterX(); point[1] = bounds.exactCenterY();
        }); return point;
    }
    private static void drain(Handler handler) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1); handler.post(delivered::countDown);
        require(delivered.await(5, TimeUnit.SECONDS), "FrameMetrics callback queue did not drain");
    }
    private static void progress(Instrumentation test, JSONObject result) {
        Bundle progress = new Bundle(); progress.putString("stream", "interaction: " + result + "\n"); test.sendStatus(0, progress);
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    /** Configuration-local Android rendering; never writes the device font/display settings. */
    private static String interactionLayouts(Instrumentation test) throws Exception {
        Context target = test.getTargetContext(); JSONArray cases = new JSONArray();
        Throwable[] failure = {null};
        String[] configuration = {"unconfigured"};
        test.runOnMainSync(() -> {
            try {
                for (int[] size : new int[][]{{320, 568}, {360, 640}, {640, 360}}) for (float font : new float[]{1f, 1.5f, 2f}) for (int optionCount : new int[]{2, 3, 5}) {
                    configuration[0] = size[0]+"x"+size[1]+" font="+font+" options="+optionCount;
                    Configuration config = new Configuration(target.getResources().getConfiguration());
                    config.fontScale = font; config.screenWidthDp = size[0]; config.screenHeightDp = size[1];
                    config.orientation = size[0] > size[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                    Context context = target.createConfigurationContext(config); float density = context.getResources().getDisplayMetrics().density;
                    int width = Math.round(size[0] * density), height = Math.round(Math.min(320,size[1]) * density), gutter = Math.round(20 * density);
                    android.widget.ScrollView viewport = new android.widget.ScrollView(context);
                    android.widget.LinearLayout content = new android.widget.LinearLayout(context);
                    content.setOrientation(android.widget.LinearLayout.VERTICAL); content.setPadding(gutter, gutter, gutter, gutter);
                    viewport.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
                    android.widget.Button button = new android.widget.Button(context); button.setText("完成日程");
                    button.setOnClickListener(view -> { }); UiStyle.button(button, true);
                    content.addView(button, new android.widget.LinearLayout.LayoutParams(-1, -2));
                    DockNavigationLayout dock = new DockNavigationLayout(context, index -> { }, () -> { });
                    dock.setPadding(Math.round(2*density), Math.round(3*density), Math.round(2*density), Math.round(3*density));
                    for (String label : optionCount == 2 ? new String[]{"拾时", "系统日程"}
                            : optionCount == 3 ? new String[]{"全部", "待确认", "已写入"} : new String[]{"全部", "今天", "本周", "本月", "自选"}) {
                        android.widget.TextView tab = new android.widget.TextView(context); tab.setText(label); tab.setTextSize(14);
                        tab.setIncludeFontPadding(false); tab.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
                        tab.setGravity(android.view.Gravity.CENTER); tab.setOnClickListener(view -> { });
                        tab.setPadding(0,0,0,0);
                        dock.addItem(tab, new android.widget.LinearLayout.LayoutParams(0, Math.round(44*density), label.length()));
                    }
                    android.widget.FrameLayout host = dock.motionHost();
                    android.widget.LinearLayout.LayoutParams dockParams = new android.widget.LinearLayout.LayoutParams(-1, -2);
                    dockParams.gravity = android.view.Gravity.CENTER_HORIZONTAL; dockParams.topMargin = Math.round(24*density);
                    content.addView(host, dockParams); dock.setSelectedIndex(0); PressFeedback.install(viewport);
                    viewport.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); viewport.layout(0, 0, width, height);
                    View firstCell = dock.itemAt(0);
                    for (int i=1;i<dock.itemCount();i++) {
                        View cell=dock.itemAt(i);
                        require(cell.getTop()==firstCell.getTop() && cell.getBottom()==firstCell.getBottom(),
                                "Dock cells have different vertical bounds: first="+firstCell.getTop()+","+firstCell.getBottom()
                                        +" cell="+i+":"+cell.getTop()+","+cell.getBottom());
                    }
                    button.setPressed(true);
                    button.getStateListAnimator().jumpToCurrentState();
                    require(button.getScaleX() >= 1.045f, "Configured button did not enter its held state");
                    android.graphics.RectF buttonBounds = boundsIn(button, viewport); contained(buttonBounds, width, height, "Held button");
                    require(!content.getClipChildren() && !content.getClipToPadding(), "Press overflow blocked by layout parent");
                    Rect clip = viewport.getClipBounds(); require(clip != null && clip.equals(new Rect(0, 0, width, height)), "Viewport boundary not retained");
                    require(button.getLayout() != null && button.getLayout().getHeight()
                            <= button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom(), "Large-font button text clipped");
                    JSONArray edges = new JSONArray();
                    for (int edge : new int[]{0, optionCount - 1}) {
                        dock.finishPageSelection(edge);
                        View edgeItem = dock.itemAt(edge);
                        android.graphics.RectF edgeBounds = boundsIn(edgeItem, dock);
                        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, edgeBounds.centerX(), edgeBounds.centerY(), 0);
                        try { dock.dispatchTouchEvent(down); } finally { down.recycle(); }
                        ((Runnable) field(dock, "longPress")).run();
                        DockSpring spring = (DockSpring) field(dock, "spring");
                        Method apply = DockNavigationLayout.class.getDeclaredMethod("applySpring"); apply.setAccessible(true); apply.invoke(dock);
                        // Sample the production physics deterministically; these are geometry checks, not frame timing.
                        for (int frame = 0; frame < 240; frame++) {
                            spring.step(1f/120); apply.invoke(dock); dockEnvelope(dock, host, viewport, "Long press edge " + edge+" frame="+frame);
                        }
                        android.graphics.RectF highlight = boundsIn((View) field(dock, "highlightView"), viewport);
                        require(spring.scale > 1.49f && dock.getScaleX() > 1.09f, "Long press did not reach its expanded envelope");
                        spring.coast(edge == 0 ? -100f : 100f, optionCount - 1);
                        for (int frame = 0; frame < 480; frame++) {
                            spring.step(1f/120); apply.invoke(dock); dockEnvelope(dock, host, viewport, "Fast outward fling edge " + edge+" frame="+frame);
                        }
                        // Exercise the limiting cap too, beyond the sampled fling's finite travel.
                        spring.position = edge == 0 ? -10000f : 10000f;
                        spring.tailPosition = edge; spring.scale = 1.53f; spring.bar = 1.12f;
                        apply.invoke(dock); dockEnvelope(dock, host, viewport, "Maximum outward cap edge " + edge);
                        edges.put(new JSONObject().put("edge", edge).put("heldHighlightBounds", highlight.toShortString())
                                .put("limitHighlightBounds",boundsIn((View) field(dock,"highlightView"),host).toShortString())
                                .put("longPressFrames",240).put("flingFrames",480).put("limitingEnvelopeFits",true));
                        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        try { dock.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
                        spring.position = spring.target = edge; spring.press(false); spring.snap(); apply.invoke(dock);
                    }
                    JSONArray labels = new JSONArray();
                    for (int i = 0; i < dock.itemCount(); i++) {
                        android.widget.TextView label = (android.widget.TextView) dock.itemAt(i);
                        int availableWidth = label.getWidth()-label.getCompoundPaddingLeft()-label.getCompoundPaddingRight();
                        int availableHeight = label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom();
                        float textWidth = label.getPaint().measureText(label.getText().toString());
                        JSONObject measured = new JSONObject().put("label",label.getText()).put("widthPx",label.getWidth())
                                .put("heightPx",label.getHeight()).put("textWidthPx",textWidth)
                                .put("layoutHeightPx",label.getLayout()==null ? -1 : label.getLayout().getHeight())
                                .put("textSizePx",label.getTextSize()).put("availableWidthPx",availableWidth).put("availableHeightPx",availableHeight);
                        require(label.getLayout() != null && label.getLayout().getLineCount()==1
                                && label.getLayout().getEllipsisCount(0)==0
                                && label.getLayout().getHeight() <= availableHeight && textWidth <= availableWidth,
                                "Configured Dock label clipped at "+size[0]+"x"+size[1]+" font="+font+": "+measured);
                        labels.put(measured);
                    }
                    DockSpring renderedSpring = (DockSpring) field(dock,"spring"); renderedSpring.press(true);
                    for (int frame=0;frame<240;frame++) renderedSpring.step(1f/120);
                    Method renderSpring = DockNavigationLayout.class.getDeclaredMethod("applySpring"); renderSpring.setAccessible(true); renderSpring.invoke(dock);
                    dockEnvelope(dock,host,viewport,"Rendered held last edge");
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap); canvas.drawColor(UiStyle.colors(context).background); viewport.draw(canvas);
                        java.io.File output = new java.io.File(target.getExternalFilesDir(null), "interaction-layout-"+size[0]+"x"+size[1]+"-font"+font+"-options"+optionCount+".png");
                        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) { require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream), "Layout PNG write failed"); }
                        JSONObject renderedCase = new JSONObject().put("widthDp",size[0]).put("heightDp",size[1]).put("fontScale",font).put("optionCount",optionCount)
                                .put("buttonBounds",buttonBounds.toShortString()).put("edges",edges).put("labels",labels).put("viewportClipPreserved",true)
                                .put("textFits",true).put("image",output.getAbsolutePath());
                        if (size[0]==320 && font==2f && optionCount!=3) {
                            android.graphics.Bitmap preview = width<=640 ? bitmap : android.graphics.Bitmap.createScaledBitmap(bitmap,640,Math.round(height*640f/width),true);
                            try {
                                java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                                require(preview.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream),"Layout preview encoding failed");
                                renderedCase.put("previewPngBase64",android.util.Base64.encodeToString(stream.toByteArray(),android.util.Base64.NO_WRAP));
                            } finally { if (preview!=bitmap) preview.recycle(); }
                        }
                        cases.put(renderedCase);
                    } finally { bitmap.recycle(); }
                    button.setPressed(false); button.getStateListAnimator().jumpToCurrentState();
                }
            } catch (Throwable error) { failure[0] = new IllegalStateException(configuration[0],error); }
        });
        if (failure[0] != null) throw new IllegalStateException("Interaction configuration rendering failed", failure[0]);
        return new JSONObject().put("phase","interaction_layout").put("scope","production Button/PressFeedback/Dock components in configuration-local Android renders")
                .put("version",target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName)
                .put("model",Build.MODEL).put("sdk",Build.VERSION.SDK_INT)
                .put("systemSettingsChanged",false).put("fullActivityLayout",false).put("cases",cases).toString();
    }
    private static void dockEnvelope(DockNavigationLayout dock, android.widget.FrameLayout host,
                                     android.widget.ScrollView viewport, String state) throws Exception {
        try {
        contained(boundsIn(host,viewport),viewport.getWidth(),viewport.getHeight(),state+" motion host");
        View highlight = (View) field(dock,"highlightView");
        for (View rendered : new View[]{dock,highlight}) {
            contained(boundsIn(rendered,host),host.getWidth(),host.getHeight(),state+" inside host");
            contained(boundsIn(rendered,viewport),viewport.getWidth(),viewport.getHeight(),state+" inside scroll viewport");
        }
        for (int i=0;i<dock.itemCount();i++) {
            View label=dock.itemAt(i);
            contained(boundsIn(label,host),host.getWidth(),host.getHeight(),state+" label "+i+" inside host");
            contained(boundsIn(label,viewport),viewport.getWidth(),viewport.getHeight(),state+" label "+i+" inside viewport");
        }
        } catch (AssertionError failure) {
            DockSpring spring=(DockSpring)field(dock,"spring");
            View highlight=(View)field(dock,"highlightView");
            StringBuilder cells=new StringBuilder();
            for (int i=0;i<dock.itemCount();i++) {
                View cell=dock.itemAt(i);
                cells.append(i).append(':').append(cell.getLeft()).append(',').append(cell.getTop()).append(',')
                        .append(cell.getRight()).append(',').append(cell.getBottom()).append(';');
            }
            throw new AssertionError(state+" host="+host.getWidth()+"x"+host.getHeight()+" measured="+host.getMeasuredWidth()+"x"+host.getMeasuredHeight()
                    +" padding="+host.getPaddingLeft()+","+host.getPaddingTop()+","+host.getPaddingRight()+","+host.getPaddingBottom()
                    +" dock="+dock.getWidth()+"x"+dock.getHeight()+" measured="+dock.getMeasuredWidth()+"x"+dock.getMeasuredHeight()
                    +" dockBounds="+dock.getLeft()+","+dock.getTop()+","+dock.getRight()+","+dock.getBottom()
                    +" dockPivot="+dock.getPivotX()+","+dock.getPivotY()+" scale="+dock.getScaleX()+","+dock.getScaleY()
                    +" highlight="+boundsIn(highlight,host)+" measured="+highlight.getMeasuredWidth()+"x"+highlight.getMeasuredHeight()
                    +" spring.position="+spring.position+" target="+spring.target+" velocity="+spring.velocity+" coasting="+spring.coasting
                    +" spring.scale="+spring.scale+" bar="+spring.bar+" tail="+spring.tailPosition
                    +" selected="+field(dock,"selectedIndex")+" visual="+field(dock,"visualPosition")+" cells="+cells,failure);
        }
    }
    private static android.graphics.RectF boundsIn(View view, ViewGroup ancestor) {
        android.graphics.RectF bounds = new android.graphics.RectF(0,0,view.getWidth(),view.getHeight());
        for (View child = view; child != ancestor;) {
            require(child.getParent() instanceof ViewGroup, "Configured control lost its viewport");
            ViewGroup parent = (ViewGroup) child.getParent(); child.getMatrix().mapRect(bounds);
            bounds.offset(child.getLeft()-parent.getScrollX(), child.getTop()-parent.getScrollY()); child = parent;
        }
        return bounds;
    }
    private static void contained(android.graphics.RectF bounds, int width, int height, String label) {
        require(Float.isFinite(bounds.left) && Float.isFinite(bounds.top) && Float.isFinite(bounds.right) && Float.isFinite(bounds.bottom)
                && bounds.left >= -.5f && bounds.top >= -.5f && bounds.right <= width+.5f && bounds.bottom <= height+.5f,
                label+" extends beyond configured viewport: "+bounds);
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
        Window.OnFrameMetricsAvailableListener listener = frameListener(frames);
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
    private static Window.OnFrameMetricsAvailableListener frameListener(List<long[]> frames) {
        return (window, metrics, dropped) -> {
            long deadline = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : 16_666_667L;
            frames.add(new long[]{metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    metrics.getMetric(FrameMetrics.DRAW_DURATION), metrics.getMetric(FrameMetrics.SYNC_DURATION), deadline, dropped,
                    metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION), metrics.getMetric(FrameMetrics.ANIMATION_DURATION),
                    metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION), metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                    metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                    Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.GPU_DURATION) : -1});
        };
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
                .put("measurementAvailable",!times.isEmpty()).put("deadlineSource",Build.VERSION.SDK_INT >= 31 ? "platform" : "60Hz_reference_only")
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
