package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Isolated real SQLite, RemoteViews and notification lifecycle; no school requests. */
final class HomeworkNoticeDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        require(context.getPackageName().equals("com.donglan.chrona.followup.validation"), "isolated package required");
        if ("lifecycle".equals(args.getString("scope"))) return lifecycle(context);
        if ("timeout_status".equals(args.getString("scope"))) {
            var check = context.getSharedPreferences("homework_notice_check", 0);
            long expiry = check.getLong("expiry", 0);
            require(expiry > System.currentTimeMillis() && active(context), "live timeout fixture exists");
            require(context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0) == expiry,
                    "notification identity survived the previous process");
            require(notice(context).when + HomeworkNotice.DURATION == expiry, "system notice and durable identity share original expiry");
            try (TaskStore store = new TaskStore(context)) {
                require(store.getTask(check.getLong("capture", 0)) != null
                        && !store.getCandidatesByIds(List.of(check.getLong("candidate", 0))).get(0).completed(),
                        "unfinished timeout fixture survived the previous process");
            }
            return "PASS: notification/identity/homework retained across process restart; expiry=" + expiry
                    + "; now=" + System.currentTimeMillis();
        }
        if ("expired".equals(args.getString("scope"))) {
            var check = context.getSharedPreferences("homework_notice_check", 0);
            long until = check.getLong("expiry", 0);
            require(check.getLong("capture", 0) > 0, "timeout fixture kept its real homework identity");
            require(until > 0 && System.currentTimeMillis() >= until, "ten actual minutes have elapsed");
            require(!active(context), "system removed the notification after its real ten-minute timeout");
            new HomeworkNotice().onReceive(context, new Intent().setAction("expire").putExtra("expiry", until));
            require(context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0) == 0,
                    "expired callback durably clears notification identity");
            try (TaskStore store = new TaskStore(context)) {
                long capture = check.getLong("capture", 0), candidate = check.getLong("candidate", 0);
                require(store.getTask(capture) != null && !store.getCandidatesByIds(List.of(candidate)).get(0).completed(),
                        "timeout did not delete or complete the retained homework");
                store.deleteTask(capture);
                String account = check.getString("account", "");
                store.getWritableDatabase().delete("zju_homework_captures", "account=?", new String[]{account});
                store.getWritableDatabase().delete("zju_homework_map", "account=?", new String[]{account});
            }
            restoreBinding(context, check.getBoolean("before_enabled", false), check.getString("before_account", ""),
                    check.getLong("before_generation", 0), check.getLong("before_capture", 0));
            require(check.edit().clear().commit(), "timeout checkpoint cleanup persisted");
            return "PASS: real 600000ms notification timeout elapsed and notification absent";
        }
        ZjuHomeworkStore.State before = new ZjuHomeworkStore(context).state();
        boolean keepHomework = false;
        ThemeStore.setMode(context, args.getString("theme", "light"));
        AgendaReminderPreferences.setEnabled(context, false);
        ZjuSync.background();
        new ZjuSettingsStore(context).options(false, false);
        long homework = 0, ordinary = 0, candidate = 0;
        Activity screen = null;
        String account = "notice-fixture-" + java.util.UUID.randomUUID();
        try {
            try (TaskStore store = new TaskStore(context)) {
                homework = store.insertTask("隐藏的学校原始内容", null, ZjuHomeworkStore.SOURCE, System.currentTimeMillis());
                store.getWritableDatabase().execSQL("INSERT INTO zju_homework_captures(capture_id,account) VALUES(?,?)", new Object[]{homework, account});
                store.getWritableDatabase().execSQL("UPDATE managed_zju_state SET enabled=1,account=?,capture_id=?,generation=generation+1 WHERE id=1", new Object[]{account, homework});
                candidate = store.appendCandidate(new EventCandidate(0, homework, "机器人与无人机基础 · 自主导航实验报告",
                        null, null, "Asia/Shanghai", "", "比较不同环境中的路径规划，提交实验记录与分析。", null,
                        false, null, "homework", false, false, 0, System.currentTimeMillis()+7*86400000L, null, false));
                store.getWritableDatabase().execSQL("INSERT INTO zju_homework_map(account,remote_key,capture_id,candidate_id,ignored,snapshot,pending_calendar) VALUES(?,?,?,?,0,?,0)",
                        new Object[]{account,"notice-fixture",homework,candidate,"{\"submitted\":false}"});
                store.updateStatus(homework, TaskRecord.READY, null);
                ordinary = store.insertTask("普通收件原始文字保留", null, "text", System.currentTimeMillis());
                store.appendCandidate(new EventCandidate(0, ordinary, "普通待办", null, null, "Asia/Shanghai", "", "", null, false, null, "task", false));
                store.updateStatus(ordinary, TaskRecord.READY, null);
            }
            screen = test.startActivitySync(new Intent(context, TaskDetailActivity.class).putExtra("task_id", homework)
                    .putExtra("candidate_id", candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            test.waitForIdleSync(); SystemClock.sleep(800);
            View root = screen.getWindow().getDecorView();
            for (String hidden : new String[]{"原始内容", "图片与文件", "还没有图片附件", "添加图片", "添加文件", "重新解析"})
                require(text(root, hidden) == null, "managed detail hides whole section: " + hidden);
            capture(test, "homework-detail-" + args.getString("theme", "light"));
            Activity managed = screen; test.runOnMainSync(managed::finish);
            screen = test.startActivitySync(new Intent(context, TaskDetailActivity.class).putExtra("task_id", ordinary).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            test.waitForIdleSync(); SystemClock.sleep(600);
            root = screen.getWindow().getDecorView();
            require(text(root, "图片与文件") != null && text(root, "重新解析") != null, "ordinary captures retain attachments and reparse");
            capture(test, "homework-ordinary-retained-" + args.getString("theme", "light"));

            ZjuSnapshot.Homework item = new ZjuSnapshot.Homework("new", "robotics", "机器人与无人机基础 · 自主导航实验报告",
                    "比较不同环境中的路径规划，提交实验记录与分析。", "", "", System.currentTimeMillis()+86400000L, false);
            PendingIntent open = PendingIntent.getActivity(context, 80001, new Intent(context, TaskDetailActivity.class).putExtra("task_id", homework), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent close = PendingIntent.getBroadcast(context, 80002, new Intent(context, HomeworkNotice.class).setAction("close"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Activity preview = screen;
            for (boolean island : new boolean[]{false, true}) {
                test.runOnMainSync(() -> {
                    FrameLayout frame = new FrameLayout(preview);
                    frame.setBackgroundColor(UiStyle.colors(preview).surface);
                    View card = HomeworkNotice.card(preview, List.of(item), open, close, island).apply(preview, frame);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER);
                    frame.addView(card, params); preview.setContentView(frame);
                    require(card.findViewById(R.id.atomic_complete) == null && card.findViewById(R.id.atomic_location) == null, "new information-only style");
                    View x = card.findViewById(R.id.homework_close);
                    require("关闭作业通知".contentEquals(x.getContentDescription()), "close action has accessible meaning");
                });
                capture(test, "homework-card-" + (island ? "island-" : "shade-") + args.getString("theme", "light"));
            }
            long started = System.currentTimeMillis();
            HomeworkNotice.show(context, List.of(item),List.of(candidate));
            long finished = System.currentTimeMillis();
            var delivery = NotificationDeliveryStatus.read(context, "zju_homework_updates");
            var binding = new ZjuHomeworkStore(context).state();
            await(() -> active(context), "real homework notification posted; appAllowed=" + delivery.appAllowed
                    + ", channelAllowed=" + delivery.channelAllowed + ", sourceEnabled=" + binding.enabled
                    + ", capturePresent=" + (binding.captureId > 0));
            android.app.Notification notice = java.util.Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications())
                    .filter(n -> n.getId() == HomeworkNotice.ID).findFirst().orElseThrow().getNotification();
            long expiry = context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0);
            require(notice.getTimeoutAfter() > 0 && notice.getTimeoutAfter() <= HomeworkNotice.DURATION
                    && notice.getTimeoutAfter() >= expiry - finished && expiry >= started + HomeworkNotice.DURATION
                    && expiry <= finished + HomeworkNotice.DURATION
                    && notice.actions == null, "absolute ten-minute expiry and no completion action");
            new HomeworkNotice().onReceive(context, new Intent().setAction("expire").putExtra("expiry", expiry));
            require(active(context), "early expiry cannot dismiss a current notice");
            new HomeworkNotice().onReceive(context, new Intent().setAction("close").putExtra("expiry", expiry-1));
            require(active(context), "stale close cannot dismiss a newer notice");
            FrameLayout holder = new FrameLayout(context);
            View applied = notice.bigContentView.apply(context, holder);
            test.runOnMainSync(() -> applied.findViewById(R.id.homework_close).performClick());
            for (int i=0;i<30 && active(context);i++) SystemClock.sleep(50);
            require(!active(context), "real X PendingIntent dismisses the notification");
            try (TaskStore store = new TaskStore(context)) {
                require(store.getCandidatesByIds(List.of(candidate)).get(0).completedAtMillis == null, "X never completes homework");
                require(store.getTask(homework).rawText.equals("隐藏的学校原始内容"), "hidden source is preserved in storage");
            }
            if ("shade".equals(args.getString("scope")) || "batch_shade".equals(args.getString("scope"))) {
                String expectedSummary = null;
                if ("batch_shade".equals(args.getString("scope"))) {
                    long second;
                    try (TaskStore store = new TaskStore(context)) {
                        second = appendHomework(store, homework, account, "batch-second", "控制系统 · 实验数据分析");
                    }
                    ZjuSnapshot.Homework other = new ZjuSnapshot.Homework("second", "", "控制系统 · 实验数据分析", "", "", "", null, false);
                    HomeworkNotice.show(context, List.of(item, other), List.of(candidate, second));
                    expectedSummary = notice(context).extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
                    require(expectedSummary.contains("2 项") && expectedSummary.contains("截止"), "batch summary retains count and deadline");
                    View batchCard = notice(context).bigContentView.apply(context, new FrameLayout(context));
                    require(text(batchCard, expectedSummary) != null && text(batchCard, other.title) != null,
                            "expanded batch retains count, deadline and other title");
                } else HomeworkNotice.show(context, List.of(item),List.of(candidate));
                shade(test, item.title, args.getString("theme", "light"), expectedSummary);
                try (TaskStore store = new TaskStore(context)) {
                    require(store.getCandidatesByIds(List.of(candidate)).get(0).completedAtMillis == null,
                            "real notification-shade X never completes homework");
                }
            }
            if ("true".equals(args.getString("leave_timeout"))) {
                HomeworkNotice.show(context, List.of(item),List.of(candidate));
                boolean recorded = context.getSharedPreferences("homework_notice_check", 0).edit().putLong("expiry",
                        context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0))
                        .putLong("capture", homework).putLong("candidate", candidate).putString("account", account)
                        .putBoolean("before_enabled", before.enabled).putString("before_account", before.account)
                        .putLong("before_generation", before.generation).putLong("before_capture", before.captureId).commit();
                require(recorded, "timeout checkpoint persisted before test process exits");
                keepHomework = true;
            }
            return "PASS: managed sections hidden, ordinary sections retained, shade/island RemoteViews, 600000ms timeout, early/stale guards, real X action and unchanged completion/source";
        } finally {
            Activity done = screen; if (done != null) test.runOnMainSync(done::finish);
            if (!keepHomework) {
                long expiry = context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0);
                new HomeworkNotice().onReceive(context, new Intent().setAction("close").putExtra("expiry", expiry));
            }
            try (TaskStore store = new TaskStore(context)) {
                if (!keepHomework && homework > 0) store.deleteTask(homework);
                if (ordinary > 0) store.deleteTask(ordinary);
                if (!keepHomework) {
                    store.getWritableDatabase().delete("zju_homework_captures", "account=?", new String[]{account});
                    store.getWritableDatabase().delete("zju_homework_map", "account=?", new String[]{account});
                }
            }
            if (!keepHomework) restoreBinding(context, before.enabled, before.account, before.generation, before.captureId);
        }
    }

    /** Uses the same TaskCompletion entry point as the phone and web controls. */
    private static String lifecycle(Context context) throws Exception {
        ZjuHomeworkStore.State before = new ZjuHomeworkStore(context).state();
        String account = "notice-lifecycle-" + java.util.UUID.randomUUID();
        long capture = 0;
        try {
            long first, second;
            try (TaskStore store = new TaskStore(context)) {
                capture = store.insertTask("通知生命周期验收", null, ZjuHomeworkStore.SOURCE, System.currentTimeMillis());
                store.getWritableDatabase().execSQL("INSERT INTO zju_homework_captures(capture_id,account) VALUES(?,?)", new Object[]{capture, account});
                restoreBinding(context, true, account, before.generation + 1, capture);
                first = appendHomework(store, capture, account, "first", "第一项作业");
                second = appendHomework(store, capture, account, "second", "第二项作业");
                store.updateStatus(capture, TaskRecord.READY, null);
            }
            ZjuSnapshot.Homework one = new ZjuSnapshot.Homework("first", "", "第一项作业", "内容一", "", "", null, false);
            ZjuSnapshot.Homework two = new ZjuSnapshot.Homework("second", "", "第二项作业", "内容二", "", "", null, false);
            HomeworkNotice.show(context, List.of(one, two), List.of(first, second));
            require(active(context), "batch notice posted");
            var prefs = context.getSharedPreferences("homework_notice", 0);
            long expiry = prefs.getLong("expiry", 0);
            require(new org.json.JSONArray(prefs.getString("items", "[]")).length() == 2, "batch stores both identities");
            require(TaskCompletion.set(context, first, capture, true) == null, "first completion saved through production entry");
            await(() -> active(context) && savedCount(context) == 1, "completed batch item removed automatically");
            require(prefs.getLong("expiry", 0) == expiry, "partial completion never extends original expiry");
            org.json.JSONObject remaining = new org.json.JSONArray(prefs.getString("items", "[]")).getJSONObject(0);
            require(remaining.getLong("id") == second && remaining.getString("title").equals(two.title), "remaining item keeps exact identity and title");
            android.app.Notification posted = notice(context);
            require(two.title.contentEquals(posted.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))
                    && (posted.flags & android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0, "real host receives remaining title without another alert");
            require(posted.when + HomeworkNotice.DURATION == expiry && posted.getTimeoutAfter() <= HomeworkNotice.DURATION,
                    "replacement notification retains original clock");
            require(TaskCompletion.set(context, second, capture, true) == null, "last completion saved");
            await(() -> !active(context), "all completed homework automatically retracts notice");
            require(prefs.getLong("expiry", 0) == 0 && !prefs.contains("items"), "completion clears active notice state");
            try (TaskStore store = new TaskStore(context)) {
                var rows = store.getCandidatesByIds(List.of(first, second));
                require(rows.size() == 2 && rows.stream().allMatch(EventCandidate::completed), "notification cleanup preserves completed tasks");
            }
            TaskCompletion.set(context, first, capture, false);
            HomeworkNotice.show(context, List.of(one), List.of(first));
            restoreBinding(context, false, account, before.generation + 1, capture);
            AgendaReminder.requestRefresh(context);
            await(() -> !active(context), "closing homework source retracts notice");
            restoreBinding(context, true, account, before.generation + 1, capture);
            HomeworkNotice.show(context, List.of(one), List.of(first));
            restoreBinding(context, true, account, before.generation + 2, 0);
            AgendaReminder.requestRefresh(context);
            await(() -> !active(context), "replaced source binding retracts old notice");
            restoreBinding(context, true, account, before.generation + 1, capture);
            HomeworkNotice.show(context, List.of(one), List.of(first));
            try (TaskStore store = new TaskStore(context)) { store.deleteTask(capture); }
            AgendaReminder.requestRefresh(context);
            await(() -> !active(context), "deleted homework retracts notice");
            return "PASS: real SQLite completion entry/partial batch/identity/original expiry/quiet update/all-complete/source off/binding replacement/deletion";
        } finally {
            long expiry = context.getSharedPreferences("homework_notice", 0).getLong("expiry", 0);
            new HomeworkNotice().onReceive(context, new Intent().setAction("close").putExtra("expiry", expiry));
            try (TaskStore store = new TaskStore(context)) {
                if (capture > 0 && store.getTask(capture) != null) store.deleteTask(capture);
                store.getWritableDatabase().delete("zju_homework_captures", "account=?", new String[]{account});
                store.getWritableDatabase().delete("zju_homework_map", "account=?", new String[]{account});
            }
            restoreBinding(context, before.enabled, before.account, before.generation, before.captureId);
        }
    }
    private static long appendHomework(TaskStore store, long capture, String account, String key, String title) {
        long id = store.appendCandidate(new EventCandidate(0, capture, title, null, null, "Asia/Shanghai", "", "", null,
                false, null, "homework", false, false, 0, null, null, false));
        store.getWritableDatabase().execSQL("INSERT INTO zju_homework_map(account,remote_key,capture_id,candidate_id,ignored,snapshot,pending_calendar) VALUES(?,?,?,?,0,?,0)",
                new Object[]{account, key, capture, id, "{\"submitted\":false}"});
        return id;
    }
    private static void restoreBinding(Context context, boolean enabled, String account, long generation, long capture) {
        try (TaskStore store = new TaskStore(context)) {
            store.getWritableDatabase().execSQL("UPDATE managed_zju_state SET enabled=?,account=?,generation=?,capture_id=? WHERE id=1",
                    new Object[]{enabled ? 1 : 0, account, generation, capture > 0 ? capture : null});
        }
    }
    private static int savedCount(Context context) {
        try { return new org.json.JSONArray(context.getSharedPreferences("homework_notice", 0).getString("items", "[]")).length(); }
        catch (org.json.JSONException failure) { throw new AssertionError(failure); }
    }
    private static android.app.Notification notice(Context context) {
        return java.util.Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications())
                .filter(n -> n.getId() == HomeworkNotice.ID).findFirst().orElseThrow().getNotification();
    }
    private static void await(java.util.function.BooleanSupplier ready, String reason) {
        long until = SystemClock.uptimeMillis() + 5000;
        do { if (ready.getAsBoolean()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError(reason);
    }
    private static void shade(Instrumentation test, String title, String theme, String expectedSummary) throws Exception {
        android.accessibilityservice.AccessibilityServiceInfo service = test.getUiAutomation().getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        test.getUiAutomation().setServiceInfo(service);
        shell(test, "cmd statusbar expand-notifications");
        android.view.accessibility.AccessibilityNodeInfo expand = expand(test, title);
        if (android.text.TextUtils.equals("收起", expand.getContentDescription())) {
            require(expand.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "collapse real notification");
            SystemClock.sleep(500);
        }
        expand = expand(test, title);
        require(android.text.TextUtils.equals("展开", expand.getContentDescription()), "native collapsed state");
        if (expectedSummary != null) require(nodeText(test.getUiAutomation().getRootInActiveWindow(), expectedSummary) != null,
                "real collapsed batch count and deadline visible");
        capture(test, "homework-notification-collapsed-" + theme);
        require(expand.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "expand real notification");
        SystemClock.sleep(500);
        require(android.text.TextUtils.equals("收起", expand(test, title).getContentDescription()), "expanded state");
        if (expectedSummary != null) require(nodeText(test.getUiAutomation().getRootInActiveWindow(), expectedSummary) != null,
                "real expanded batch count and deadline visible");
        capture(test, "homework-notification-expanded-" + theme);
        android.view.accessibility.AccessibilityNodeInfo close = described(test.getUiAutomation().getRootInActiveWindow(), "关闭作业通知");
        require(close != null && close.isVisibleToUser(), "real expanded X is visible");
        require(close.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "real expanded X click");
        for (int i=0;i<40 && active(test.getTargetContext());i++) SystemClock.sleep(50);
        require(!active(test.getTargetContext()), "real notification shade X dismisses notice");
        shell(test, "cmd statusbar collapse");
    }
    private static android.view.accessibility.AccessibilityNodeInfo expand(Instrumentation test, String title) throws Exception {
        long until = SystemClock.uptimeMillis()+8000;
        do {
            android.view.accessibility.AccessibilityNodeInfo node = nodeText(test.getUiAutomation().getRootInActiveWindow(), title);
            while (node != null) {
                android.view.accessibility.AccessibilityNodeInfo button = viewId(node, "android:id/expand_button");
                if (button != null && button.isVisibleToUser()) return button;
                node = node.getParent();
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis()<until);
        throw new AssertionError("real notification title and expand control are visible");
    }
    private static android.view.accessibility.AccessibilityNodeInfo nodeText(android.view.accessibility.AccessibilityNodeInfo root, String value) {
        if (root == null) return null;
        if (android.text.TextUtils.equals(value, root.getText()) && root.isVisibleToUser()) return root;
        for (int i=0;i<root.getChildCount();i++) {var found=nodeText(root.getChild(i),value);if(found!=null)return found;}return null;
    }
    private static android.view.accessibility.AccessibilityNodeInfo viewId(android.view.accessibility.AccessibilityNodeInfo root, String value) {
        if (root == null) return null;
        if (value.equals(root.getViewIdResourceName())) return root;
        for (int i=0;i<root.getChildCount();i++) {var found=viewId(root.getChild(i),value);if(found!=null)return found;}return null;
    }
    private static android.view.accessibility.AccessibilityNodeInfo described(android.view.accessibility.AccessibilityNodeInfo root, String value) {
        if (root == null) return null;
        if (android.text.TextUtils.equals(value, root.getContentDescription())) return root;
        for (int i=0;i<root.getChildCount();i++) {var found=described(root.getChild(i),value);if(found!=null)return found;}return null;
    }
    private static void shell(Instrumentation test, String command) throws Exception {
        try (android.os.ParcelFileDescriptor result=test.getUiAutomation().executeShellCommand(command);
                java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)) {
            byte[] buffer=new byte[1024];while(in.read(buffer)!=-1) { }
        }
    }
    private static boolean active(Context context) { return java.util.Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n -> n.getId() == HomeworkNotice.ID); }
    private static TextView text(View root, String value) { if (root instanceof TextView t && value.contentEquals(t.getText())) return t; if (root instanceof ViewGroup g) for (int i=0;i<g.getChildCount();i++) { TextView t=text(g.getChildAt(i),value);if(t!=null)return t; } return null; }
    private static void capture(Instrumentation test, String name) throws Exception { test.waitForIdleSync();SystemClock.sleep(500);Bitmap b=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();} }
    private static void require(boolean value, String reason) { if(!value)throw new AssertionError(reason); }
}
