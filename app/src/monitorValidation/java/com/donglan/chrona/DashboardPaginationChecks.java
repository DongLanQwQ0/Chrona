package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real parent touch dispatch and retained pagination rails; isolated AVD fixture only. */
final class DashboardPaginationChecks {
    private final Instrumentation test;
    private final Bundle args;
    private final Context app;
    private final String keyword = "分页验收" + Long.toUnsignedString(System.nanoTime());
    private final Map<String, Map<String, ?>> saved = new HashMap<>();
    private final List<Long> tasks = new ArrayList<>();
    private final JSONArray evidence = new JSONArray();
    private DashboardActivity screen;
    private int checks;

    private DashboardPaginationChecks(Instrumentation test, Bundle args) {
        this.test = test; this.args = args; app = test.getTargetContext();
    }

    static int run(Instrumentation test, Bundle args) throws Exception {
        DashboardPaginationChecks c = new DashboardPaginationChecks(test, args);
        if (!"com.donglan.chrona.monitor.validation".equals(c.app.getPackageName())
                || !Build.HARDWARE.contains("ranchu")) throw new SecurityException("Owned AVD only");
        Throwable error = null;
        try { c.seed(); c.list(true); c.list(false); return c.checks; }
        catch (Throwable failure) { error = failure; c.capture("failure"); throw failure; }
        finally {
            if (c.screen != null) test.runOnMainSync(c.screen::finish);
            try (TaskStore store = new TaskStore(c.app)) { for (long id : c.tasks) store.deleteTask(id); }
            for (var entry : c.saved.entrySet()) restore(c.app.getSharedPreferences(entry.getKey(), 0), entry.getValue());
            JSONObject report = new JSONObject().put("assertions", c.checks).put("cases", c.evidence)
                    .put("failure", error == null ? JSONObject.NULL : error.toString());
            java.nio.file.Files.write(new File(c.app.getExternalFilesDir(null), c.name("report") + ".json").toPath(),
                    report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void seed() throws Exception {
        for (String name : List.of("chrona_zju", "appearance", "release_updates", "usage_guide"))
            saved.put(name, new HashMap<>(app.getSharedPreferences(name, 0).getAll()));
        app.getSharedPreferences("chrona_zju", 0).edit().clear().putBoolean("enabled", false).commit();
        app.getSharedPreferences("release_updates", 0).edit().putBoolean("automatic", false).commit();
        app.getSharedPreferences("usage_guide", 0).edit().putInt("version", Integer.MAX_VALUE).commit();
        app.getSharedPreferences("appearance", 0).edit().putString("mode", args.getString("theme", "light"))
                .putBoolean("background_enabled", false).commit();
        long now = System.currentTimeMillis();
        try (TaskStore store = new TaskStore(app)) {
            for (int i = 0; i < 51; i++) {
                String text = keyword + " " + (i == 0 ? "唯一" : "事项") + i;
                long id = store.insertTask(text, null, "pagination-fixture", now + i);
                tasks.add(id);
                store.appendCandidate(new EventCandidate(0, id, text, now + 3_600_000L + i * 60_000L,
                        now + 7_200_000L + i * 60_000L, "Asia/Shanghai", "验收教室", "", null, false));
                store.updateStatus(id, TaskRecord.READY, null);
            }
        }
    }

    private void list(boolean schedule) throws Exception {
        int section = schedule ? DashboardActivity.SCHEDULE : DashboardActivity.INBOX;
        int pages = schedule ? 3 : 5;
        String prefix = schedule ? "schedule" : "inbox";
        screen = (DashboardActivity) test.startActivitySync(new Intent(app, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DashboardActivity.EXTRA_SECTION, section));
        await(() -> field(screen, "results") != null);
        EditText search = search(screen.getWindow().getDecorView());
        if (search == null) { await(() -> search(screen.getWindow().getDecorView()) != null); search = search(screen.getWindow().getDecorView()); }
        EditText input = search;
        test.runOnMainSync(() -> input.setText(keyword));
        await(() -> pageCount(schedule) == pages && !loading());
        DateIntervalBar bar = bar(schedule);
        check(bar != null && bar.getParent() == field(screen, "content"), prefix + " rail is the retained scrolling footer");
        ViewGroup content = (ViewGroup) field(screen, "content");
        check(content.indexOfChild(bar) == content.indexOfChild((View) field(screen, "results")) + 1,
                prefix + " footer immediately follows the rebuilt result list");
        int[] detached = {0};
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { detached[0]++; }
        });
        Map<?, ?> rows = (Map<?, ?>) field(screen, "inboxRows");
        check(!rows.isEmpty(), prefix + " real fixture rows loaded");
        Object first = rows.values().iterator().next();
        View firstCard = (View) field(first, "card");
        test.runOnMainSync(() -> {
            // Short windows can put the first record below the tools even at scroll zero.
            // Reveal a real press-sized area within the record instead of assuming its position.
            int targetHeight = Math.min(firstCard.getHeight(), Math.round(48 * firstCard.getResources().getDisplayMetrics().density));
            int top = Math.max(0, (firstCard.getHeight() - targetHeight) / 2);
            firstCard.requestRectangleOnScreen(new Rect(0, top, firstCard.getWidth(), top + targetHeight), true);
        });
        SystemClock.sleep(180);
        longPress(firstCard);
        Set<?> selection = selection(schedule);
        check(selection.size() == 1, prefix + " actual long press selects a row");
        Object selected = selection.iterator().next();
        boolean oldest = (Boolean) field(screen, schedule ? "scheduleOldestFirst" : "inboxOldestFirst");
        test.runOnMainSync(input::requestFocus);
        footer();
        check(!bar.getChildAt(1).isEnabled() && bar.getChildAt(2).isEnabled(), prefix + " first boundary arrows");
        checkRailLabel(bar, "1 / " + pages, prefix + " initial rendered page label");
        capture(prefix + "-first");
        tap(bar.getChildAt(2));
        await(() -> page(schedule) == 1 && !loading() && bar.text().equals("2 / " + pages));
        checkRailLabel(bar, "2 / " + pages, prefix + " next arrow rendered page label");
        check(bar == bar(schedule) && detached[0] == 0, prefix + " arrow keeps the attached rail while its text animates");
        check(search(screen.getWindow().getDecorView()) == input && input.hasFocus() && input.getText().toString().equals(keyword)
                && selection(schedule).contains(selected)
                && (Boolean) field(screen, schedule ? "scheduleOldestFirst" : "inboxOldestFirst") == oldest,
                prefix + " page change preserves search, order and cross-page selection");
        footer(); tap(bar.getChildAt(1));
        await(() -> page(schedule) == 0 && !loading());
        checkRailLabel(bar, "1 / " + pages, prefix + " previous arrow rendered page label");
        footer(); swipe(bar, true);
        await(() -> page(schedule) == 1 && !loading());
        checkRailLabel(bar, "2 / " + pages, prefix + " swipe rendered page label");
        check((Integer) field(screen, "section") == section, prefix + " real rail swipe does not switch the parent section");
        footer(); tap(bar);
        await(() -> node("第 " + pages + " 页") != null);
        tapNode("第 " + pages + " 页");
        await(() -> page(schedule) == pages - 1 && !loading() && bar.text().equals(pages + " / " + pages));
        checkRailLabel(bar, pages + " / " + pages, prefix + " page picker rendered last-page label");
        footer();
        check(!bar.getChildAt(2).isEnabled(), prefix + " common page picker reaches last page");
        swipe(bar, true); SystemClock.sleep(350);
        check(page(schedule) == pages - 1 && (Integer) field(screen, "section") == section,
                prefix + " last-page swipe is clamped without parent paging");
        checkRailLabel(bar, pages + " / " + pages, prefix + " last boundary rendered page label");
        capture(prefix + "-last");
        tap(bar); await(() -> node("第 1 页") != null); tapNode("第 1 页");
        await(() -> page(schedule) == 0 && !loading());
        checkRailLabel(bar, "1 / " + pages, prefix + " page picker rendered first-page label");
        footer(); swipe(bar, false); SystemClock.sleep(350);
        check(page(schedule) == 0 && (Integer) field(screen, "section") == section,
                prefix + " first-page reverse swipe is clamped");
        checkRailLabel(bar, "1 / " + pages, prefix + " first boundary rendered page label");
        test.runOnMainSync(() -> { input.requestFocus(); input.setText(keyword + " 唯一"); });
        await(() -> pageCount(schedule) == 1 && !loading() && bar.getVisibility() == View.GONE);
        check(page(schedule) == 0 && input.hasFocus() && search(screen.getWindow().getDecorView()) == input,
                prefix + " search returns to first page and keeps the focused input");
        checkRailLabel(bar, "1 / 1", prefix + " reduced search rendered page label");
        test.runOnMainSync(() -> input.setText(keyword));
        await(() -> pageCount(schedule) == pages && !loading() && bar.getVisibility() == View.VISIBLE);
        check(bar == bar(schedule) && detached[0] == 0, prefix + " hiding and restoring the rail never rebuilds or detaches it");
        checkRailLabel(bar, "1 / " + pages, prefix + " cleared search rendered page label");
        evidence.put(new JSONObject().put("list", prefix).put("pages", pages).put("railDetachCount", detached[0])
                .put("touch", "arrow, actual rail swipe, common page menu and both boundaries")
                .put("search", "same input and focus; reduced result resets and hides footer"));
        if (schedule) delayedRailResult();
        Activity finished = screen; test.runOnMainSync(finished::finish); SystemClock.sleep(400); screen = null;
    }

    /** A result arriving inside the 180ms incoming phase must reach the visible TextView. */
    private void delayedRailResult() throws Exception {
        check(UiMotion.isEnabled(), "delayed rail regression uses the normal public animation");
        ViewGroup content = (ViewGroup) field(screen, "content");
        DateIntervalBar[] rail = {null};
        Runnable[] delivery = {null};
        long[] shiftedAt = {0}, deliveredAt = {0};
        boolean[] incoming = {false};
        test.runOnMainSync(() -> {
            rail[0] = new DateIntervalBar(screen, () -> {}, direction -> {
                shiftedAt[0] = SystemClock.uptimeMillis();
                delivery[0] = () -> {
                    deliveredAt[0] = SystemClock.uptimeMillis();
                    incoming[0] = (Boolean) field(rail[0], "moving") && railLabel(rail[0]).getTranslationX() > 0f;
                    rail[0].setText("2 / 5");
                };
                rail[0].postDelayed(delivery[0], 170);
                return true;
            });
            rail[0].setText("1 / 5");
            content.addView(rail[0], new LinearLayout.LayoutParams(-1, -2));
        });
        try {
            footer();
            checkRailLabel(rail[0], "1 / 5", "delayed public rail initial rendered label");
            tap(rail[0].getChildAt(2));
            await(() -> deliveredAt[0] != 0 && !(Boolean) field(rail[0], "moving"));
            check(incoming[0], "170ms result actually arrived during the public rail incoming phase");
            checkRailLabel(rail[0], "2 / 5", "delayed public rail publishes latest desired text at animation end");
            check(rail[0].text().contentEquals(railLabel(rail[0]).getText()), "delayed public rail desired and actual label agree");
            evidence.put(new JSONObject().put("regression", "170ms result during incoming animation")
                    .put("deliveredAfterMs", deliveredAt[0] - shiftedAt[0]).put("incoming", incoming[0])
                    .put("desired", rail[0].text()).put("rendered", railLabel(rail[0]).getText().toString()));
        } finally {
            test.runOnMainSync(() -> { if (delivery[0] != null) rail[0].removeCallbacks(delivery[0]); content.removeView(rail[0]); });
        }
    }

    private static TextView railLabel(DateIntervalBar bar) {
        return (TextView) ((ViewGroup) bar.getChildAt(0)).getChildAt(0);
    }

    private void checkRailLabel(DateIntervalBar bar, String expected, String description) throws Exception {
        await(() -> !(Boolean) field(bar, "moving"));
        String[] actual = {null}; boolean[] settled = {false};
        test.runOnMainSync(() -> {
            TextView label = railLabel(bar);
            actual[0] = label.getText().toString();
            settled[0] = label.getAlpha() == 1f && Math.abs(label.getTranslationX()) < .5f;
        });
        check(expected.equals(actual[0]) && settled[0], description + ": expected " + expected + ", rendered " + actual[0]);
        evidence.put(new JSONObject().put("labelCheck", description).put("expected", expected).put("rendered", actual[0]));
    }

    private DateIntervalBar bar(boolean schedule) { return (DateIntervalBar) field(screen, schedule ? "schedulePagination" : "inboxPagination"); }
    private int page(boolean schedule) { return (Integer) field(screen, schedule ? "schedulePage" : "inboxPage"); }
    private int pageCount(boolean schedule) { return (Integer) field(screen, schedule ? "schedulePageCount" : "inboxPageCount"); }
    private Set<?> selection(boolean schedule) { return (Set<?>) field(screen, schedule ? "selectedScheduleIds" : "selectedInboxIds"); }
    private boolean loading() { return (Boolean) field(screen, "scheduleResultLoading"); }
    private void footer() throws Exception {
        test.runOnMainSync(() -> ((ScrollView) field(screen, "scroll")).fullScroll(View.FOCUS_DOWN));
        SystemClock.sleep(250); test.waitForIdleSync();
    }
    private static EditText search(View view) {
        if (view instanceof EditText input && input.getHint() != null && input.getHint().toString().startsWith("搜索")) return input;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { EditText found = search(group.getChildAt(i)); if (found != null) return found; }
        return null;
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    private void tap(View view) throws Exception { Rect bounds = new Rect(); check(view.getGlobalVisibleRect(bounds), "touch target is visible"); touch(bounds.centerX(), bounds.centerY(), false); }
    private void longPress(View view) throws Exception { Rect bounds = new Rect(); check(view.getGlobalVisibleRect(bounds), "long-press target is visible"); touch(bounds.centerX(), bounds.centerY(), true); }
    private void touch(float x, float y, boolean hold) {
        long start = SystemClock.uptimeMillis(); pointer(start, start, MotionEvent.ACTION_DOWN, x, y);
        SystemClock.sleep(hold ? android.view.ViewConfiguration.getLongPressTimeout() + 100 : 50);
        pointer(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
    }
    private void swipe(View view, boolean next) {
        Rect bounds = new Rect(); check(view.getGlobalVisibleRect(bounds), "rail swipe target is visible");
        float distance = Math.min(bounds.width() * .3f, 110 * view.getResources().getDisplayMetrics().density);
        float from = bounds.centerX() + (next ? distance / 2 : -distance / 2), to = bounds.centerX() + (next ? -distance / 2 : distance / 2);
        long start = SystemClock.uptimeMillis(); pointer(start, start, MotionEvent.ACTION_DOWN, from, bounds.centerY());
        for (int i = 1; i <= 10; i++) { SystemClock.sleep(20); pointer(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, from + (to - from) * i / 10, bounds.centerY()); }
        pointer(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, to, bounds.centerY());
    }
    private void pointer(long start, long now, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, now, action, x, y, 0); event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { test.sendPointerSync(event); } finally { event.recycle(); }
    }
    private AccessibilityNodeInfo node(String text) {
        AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo item : matches) if (item.isVisibleToUser() && text.contentEquals(item.getText())) return item;
        return null;
    }
    private void tapNode(String text) {
        AccessibilityNodeInfo item = node(text); if (item == null) throw new AssertionError("Missing menu item: " + text);
        Rect bounds = new Rect(); item.getBoundsInScreen(bounds); touch(bounds.centerX(), bounds.centerY(), false);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long until = SystemClock.uptimeMillis() + 12_000;
        while (!condition.getAsBoolean()) { if (SystemClock.uptimeMillis() > until) throw new AssertionError("Timed out waiting for pagination state"); SystemClock.sleep(60); }
        test.waitForIdleSync(); SystemClock.sleep(320);
    }
    private String name(String suffix) { return "dashboard-pagination-" + args.getString("orientation", "portrait") + "-" + args.getString("theme", "light") + "-" + suffix; }
    private void capture(String suffix) throws Exception {
        Bitmap original = test.getUiAutomation().takeScreenshot(); if (original == null) return;
        int width = Math.min(1000, original.getWidth());
        Bitmap preview = Bitmap.createScaledBitmap(original, width, Math.max(1, original.getHeight() * width / original.getWidth()), true);
        try (FileOutputStream out = new FileOutputStream(new File(app.getExternalFilesDir(null), name(suffix) + ".jpg"))) { preview.compress(Bitmap.CompressFormat.JPEG, 82, out); }
        if (preview != original) preview.recycle(); original.recycle();
    }
    private void check(boolean value, String description) { checks++; if (!value) throw new AssertionError(description); }
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
