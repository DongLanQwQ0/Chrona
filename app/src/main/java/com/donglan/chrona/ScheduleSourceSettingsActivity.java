package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.donglan.chrona.ScheduleSourceRepository.Source;

/** Global permissions for each schedule source. Provider enumeration runs off the UI thread. */
public final class ScheduleSourceSettingsActivity extends Activity {
    private static final int READ_PERMISSION = 760;
    private static final int EXPORT_PERMISSION = 761;
    private boolean exporting;
    private Button exportCourses;
    private LinearLayout calendars;
    private LinearLayout builtins;
    private Button primary;
    private List<Source> sources = new ArrayList<>();
    private int loadGeneration;
    private boolean firstBuiltins,firstCalendars;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        firstBuiltins=state==null||state.getBoolean("first_builtins",false);
        firstCalendars=state==null||state.getBoolean("first_calendars",false);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "日程来源");
        builtins = new LinearLayout(this); builtins.setOrientation(LinearLayout.VERTICAL);
        root.addView(builtins, new LinearLayout.LayoutParams(-1, -2));
        primary = new Button(this); UiStyle.button(primary, false);
        UiStyle.addSpaced(root, primary, 0, 16);
        primary.setOnClickListener(view -> choosePrimary());
        Button matching=new Button(this);matching.setText("课程关联");UiStyle.button(matching,false);
        UiStyle.addSpaced(root,matching,0,16);
        matching.setOnClickListener(view -> startActivity(new android.content.Intent(this,CourseSourceMatchingActivity.class)));
        exportCourses = new Button(this);
        exportCourses.setText("将课表写入系统日历");
        UiStyle.button(exportCourses, false);
        UiStyle.addSpaced(root, exportCourses, 0, 16);
        exportCourses.setOnClickListener(view -> chooseCourseExport());
        calendars = new LinearLayout(this); calendars.setOrientation(LinearLayout.VERTICAL);
        root.addView(calendars, new LinearLayout.LayoutParams(-1, -2));
        SettingsPageLayout.show(this, root, state == null);
    }
    @Override protected void onResume() {
        super.onResume();
        loadCalendars();
        loadBuiltins(loadGeneration);
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("first_builtins",firstBuiltins);state.putBoolean("first_calendars",firstCalendars);super.onSaveInstanceState(state);}

    private void loadBuiltins(int generation) {
        builtins.removeAllViews();
        new Thread(() -> {
            int imported = -1, school = -1;
            try { imported = ScheduleSourceRepository.importedCount(this); }
            catch (Exception failure) { android.util.Log.w("Chrona", "Unable to count imported courses", failure); }
            try {
                ZjuSettingsStore settings = new ZjuSettingsStore(this);
                if (settings.enabled()) school = ScheduleSourceRepository.zjuCount(this);
            } catch (Exception failure) { android.util.Log.w("Chrona", "Unable to count school courses", failure); }
            int importedCount = imported, schoolCount = school;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                builtins.removeAllViews();
                if (importedCount != 0) toggle(builtins, "导入课表", countLabel(importedCount), ScheduleSources.importedEnabled(this),
                        checked -> ScheduleSources.setImportedEnabled(this, checked), "imported");
                if (new ZjuSettingsStore(this).enabled() && schoolCount != 0)
                    toggle(builtins, "浙大课表", countLabel(schoolCount), ScheduleSources.zjuEnabled(this),
                            checked -> ScheduleSources.setZjuEnabled(this, checked), "zju");
                if(firstBuiltins){firstBuiltins=false;UiMotion.enterPage(builtins);}
            });
        }, "chrona-source-counts").start();
    }

    private static String countLabel(int count) { return count < 0 ? "数量读取失败，点击重试查看" : count + " 条日程"; }

    private void chooseCourseExport() {
        if (exporting) return;
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, EXPORT_PERMISSION);
            return;
        }
        boolean school = new ZjuSettingsStore(this).enabled();
        String[] choices = school ? new String[]{"导入课表", "折姜星辰课表", "两种课表"} : new String[]{"导入课表"};
        UiDialogs.choiceDialog(this, "选择写入来源", choices, -1, index -> {
            if (index < 0 || index >= choices.length || exporting) return;
            loadCourseExport(index != 1, school && index != 0);
        });
    }

    private void loadCourseExport(boolean imported, boolean school) {
        exporting = true;
        exportCourses.setEnabled(false);
        android.content.Context context = getApplicationContext();
        new Thread(() -> {
            try {
                List<com.donglan.chrona.calendar.CalendarOccurrence> lessons = CourseCalendarExport.load(context, imported, school);
                runOnUiThread(() -> {
                    exporting = false;
                    if (isFinishing() || isDestroyed()) return;
                    exportCourses.setEnabled(true);
                    if (lessons.isEmpty()) { Feedback.show(this, "没有可写入的本地课表"); return; }
                    UiDialogs.confirmDialog(this, "写入 " + lessons.size() + " 节课程？",
                            "包括所选来源已保存的全部学期；相同课程会跳过。此次写入为副本，后续课表改动不会自动更新副本。",
                            "写入", () -> writeCourses(lessons));
                });
            } catch (Exception failure) {
                runOnUiThread(() -> { exporting = false; if (!isFinishing() && !isDestroyed()) {
                    exportCourses.setEnabled(true); Feedback.showLong(this, "读取课表失败：" + failure.getMessage());
                }});
            }
        }, "chrona-course-export-preview").start();
    }

    private void writeCourses(List<com.donglan.chrona.calendar.CalendarOccurrence> lessons) {
        if (exporting) return;
        exporting = true;
        exportCourses.setEnabled(false);
        android.content.Context context = getApplicationContext();
        new Thread(() -> {
            String message;
            try {
                CourseCalendarExport.Result result = CourseCalendarExport.write(context, lessons);
                message = "已写入 " + result.inserted + " 节，跳过 " + result.skipped + " 节相同课程";
            } catch (Exception failure) { message = "写入失败：" + failure.getMessage(); }
            AgendaWidgetProvider.requestRefresh(context);
            String summary = message;
            runOnUiThread(() -> {
                exporting = false;
                if (isFinishing() || isDestroyed()) return;
                exportCourses.setEnabled(true);
                Feedback.showLong(this, summary);
                loadCalendars(); loadBuiltins(loadGeneration);
            });
        }, "chrona-course-export").start();
    }

    private void loadCalendars() {
        int generation = ++loadGeneration;
        calendars.removeAllViews();
        updatePrimary();
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Button permission = new Button(this); permission.setText("允许读取系统日历"); UiStyle.button(permission, true);
            calendars.addView(permission, new LinearLayout.LayoutParams(-1, -2));
            permission.setOnClickListener(view -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, READ_PERMISSION));
            return;
        }
        label(calendars, "正在读取系统日历…");
        new Thread(() -> {
            try {
                List<Source> result = ScheduleSourceRepository.calendars(this);
                java.util.Map<String,Integer> groups=new java.util.HashMap<>();
                String probeIssue="";
                try{groups.putAll(CourseSourceProbe.weeklyGroups(CourseSourceProbe.load(this).rows));}
                catch(Exception failure){
                    android.util.Log.w("Chrona","Unable to probe course structure",failure);
                    probeIssue="课程结构探测失败，可在课程关联中重试";
                }
                String probeMessage=probeIssue;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                    sources = result; calendars.removeAllViews(); updatePrimary();
                    if(!probeMessage.isEmpty())label(calendars,probeMessage);
                    if (sources.isEmpty()) label(calendars, "系统中暂无其他日历");
                    else for (Source source : sources) {
                        String info = source.account + " · ID " + source.id;
                        int recurring=groups.getOrDefault("system:"+source.id,0);
                        if(recurring>0)info+=" · "+recurring+" 组周重复日程";
                        else if (looksLikeCourses(source.name)) info += " · 名称含课表关键词";
                        toggle(calendars, source.name, info, ScheduleSources.allowsCalendar(this, source.id),
                                checked -> ScheduleSources.setCalendarEnabled(this, source.id, checked), "system:" + source.id);
                    }
                    if(firstCalendars){firstCalendars=false;UiMotion.enterPage(calendars);}
                });
            } catch (Exception failure) {
                android.util.Log.w("Chrona", "Unable to enumerate schedule calendars", failure);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                    calendars.removeAllViews();
                    Button retry = new Button(this); retry.setText("读取系统日历失败 · 重试"); UiStyle.button(retry, false);
                    calendars.addView(retry, new LinearLayout.LayoutParams(-1, -2));
                    retry.setOnClickListener(view -> loadCalendars());
                });
            }
        }, "chrona-source-calendars").start();
    }
    private void choosePrimary() {
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        keys.add("all"); labels.add("自动合并相同项");
        keys.add("imported"); labels.add("导入课表优先");
        if (new ZjuSettingsStore(this).enabled()) { keys.add("zju"); labels.add("浙大课表优先"); }
        for (Source source : sources) { keys.add("system:" + source.id); labels.add(source.name + " · " + source.account + " · 优先"); }
        UiDialogs.choiceDialog(this, "重复课表优先来源", labels.toArray(new String[0]),
                keys.indexOf(effectivePrimary()), selected -> {
                    ScheduleSources.setPrimary(this, keys.get(selected)); updatePrimary();
                });
    }
    private void updatePrimary() {
        String key = effectivePrimary();
        String name = switch (key) {
            case "imported" -> "导入课表";
            case "zju" -> "浙大课表";
            case "all" -> "自动合并";
            default -> "系统日历";
        };
        for (Source source : sources) if (key.equals("system:" + source.id)) name = source.name;
        primary.setText("重复课表 · " + name + ("all".equals(key) ? "" : "优先"));
    }
    private String effectivePrimary() {
        String key = ScheduleSources.primary(this);
        return "zju".equals(key) && !new ZjuSettingsStore(this).enabled() ? "all" : key;
    }
    private void toggle(LinearLayout parent, String name, String detail, boolean enabled,
            java.util.function.Consumer<Boolean> save, String key) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12)); UiStyle.card(row);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(copy, name); title.setMaxLines(2);
        if (!detail.isEmpty()) { TextView meta = label(copy, detail); meta.setTextSize(13); meta.setTextColor(UiStyle.colors(this).muted); }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).muted));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        arrowParams.setMargins(dp(8), 0, dp(12), 0); row.addView(arrow, arrowParams);
        Switch control = new Switch(this); UiStyle.toggle(control); control.setChecked(enabled);
        control.setContentDescription(name); control.setMinHeight(dp(48));
        row.addView(control, new LinearLayout.LayoutParams(-2, -2));
        control.setOnCheckedChangeListener((button, checked) -> save.accept(checked));
        row.setOnClickListener(view -> startActivity(ScheduleSourceContentsActivity.intent(this, key, name)));
        UiStyle.addSpaced(parent, row, 0, 12);
    }
    private TextView label(LinearLayout parent, String value) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(16);
        label.setTextColor(UiStyle.colors(this).text); parent.addView(label); return label;
    }
    private static boolean looksLikeCourses(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("课表") || normalized.contains("课程表") || normalized.contains("timetable")
                || normalized.contains("class schedule") || normalized.contains("course schedule");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(request, permissions, result);
        if (request == EXPORT_PERMISSION) {
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                chooseCourseExport();
            else Feedback.show(this, "需要读取和写入日历权限");
            return;
        }
        if (request != READ_PERMISSION) return;
        if (result.length == 0 || result[0] != PackageManager.PERMISSION_GRANTED) Feedback.show(this, "未获得读取日历权限");
        loadCalendars(); loadBuiltins(loadGeneration);
    }
    @Override protected void onDestroy() { loadGeneration++; super.onDestroy(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
