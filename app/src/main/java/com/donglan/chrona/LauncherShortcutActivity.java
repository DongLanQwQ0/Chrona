package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Public launcher entry; only fixed destinations are accepted. */
public final class LauncherShortcutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getAction();
        Intent destination;
        if ("com.donglan.chrona.shortcut.CAPTURE".equals(action))
            destination = new Intent(this, MainActivity.class);
        else if ("com.donglan.chrona.shortcut.WEEK".equals(action))
            destination = new Intent(this, CalendarOverviewActivity.class).putExtra("open_week", true);
        else if ("com.donglan.chrona.shortcut.SCHEDULE".equals(action))
            destination = new Intent(this, DashboardActivity.class).putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.SCHEDULE);
        else if ("com.donglan.chrona.shortcut.INBOX".equals(action))
            destination = new Intent(this, DashboardActivity.class).putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.INBOX);
        else { finish(); return; }
        startActivity(destination); finish();
    }
}
