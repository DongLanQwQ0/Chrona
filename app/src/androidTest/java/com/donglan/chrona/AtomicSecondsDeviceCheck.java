package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Applied production RemoteViews in a real window; never posts notifications or writes schedules. */
final class AtomicSecondsDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        var context = test.getTargetContext();
        if (!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated package only");
        String theme = ThemeStore.mode(context);
        var prefs = AgendaReminderPreferences.prefs(context);
        boolean had = prefs.contains("atomic_seconds"), old = prefs.getBoolean("atomic_seconds", true);
        Activity page = null;
        StringBuilder report = new StringBuilder("PASS actual RemoteViews apply/reapply; ");
        try {
            ThemeStore.setMode(context, args.getString("theme", "light"));
            prefs.edit().putBoolean("atomic_seconds", true).commit();
            page = test.startActivitySync(new Intent(context, AppearanceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity owner = page;
            PendingIntent details = PendingIntent.getActivity(context, 901, new Intent(context, DashboardActivity.class), PendingIntent.FLAG_IMMUTABLE);
            long now = System.currentTimeMillis();
            long createdAt = now;
            AgendaEntry future = entry(now + 185000, now + 400000, "event", null);
            View[] cards = new View[2]; RemoteViews[] baselines = new RemoteViews[2]; LinearLayout[] root = {null};
            test.runOnMainSync(() -> {
                root[0] = new LinearLayout(owner); root[0].setOrientation(LinearLayout.VERTICAL);
                int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
                root[0].setPadding(pad, pad, pad, pad);
                baselines[0] = AtomicAgendaCard.create(owner, future, createdAt, createdAt, "下一项", details);
                baselines[1] = AtomicAgendaCard.forIsland(owner, future, createdAt, createdAt, "下一项", details);
                for (int i = 0; i < 2; i++) cards[i] = baselines[i].apply(owner, root[0]);
                for (View card : cards) root[0].addView(card, new LinearLayout.LayoutParams(-1, -2));
                SettingsPageLayout.show(owner, root[0], false);
            });
            test.waitForIdleSync(); SystemClock.sleep(100);
            String[] before = texts(test, cards);
            SystemClock.sleep(1250);
            tick(test, owner, cards, baselines, future.start - System.currentTimeMillis());
            String[] after = texts(test, cards);
            for (int i = 0; i < 2; i++) require(!before[i].equals(after[i]) && after[i].contains(":"), "seconds tick beyond two minutes " + after[i]);
            report.append(java.util.Arrays.toString(before)).append(" → ").append(java.util.Arrays.toString(after)).append(';');
            test.runOnMainSync(() -> {
                for (View card : cards) {
                    TextView clock = visibleClock(card);
                    require(clock.getLayout() != null && clock.getLayout().getEllipsisCount(0) == 0, "clock not ellipsized");
                    require(clock.getPaint().measureText(clock.getText().toString()) <= clock.getWidth(), "clock fits width");
                }
            });
            capture(test, root[0], "atomic-seconds-" + args.getString("theme", "light"));
            reapply(test, owner, cards, future, details);
            SystemClock.sleep(1200);
            for (String value : texts(test, cards)) require(value.contains(":"), "reapply retains seconds text");
            prefs.edit().putBoolean("atomic_seconds", false).commit();
            reapply(test, owner, cards, future, details); String[] stopped = texts(test, cards);
            SystemClock.sleep(1200);
            require(java.util.Arrays.equals(stopped, texts(test, cards)), "seconds off stays static");
            prefs.edit().putBoolean("atomic_seconds", true).commit();
            tick(test, owner, cards, baselines, 1000);
            SystemClock.sleep(2300);
            for (String value : texts(test, cards)) require(value.equals("00:01"), "delayed boundary refresh cannot produce negative time");
            tick(test, owner, cards, baselines, -3000);
            for (String value : texts(test, cards)) require(value.equals("00:00"), "late tick clamps at zero");
            now = System.currentTimeMillis();
            AgendaEntry active = entry(now - 1000, now + 61000, "event", null);
            reapply(test, owner, cards, active, details);
            test.runOnMainSync(() -> {
                for (View card : cards) {
                    require("距结束".contentEquals(((TextView) card.findViewById(R.id.atomic_remaining_label)).getText()), "start boundary changes label");
                    require(visibleClock(card).getText().toString().contains(":"), "active end countdown");
                }
            });
            now = System.currentTimeMillis();
            reapply(test, owner, cards, entry(now - 60000, now - 1000, "event", null), details);
            for (String value : texts(test, cards)) require(value.equals("已结束"), "end state replaces running clock");
            SystemClock.sleep(1200);
            for (String value : texts(test, cards)) require(value.equals("已结束"), "stopped end state survives old ticks");
            now = System.currentTimeMillis();
            reapply(test, owner, cards, entry(now - 1000, now + 60000, "task", now + 120000), details);
            for (String value : texts(test, cards)) require(value.equals("待完成"), "started task remains static");
            reapply(test, owner, cards, entry(now - 60000, now - 1000, "task", now - 1000), details);
            for (String value : texts(test, cards)) require(value.equals("已逾期"), "overdue task remains static");
            now = System.currentTimeMillis();
            reapply(test, owner, cards, entry(now + 3700000, now + 4000000, "event", null), details);
            for (String value : texts(test, cards)) require(value.split(":").length == 3, "hour format includes seconds " + value);
            capture(test, root[0], "atomic-hours-" + args.getString("theme", "light"));
            return report.append("minute preference/static end/task/overdue/hour format passed").toString();
        } finally {
            if (had) prefs.edit().putBoolean("atomic_seconds", old).commit(); else prefs.edit().remove("atomic_seconds").commit();
            ThemeStore.setMode(context, theme);
            if (page != null) { Activity owner = page; test.runOnMainSync(owner::finish); }
        }
    }
    private static AgendaEntry entry(long start, long end, String category, Long due) {
        return new AgendaEntry(-901, 0, start, end, "信息资源部第三次例会与项目开发交流", "开物苑2幢学生交流中心", "", "验证", false, false, true, null, category, 30, due);
    }
    private static TextView visibleClock(View card) {
        return card.findViewById(card.findViewById(R.id.atomic_top_remaining).getVisibility() == View.VISIBLE ? R.id.atomic_top_countdown : R.id.atomic_remaining);
    }
    private static void tick(Instrumentation test, Activity owner, View[] cards, RemoteViews[] baselines, long remaining) throws Exception {
        var method = AtomicCountdownUpdates.class.getDeclaredMethod("countdown", RemoteViews.class, String.class);
        method.setAccessible(true);
        test.runOnMainSync(() -> {
            try {
                for (int i = 0; i < cards.length; i++)
                    ((RemoteViews) method.invoke(null, baselines[i], AtomicAgendaCard.expandedRemainingText(remaining))).reapply(owner, cards[i]);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        test.waitForIdleSync();
    }
    private static String[] texts(Instrumentation test, View[] cards) {
        String[] values = new String[cards.length];
        test.runOnMainSync(() -> { for (int i = 0; i < cards.length; i++) values[i] = visibleClock(cards[i]).getText().toString(); });
        return values;
    }
    private static void reapply(Instrumentation test, Activity owner, View[] cards, AgendaEntry entry, PendingIntent details) {
        test.runOnMainSync(() -> {
            long now = System.currentTimeMillis();
            AtomicAgendaCard.create(owner, entry, now, now, "下一项", details).reapply(owner, cards[0]);
            AtomicAgendaCard.forIsland(owner, entry, now, now, "下一项", details).reapply(owner, cards[1]);
        });
        test.waitForIdleSync();
    }
    private static void capture(Instrumentation test, View view, String name) {
        test.runOnMainSync(() -> {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            Bitmap small = Bitmap.createScaledBitmap(bitmap, Math.min(1000, bitmap.getWidth()),
                    Math.round(bitmap.getHeight() * Math.min(1f, 1000f / bitmap.getWidth())), true);
            try (FileOutputStream out = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null), name + ".jpg"))) {
                small.compress(Bitmap.CompressFormat.JPEG, 76, out);
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { if (small != bitmap) small.recycle(); bitmap.recycle(); }
        });
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
