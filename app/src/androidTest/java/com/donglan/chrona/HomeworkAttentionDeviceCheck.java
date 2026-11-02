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
            int targetIndex = Integer.parseInt(args.getString("targetIndex", Integer.toString(count - 1)));
            long target = result.newHomeworkIds.get(targetIndex);
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
            for (int i = 0; i < count; i++) if ("focus_frames".equals(scope)
                    ? i != targetIndex : i < count - unseen) managed.markSeen(result.newHomeworkIds.get(i));
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
            if ("focus_frames".equals(scope)) return focusFrames(test, context, opened[0], homeButton, target, targetIndex, args);
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
            require("新作业".equals(card.getStateDescription()) && highlight(opened[0],card) != null, "centered row carries visible focus");
            capture(test, context, "homework-centered-" + args.getString("theme", "light"));
            if (Boolean.parseBoolean(args.getString("reduced", "false"))) {
                android.graphics.drawable.Drawable emphasized = highlight(opened[0],card);
                SystemClock.sleep(2200);
                test.runOnMainSync(() -> {});
                require(card.getStateDescription() == null && highlight(opened[0],card) != emphasized,
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
    /** Observe every actual draw, including transient list replacement and scroll restoration. */
    private static String focusFrames(Instrumentation test, Context context, Activity activity, View button,
                                      long target, int targetIndex, Bundle args) throws Exception {
        String mode = args.getString("refresh", "none"), label = args.getString("label", "frames");
        boolean reduced = Boolean.parseBoolean(args.getString("reduced","false"));
        if (reduced) require(!UiMotion.isEnabled(), "real system reduced-motion branch is active");
        File directory = new File(context.getCacheDir(), "homework-focus99");
        require(directory.isDirectory() || directory.mkdirs(), "frame evidence directory exists");
        File output = new File(directory, label + ".json");
        org.json.JSONArray frames = new org.json.JSONArray();
        long started = SystemClock.uptimeMillis();
        long[] firstHighlight = {0}; boolean[] observing = {true}; Throwable[] observationError = {null};
        boolean independentHighlight;
        try { activity.getClass().getDeclaredField("homeworkHighlight"); independentHighlight = true; }
        catch (NoSuchFieldException original98) { independentHighlight = false; }
        final boolean overlayHighlight = independentHighlight;
        View decor = activity.getWindow().getDecorView();
        ViewTreeObserver.OnDrawListener observer = () -> {
            if (!observing[0]) return;
            try {
                ScrollView scroll = (ScrollView)field(activity, "scroll");
                Object row = ((Map<?, ?>)field(activity, "inboxRows")).get(target);
                View card = row == null ? null : (View)field(row, "card");
                View results = (View)field(activity,"results");
                int[] xy = new int[2], vp = new int[2]; scroll.getLocationOnScreen(vp);
                android.graphics.Rect visible = new android.graphics.Rect();
                boolean shown = card != null && card.isAttachedToWindow() && card.getGlobalVisibleRect(visible);
                if (card != null) card.getLocationOnScreen(xy);
                android.graphics.drawable.Drawable emphasis = overlayHighlight
                        ? field(activity,"homeworkHighlightCard") == card ? (android.graphics.drawable.Drawable)field(activity,"homeworkHighlight") : null
                        : card == null ? null : card.getForeground();
                boolean emphasized = card != null && "新作业".equals(card.getStateDescription()) && emphasis != null;
                boolean destinationVisible = false;
                if (Integer.valueOf(DashboardActivity.SCHEDULE).equals(field(activity,"section")))
                    for (Object mapped : ((Map<?, ?>)field(activity,"inboxRows")).values()) {
                        View candidate = (View)field(mapped,"card");
                        if (candidate.isAttachedToWindow() && visibleAlpha(candidate) > .001f
                                && candidate.getGlobalVisibleRect(new android.graphics.Rect())) { destinationVisible = true; break; }
                    }
                long now = SystemClock.uptimeMillis();
                if (emphasized && firstHighlight[0] == 0) firstHighlight[0] = now;
                frames.put(new JSONObject().put("ms", now-started).put("card", card == null ? 0 : System.identityHashCode(card))
                        .put("attached", card != null && card.isAttachedToWindow()).put("visible", shown)
                        .put("windowFocus", activity.hasWindowFocus()).put("scrollY", scroll.getScrollY())
                        .put("centerY", card == null ? JSONObject.NULL : xy[1]+card.getHeight()/2f)
                        .put("viewportCenterY", vp[1]+scroll.getHeight()/2f).put("height", card == null ? 0 : card.getHeight())
                        .put("cardAlpha", card == null ? -1 : card.getAlpha()).put("cardTranslationY",card == null ? 0 : card.getTranslationY())
                        .put("emphasized", emphasized).put("alpha", emphasized ? emphasis.getAlpha() : -1)
                        .put("destinationContentVisible",destinationVisible).put("highlightPresent",emphasis != null)
                        .put("highlightOwner",overlayHighlight && field(activity,"homeworkHighlightCard") != null ? System.identityHashCode(field(activity,"homeworkHighlightCard")) : 0)
                        .put("foregroundType", card == null || card.getForeground() == null ? "" : card.getForeground().getClass().getSimpleName())
                        .put("resultsAlpha", results == null ? -1 : results.getAlpha())
                        .put("resultsTranslationY", results == null ? 0 : results.getTranslationY())
                        .put("scrollAlpha", scroll.getAlpha()).put("scrollTranslationY",scroll.getTranslationY())
                        .put("pending", field(activity,"pendingHomeworkFocus")).put("page", field(activity,"schedulePage"))
                        .put("transition", field(activity,"pageTransitionRunning")).put("loading", field(activity,"scheduleResultLoading")));
            } catch (Throwable failure) { observationError[0] = failure; }
        };
        Runnable pulse = new Runnable() { @Override public void run() { if (observing[0]) { decor.invalidate(); decor.postOnAnimation(this); } } };
        test.runOnMainSync(() -> { decor.getViewTreeObserver().addOnDrawListener(observer); decor.postOnAnimation(pulse); });
        boolean refreshed = false, capturedHighlight = false, capturedAfterRefresh = false;
        try {
            if (Boolean.parseBoolean(args.getString("cancelReturn","false"))) {
                int[] point = new int[2]; test.runOnMainSync(() -> button.getLocationOnScreen(point));
                long down = SystemClock.uptimeMillis();
                for (int action : new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_CANCEL}) {
                    MotionEvent event = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,
                            point[0]+button.getWidth()/2f,point[1]+button.getHeight()/2f,0);
                    try { test.sendPointerSync(event); } finally { event.recycle(); }
                }
                test.runOnMainSync(() -> {});
                require(Integer.valueOf(DashboardActivity.HOME).equals(field(activity,"section"))
                        && ((Number)field(activity,"pendingHomeworkFocus")).longValue() == 0
                        && new ZjuHomeworkStore(context).newestUnread() == target,
                        "cancelled touch neither navigates nor consumes new homework");
            }
            tap(test, button);
            long deadline = started+10000;
            while (SystemClock.uptimeMillis() < deadline) {
                test.runOnMainSync(() -> {});
                if (observationError[0] != null) throw new AssertionError("draw observation failed", observationError[0]);
                long first = firstHighlight[0], now = SystemClock.uptimeMillis();
                if (Boolean.parseBoolean(args.getString("captures", "false")) && first > 0) {
                    if (!capturedHighlight && now-first >= 80) { capturedHighlight = true; captureJpeg(test,directory,label+"-highlight"); }
                    if (!capturedAfterRefresh && now-first >= 1000) { capturedAfterRefresh = true; captureJpeg(test,directory,label+"-after-refresh"); }
                }
                if (first > 0 && !refreshed && now-first >= 400) {
                    refreshed = true;
                    if ("local".equals(mode)) test.runOnMainSync(() -> invoke(activity, "refreshCompletionRows"));
                    if ("resume".equals(mode)) {
                        shell(test,"input keyevent KEYCODE_HOME");
                        await(test, () -> !activity.hasWindowFocus(), "real home pauses focused activity");
                        test.runOnMainSync(() -> context.startActivity(new Intent(context, DashboardActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
                        await(test, activity::hasWindowFocus, "same activity resumes from real home");
                    }
                }
                if (first > 0 && now-first >= UiMotion.ENTER+2600) break;
                SystemClock.sleep(8);
            }
        } finally {
            test.runOnMainSync(() -> { observing[0] = false; decor.removeCallbacks(pulse);
                if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnDrawListener(observer); });
        }
        int highlighted = 0, offCenter = 0, gaps = 0, offCenterContent = 0, fadeFrames = 0, minAlpha = 255, maxAlpha = 0;
        long first = -1, last = -1, firstContent = -1, firstClear = -1; Set<Integer> cards = new HashSet<>();
        for (int i=0;i<frames.length();i++) {
            JSONObject f = frames.getJSONObject(i);
            if (f.getBoolean("destinationContentVisible")) {
                if (firstContent < 0) firstContent = f.getLong("ms");
                if (f.isNull("centerY") || Math.abs(f.getDouble("centerY")-f.getDouble("viewportCenterY")) > 3) offCenterContent++;
            }
            if (f.getBoolean("emphasized")) {
                if (first < 0) first = f.getLong("ms"); last = f.getLong("ms"); highlighted++;
                cards.add(f.getInt("card"));
                minAlpha = Math.min(minAlpha,f.getInt("alpha")); maxAlpha = Math.max(maxAlpha,f.getInt("alpha"));
                if (f.getInt("alpha") > 0 && f.getInt("alpha") < 250) fadeFrames++;
                if (f.getBoolean("windowFocus") && Math.abs(f.getDouble("centerY")-f.getDouble("viewportCenterY")) > 3) offCenter++;
            } else if (first >= 0) {
                if (firstClear < 0) firstClear = f.getLong("ms");
                if (f.getLong("ms")-first < (reduced ? 1968 : UiMotion.ENTER+1900) && f.getBoolean("windowFocus")) gaps++;
            }
        }
        JSONObject report = new JSONObject().put("label",label).put("mode",mode).put("target",target).put("targetIndex",targetIndex)
                .put("frames",frames).put("highlightedFrames",highlighted).put("highlightCardInstances",cards.size())
                .put("firstHighlightMs",first).put("lastHighlightMs",last).put("highlightSpanMs",last-first)
                .put("firstDestinationContentMs",firstContent).put("offCenterContentFrames",offCenterContent).put("fadeFrames",fadeFrames)
                .put("reducedMotion",reduced).put("firstClearAfterMs",firstClear-first)
                .put("minimumHighlightAlpha",minAlpha).put("maximumHighlightAlpha",maxAlpha)
                .put("offCenterHighlightFrames",offCenter).put("earlyMissingHighlightFrames",gaps).put("evidence",output.getPath());
        try (var out = new FileOutputStream(output)) { out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        JSONObject summary = new JSONObject(report.toString()); summary.remove("frames");
        Bundle progress = new Bundle(); progress.putString("stream", "focus_frames: "+summary+"\n");
        // Keep the structured artifact even on assertion failure.
        test.sendStatus(0, progress);
        require(highlighted > 0, "target has an actual emphasized draw: " + output);
        require(firstContent >= 0 && offCenterContent == 0, "first and later destination content draws are already positioned: " + offCenterContent + "; " + output);
        require(offCenter == 0, "highlight must never draw at an intermediate scroll offset: " + offCenter + "; " + output);
        require(gaps == 0 && last-first >= (reduced ? 1968 : UiMotion.ENTER+1900),
                "highlight survives full ENTER hold and 2000ms fade without row-rebuild loss: span="+(last-first)+" gaps="+gaps+"; "+output);
        JSONObject finalFrame = frames.getJSONObject(frames.length()-1);
        require((reduced ? minAlpha == 255 && maxAlpha == 255 && firstClear-first >= 1968 && firstClear-first <= 2100 : fadeFrames > 0)
                        && !finalFrame.getBoolean("emphasized") && (!overlayHighlight || !finalFrame.getBoolean("highlightPresent")),
                "highlight visibly fades and clears its independent overlay: " + output);
        if (Boolean.parseBoolean(args.getString("cancelReturn","false"))) {
            shell(test,"input keyevent KEYCODE_BACK");
            await(test,() -> Integer.valueOf(DashboardActivity.HOME).equals(field(activity,"section"))
                    && !Boolean.TRUE.equals(field(activity,"pageTransitionRunning")),"actual back returns to home");
            View ordinary = decor.findViewWithTag("home-homework");
            require(ordinary != null && !ordinary.getContentDescription().toString().contains("新作业"),
                    "return retains consumed red-dot state");
            tap(test,ordinary);
            await(test,() -> Integer.valueOf(DashboardActivity.SCHEDULE).equals(field(activity,"section"))
                    && !Boolean.TRUE.equals(field(activity,"pageTransitionRunning"))
                    && !Boolean.TRUE.equals(field(activity,"scheduleResultLoading")),"ordinary homework entry remains available");
            require(((Number)field(activity,"pendingHomeworkFocus")).longValue() == 0
                    && (!overlayHighlight || field(activity,"homeworkHighlight") == null),
                    "ordinary entry does not replay consumed focus");
            report.put("cancelTouchPreservedUnread",true).put("backReturnedHome",true).put("ordinaryEntryNoReplay",true);
            try (var out = new FileOutputStream(output)) { out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        }
        report.remove("frames"); return report.put("assertions", assertions).toString();
    }
    private static float visibleAlpha(View view) {
        float alpha = 1f;
        for (View current=view; current!=null; current=current.getParent() instanceof View ? (View)current.getParent() : null)
            alpha *= current.getVisibility() == View.VISIBLE ? current.getAlpha() : 0f;
        return alpha;
    }
    private static android.graphics.drawable.Drawable highlight(Activity activity, View card) {
        try { activity.getClass().getDeclaredField("homeworkHighlight"); }
        catch (NoSuchFieldException original98) { return card.getForeground(); }
        return field(activity,"homeworkHighlightCard") == card
                ? (android.graphics.drawable.Drawable)field(activity,"homeworkHighlight") : null;
    }
    private static void captureJpeg(Instrumentation test, File directory, String name) throws Exception {
        Bitmap image = test.getUiAutomation().takeScreenshot();
        if (image == null) throw new AssertionError("No screenshot");
        Bitmap preview = Bitmap.createScaledBitmap(image, Math.round(image.getWidth()*Math.min(1f,720f/image.getWidth())),
                Math.round(image.getHeight()*Math.min(1f,720f/image.getWidth())), true);
        try (var out = new FileOutputStream(new File(directory,name+".jpg"))) { preview.compress(Bitmap.CompressFormat.JPEG,78,out); }
        finally { if (preview != image) preview.recycle(); image.recycle(); }
    }
    private static void invoke(Object target, String method) {
        try { var call = target.getClass().getDeclaredMethod(method); call.setAccessible(true); call.invoke(target); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
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
