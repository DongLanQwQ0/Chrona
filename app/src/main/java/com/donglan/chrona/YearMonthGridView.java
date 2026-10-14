package com.donglan.chrona;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.function.Consumer;

/** A month overview painted in one view instead of 42 nested date-cell hierarchies. */
final class YearMonthGridView extends View {
    private final LocalDate month, first, selected;
    private final CalendarDayCounts counts;
    private final UiStyle.Palette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density, scaledDensity, rowHeight;
    private LocalDate pressed, clicked;
    YearMonthGridView(Context context, LocalDate month, LocalDate selected, CalendarDayCounts counts, Consumer<LocalDate> open) {
        super(context); this.month = month; this.selected = selected; this.counts = counts;
        first = month.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); palette = UiStyle.colors(context);
        density = getResources().getDisplayMetrics().density; scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        rowHeight = Math.max(25 * density, 12 * scaledDensity + 8 * density);
        setContentDescription(month.getYear() + "年" + month.getMonthValue() + "月，查看月历"); setFocusable(true);
        setOnClickListener(view -> { open.accept(clicked == null ? month : clicked); clicked = null; });
    }
    @Override protected void onMeasure(int width, int height) { setMeasuredDimension(MeasureSpec.getSize(width), Math.round(rowHeight * 7)); }
    @Override protected void onDraw(Canvas canvas) {
        float cell = getWidth() / 7f; paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10 * scaledDensity); paint.setColor(palette.muted);
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        for (int col = 0; col < 7; col++) canvas.drawText(weekdays[col], (col + .5f) * cell, rowHeight * .65f, paint);
        paint.setTextSize(12 * scaledDensity);
        LocalDate today = LocalDate.now();
        for (int index = 0; index < 42; index++) {
            LocalDate day = first.plusDays(index); float x = (index % 7 + .5f) * cell, top = (index / 7 + 1) * rowHeight;
            boolean ownMonth = day.getMonth() == month.getMonth();
            if (day.equals(pressed)) { paint.setColor((palette.primary & 0xffffff) | 0x33000000); canvas.drawCircle(x, top + rowHeight * .45f, cell * .45f, paint); }
            paint.setColor(!ownMonth ? palette.muted : day.equals(today) || day.equals(selected) ? palette.primary : palette.text);
            canvas.drawText(Integer.toString(day.getDayOfMonth()), x, top + rowHeight * .63f, paint);
            int count = counts.on(day);
            if (count > 0) { paint.setColor(count <= 2 ? palette.primary : count <= 5 ? 0xFFAD873F : 0xFFD96B72); canvas.drawCircle(x, top + rowHeight - 3 * density, 1.4f * density, paint); }
        }
    }
    private LocalDate dayAt(MotionEvent event) {
        int row = (int)(event.getY() / rowHeight) - 1, col = (int)(event.getX() / (getWidth() / 7f));
        return row < 0 || row >= 6 || col < 0 || col >= 7 ? null : first.plusDays(row * 7L + col);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { pressed = dayAt(event); invalidate(); return pressed != null; }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) { clicked = dayAt(event); boolean activate = pressed != null && pressed.equals(clicked); pressed = null; invalidate(); if (activate) performClick(); return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { pressed = null; invalidate(); return true; }
        return pressed != null;
    }
}
