package com.donglan.chrona;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Read-only system occurrence; editing stays with its owning calendar application. */
public final class SystemAgendaDetailActivity extends Activity {
    private Button acknowledgement;
    private String occurrenceKey;
    private long acknowledgementUntil;
    private boolean occurrenceAvailable = true;
    static Intent intent(Context context, long id, long start, long end, boolean allDay,
            String title, String location, String calendar) {
        return new Intent(context, SystemAgendaDetailActivity.class)
                .setData(android.net.Uri.parse("chrona://system-agenda/" + id + "/" + start))
                .putExtra("event_id", id).putExtra("beginTime", start).putExtra("endTime", end)
                .putExtra("all_day", allDay).putExtra("title", title)
                .putExtra("location", location).putExtra("calendar", calendar);
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        Intent input = getIntent();
        long id = input.getLongExtra("event_id", -1);
        if (id < 0) {
            if (input.hasExtra("event_id")) startActivity(ExternalCourseAgenda.details(this, id,
                    input.getLongExtra("beginTime", 0), input.getLongExtra("endTime", 0)));
            finish(); return;
        }
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "系统日程");
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        card.setPadding(padding, padding, padding, padding); UiStyle.card(card);
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView title = text(card, value(input.getStringExtra("title")), 22);
        long start = input.getLongExtra("beginTime", 0), end = input.getLongExtra("endTime", start);
        boolean allDay = input.getBooleanExtra("all_day", false);
        ZoneId zone = allDay ? ZoneOffset.UTC : ZoneId.systemDefault();
        DateTimeFormatter format = DateTimeFormatter.ofPattern(allDay ? "yyyy年M月d日" : "yyyy年M月d日 HH:mm");
        String range = format.format(Instant.ofEpochMilli(start).atZone(zone));
        if (end > start) range += " — " + format.format(Instant.ofEpochMilli(allDay ? end - 1 : end).atZone(zone));
        text(card, range + (allDay ? " · 全天" : ""), 16);
        TextView location = text(card, value(input.getStringExtra("location")), 16);
        text(card, value(input.getStringExtra("calendar")), 14);
        TextView description = text(card, "", 16); description.setVisibility(android.view.View.GONE);
        long occurrenceStart = allDay ? Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() : start;
        long occurrenceEnd = allDay ? Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() : end;
        occurrenceKey = AgendaAcknowledgement.key(id, occurrenceStart);
        acknowledgementUntil = occurrenceEnd > occurrenceStart ? occurrenceEnd : occurrenceStart + 60_000L;
        acknowledgement = new Button(this); UiStyle.button(acknowledgement, false);
        LinearLayout.LayoutParams acknowledgementParams = new LinearLayout.LayoutParams(-1, -2);
        acknowledgementParams.topMargin = padding;
        root.addView(acknowledgement, acknowledgementParams);
        acknowledgement.setOnClickListener(view -> {
            if (!occurrenceAvailable || acknowledgementUntil <= System.currentTimeMillis()) {
                refreshAcknowledgement(); return;
            }
            AgendaAcknowledgement.set(this, occurrenceKey, acknowledgementUntil,
                    !AgendaAcknowledgement.isAcknowledged(this, occurrenceKey));
            refreshAcknowledgement();
        });
        refreshAcknowledgement();
        if (input.getBooleanExtra("source_series", false)) acknowledgement.setVisibility(android.view.View.GONE);
        Button open = new Button(this); open.setText("跳转至系统日程"); UiStyle.button(open, true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = padding; root.addView(open, buttonParams);
        open.setOnClickListener(view -> {
            Intent external = new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay);
            try { startActivity(external); }
            catch (android.content.ActivityNotFoundException failure) { Feedback.show(this, "未找到可打开日程的日历应用"); }
        });
        SettingsPageLayout.show(this, root, true);
        new Thread(() -> {
            try (Cursor cursor = getContentResolver().query(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    new String[]{CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_LOCATION,
                            CalendarContract.Events.DESCRIPTION}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    runOnUiThread(() -> { if (!isDestroyed()) { open.setEnabled(false); occurrenceAvailable = false; refreshAcknowledgement(); Feedback.show(this, "日程已不存在"); } });
                    return;
                }
                String currentTitle = value(cursor.getString(0)), currentLocation = value(cursor.getString(1)), details = value(cursor.getString(2));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    title.setText(currentTitle); location.setText(currentLocation);
                    description.setText(details); description.setVisibility(details.isBlank() ? android.view.View.GONE : android.view.View.VISIBLE);
                });
            } catch (SecurityException failure) {
                runOnUiThread(() -> { if (!isDestroyed()) Feedback.show(this, "未允许读取系统日历，显示进入页面时的内容"); });
            } catch (RuntimeException failure) {
                android.util.Log.w("Chrona", "Unable to read system event details", failure);
            }
        }, "chrona-system-event-details").start();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAcknowledgement();
    }

    private void refreshAcknowledgement() {
        if (acknowledgement != null) {
            acknowledgement.setEnabled(occurrenceAvailable && acknowledgementUntil > System.currentTimeMillis());
            acknowledgement.setText(AgendaAcknowledgement.isAcknowledged(this, occurrenceKey) ? "取消确认" : "确认活动");
        }
    }

    private TextView text(LinearLayout parent, String content, int size) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size);
        view.setTextColor(UiStyle.colors(this).text); view.setTextIsSelectable(true);
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = gap;
        parent.addView(view, params); return view;
    }
    private static String value(String text) { return text == null ? "" : text; }
}
