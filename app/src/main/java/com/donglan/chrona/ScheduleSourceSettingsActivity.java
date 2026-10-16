package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Global permissions for each schedule source. Provider enumeration runs off the UI thread. */
public final class ScheduleSourceSettingsActivity extends Activity {
    private static final int READ_PERMISSION = 760;
    private LinearLayout calendars;
    private Button primary;
    private List<Source> sources = new ArrayList<>();
    private int loadGeneration;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "日程来源");
        toggle(root, "导入课表", "", ScheduleSources.importedEnabled(this),
                checked -> ScheduleSources.setImportedEnabled(this, checked));
        toggle(root, "浙大课表服务", "", ScheduleSources.zjuEnabled(this),
                checked -> ScheduleSources.setZjuEnabled(this, checked));
        primary = new Button(this); UiStyle.button(primary, false);
        UiStyle.addSpaced(root, primary, 0, 16);
        primary.setOnClickListener(view -> choosePrimary());
        Button matching=new Button(this);matching.setText("课程关联");UiStyle.button(matching,false);
        UiStyle.addSpaced(root,matching,0,16);
        matching.setOnClickListener(view -> startActivity(new android.content.Intent(this,CourseSourceMatchingActivity.class)));
        calendars = new LinearLayout(this); calendars.setOrientation(LinearLayout.VERTICAL);
        root.addView(calendars, new LinearLayout.LayoutParams(-1, -2));
        SettingsPageLayout.show(this, root, state == null);
    }
    @Override protected void onResume() { super.onResume(); loadCalendars(); }

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
                List<Source> result = new ArrayList<>();
                String[] projection = {CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.NAME};
                try (Cursor cursor = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                        projection, null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
                    if (cursor == null) throw new IllegalStateException("Calendar provider returned no cursor");
                    while (cursor.moveToNext()) {
                        String account = cursor.getString(2);
                        if ((getPackageName() + ".calendar").equals(account)
                                && CalendarContract.ACCOUNT_TYPE_LOCAL.equals(cursor.getString(3))
                                && com.donglan.chrona.calendar.CalendarStore.CALENDAR_NAME.equals(cursor.getString(4))) continue;
                        result.add(new Source(cursor.getLong(0), cursor.getString(1), account));
                    }
                }
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
                                checked -> ScheduleSources.setCalendarEnabled(this, source.id, checked));
                    }
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
        keys.add("all"); labels.add("全部保留");
        keys.add("imported"); labels.add("导入课表优先");
        keys.add("zju"); labels.add("浙大课表服务优先");
        for (Source source : sources) { keys.add("system:" + source.id); labels.add(source.name + " · " + source.account + " · 优先"); }
        UiStyle.choiceDialog(this, "重复日程优先来源", labels.toArray(new String[0]),
                keys.indexOf(ScheduleSources.primary(this)), selected -> {
                    ScheduleSources.setPrimary(this, keys.get(selected)); updatePrimary();
                });
    }
    private void updatePrimary() {
        String key = ScheduleSources.primary(this);
        String name = switch (key) {
            case "imported" -> "导入课表";
            case "zju" -> "浙大课表服务";
            case "all" -> "全部保留";
            default -> "系统日历";
        };
        for (Source source : sources) if (key.equals("system:" + source.id)) name = source.name;
        primary.setText("重复日程 · " + name);
    }
    private void toggle(LinearLayout parent, String name, String detail, boolean enabled,
            java.util.function.Consumer<Boolean> save) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12)); UiStyle.card(row);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        label(copy, name);
        if (!detail.isEmpty()) { TextView meta = label(copy, detail); meta.setTextSize(13); meta.setTextColor(UiStyle.colors(this).muted); }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this); UiStyle.toggle(control); control.setChecked(enabled);
        control.setContentDescription(name); control.setMinHeight(dp(48));
        row.addView(control, new LinearLayout.LayoutParams(-2, -2));
        control.setOnCheckedChangeListener((button, checked) -> save.accept(checked));
        row.setOnClickListener(view -> control.setChecked(!control.isChecked()));
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
        if (request != READ_PERMISSION) return;
        if (result.length == 0 || result[0] != PackageManager.PERMISSION_GRANTED) Feedback.show(this, "未获得读取日历权限");
        loadCalendars();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Source {
        final long id;
        final String name, account;
        Source(long id, String name, String account) {
            this.id = id; this.name = name == null || name.isBlank() ? "未命名日历" : name;
            this.account = account == null || account.isBlank() ? "本地账户" : account;
        }
    }
}
