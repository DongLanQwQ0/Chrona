package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real window-dispatched touches; owns only synthetic records in the isolated shared AVD package. */
final class DashboardDockChecks {
    private static final int[] SECTIONS = {DashboardActivity.HOME, DashboardActivity.SCHEDULE, DashboardActivity.INBOX};
    private static final String[] LABELS = {"首页", "日程", "收件箱"};
    private final ClassroomMonitorInstrumentation test;
    private final Bundle args;
    private final Context app;
    private final Map<String, Map<String, ?>> saved = new LinkedHashMap<>();
    private final List<Long> tasks = new ArrayList<>();
    private final Map<Integer, ScrollView> pages = new HashMap<>();
    private final Map<Integer, Snapshot> scrolled = new HashMap<>();
    private final Map<Integer, Integer> offsets = new HashMap<>();
    private final JSONArray evidence = new JSONArray();
    private final String keyword = "导航滚动验收" + Long.toUnsignedString(System.nanoTime());
    private DashboardActivity screen;
    private DockNavigationLayout dock;
    private File output;
    private int checks, detachCount;
    private JSONObject dockReadyChrome, postIdleChrome;

    private DashboardDockChecks(ClassroomMonitorInstrumentation test, Bundle args) {
        this.test = test; this.args = args; app = test.getTargetContext();
    }

    static int run(ClassroomMonitorInstrumentation test, Bundle args) throws Exception {
        DashboardDockChecks c = new DashboardDockChecks(test, args);
        if (!"com.donglan.chrona.monitor.validation".equals(c.app.getPackageName())
                || !Build.HARDWARE.contains("ranchu")) throw new SecurityException("Owned AVD fixture only");
        // Each run gets one named output directory. Never overwrite another task's evidence.
        String directory = args.getString("outputDir", "");
        if (!directory.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))
            throw new IllegalArgumentException("Supply a fresh outputDir name under this package's externalFiles");
        File external = c.app.getExternalFilesDir(null);
        if (external == null) throw new IllegalStateException("Package externalFiles is unavailable");
        c.output = new File(external, directory);
        if (!c.output.mkdir()) throw new IllegalStateException("Output directory already exists or cannot be created: " + c.output);
        Throwable error = null;
        try { c.seed(); c.exercise(); return c.checks; }
        catch (Throwable failure) {
            error = failure;
            try { c.capture("failure"); } catch (Throwable captureFailure) { failure.addSuppressed(captureFailure); }
            throw failure;
        } finally {
            // finish() below deliberately detaches the tree; report only detaches during the exercise.
            int[] exercisedDetaches = {0};
            test.runOnMainSync(() -> exercisedDetaches[0] = c.detachCount);
            try {
                Activity resumed = test.lastResumed;
                if (resumed instanceof MainActivity) test.runOnMainSync(resumed::finish);
                if (c.screen != null) test.runOnMainSync(c.screen::finish);
                test.waitForIdleSync();
                try (TaskStore store = new TaskStore(c.app)) { for (long id : c.tasks) store.deleteTask(id); }
                for (var entry : c.saved.entrySet()) restore(c.app.getSharedPreferences(entry.getKey(), 0), entry.getValue());
            } catch (Throwable cleanupFailure) {
                if (error != null) error.addSuppressed(cleanupFailure);
                else { error = cleanupFailure; throw cleanupFailure; }
            } finally {
                JSONObject report = new JSONObject().put("assertions", c.checks).put("cases", c.evidence)
                        .put("orientation", args.getString("orientation", "portrait"))
                        .put("theme", args.getString("theme", "light"))
                        .put("nativeScreen", args.getString("nativeScreen", "false"))
                        .put("dockDetachCount", exercisedDetaches[0])
                        .put("cleanupDockDetachCount", c.detachCount - exercisedDetaches[0])
                        .put("failure", error == null ? JSONObject.NULL : android.util.Log.getStackTraceString(error));
                java.nio.file.Files.write(new File(c.output, "report.json").toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void seed() {
        CaptureDraftStore.Draft draft = new CaptureDraftStore(app).load();
        if (!draft.text.isEmpty() || !draft.images.isEmpty() || !draft.files.isEmpty())
            throw new SecurityException("Capture fixture requires an empty existing draft; preserve and finish that draft first");
        for (String name : List.of("chrona_zju", "appearance", "release_updates", "usage_guide", "home_timeline", "capture_draft", "startup_permissions"))
            saved.put(name, new HashMap<>(app.getSharedPreferences(name, 0).getAll()));
        app.getSharedPreferences("chrona_zju", 0).edit().clear().putBoolean("enabled", false).commit();
        app.getSharedPreferences("release_updates", 0).edit().putBoolean("automatic", false).commit();
        app.getSharedPreferences("usage_guide", 0).edit().putInt("version", Integer.MAX_VALUE).commit();
        app.getSharedPreferences("appearance", 0).edit().putString("mode", args.getString("theme", "light"))
                .putBoolean("background_enabled", false).commit();
        app.getSharedPreferences("home_timeline", 0).edit().putInt("item_limit", 50).putBoolean("include_system_calendar", false).commit();
        // Opening + exercises an empty capture without importing or submitting any content.
        app.getSharedPreferences("capture_draft", 0).edit().clear().commit();
        long now = System.currentTimeMillis();
        try (TaskStore store = new TaskStore(app)) {
            for (int i = 0; i < 51; i++) {
                String title = keyword + " 事项 " + i;
                long id = store.insertTask(title, null, "dock-scroll-fixture", now + i);
                tasks.add(id);
                store.appendCandidate(new EventCandidate(0, id, title, now + 3_600_000L + i * 60_000L,
                        now + 7_200_000L + i * 60_000L, "Asia/Shanghai", "验收教室", "", null, false));
                store.updateStatus(id, TaskRecord.READY, null);
            }
        }
    }

    private void exercise() throws Exception {
        screen = (DashboardActivity) test.startActivitySync(new Intent(app, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.HOME));
        await("first real home data", () -> bool(screen, "initialDataReady") && idle());
        dock = (DockNavigationLayout) field(screen, "navigation");
        test.runOnMainSync(() -> dock.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { detachCount++; }
        }));
        awaitDock();
        check(UiMotion.isEnabled(), "normal animation setting is required for held/release regression");
        evidence.put(new JSONObject().put("windowWidthDp", screen.getResources().getConfiguration().screenWidthDp)
                .put("windowHeightDp", screen.getResources().getConfiguration().screenHeightDp)
                .put("fontScale", screen.getResources().getConfiguration().fontScale)
                .put("wideSideDock", field(screen, "wide")));

        for (int index = 0; index < SECTIONS.length; index++) {
            if (index > 0) clickPage(index);
            scrollCase(SECTIONS[index], LABELS[index]);
        }
        // Back consumes real committed section history, returning to the retained scrolled trees.
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); awaitSection(DashboardActivity.SCHEDULE); verifyRetained(DashboardActivity.SCHEDULE, "back-schedule");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); awaitSection(DashboardActivity.HOME); verifyRetained(DashboardActivity.HOME, "back-home");
        clickPage(2); verifyRetained(DashboardActivity.INBOX, "revisit-inbox");
        clickPage(0); verifyRetained(DashboardActivity.HOME, "revisit-home");
        dragDock(0, 2, "first-to-last");
        dragDock(2, 0, "last-to-first");
        bodySwipe(true); awaitSection(DashboardActivity.SCHEDULE); verifyRetained(DashboardActivity.SCHEDULE, "body-next");
        bodySwipe(false); awaitSection(DashboardActivity.HOME); verifyRetained(DashboardActivity.HOME, "body-previous");
        check(detachCount == 0, "all scroll, capture return, click, drag and Back paths retain attached Dock instance");
    }

    private void scrollCase(int section, String label) throws Exception {
        awaitDock();
        ScrollView viewport = scroll(); pages.put(section, viewport);
        Snapshot baseline = snapshot(); assertFull(baseline, label + " initial"); capture("page-" + section + "-top");
        scrollPastThreshold(viewport);
        Snapshot after = snapshot(); assertSame(baseline, after, label + " >160dp");
        evidence.put(after.json().put("case", label + " scrolled").put("scrollY", viewport.getScrollY()).put("thresholdPx", dp(160)));
        capture("page-" + section + "-scrolled");
        if (section == DashboardActivity.HOME) captureRoundTrip(viewport, after);
        for (int attempt = 0; viewport.getScrollY() > 0 && attempt < 24; attempt++) verticalSwipe(viewport, false);
        await("actual touch returns to top", () -> viewport.getScrollY() == 0); awaitDock();
        assertSame(baseline, snapshot(), label + " touch returned to top"); capture("page-" + section + "-back-top");
        scrollPastThreshold(viewport);
        scrolled.put(section, snapshot()); offsets.put(section, viewport.getScrollY());
    }

    private void scrollPastThreshold(ScrollView viewport) throws Exception {
        for (int attempt = 0; viewport.getScrollY() <= dp(200) && attempt < 12; attempt++) verticalSwipe(viewport, true);
        await("real content scroll >160dp", () -> viewport.getScrollY() > dp(160));
        stableScroll(viewport); awaitDock();
        check(viewport == scroll(), "vertical scroll retains the active real ScrollView");
    }

    private void captureRoundTrip(ScrollView viewport, Snapshot before) throws Exception {
        View button = (View) field(chrome(), "captureButton");
        View host = (View) field(chrome(), "captureHost");
        check(button.isEnabled() && host.getAlpha() == 1f && host.getVisibility() == View.VISIBLE,
                "home + remains fully visible and enabled after >160dp scroll");
        int y = viewport.getScrollY(); tap(button);
        await("actual + opens capture", () -> test.lastResumed instanceof MainActivity && test.lastResumed.hasWindowFocus());
        check(test.lastResumed instanceof MainActivity, "actual home + touch opens MainActivity without submitting content");
        capture("capture-opened-from-scrolled-home");
        View back = description(test.lastResumed.getWindow().getDecorView(), "返回");
        check(back != null, "capture retains its real header Back control"); tap(back);
        await("capture Back returns to same Dashboard", () -> test.lastResumed == screen && screen.hasWindowFocus());
        awaitDock();
        check(viewport == scroll() && Math.abs(viewport.getScrollY() - y) <= 2, "capture return keeps the scrolled home tree and offset");
        assertSame(before, snapshot(), "capture return Dock");
    }

    private void clickPage(int index) throws Exception {
        boolean exiting = section() == DashboardActivity.HOME && index != 0;
        ChromeTrace trace = new ChromeTrace();
        test.runOnMainSync(() -> screen.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(trace));
        try { tap(dock.itemAt(index)); awaitSection(SECTIONS[index]); }
        finally { test.runOnMainSync(() -> screen.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(trace)); }
        if (exiting) check(trace.exitingDisabled, "page-switch + exit has a real intermediate frame and disables touch");
        evidence.put(new JSONObject().put("case", "actual Dock click " + LABELS[index])
                .put("exitIntermediateDisabled", trace.exitingDisabled).put("intermediateFrames", trace.intermediateFrames));
        capture("click-page-" + SECTIONS[index]);
    }

    private final class ChromeTrace implements ViewTreeObserver.OnPreDrawListener {
        boolean exitingDisabled;
        int intermediateFrames;
        @Override public boolean onPreDraw() {
            View host = (View) field(chrome(), "captureHost"), button = (View) field(chrome(), "captureButton");
            if (host.getAlpha() > .01f && host.getAlpha() < .99f) {
                intermediateFrames++;
                if (!button.isEnabled()) exitingDisabled = true;
            }
            return true;
        }
    }

    private void verifyRetained(int section, String suffix) throws Exception {
        stableScroll(scroll()); awaitDock();
        check(scroll() == pages.get(section), suffix + " retains its actual page tree");
        check(Math.abs(scroll().getScrollY() - offsets.get(section)) <= 2, suffix + " retains the previous reading offset");
        assertSame(scrolled.get(section), snapshot(), suffix + " full Dock"); capture(suffix);
    }

    private void dragDock(int from, int to, String suffix) throws Exception {
        awaitDock();
        Rect origin = bounds(dock.itemAt(from)), destination = bounds(dock.itemAt(to));
        boolean vertical = (Boolean) field(screen, "wide");
        float x = origin.centerX(), y = origin.centerY();
        final boolean[] coastObserved = {false};
        ViewTreeObserver.OnPreDrawListener observer = () -> { coastObserved[0] |= spring().coasting; return true; };
        test.runOnMainSync(() -> dock.getViewTreeObserver().addOnPreDrawListener(observer));
        long start = SystemClock.uptimeMillis(); pointer(start, MotionEvent.ACTION_DOWN, x, y);
        boolean released = false;
        try {
            await("real long press owns Dock drag", () -> dock.isTrackingTouch() && bool(dock, "dragging"));
            await("held Dock and lens reach their existing enlarged states", () -> dock.getScaleY() > 1.07f && spring().scale > 1.4f);
            capture(suffix + "-held");
            float extension = (vertical ? destination.height() : destination.width()) * .35f * (to > from ? 1 : -1);
            float endX = vertical ? destination.centerX() : destination.centerX() + extension;
            float endY = vertical ? destination.centerY() + extension : destination.centerY();
            Rect window = bounds(screen.getWindow().getDecorView());
            endX = Math.max(window.left + 2, Math.min(window.right - 2, endX));
            endY = Math.max(window.top + 2, Math.min(window.bottom - 2, endY));
            for (int step = 1; step <= 12; step++) {
                SystemClock.sleep(20); pointer(start, MotionEvent.ACTION_MOVE, x + (endX - x) * step / 12, y + (endY - y) * step / 12);
            }
            check(onMain(() -> dock.isTrackingTouch() && bool(dock, "dragging")), suffix + " drag remains owned beyond end cell");
            float position = spring().position;
            check(to > from ? position > 1f : position < 1f, suffix + " actual MOVE advances the lens toward the opposite end");
            capture(suffix + "-dragged");
            // Re-send an actual final movement before release after screenshot latency.
            pointer(start, MotionEvent.ACTION_MOVE, endX, endY);
            pointer(start, MotionEvent.ACTION_UP, endX, endY); released = true;
            awaitSection(SECTIONS[to]);
            check(coastObserved[0], suffix + " real release traverses the existing inertia/coast state");
            check(!dock.isTrackingTouch() && !dock.isAnimating(), suffix + " release converges and relinquishes touch ownership");
            Snapshot rest = snapshot(); assertFull(rest, suffix + " released");
            evidence.put(rest.json().put("case", suffix).put("heldLensPosition", position)
                    .put("releasedDestination", section()).put("springPosition", spring().position).put("coastObserved", coastObserved[0]));
            capture(suffix + "-released");
        } finally {
            if (!released) pointer(start, MotionEvent.ACTION_CANCEL, x, y);
            test.runOnMainSync(() -> { if (dock.getViewTreeObserver().isAlive()) dock.getViewTreeObserver().removeOnPreDrawListener(observer); });
        }
    }

    private void bodySwipe(boolean next) throws Exception {
        Rect area = bounds(scroll());
        float y = area.top + Math.min(area.height() * .35f, dp(80));
        gesture(area.left + area.width() * (next ? .78f : .22f), y,
                area.left + area.width() * (next ? .22f : .78f), y, 14);
    }

    private void verticalSwipe(ScrollView viewport, boolean downContent) throws Exception {
        Rect area = bounds(viewport);
        // Avoid bottom overlays in portrait; the same content coordinates work next to the landscape rail.
        Rect nav = bounds(dock);
        if (!(Boolean) field(screen, "wide")) area.bottom = Math.min(area.bottom, nav.top - dp(20));
        check(area.height() > dp(80), "real scroll viewport has usable touch space");
        float high = area.top + dp(20), low = area.bottom - dp(20), x = area.centerX();
        gesture(x, downContent ? low : high, x, downContent ? high : low, 16);
        stableScroll(viewport);
    }

    private void gesture(float fromX, float fromY, float toX, float toY, int steps) {
        long start = SystemClock.uptimeMillis(); pointer(start, MotionEvent.ACTION_DOWN, fromX, fromY);
        for (int step = 1; step <= steps; step++) {
            SystemClock.sleep(20); pointer(start, MotionEvent.ACTION_MOVE,
                    fromX + (toX - fromX) * step / steps, fromY + (toY - fromY) * step / steps);
        }
        // A short stationary tail avoids an uncontrolled list fling without changing production velocity handling.
        SystemClock.sleep(100); pointer(start, MotionEvent.ACTION_MOVE, toX, toY);
        pointer(start, MotionEvent.ACTION_UP, toX, toY);
    }

    private void tap(View view) {
        Rect target = bounds(view); long start = SystemClock.uptimeMillis();
        pointer(start, MotionEvent.ACTION_DOWN, target.centerX(), target.centerY()); SystemClock.sleep(45);
        pointer(start, MotionEvent.ACTION_UP, target.centerX(), target.centerY());
    }
    private void pointer(long start, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { test.sendPointerSync(event); } finally { event.recycle(); }
    }

    private void awaitSection(int expected) throws Exception {
        await("committed section " + expected, () -> section() == expected && idle()); awaitDock();
        JSONObject[] actual = {null}; boolean[] correct = {false};
        test.runOnMainSync(() -> {
            View host = (View) field(chrome(), "captureHost"), button = (View) field(chrome(), "captureButton");
            actual[0] = chromeStateOnMain();
            correct[0] = expected == DashboardActivity.HOME
                    ? host.getVisibility() == View.VISIBLE && host.getAlpha() == 1f && button.isEnabled()
                    : host.getVisibility() != View.VISIBLE && !button.isEnabled()
                        && host.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
        });
        JSONObject diagnostic = new JSONObject().put("case", "section chrome assertion").put("expectedSection", expected)
                .put("dockReadyObserved", dockReadyChrome).put("afterIdle", postIdleChrome).put("atAssertion", actual[0]);
        evidence.put(diagnostic);
        if (!correct[0]) {
            // Diagnostic only: never turn a failed settled-state assertion into a delayed pass.
            // One actual animation callback shows whether already-queued work changes the state.
            CountDownLatch frame = new CountDownLatch(1); JSONObject[] next = {null};
            test.runOnMainSync(() -> screen.getWindow().getDecorView().postOnAnimation(() -> {
                try { next[0] = chromeStateOnMain(); } finally { frame.countDown(); }
            }));
            boolean observed = frame.await(2, TimeUnit.SECONDS);
            diagnostic.put("nextAnimationFrameObserved", observed)
                    .put("nextAnimationFrame", next[0] == null ? JSONObject.NULL : next[0]);
        }
        check(correct[0], (expected == DashboardActivity.HOME
                ? "home + returns to its existing visible interactive state"
                : "non-home + remains hidden, untouchable and absent from accessibility") + "; state=" + diagnostic);
    }
    private void awaitDock() throws Exception {
        await("settled Dock/chrome", () -> {
            boolean ready = idle() && dock != null && !dock.isTrackingTouch() && !dock.isAnimating()
                    && field(chrome(), "animator") == null && dock.getWidth() > 0 && dock.itemCount() == 3;
            if (ready) dockReadyChrome = chromeStateOnMain();
            return ready;
        });
        test.runOnMainSync(() -> postIdleChrome = chromeStateOnMain());
    }
    /** Call only on the UI thread so cached flags and effective View state describe the same instant. */
    private JSONObject chromeStateOnMain() {
        try {
            Object value = chrome();
            View host = (View) field(value, "captureHost"), button = (View) field(value, "captureButton");
            android.animation.ValueAnimator animator = (android.animation.ValueAnimator) field(value, "animator");
            return new JSONObject().put("uptimeMs", SystemClock.uptimeMillis()).put("section", section())
                    .put("home", field(value, "home")).put("homeFraction", field(value, "homeFraction"))
                    .put("dockHomeFraction", field(value, "dockHomeFraction"))
                    .put("appliedInteractive", field(value, "appliedInteractive"))
                    .put("appliedVisibility", field(value, "appliedVisibility"))
                    .put("appliedPresence", field(value, "appliedPresence")).put("appliedBlur", field(value, "appliedBlur"))
                    .put("animatorNull", animator == null).put("animatorRunning", animator != null && animator.isRunning())
                    .put("animatorStarted", animator != null && animator.isStarted())
                    .put("hostAlpha", host.getAlpha()).put("hostVisibility", host.getVisibility())
                    .put("hostScaleX", host.getScaleX()).put("hostScaleY", host.getScaleY())
                    .put("hostAttached", host.isAttachedToWindow()).put("hostShown", host.isShown())
                    .put("hostAccessibility", host.getImportantForAccessibility())
                    .put("buttonEnabled", button.isEnabled()).put("buttonClickable", button.isClickable())
                    .put("buttonVisibility", button.getVisibility()).put("buttonAlpha", button.getAlpha())
                    .put("dockTracking", dock.isTrackingTouch()).put("dockAnimating", dock.isAnimating())
                    .put("pageTransitionRunning", field(screen, "pageTransitionRunning"))
                    .put("sectionLoadRunning", field(screen, "sectionLoadRunning"))
                    .put("scheduleResultLoading", field(screen, "scheduleResultLoading"));
        } catch (org.json.JSONException failure) { throw new AssertionError("Chrome diagnostic JSON", failure); }
    }
    private boolean idle() { return !bool(screen, "pageTransitionRunning") && !bool(screen, "sectionLoadRunning") && !bool(screen, "scheduleResultLoading"); }
    private int section() { return (Integer) field(screen, "section"); }
    private ScrollView scroll() { return (ScrollView) field(screen, "scroll"); }
    private Object chrome() { return field(screen, "dockChrome"); }
    private DockSpring spring() { return (DockSpring) field(dock, "spring"); }
    private int dp(int value) { return Math.round(value * screen.getResources().getDisplayMetrics().density); }
    private boolean onMain(BooleanSupplier condition) {
        boolean[] value = {false}; test.runOnMainSync(() -> value[0] = condition.getAsBoolean()); return value[0];
    }
    private void await(String label, BooleanSupplier condition) throws Exception {
        long until = SystemClock.uptimeMillis() + 15_000;
        while (!onMain(condition)) { if (SystemClock.uptimeMillis() > until) throw new AssertionError("Timed out: " + label); SystemClock.sleep(40); }
        test.waitForIdleSync();
    }
    private void stableScroll(ScrollView viewport) throws Exception {
        int[] previous = {-1}, stable = {0};
        await("scroll settles", () -> { int y = viewport.getScrollY(); stable[0] = y == previous[0] ? stable[0] + 1 : 0; previous[0] = y; return stable[0] >= 4; });
    }
    private static boolean bool(Object owner, String name) { return (Boolean) field(owner, name); }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    private static Rect bounds(View view) {
        Rect rect = new Rect(); if (!view.getGlobalVisibleRect(rect)) throw new AssertionError("Touch target is not visible: " + view); return rect;
    }
    private static View description(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = description(group.getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }

    private Snapshot snapshot() {
        Snapshot[] result = {null}; test.runOnMainSync(() -> result[0] = new Snapshot()); return result[0];
    }
    private final class Snapshot {
        final Rect rect = bounds(dock);
        final int width = dock.getWidth(), height = dock.getHeight();
        final float scaleX = dock.getScaleX(), scaleY = dock.getScaleY(), alpha = dock.getAlpha();
        final float x = dock.getTranslationX(), y = dock.getTranslationY();
        final List<View> cells = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        final List<Float> sizes = new ArrayList<>();
        Snapshot() {
            for (int i = 0; i < dock.itemCount(); i++) {
                View cell = dock.itemAt(i); cells.add(cell);
                TextView text = cell instanceof TextView value ? value : (TextView) ((ViewGroup) cell).getChildAt(0);
                labels.add(text); texts.add(text.getText().toString()); sizes.add(text.getTextSize());
            }
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("bounds", rect.toShortString()).put("width", width).put("height", height)
                    .put("scaleX", scaleX).put("scaleY", scaleY).put("alpha", alpha)
                    .put("translationX", x).put("translationY", y).put("labels", new JSONArray(texts));
        }
    }
    private void assertFull(Snapshot value, String label) {
        check(dock == field(screen, "navigation") && detachCount == 0, label + " retains original attached Dock");
        check(Math.abs(value.scaleX - 1f) < .001f && Math.abs(value.scaleY - 1f) < .001f
                && Math.abs(value.x) < .1f && Math.abs(value.y) < .1f && value.alpha == 1f, label + " has rest scale 1, full alpha and no corner translation");
        check(value.texts.equals(List.of(LABELS)), label + " preserves all three full labels");
        for (int i = 0; i < value.labels.size(); i++) {
            View cell = value.cells.get(i); TextView text = value.labels.get(i);
            check(cell.getVisibility() == View.VISIBLE && cell.getAlpha() == 1f && text.getVisibility() == View.VISIBLE
                    && text.getAlpha() == 1f && text.getTextSize() > 0, label + " visible opaque label " + i);
        }
    }
    private void assertSame(Snapshot before, Snapshot after, String label) {
        assertFull(after, label);
        check(before.rect.equals(after.rect) && before.width == after.width && before.height == after.height,
                label + " unchanged Dock position and dimensions");
        check(before.cells.equals(after.cells) && before.labels.equals(after.labels) && before.sizes.equals(after.sizes),
                label + " unchanged item/label instances and text sizes");
    }
    private void check(boolean value, String description) {
        checks++; if (!value) throw new AssertionError(description);
    }
    private void capture(String suffix) throws Exception {
        Bitmap original = test.getUiAutomation().takeScreenshot();
        if (original == null) throw new AssertionError("Screenshot unavailable: " + suffix);
        int width = Math.min(1000, original.getWidth());
        Bitmap preview = Bitmap.createScaledBitmap(original, width, Math.max(1, original.getHeight() * width / original.getWidth()), true);
        try (FileOutputStream out = new FileOutputStream(new File(output, suffix + ".jpg"))) { preview.compress(Bitmap.CompressFormat.JPEG, 82, out); }
        finally { if (preview != original) preview.recycle(); original.recycle(); }
    }
    @SuppressWarnings("unchecked")
    private static void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (var entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String text) edit.putString(key, text); else if (value instanceof Boolean bool) edit.putBoolean(key, bool);
            else if (value instanceof Integer number) edit.putInt(key, number); else if (value instanceof Long number) edit.putLong(key, number);
            else if (value instanceof Float number) edit.putFloat(key, number); else if (value instanceof Set<?> set) edit.putStringSet(key, (Set<String>) set);
        }
        edit.commit();
    }
}
