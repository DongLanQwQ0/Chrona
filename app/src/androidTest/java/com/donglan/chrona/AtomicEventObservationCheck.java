package com.donglan.chrona;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicInteger;

/** Temporary notification and metadata-only UI observation. No task/calendar or permission writes. */
final class AtomicEventObservationCheck {
    private static final int ID = 0x4349f1;
    private static final String TITLE = "胶囊计时验证课程长标题";

    static String run(Instrumentation test, Bundle arguments) throws Exception {
        Context context = test.getTargetContext();
        if (!"com.donglan.chrona".equals(context.getPackageName()))
            throw new IllegalStateException("Probe requires the configured Chrona scene");
        requireIdleAudio(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!Boolean.TRUE.equals(VivoAtomicNotification.sceneEnabled(context)))
            throw new IllegalStateException("Chrona atomic scene is not enabled");
        for (var item : manager.getActiveNotifications()) if (item.getId() == ID)
            throw new IllegalStateException("Probe notification ID is already in use");
        var home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (home == null) throw new IllegalStateException("Cannot resolve launcher");
        String launcher = home.activityInfo.packageName;
        String channel = "chrona_event_probe_" + SystemClock.elapsedRealtime();
        UiAutomation automation = "false".equals(arguments.getString("observe")) ? null
                : test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo previous = automation == null ? null : automation.getServiceInfo();
        AccessibilityServiceInfo observation = new AccessibilityServiceInfo();
        observation.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED;
        observation.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        observation.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        observation.notificationTimeout = 100;
        observation.packageNames = new String[]{"com.android.systemui", "com.vivo.systemuiplugin", launcher, context.getPackageName()};
        AtomicInteger count = new AtomicInteger();
        long started = SystemClock.elapsedRealtime();
        boolean productionSeconds = "true".equals(arguments.getString("productionSeconds"));
        boolean automaticSeconds = "true".equals(arguments.getString("automaticSeconds"));
        if (automaticSeconds && (!productionSeconds || !HomeworkNotice.activeAtomic(context)))
            throw new IllegalStateException("Automatic seconds probe requires an existing live homework notice");
        if (productionSeconds && (!AgendaReminderPreferences.enabled(context) || !AgendaReminderPreferences.showSeconds(context)))
            throw new IllegalStateException("Seconds/reminders must already be enabled; probe does not change preferences");
        try {
            if (automaticSeconds) {
                onWorker(() -> { HomeworkNotice.refreshCapsule(context); HomeworkNotice.refresh(context); });
                AgendaReminderLiveService.update(context, false, 0);
            }
            if (automation != null) automation.setServiceInfo(observation);
            if (automation != null) automation.setOnAccessibilityEventListener(event -> {
                if (count.incrementAndGet() > 1200) return;
                try {
                    JSONObject row = new JSONObject().put("ms", SystemClock.elapsedRealtime() - started)
                            .put("type", AccessibilityEvent.eventTypeToString(event.getEventType()))
                            .put("package", String.valueOf(event.getPackageName()))
                            .put("class", String.valueOf(event.getClassName()))
                            .put("window", event.getWindowId()).put("changes", event.getContentChangeTypes());
                    AccessibilityNodeInfo source = event.getSource();
                    if (source != null) { row.put("source", describe(source)); source.recycle(); }
                    emit(test, "event " + row);
                } catch (Exception error) { emit(test, "observer-error " + error.getClass().getSimpleName()); }
            });
            manager.createNotificationChannel(new NotificationChannel(channel, "胶囊事件临时验证", NotificationManager.IMPORTANCE_HIGH));
            long now = System.currentTimeMillis();
            AgendaEntry entry = new AgendaEntry(-99002, 0, now + (productionSeconds ? 110000 : 600000), now + 1200000,
                    TITLE, "临时通知，无日程写入", "", "测试", true, false, true);
            PendingIntent details = PendingIntent.getActivity(context, ID,
                    new Intent(context, DashboardActivity.class), PendingIntent.FLAG_IMMUTABLE);
            Bundle extras = VivoAtomicNotification.extras(context, TITLE, "验证", "10分钟后开始", "验证", "10分钟",
                    details, false, AtomicAgendaCard.create(context, entry, now, now, "", details),
                    AtomicAgendaCard.forIsland(context, entry, now, now, "", details),
                    AtomicAgendaCard.marqueeConfiguration(), "胶囊验证");
            String identity = "probe:" + started;
            AtomicCapsuleController.decorate(context, ID, identity, extras);
            Notification notice = new Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle(TITLE).setContentText("临时通知，无日程写入")
                    .setContentIntent(details).setOngoing(true).setTimeoutAfter(240000).addExtras(extras).build();
            manager.notify(ID, notice);
            if (productionSeconds) onWorker(() -> AtomicCountdownUpdates.remember(ID, notice, entry, now, now + 240000));
            emit(test, "READY launcher=" + launcher + " id=" + ID);
            emit(test, "productionSeconds=" + productionSeconds);
            emit(test, "automaticSeconds=" + automaticSeconds);
            String last = "";
            int duration = Math.min(210, Math.max(30, Integer.parseInt(arguments.getString("seconds", "75"))));
            String lastPresentation = "";
            String lastState = "";
            for (int second = 0; second < duration; second++) {
                requireIdleAudio(context);
                if (productionSeconds && !automaticSeconds) onWorker(() -> AtomicCountdownUpdates.tick(context));
                Bundle current = new Bundle(extras);
                AtomicCapsuleController.apply(context, current);
                String presentation = current.getBundle("notification.superx.island").getBundle("island.superx.rightInfo")
                        .getCharSequence("island.superx.rightInfo.content").toString();
                if (!productionSeconds && !presentation.equals(lastPresentation)) {
                    current.putInt("notification.superx.operation", 1);
                    manager.notify(ID, Notification.Builder.recoverBuilder(context, notice).addExtras(current).setOnlyAlertOnce(true).build());
                    lastPresentation = presentation;
                }
                var state = context.getSharedPreferences("atomic_capsule", Context.MODE_PRIVATE);
                JSONObject timing = new JSONObject().put("compact", !TITLE.equals(presentation))
                        .put("desktopUntil", state.getLong("wake_until", 0)).put("itemUntil", state.getLong("until:"+ID, 0));
                if (!lastState.equals(timing.toString())) {
                    lastState = timing.toString();
                    emit(test, "presentation " + timing.put("ms", SystemClock.elapsedRealtime()-started).put("now", SystemClock.elapsedRealtime()));
                }
                JSONArray windows = new JSONArray();
                for (var window : automation == null ? java.util.List.<android.view.accessibility.AccessibilityWindowInfo>of() : automation.getWindows()) {
                    Rect bounds = new Rect(); window.getBoundsInScreen(bounds);
                    JSONObject description = new JSONObject().put("id", window.getId())
                            .put("type", window.getType()).put("bounds", bounds.toShortString());
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root == null) { windows.put(description.put("root", false)); continue; }
                    String pkg = String.valueOf(root.getPackageName());
                    description.put("package", pkg);
                    if ("com.vivo.systemuiplugin".equals(pkg)) {
                        JSONArray nodes = new JSONArray(); scan(root, nodes, 0);
                        description.put("nodes", nodes);
                    }
                    windows.put(description);
                    root.recycle();
                }
                String snapshot = windows.toString();
                if (!snapshot.equals(last)) { emit(test, "windows ms=" + (SystemClock.elapsedRealtime()-started) + " " + snapshot); last = snapshot; }
                SystemClock.sleep(1000);
            }
            return new JSONObject().put("observedEvents", count.get()).put("taskWrites", false).toString();
        } finally {
            if (automation != null) {
                automation.setOnAccessibilityEventListener(null);
                automation.setServiceInfo(previous);
            }
            try {
                if (productionSeconds) onWorker(() -> AtomicCountdownUpdates.clear(ID));
            } finally {
                VivoAtomicNotification.end(context, manager, ID);
                manager.deleteNotificationChannel(channel);
                emit(test, "CLEANED temporary notification and observer");
            }
        }
    }

    private static void requireIdleAudio(Context context) {
        android.media.AudioManager audio = context.getSystemService(android.media.AudioManager.class);
        if (audio != null && audio.getMode() != android.media.AudioManager.MODE_NORMAL)
            throw new IllegalStateException("Phone call/communication/ringing active; do not operate the island");
    }

    private static void onWorker(Runnable action) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        AgendaReminder.WORKER.execute(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        if (!done.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Reminder worker did not finish");
        if (failure.get() != null) throw new IllegalStateException("Production countdown failed", failure.get());
    }

    private static void scan(AccessibilityNodeInfo node, JSONArray output, int depth) throws Exception {
        if (depth > 30 || output.length() > 150) return;
        if (node.isVisibleToUser()) output.put(describe(node));
        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null){scan(child,output,depth+1);child.recycle();}
        }
    }
    private static JSONObject describe(AccessibilityNodeInfo node) throws Exception {
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        return new JSONObject().put("id", String.valueOf(node.getViewIdResourceName()))
                .put("class", String.valueOf(node.getClassName())).put("bounds", bounds.toShortString())
                .put("clickable", node.isClickable())
                .put("identityMarker", AtomicCapsuleIdentity.read(node.getText()) != null)
                .put("probeTitle", TITLE.contentEquals(node.getText() == null ? "" : node.getText()));
    }
    private static void emit(Instrumentation test, String text) {
        Bundle result=new Bundle();result.putString("stream",text+"\n");test.sendStatus(0,result);
    }
}
