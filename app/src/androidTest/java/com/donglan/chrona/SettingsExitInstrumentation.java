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
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
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
    private Activity introductionActivity;
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
            if(Boolean.parseBoolean(arguments.getString("nativeScreen","false")))return activity;
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
            boolean cellularOnly = Boolean.parseBoolean(arguments.getString("cellularOnly", "false"));
            boolean protocolOnly = Boolean.parseBoolean(arguments.getString("protocolOnly", "false"));
            if (protocolOnly) checkProtocolOnly(); else if (cellularOnly) checkCellularOnly(); else check();
            report.putString("settings_exit_report", protocolOnly ? "PASS: " + assertions
                    + " assertions; native landscape protocol field/menu, real Chat Completions choice changes draft only, rotation retains draft/baseline, cancel protects exit, scrolling title/save reachability, save/reopen retains protocol and key; orientation and preferences restored"
                    : cellularOnly ? "PASS: " + assertions
                    + " assertions; classroom mobile data native default/tap/cancel/discard/save both states, landscape window with retained draft/baseline, reachable switch/save and scrolling header; orientation restored"
                    : "PASS: " + assertions
                    + " assertions; all 13 form fields, protocol default/native selection/key retention/image-rejection identity, classroom mobile data native toggle/default/draft/save/discard, note strength native drag/info navigation/draft/save/discard, concurrency, enhanced classroom, reverted values, title/system back, cancel,"
                    + " dialog back/outside, discard, validation failures, save, recreation, immediate image reset");
            finish(-1, report);
        } catch (Throwable error) {
            report.putString("error", android.util.Log.getStackTraceString(error));
            finish(0, report);
        }
    }
    private void checkProtocolOnly() throws Exception {
        FixtureIsolation.require(getTargetContext());
        require(Boolean.parseBoolean(arguments.getString("nativeScreen", "false")),
                "protocol-only validation requires the actual native screen configuration");
        SharedPreferences ai = getTargetContext().getSharedPreferences("chrona_ai_settings", 0);
        SharedPreferences schedule = getTargetContext().getSharedPreferences("chrona_schedule_preferences", 0);
        SharedPreferences appearance = getTargetContext().getSharedPreferences("appearance", 0);
        Map<String, ?> oldAi = ai.getAll(), oldSchedule = schedule.getAll(), oldAppearance = appearance.getAll();
        int originalRequest = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        int originalOrientation = Configuration.ORIENTATION_UNDEFINED;
        boolean restoreOrientation = false;
        try {
            appearance.edit().putString("mode", arguments.getString("mode", "dark")).commit();
            new AiSettingsStore(getTargetContext()).save("https://synthetic.invalid/v1", "synthetic-model",
                    "chrona-settings-exit-synthetic", "auto");
            new SchedulePreferences(600, 1320, "初始日程", "初始课堂").save(getTargetContext());
            open();
            originalRequest = current.getRequestedOrientation();
            originalOrientation = current.getResources().getConfiguration().orientation;
            restoreOrientation = true;
            rotateSettings(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE);
            View decor = current.getWindow().getDecorView();
            require(current.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                    && decor.getWidth() > decor.getHeight(), "protocol evidence uses an actual visible landscape window");
            TextView protocolChoice = (TextView)field(current, "protocolChoice");scrollTo(protocolChoice);
            requireFullyVisible(protocolChoice, "landscape protocol choice is fully visible");
            require("Responses".contentEquals(protocolChoice.getText()), "landscape protocol defaults Responses");
            capture("ai-protocol-only-landscape-field");tapCenter(protocolChoice);
            TextView chatOption = null, responseOption = null;
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                if (findText(window,"API 协议") == null) continue;
                chatOption = findText(window,"Chat Completions");
                responseOption = findText(window,"Responses");
                if (chatOption != null && responseOption != null) break;
            }
            require(chatOption != null && responseOption != null, "landscape shared menu exposes both protocols");
            requireFullyVisible(chatOption, "landscape Chat Completions option is fully visible");
            requireFullyVisible(responseOption, "landscape Responses option is fully visible");
            capture("ai-protocol-only-landscape-menu");click("Chat Completions");
            require(AiSettings.CHAT_COMPLETIONS.equals(values()[12])
                    && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "real landscape protocol choice updates only draft");
            String[] draft = values();
            rotateSettings(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT);
            require(java.util.Arrays.equals(draft, values())
                    && AiSettings.RESPONSES.equals(((String[])field(current,"savedForm"))[12]),
                    "portrait recreation retains protocol draft and original baseline");
            rotateSettings(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE);
            require(java.util.Arrays.equals(draft, values())
                    && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "returning to landscape retains draft without saving");
            titleBack();require(prompt(), "rotated protocol draft protects exit");click("取消");
            require(!current.isFinishing() && AiSettings.CHAT_COMPLETIONS.equals(values()[12]),
                    "cancel retains rotated protocol draft");
            TextView save = findText(current.getWindow().getDecorView(), "保存设置");
            require(save != null, "landscape save action exists");scrollTo(save);
            requireFullyVisible(save, "landscape save action is fully scroll reachable");
            TextView title = findText(current.getWindow().getDecorView(), "AI 服务设置");
            require(title != null && !title.getGlobalVisibleRect(new Rect()), "landscape title scrolls outside viewport");
            tapCenter(save);
            require(current.isFinishing() && AiSettings.CHAT_COMPLETIONS.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "landscape save commits selected protocol");
            open();protocolChoice = (TextView)field(current, "protocolChoice");scrollTo(protocolChoice);
            requireFullyVisible(protocolChoice, "reopened protocol choice is reachable");
            AiSettings saved = new AiSettingsStore(getTargetContext()).load();
            require("Chat Completions".contentEquals(protocolChoice.getText())
                    && AiSettings.CHAT_COMPLETIONS.equals(values()[12])
                    && saved.apiKey.equals("chrona-settings-exit-synthetic"),
                    "reopened form retains saved protocol and existing key");
        } finally {
            try {
                if (current != null && !current.isDestroyed() && !current.isFinishing()) {
                    if (prompt()) systemBack();
                    if (restoreOrientation) rotateSettings(originalRequest, originalOrientation);
                    runOnMainSync(() -> {
                        try { method("restoreForm", String[].class).invoke(current, (Object)field(current, "savedForm")); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        current.finish();
                    });
                }
            } finally {
                try { restore(ai, oldAi); }
                finally { try { restore(schedule, oldSchedule); } finally { restore(appearance, oldAppearance); } }
            }
        }
    }
    private void checkCellularOnly() throws Exception {
        FixtureIsolation.require(getTargetContext());
        SharedPreferences ai = getTargetContext().getSharedPreferences("chrona_ai_settings", 0);
        SharedPreferences schedule = getTargetContext().getSharedPreferences("chrona_schedule_preferences", 0);
        SharedPreferences appearance = getTargetContext().getSharedPreferences("appearance", 0);
        Map<String, ?> oldAi = ai.getAll(), oldSchedule = schedule.getAll(), oldAppearance = appearance.getAll();
        int originalRequest = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        int originalOrientation = Configuration.ORIENTATION_UNDEFINED;
        boolean restoreOrientation = false;
        try {
            appearance.edit().putString("mode", arguments.getString("mode", "light")).commit();
            new AiSettingsStore(getTargetContext()).save("https://synthetic.invalid/v1", "synthetic-model",
                    "chrona-settings-exit-synthetic", "auto");
            new SchedulePreferences(600, 1320, "初始日程", "初始课堂").save(getTargetContext());
            open();
            originalRequest = current.getRequestedOrientation();
            originalOrientation = current.getResources().getConfiguration().orientation;
            Switch cellular = (Switch)field(current, "classroomCellular");
            scrollTo(cellular); requireFullyVisible(cellular, "mobile data switch is fully scroll reachable");
            require("AI总结使用移动数据".contentEquals(cellular.getText()) && cellular.isChecked(),
                    "mobile data switch has exact label and defaults on");
            capture("classroom-cellular-only-default");tapCenter(cellular);
            require("false".equals(values()[11]) && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "real switch tap changes only draft");
            titleBack();require(prompt(), "mobile data draft protects title exit");click("取消");
            require(!current.isFinishing() && "false".equals(values()[11])
                    && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "cancel retains mobile data draft without saving");
            String[] draft = values();
            restoreOrientation = true;
            rotateSettings(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE);
            require(java.util.Arrays.equals(draft, values())
                    && "true".equals(((String[])field(current, "savedForm"))[11])
                    && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "landscape settings retains mobile data draft and original baseline");
            cellular = (Switch)field(current, "classroomCellular");scrollTo(cellular);
            requireFullyVisible(cellular, "landscape mobile data switch is fully scroll reachable");
            require(cellular.getHeight() >= Math.round(54 * current.getResources().getDisplayMetrics().density),
                    "landscape switch retains 54dp touch height");
            TextView title = findText(current.getWindow().getDecorView(), "AI 服务设置");
            require(title != null && !title.getGlobalVisibleRect(new Rect()),
                    "landscape scrolling moves settings title outside viewport");
            capture("classroom-cellular-only-landscape");
            TextView save = findText(current.getWindow().getDecorView(), "保存设置");
            require(save != null, "save action exists in landscape");scrollTo(save);
            requireFullyVisible(save, "landscape save action remains fully scroll reachable");
            capture("classroom-cellular-only-landscape-save");
            rotateSettings(originalRequest, originalOrientation);restoreOrientation = false;
            require(java.util.Arrays.equals(draft, values()), "restoring orientation retains mobile data draft");
            systemBack();require(prompt(), "mobile data draft protects system exit after rotation");click("放弃");
            require(current.isFinishing() && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "discard after rotation leaves saved mobile data on");
            open();cellular = (Switch)field(current, "classroomCellular");scrollTo(cellular);tapCenter(cellular);
            save = findText(current.getWindow().getDecorView(), "保存设置");scrollTo(save);
            requireFullyVisible(save, "save action is reachable for explicit disabled state");tapCenter(save);
            require(current.isFinishing() && !SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "visible save button persists disabled mobile data");
            open();cellular = (Switch)field(current, "classroomCellular");scrollTo(cellular);
            require(!cellular.isChecked(), "reopened form shows saved mobile data off");tapCenter(cellular);
            titleBack();require(prompt(), "enabling mobile data protects exit");click("保存");
            require(current.isFinishing() && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "exit dialog save persists enabled mobile data");
        } finally {
            try {
                if (current != null && !current.isDestroyed() && !current.isFinishing()) {
                    if (restoreOrientation) rotateSettings(originalRequest, originalOrientation);
                    runOnMainSync(() -> {
                        try { method("restoreForm", String[].class).invoke(current, (Object)field(current, "savedForm")); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        current.finish();
                    });
                }
            } finally { restore(ai, oldAi);restore(schedule, oldSchedule);restore(appearance, oldAppearance); }
        }
    }
    private void rotateSettings(int request, int orientation) {
        SettingsActivity previous = current;
        boolean changesOrientation = previous.getResources().getConfiguration().orientation != orientation;
        ActivityMonitor monitor = changesOrientation ? addMonitor(SettingsActivity.class.getName(), null, false) : null;
        try {
            runOnMainSync(() -> previous.setRequestedOrientation(request));
            if (changesOrientation) {
                Activity next = waitForMonitorWithTimeout(monitor, 10000);
                require(next instanceof SettingsActivity && next != previous, "orientation change recreates settings Activity");
                current = (SettingsActivity)next;
            }
        } finally { if (monitor != null) removeMonitor(monitor); }
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            waitForIdleSync();
            if (current.getResources().getConfiguration().orientation == orientation && current.hasWindowFocus()) break;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        require(current.getResources().getConfiguration().orientation == orientation && current.hasWindowFocus(),
                "requested orientation is visible and settings owns window focus");
        settle();
    }
    private void requireFullyVisible(View view, String label) {
        Rect visible = new Rect();
        require(view.getGlobalVisibleRect(visible) && visible.width() == view.getWidth()
                && visible.height() == view.getHeight(), label);
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
            require(original.length == 13 && "false".equals(original[8]) && "3".equals(original[9])
                    && "3".equals(original[10]) && "true".equals(original[11]) && AiSettings.RESPONSES.equals(original[12]),
                    "enhanced classroom, concurrency, note strength, mobile data and Responses defaults participate in form baseline");
            checkProtocolStore();
            TextView protocolChoice = (TextView)field(current,"protocolChoice");
            scrollTo(protocolChoice);requireFullyVisible(protocolChoice,"protocol choice is fully reachable");
            require("Responses".contentEquals(protocolChoice.getText()),"protocol choice defaults to Responses");
            capture("ai-protocol-field");tapCenter(protocolChoice);capture("ai-protocol-choices");click("Chat Completions");
            require(AiSettings.CHAT_COMPLETIONS.equals(values()[12])
                    && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "native protocol choice updates draft without saving");
            titleBack();require(prompt(),"protocol draft protects title exit");click("取消");
            require(AiSettings.CHAT_COMPLETIONS.equals(values()[12]),"cancel retains native protocol draft");
            runOnMainSync(() -> edit(12,AiSettings.RESPONSES));
            Switch cellular = (Switch)field(current,"classroomCellular");
            scrollTo(cellular);
            require("AI总结使用移动数据".contentEquals(cellular.getText()) && cellular.isChecked(),
                    "native mobile data switch has exact label and defaults on");
            require(cellular.getHeight() >= Math.round(54 * current.getResources().getDisplayMetrics().density),
                    "mobile data switch retains 54dp touch height");
            capture("classroom-cellular-field");tapCenter(cellular);
            require("false".equals(values()[11]) && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "native mobile data switch tap updates draft without saving");
            tapCenter(cellular);
            dragNoteStrength(2);
            SeekBar strengthSlider=(SeekBar)field(current,"classroomNoteStrengthSlider");
            int touchSize=Math.round(48*current.getResources().getDisplayMetrics().density);
            String sliderActual="; actual min="+strengthSlider.getMin()+" (expected 1), max="+strengthSlider.getMax()
                    +" (expected 3), progress="+strengthSlider.getProgress()
                    +" (expected 2), height="+strengthSlider.getHeight()+"px (expected >= "+touchSize+"px / 48dp), width="
                    +strengthSlider.getWidth()+"px, padding="+strengthSlider.getPaddingLeft()+"/"+strengthSlider.getPaddingRight()
                    +"px, thumbOffset="+strengthSlider.getThumbOffset()+"px, layoutDirection="+strengthSlider.getLayoutDirection();
            require(strengthSlider.getMin()==1 && strengthSlider.getMax()==3,"native strength slider has stops one through three"+sliderActual);
            require(strengthSlider.getHeight()>=touchSize,"native strength slider retains 48dp touch height"+sliderActual);
            require(strengthSlider.getProgress()==2,"native strength touch drag reaches level two"+sliderActual);
            assertNoteStrengthAccessibility(strengthSlider,2);
            require("2".equals(values()[10]) && SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 3,
                    "native strength drag updates draft without saving");
            require("笔记整理强度 · 2档".contentEquals(((TextView)field(current,"classroomNoteStrengthLabel")).getText()),
                    "native strength drag updates level label");
            ActivityMonitor introduction = addMonitor(ClassroomNoteStrengthActivity.class.getName(), null, false);
            View info = (View)field(current,"classroomNoteStrengthInfo");
            require("笔记整理强度介绍".contentEquals(info.getContentDescription()),"strength info has accessible name");
            scrollTo(info);tapCenter(info);
            require(info.getWidth()==touchSize && info.getHeight()==touchSize,"strength introduction retains 48dp icon target");
            introductionActivity = waitForMonitorWithTimeout(introduction, 10000);removeMonitor(introduction);
            require(introductionActivity instanceof ClassroomNoteStrengthActivity,"strength info opens dedicated introduction Activity");
            settle();capture("classroom-note-strength-introduction");verifyIntroduction();systemBack();
            require(!current.isFinishing() && current.hasWindowFocus() && "2".equals(values()[10])
                    && SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 3,
                    "return from introduction retains unsaved strength draft");
            dragNoteStrength(3);assertNoteStrengthAccessibility(strengthSlider,3);capture("classroom-note-strength-field");
            runOnMainSync(() -> { View choice=(View)field(current,"classroomConcurrencyChoice");choice.requestRectangleOnScreen(new Rect(0,0,choice.getWidth(),choice.getHeight()),true); });
            settle();capture("classroom-concurrency-field");click("课堂总结并发数  3");capture("classroom-concurrency-choices");
            click("2");
            require("2".equals(values()[9]) && SchedulePreferences.load(getTargetContext()).classroomConcurrency == 3,
                    "shared choice changes concurrency draft without persisting");
            runOnMainSync(() -> edit(9, "3"));
            for (int i = 0; i < original.length; i++) {
                final int index = i;
                runOnMainSync(() -> edit(index, index == 4 ? "660" : index == 5 ? "1380"
                        : index == 3 ? "high" : index == 8 ? "true" : index == 11 ? "false"
                        : index == 12 ? AiSettings.CHAT_COMPLETIONS
                        : index == 9 || index == 10 ? "2" : original[index] + " changed"));
                if (i % 2 == 0) titleBack(); else systemBack();
                if (!prompt()) {
                    capture("failed-field-" + i);
                    throw new AssertionError("field " + i + " protects exit; finishing=" + current.isFinishing()
                            + "; dirty=" + !java.util.Arrays.equals(original, values())
                            + "; focused=" + current.hasWindowFocus());
                }
                assertions++;
                click("取消"); require(!current.isFinishing(), "cancel retains field " + i);
                if (i == 8) {
                    require("true".equals(values()[8]), "cancel retains enhanced classroom draft");
                    require(!SchedulePreferences.load(getTargetContext()).enhancedClassroom,
                            "changing and cancelling enhanced classroom does not persist");
                }
                if (i == 9) require("2".equals(values()[9])
                        && SchedulePreferences.load(getTargetContext()).classroomConcurrency == 3,
                        "cancel retains unsaved concurrency draft");
                if (i == 10) require("2".equals(values()[10])
                        && SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 3,
                        "cancel retains unsaved strength draft");
                if (i == 11) require("false".equals(values()[11])
                        && SchedulePreferences.load(getTargetContext()).classroomCellular,
                        "cancel retains unsaved mobile data draft");
                if (i == 12) require(AiSettings.CHAT_COMPLETIONS.equals(values()[12])
                        && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                        "cancel retains unsaved protocol draft");
                runOnMainSync(() -> edit(index, original[index]));
            }
            titleBack(); require(current.isFinishing(), "all values reverted exits without prompt");

            open(); runOnMainSync(() -> { edit(6, "未保存日程"); edit(8, "true"); edit(9, "1"); edit(10, "1"); edit(11, "false"); edit(12,AiSettings.CHAT_COMPLETIONS); });
            titleBack(); capture("exit-dialog");
            systemBack(); require(!current.isFinishing() && !prompt(), "dialog back keeps editing");
            titleBack(); tap(4, getTargetContext().getResources().getDisplayMetrics().heightPixels * .8f);
            settle(); require(!current.isFinishing() && !prompt(), "outside closes dialog without saving");
            require(SchedulePreferences.load(getTargetContext()).customPrompt.equals("初始日程"), "cancel does not persist");
            require("true".equals(values()[8]) && !SchedulePreferences.load(getTargetContext()).enhancedClassroom,
                    "dialog back and outside dismissal retain unsaved enhanced classroom draft");
            require("false".equals(values()[11]) && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "dialog back and outside dismissal retain unsaved mobile data draft");
            require(AiSettings.CHAT_COMPLETIONS.equals(values()[12])
                    && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "dialog back and outside dismissal retain protocol draft without saving");
            titleBack(); click("放弃"); require(current.isFinishing(), "discard exits");
            require(SchedulePreferences.load(getTargetContext()).customPrompt.equals("初始日程"), "discard leaves preferences");
            require(!SchedulePreferences.load(getTargetContext()).enhancedClassroom,
                    "discard leaves enhanced classroom disabled");
            require(SchedulePreferences.load(getTargetContext()).classroomConcurrency == 3,
                    "discard leaves saved concurrency unchanged");
            require(SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 3,
                    "discard leaves saved note strength unchanged");
            require(SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "discard leaves saved mobile data enabled");
            require(AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "discard preserves original protocol");

            open(); runOnMainSync(() -> {
                edit(0, "https://new.synthetic.invalid/v1"); edit(1, "new-synthetic-model");
                edit(2, "replacement-synthetic-key"); edit(3, "high"); edit(4, "660");
                edit(5, "1380"); edit(6, "重建日程"); edit(7, "重建课堂");
                edit(8, "true"); edit(9, "2"); edit(10, "2"); edit(11, "false");
                edit(12,AiSettings.CHAT_COMPLETIONS);
            });
            String[] draft = values();
            ActivityMonitor monitor = addMonitor(SettingsActivity.class.getName(), null, false);
            runOnMainSync(current::recreate);
            current = (SettingsActivity) waitForMonitorWithTimeout(monitor, 10000);
            removeMonitor(monitor);
            require(current != null, "recreated activity delivered"); settle();
            require(java.util.Arrays.equals(draft, values()), "all draft fields survive recreation");
            require("false".equals(((String[]) field(current, "savedForm"))[8])
                    && "3".equals(((String[]) field(current, "savedForm"))[9])
                    && "3".equals(((String[]) field(current, "savedForm"))[10])
                    && "true".equals(((String[]) field(current, "savedForm"))[11])
                    && AiSettings.RESPONSES.equals(((String[])field(current,"savedForm"))[12])
                    && AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol)
                    && !SchedulePreferences.load(getTargetContext()).enhancedClassroom
                    && SchedulePreferences.load(getTargetContext()).classroomConcurrency == 3
                    && SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 3
                    && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "recreation retains original preference baseline without saving draft");
            titleBack(); require(prompt(), "original baseline survives recreation"); click("取消");
            runOnMainSync(() -> edit(9, "4")); titleBack(); click("保存");
            require(!current.isFinishing() && SchedulePreferences.load(getTargetContext()).classroomConcurrency == 3,
                    "invalid concurrency save stays on page and preserves preferences");
            runOnMainSync(() -> edit(9, "2"));
            runOnMainSync(() -> edit(5, "660")); titleBack(); click("保存");
            require(!current.isFinishing(), "invalid hours stay on page");
            require(SchedulePreferences.load(getTargetContext()).workStartMinutes == 600, "invalid save leaves preferences");
            require(!SchedulePreferences.load(getTargetContext()).enhancedClassroom,
                    "invalid save does not persist enhanced classroom draft");
            require(AiSettings.RESPONSES.equals(new AiSettingsStore(getTargetContext()).load().protocol),
                    "invalid save does not persist protocol draft");
            require(SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "invalid save does not persist mobile data draft");
            runOnMainSync(() -> { edit(5, "1380"); edit(0, "http://invalid.invalid"); });
            titleBack(); click("保存"); require(!current.isFinishing(), "invalid endpoint stays on page");
            require(new AiSettingsStore(getTargetContext()).load().model.equals("synthetic-model"), "invalid endpoint leaves connection");
            runOnMainSync(() -> edit(0, "https://new.synthetic.invalid/v1"));
            titleBack(); click("保存"); require(current.isFinishing() && !prompt(), "successful save exits without second prompt");
            AiSettings saved = new AiSettingsStore(getTargetContext()).load();
            require(saved.model.equals("new-synthetic-model") && saved.reasoningEffort.equals("high")
                    && saved.apiKey.equals("replacement-synthetic-key") && AiSettings.CHAT_COMPLETIONS.equals(saved.protocol),
                    "connection and selected Chat Completions protocol saved");
            SchedulePreferences prefs = SchedulePreferences.load(getTargetContext());
            require(prefs.workStartMinutes == 660 && prefs.workEndMinutes == 1380
                    && prefs.customPrompt.equals("重建日程") && prefs.classroomPrompt.equals("重建课堂")
                    && prefs.enhancedClassroom && prefs.classroomConcurrency == 2 && prefs.classroomNoteStrength == 2
                    && !prefs.classroomCellular,
                    "all preferences including enhanced classroom, concurrency, note strength and mobile data saved");
            new AiSettingsStore(getTargetContext()).markImageUnsupported(saved);
            open();
            runOnMainSync(() -> ((View) field(current, "allowImages")).performClick());
            require(!new AiSettingsStore(getTargetContext()).isImageUnsupported(saved), "image reset remains immediate");
            titleBack(); require(current.isFinishing(), "immediate image reset not a dirty form");
            open();
            require("true".equals(values()[8]) && "2".equals(values()[9]) && "2".equals(values()[10])
                    && "false".equals(values()[11]) && AiSettings.CHAT_COMPLETIONS.equals(values()[12]),
                    "reopened form reflects saved enhanced classroom, concurrency, note strength, mobile data and protocol");
            runOnMainSync(() -> edit(8, "false"));
            titleBack(); require(prompt(), "disabling enhanced classroom protects exit");
            click("保存"); require(current.isFinishing(), "saving disabled enhanced classroom exits");
            require(!SchedulePreferences.load(getTargetContext()).enhancedClassroom,
                    "explicit save persists enhanced classroom disabled");
            open();dragNoteStrength(1);assertNoteStrengthAccessibility((SeekBar)field(current,"classroomNoteStrengthSlider"),1);
            titleBack();require(prompt(),"native strength change protects exit");
            click("保存");require(current.isFinishing() && SchedulePreferences.load(getTargetContext()).classroomNoteStrength == 1,
                    "explicit save persists native strength slider value");
            open();cellular=(Switch)field(current,"classroomCellular");scrollTo(cellular);tapCenter(cellular);
            titleBack();require(prompt(),"enabling mobile data protects exit");
            click("保存");require(current.isFinishing() && SchedulePreferences.load(getTargetContext()).classroomCellular,
                    "explicit save persists mobile data enabled");
            open();protocolChoice=(TextView)field(current,"protocolChoice");scrollTo(protocolChoice);tapCenter(protocolChoice);click("Responses");
            titleBack();require(prompt(),"switching back to Responses protects exit");click("保存");
            AiSettings restoredProtocol=new AiSettingsStore(getTargetContext()).load();
            require(current.isFinishing() && AiSettings.RESPONSES.equals(restoredProtocol.protocol)
                    && restoredProtocol.apiKey.equals("replacement-synthetic-key"),
                    "protocol-only save with blank key retains existing secret");
        } finally {
            if (introductionActivity != null && !introductionActivity.isDestroyed()) runOnMainSync(introductionActivity::finish);
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
    private void checkProtocolStore() throws Exception {
        AiSettingsStore store = new AiSettingsStore(getTargetContext());
        AiSettings response = store.load();
        require(AiSettings.RESPONSES.equals(response.protocol),"original store save entry defaults Responses");
        store.save(response.baseUrl,response.model,response.apiKey,response.reasoningEffort,AiSettings.CHAT_COMPLETIONS);
        AiSettings chat = store.load();
        require(AiSettings.CHAT_COMPLETIONS.equals(chat.protocol),"store persists explicit Chat Completions protocol");
        store.markImageUnsupported(chat);
        require(store.isImageUnsupported(chat) && !store.isImageUnsupported(response),
                "learned image rejection is scoped to Chat Completions protocol");
        store.markImageUnsupported(response);
        require(store.isImageUnsupported(response) && !store.isImageUnsupported(chat),
                "learned image rejection is scoped to Responses protocol");
        store.clearImageSupport();
        require(getTargetContext().getSharedPreferences("chrona_ai_settings",0).edit().remove("protocol").commit(),
                "old configuration fixture removes protocol field");
        AiSettings legacy = store.load();
        require(AiSettings.RESPONSES.equals(legacy.protocol) && legacy.apiKey.equals(response.apiKey),
                "old stored configuration missing protocol loads Responses with key preserved");
    }
    private void verifyIntroduction()throws Exception{
        for(int level=1;level<=3;level++){
            String label=level+"档 · "+com.donglan.chrona.classroom.ClassroomNoteStrength.label(level);
            TextView heading=findText(introductionActivity.getWindow().getDecorView(),label);
            require(heading!=null,"introduction includes same-source result "+level);scrollTo(heading);
            android.graphics.Rect visible=new android.graphics.Rect();require(heading.getGlobalVisibleRect(visible),"example heading is scroll reachable "+level);
            capture("classroom-note-strength-example-"+level);
        }
        Activity previous=introductionActivity;ScrollView reader=(ScrollView)field(previous,"viewport");int before=reader.getScrollY();
        ActivityMonitor restored=addMonitor(ClassroomNoteStrengthActivity.class.getName(),null,false);
        runOnMainSync(previous::recreate);introductionActivity=waitForMonitorWithTimeout(restored,10000);removeMonitor(restored);settle();
        require(introductionActivity!=null&&introductionActivity!=previous,"introduction recreates as the same reader");
        require(((ScrollView)field(introductionActivity,"viewport")).getScrollY()==before,"introduction restores its reading position");
        previous=introductionActivity;ActivityMonitor rotated=addMonitor(ClassroomNoteStrengthActivity.class.getName(),null,false);
        Activity portrait=previous;runOnMainSync(()->portrait.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        introductionActivity=waitForMonitorWithTimeout(rotated,10000);removeMonitor(rotated);settle();
        require(introductionActivity!=null&&introductionActivity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE,"introduction displays in ordinary native landscape");
        reader=(ScrollView)field(introductionActivity,"viewport");TextView title=findText(introductionActivity.getWindow().getDecorView(),"笔记整理强度");
        TextView last=findText(introductionActivity.getWindow().getDecorView(),"3档 · 精简讲义");scrollTo(last);
        android.graphics.Rect visible=new android.graphics.Rect();
        require(reader.getHeight()>0&&last.getGlobalVisibleRect(visible)&&!title.getGlobalVisibleRect(new android.graphics.Rect()),"landscape reader scrolls header out instead of reserving fixed tool height");
        capture("classroom-note-strength-landscape");
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(300); }
    private void scrollTo(View view) {
        runOnMainSync(() -> view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));settle();
    }
    private void tapCenter(View view) {
        int[] point=new int[2];view.getLocationOnScreen(point);
        tap(point[0]+view.getWidth()/2f,point[1]+view.getHeight()/2f);settle();
    }
    private void dragNoteStrength(int level) {
        SeekBar slider=(SeekBar)field(current,"classroomNoteStrengthSlider");scrollTo(slider);
        int[] point=new int[2];slider.getLocationOnScreen(point);
        float track=slider.getWidth()-slider.getPaddingLeft()-slider.getPaddingRight();
        float range=slider.getMax()-slider.getMin();
        float start=point[0]+slider.getPaddingLeft()+track*(slider.getProgress()-slider.getMin())/range;
        float end=point[0]+slider.getPaddingLeft()+track*(level-slider.getMin())/range,y=point[1]+slider.getHeight()/2f;
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,start,y,0);sendPointerSync(down);down.recycle();
        for(int i=1;i<=12;i++){
            SystemClock.sleep(16);MotionEvent move=MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start+(end-start)*i/12f,y,0);
            sendPointerSync(move);move.recycle();
        }
        MotionEvent up=MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,end,y,0);sendPointerSync(up);up.recycle();settle();
    }
    private void assertNoteStrengthAccessibility(SeekBar slider,int level) {
        AccessibilityNodeInfo[] result=new AccessibilityNodeInfo[1];
        runOnMainSync(() -> result[0]=slider.createAccessibilityNodeInfo());
        AccessibilityNodeInfo node=result[0];
        try {
            AccessibilityNodeInfo.RangeInfo range=node.getRangeInfo();
            String actual=range==null?"null":"min="+range.getMin()+", max="+range.getMax()+", current="+range.getCurrent()+", type="+range.getType();
            require(range!=null && range.getType()==AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT
                    && range.getMin()==1 && range.getMax()==3 && range.getCurrent()==level,
                    "strength accessibility range matches levels 1..3 and current "+level+"; actual "+actual);
            if(android.os.Build.VERSION.SDK_INT>=30)
                require((level+"档").equals(String.valueOf(node.getStateDescription())),
                        "strength accessibility state announces "+level+"档; actual="+node.getStateDescription());
            else require(("笔记整理强度 · "+level+"档").equals(String.valueOf(node.getContentDescription())),
                    "strength accessibility description announces level on older Android; actual="+node.getContentDescription());
        } finally { node.recycle(); }
    }
    private void systemBack() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle(); }
    private void titleBack() {
        View back = findBack(current.getWindow().getDecorView());
        require(back != null, "shared title back exists");
        scrollTo(back);
        int[] point = new int[2]; back.getLocationOnScreen(point);
        Rect visible = new Rect();
        require(back.getGlobalVisibleRect(visible)
                && visible.contains(point[0] + back.getWidth() / 2, point[1] + back.getHeight() / 2),
                "shared title back touch point is visible after scrolling");
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
                    "workEndMinutes", "customPrompt", "classroomPrompt", "enhancedClassroom", "classroomConcurrency", "classroomNoteStrength", "classroomCellular", "protocol"};
            if (index == 3) method("setEffort", String.class).invoke(current, value);
            else if (index == 12) method("setProtocol", String.class).invoke(current, value);
            else if (index == 8 || index == 11) ((Switch) field(current, fields[index])).setChecked(Boolean.parseBoolean(value));
            else if (index == 10) ((SeekBar) field(current, "classroomNoteStrengthSlider")).setProgress(Integer.parseInt(value));
            else if (index == 4 || index == 5 || index == 9) {
                Field target = SettingsActivity.class.getDeclaredField(fields[index]); target.setAccessible(true);
                target.setInt(current, Integer.parseInt(value));
                method(index == 9 ? "refreshClassroomConcurrency" : "refreshWorkTime").invoke(current);
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
        float scale=Math.min(1f,1200f/Math.max(image.getWidth(),image.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*scale),Math.round(image.getHeight()*scale),true);
        File dir=new File(getTargetContext().getExternalFilesDir(null),"classroom-export-layout");dir.mkdirs();
        File file = new File(dir, name + "-" + arguments.getString("mode", "light") + "-"
                + (Boolean.parseBoolean(arguments.getString("nativeScreen","false"))?"native":arguments.getString("widthDp", "400"))
                + "-" + arguments.getString("font", "1.0") + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) { small.compress(Bitmap.CompressFormat.JPEG, 75, out); }
        finally { if(small!=image)small.recycle();image.recycle(); }
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
