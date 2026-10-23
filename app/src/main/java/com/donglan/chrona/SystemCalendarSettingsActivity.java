package com.donglan.chrona;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Entry points share preferences with the switches on the original screens. */
public final class SystemCalendarSettingsActivity extends Activity {
    private static final int HOME_PERMISSION = 711, CALENDAR_PERMISSION = 712;
    private Switch home, calendar;
    private boolean binding;
    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "系统日程");
        home = toggle(root, "首页包含系统其他日程", HOME_PERMISSION);
        calendar = toggle(root, "日历包含系统日程", CALENDAR_PERMISSION);
        android.widget.Button merge = new android.widget.Button(this);
        merge.setText("合并重复日程");
        UiStyle.button(merge, false);
        merge.setOnClickListener(view -> startActivity(new android.content.Intent(this, CandidateMergeActivity.class)));
        UiStyle.addSpaced(root, merge, 0, 12);
        SettingsPageLayout.show(this, root, state == null);
    }
    private Switch toggle(LinearLayout root, String label, int permission) {
        Switch control = new Switch(this); control.setText(label); control.setTextSize(16);
        control.setPadding(dp(16), dp(6), dp(16), dp(6)); control.setSwitchPadding(dp(12));
        UiStyle.toggle(control);
        control.setOnCheckedChangeListener((button, enabled) -> {
            if (binding) return;
            if (enabled && !new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
                binding = true; control.setChecked(false); binding = false;
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, permission); return;
            }
            save(permission, enabled);
        });
        UiStyle.addSpaced(root, control, 0, 12);
        return control;
    }
    private void save(int target, boolean enabled) {
        if (target == HOME_PERMISSION) HomeTimelinePreferences.setIncludesSystemCalendar(this, enabled);
        else CalendarSourcePreferences.setIncludesSystem(this, enabled);
    }
    @Override protected void onResume() { super.onResume(); bind(); }
    private void bind() {
        binding = true;
        home.setChecked(HomeTimelinePreferences.includesSystemCalendar(this));
        calendar.setChecked(CalendarSourcePreferences.includesSystem(this));
        binding = false;
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != HOME_PERMISSION && request != CALENDAR_PERMISSION) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) save(request, true);
        else Feedback.show(this, "未获得读取日历权限");
        bind();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
