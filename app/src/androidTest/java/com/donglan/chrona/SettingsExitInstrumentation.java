package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.ai.SchedulePreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Local-only form exit checks. Run with the isolated v33 application and this runner. */
public final class SettingsExitInstrumentation extends Instrumentation {
    private Bundle arguments;
    private SettingsActivity current;
    private int assertions;
    @Override public void onCreate(Bundle value) {
        arguments = value == null ? new Bundle() : value;
        super.onCreate(value);
        start();
    }
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity activity = super.newActivity(loader, name, intent);
        if (SettingsActivity.class.getName().equals(name)) {
            FixtureIsolation.require(getTargetContext());
            int width = Integer.parseInt(arguments.getString("widthDp", "400"));
            float font = Float.parseFloat(arguments.getString("font", "1.0"));
            if (width < 320 || width > 600 || font < 1 || font > 2)
                throw new IllegalArgumentException("Invalid render configuration");
            var metrics = getTargetContext().getResources().getDisplayMetrics();
            Configuration config = new Configuration();
            config.densityDpi = Math.round(metrics.widthPixels * 160f / width);
            config.screenWidthDp = width;
            config.screenHeightDp = Math.round(metrics.heightPixels * 160f / config.densityDpi);
            config.smallestScreenWidthDp = width;
            config.fontScale = font;
            activity.applyOverrideConfiguration(config);
        }
        return activity;
    }
    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            check();
            report.putString("settings_exit_report", "PASS: " + assertions
                    + " assertions; all 8 form fields, reverted values, title/system back, cancel,"
                    + " dialog back/outside, discard, validation failures, save, recreation, immediate image reset");
            finish(-1, report);
        } catch (Throwable error) {
            report.putString("error", android.util.Log.getStackTraceString(error));
            finish(0, report);
        }
    }
    private void check() throws Exception {
        FixtureIsolation.require(getTargetContext());
        SharedPreferences ai = getTargetContext().getSharedPreferences("chrona_ai_settings", 0);
        SharedPreferences schedule = getTargetContext().getSharedPreferences("chrona_schedule_preferences", 0);
        SharedPreferences appearance = getTargetContext().getSharedPreferences("appearance", 0);
        Map<String, ?> oldAi = ai.getAll(), oldSchedule = schedule.getAll(), oldAppearance = appearance.getAll();
        try {
            appearance.edit().putString("mode", arguments.getString("mode", "light")).commit();
            new AiSettingsStore(getTargetContext()).save("https://synthetic.invalid/v1", "synthetic-model",
                    "chrona-settings-exit-synthetic", "auto");
            new SchedulePreferences(600, 1320, "初始日程", "初始课堂").save(getTargetContext());
            open();
            titleBack(); require(current.isFinishing(), "pristine title back exits");
            open(); systemBack(); require(current.isFinishing(), "pristine system back exits");
            open();
            String[] original = values();
            for (int i = 0; i < original.length; i++) {
                final int index = i;
                runOnMainSync(() -> edit(index, index == 4 ? "660" : index == 5 ? "1380"
                        : index == 3 ? "high" : original[index] + " changed"));
                if (i % 2 == 0) titleBack(); else systemBack();
                if (!prompt()) {
                    capture("failed-field-" + i);
                    throw new AssertionError("field " + i + " protects exit; finishing=" + current.isFinishing()
                            + "; dirty=" + !java.util.Arrays.equals(original, values())
                            + "; focused=" + current.hasWindowFocus());
                }
                assertions++;
                click("取消"); require(!current.isFinishing(), "cancel retains field " + i);
                runOnMainSync(() -> edit(index, original[index]));
            }
            titleBack(); require(current.isFinishing(), "all values reverted exits without prompt");

            open(); runOnMainSync(() -> edit(6, "未保存日程"));
            titleBack(); capture("exit-dialog");
            systemBack(); require(!current.isFinishing() && !prompt(), "dialog back keeps editing");
            titleBack(); tap(4, getTargetContext().getResources().getDisplayMetrics().heightPixels * .8f);
            settle(); require(!current.isFinishing() && !prompt(), "outside closes dialog without saving");
            require(SchedulePreferences.load(getTargetContext()).customPrompt.equals("初始日程"), "cancel does not persist");
            titleBack(); click("放弃"); require(current.isFinishing(), "discard exits");
            require(SchedulePreferences.load(getTargetContext()).customPrompt.equals("初始日程"), "discard leaves preferences");

            open(); runOnMainSync(() -> {
                edit(0, "https://new.synthetic.invalid/v1"); edit(1, "new-synthetic-model");
                edit(2, "replacement-synthetic-key"); edit(3, "high"); edit(4, "660");
                edit(5, "1380"); edit(6, "重建日程"); edit(7, "重建课堂");
            });
            String[] draft = values();
            ActivityMonitor monitor = addMonitor(SettingsActivity.class.getName(), null, false);
            runOnMainSync(current::recreate);
            current = (SettingsActivity) waitForMonitorWithTimeout(monitor, 10000);
            removeMonitor(monitor);
            require(current != null, "recreated activity delivered"); settle();
            require(java.util.Arrays.equals(draft, values()), "all draft fields survive recreation");
            titleBack(); require(prompt(), "original baseline survives recreation"); click("取消");
            runOnMainSync(() -> edit(5, "660")); titleBack(); click("保存");
            require(!current.isFinishing(), "invalid hours stay on page");
            require(SchedulePreferences.load(getTargetContext()).workStartMinutes == 600, "invalid save leaves preferences");
            runOnMainSync(() -> { edit(5, "1380"); edit(0, "http://invalid.invalid"); });
            titleBack(); click("保存"); require(!current.isFinishing(), "invalid endpoint stays on page");
            require(new AiSettingsStore(getTargetContext()).load().model.equals("synthetic-model"), "invalid endpoint leaves connection");
            runOnMainSync(() -> edit(0, "https://new.synthetic.invalid/v1"));
            titleBack(); click("保存"); require(current.isFinishing() && !prompt(), "successful save exits without second prompt");
            AiSettings saved = new AiSettingsStore(getTargetContext()).load();
            require(saved.model.equals("new-synthetic-model") && saved.reasoningEffort.equals("high")
                    && saved.apiKey.equals("replacement-synthetic-key"), "connection saved");
            SchedulePreferences prefs = SchedulePreferences.load(getTargetContext());
            require(prefs.workStartMinutes == 660 && prefs.workEndMinutes == 1380
                    && prefs.customPrompt.equals("重建日程") && prefs.classroomPrompt.equals("重建课堂"), "all preferences saved");
            new AiSettingsStore(getTargetContext()).markImageUnsupported(saved);
            open();
            runOnMainSync(() -> ((View) field(current, "allowImages")).performClick());
            require(!new AiSettingsStore(getTargetContext()).isImageUnsupported(saved), "image reset remains immediate");
            titleBack(); require(current.isFinishing(), "immediate image reset not a dirty form");
        } finally {
            if (current != null && !current.isDestroyed()) runOnMainSync(() -> {
                try { method("restoreForm", String[].class).invoke(current, (Object) field(current, "savedForm")); }
                catch (Exception error) { throw new IllegalStateException(error); }
                current.finish();
            });
            restore(ai, oldAi); restore(schedule, oldSchedule); restore(appearance, oldAppearance);
        }
    }
    private void open() {
        current = (SettingsActivity) startActivitySync(new Intent(getTargetContext(), SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); settle();
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(300); }
    private void systemBack() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle(); }
    private void titleBack() {
        View back = findBack(current.getWindow().getDecorView());
        require(back != null, "shared title back exists");
        int[] point = new int[2]; back.getLocationOnScreen(point);
        tap(point[0] + back.getWidth() / 2f, point[1] + back.getHeight() / 2f); settle();
    }
    private static View findBack(View root) {
        if ("返回".contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = findBack(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 65, MotionEvent.ACTION_UP, x, y, 0);
        try { sendPointerSync(down); sendPointerSync(up); } finally { down.recycle(); up.recycle(); }
    }
    private boolean prompt() {
        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews())
            if (findText(root, "保存修改？") != null) return true;
        return false;
    }
    private void click(String label) {
        TextView node = null;
        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
            TextView found = findText(root, label); if (found != null) { node = found; break; }
        }
        require(node != null, "dialog action " + label);
        int[] point = new int[2]; node.getLocationOnScreen(point);
        tap(point[0] + node.getWidth() / 2f, point[1] + node.getHeight() / 2f);
        settle();
    }
    private static TextView findText(View root, String text) {
        if (root instanceof TextView value && text.contentEquals(value.getText())) return value;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findText(group.getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private String[] values() throws Exception {
        final String[][] result = new String[1][];
        runOnMainSync(() -> { try { result[0] = (String[]) method("formValues").invoke(current); }
            catch (Exception error) { throw new IllegalStateException(error); } });
        return result[0];
    }
    private void edit(int index, String value) {
        try {
            String[] fields = {"baseUrl", "model", "apiKey", "reasoningEffort", "workStartMinutes",
                    "workEndMinutes", "customPrompt", "classroomPrompt"};
            if (index == 3) method("setEffort", String.class).invoke(current, value);
            else if (index == 4 || index == 5) {
                Field target = SettingsActivity.class.getDeclaredField(fields[index]); target.setAccessible(true);
                target.setInt(current, Integer.parseInt(value)); method("refreshWorkTime").invoke(current);
            } else ((EditText) field(current, fields[index])).setText(value);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static Object field(Object owner, String name) {
        try { Field value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = SettingsActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method;
    }
    private void require(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private void capture(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        File file = new File(getTargetContext().getExternalFilesDir(null), name + "-"
                + arguments.getString("mode", "light") + "-" + arguments.getString("widthDp", "400")
                + "-" + arguments.getString("font", "1.0") + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        finally { image.recycle(); }
    }
    private static void restore(SharedPreferences preferences, Map<String, ?> original) {
        SharedPreferences.Editor edit = preferences.edit().clear();
        for (var entry : original.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String text) edit.putString(key, text);
            else if (value instanceof Integer number) edit.putInt(key, number);
            else if (value instanceof Long number) edit.putLong(key, number);
            else if (value instanceof Float number) edit.putFloat(key, number);
            else if (value instanceof Boolean flag) edit.putBoolean(key, flag);
            else if (value instanceof java.util.Set<?> set) {
                java.util.Set<String> strings = new java.util.HashSet<>();
                for (Object item : set) strings.add((String) item);
                edit.putStringSet(key, strings);
            }
        }
        if (!edit.commit()) throw new IllegalStateException("Cannot restore fixture preferences");
    }
}
