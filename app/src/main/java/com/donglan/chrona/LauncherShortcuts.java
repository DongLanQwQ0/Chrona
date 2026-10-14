package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Theme-coloured launcher actions, republished when the theme or process changes. */
final class LauncherShortcuts {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static Integer publishedColor;
    static void requestRefresh(Context context) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try { publish(app); }
            catch (RuntimeException error) { android.util.Log.w("Chrona", "Launcher shortcut refresh failed", error); }
        });
    }
    private static void publish(Context context) {
        int color = UiStyle.colors(context).primary;
        if (publishedColor != null && publishedColor == color) return;
        String[] ids = {"quick_capture", "quick_week", "quick_schedule", "quick_inbox"};
        String[] actions = {"CAPTURE", "WEEK", "SCHEDULE", "INBOX"};
        int[] titles = {R.string.shortcut_capture, R.string.shortcut_week, R.string.shortcut_schedule, R.string.shortcut_inbox};
        int[] resources = {R.drawable.ic_add, R.drawable.ic_week_calendar, R.drawable.ic_nav_schedule, R.drawable.ic_nav_inbox};
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Drawable drawable = context.getDrawable(resources[i]).mutate(); drawable.setTint(color);
            drawable.setBounds(0, 0, size, size); drawable.draw(new Canvas(bitmap));
            shortcuts.add(new ShortcutInfo.Builder(context, ids[i]).setRank(i)
                    .setShortLabel(context.getString(titles[i])).setIcon(Icon.createWithBitmap(bitmap))
                    .setIntent(new Intent(context, LauncherShortcutActivity.class)
                            .setAction("com.donglan.chrona.shortcut." + actions[i])).build());
        }
        if (context.getSystemService(ShortcutManager.class).setDynamicShortcuts(shortcuts)) publishedColor = color;
    }
    private LauncherShortcuts() { }
}
