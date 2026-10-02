package com.donglan.chrona;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.donglan.chrona.timetable.Timetable;
import com.donglan.chrona.timetable.TimetableLayout;
import java.util.function.Consumer;

/** Real accessible course views over a shared compact time grid, in the active app palette. */
final class TimetableGrid extends FrameLayout {
    static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private final TimetableLayout geometry;
    private final UiStyle.Palette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] dayLeft = new float[8];
    private final float[] dayWidth = new float[7];
    private final float density, fontScale;

    TimetableGrid(Context context, Timetable table, int widthPx, Consumer<Timetable.Block> selected) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        fontScale = getResources().getConfiguration().fontScale;
        geometry = new TimetableLayout(table.blocks);
        palette = UiStyle.colors(context);
        setWillNotDraw(false);
        setClipChildren(true);
        int width = Math.max(widthPx, dp(geometry.minimumWidth(fontScale)));
        int columns = java.util.Arrays.stream(geometry.dayColumns).sum();
        float unit = (width - dp(TimetableLayout.TIME_GUTTER)) / (float) columns;
        dayLeft[0] = dp(TimetableLayout.TIME_GUTTER);
        for (int day = 0; day < 7; day++) {
            dayWidth[day] = unit * geometry.dayColumns[day];
            dayLeft[day + 1] = dayLeft[day] + dayWidth[day];
            TextView heading = new TextView(context);
            heading.setText(DAYS[day]);
            heading.setTextSize(12);
            heading.setTypeface(null, Typeface.BOLD);
            heading.setTextColor(day >= 5 ? palette.muted : palette.text);
            heading.setIncludeFontPadding(false);
            heading.setGravity(Gravity.CENTER);
            if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
            place(heading, dayLeft[day], 0, dayWidth[day], dp(TimetableLayout.HEADER_HEIGHT));
        }
        for (TimetableLayout.Placement placement : geometry.placements) {
            Timetable.Block block = placement.block;
            TextView course = new TextView(context);
            String label = block.title + (block.location.isEmpty() ? "" : "\n" + block.location);
            SpannableString styled = new SpannableString(label);
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, block.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!block.location.isEmpty()) {
                styled.setSpan(new RelativeSizeSpan(.9f), block.title.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new ForegroundColorSpan(palette.muted), block.title.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            course.setText(styled);
            course.setTextSize(11);
            course.setTextColor(palette.text);
            course.setIncludeFontPadding(false);
            course.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            course.setLineSpacing(dp(1), 1f);
            course.setEllipsize(TextUtils.TruncateAt.END);
            float height = dp(placement.height) - dp(4);
            course.setMaxLines(Math.max(1, (int) ((height - dp(12))
                    / (course.getPaint().getFontSpacing() + dp(1)))));
            UiStyle.acrylicChoice(course, true, UiStyle.RADIUS_CHIP, false);
            course.setElevation(0);
            course.setPadding(dp(4), dp(6), dp(4), dp(6));
            course.setContentDescription(block.title + "，" + DAYS[block.day] + "，"
                    + (block.allDay ? "全天" : Timetable.time(block.startMinute) + " 至 " + Timetable.time(block.endMinute))
                    + (block.location.isEmpty() ? "" : "，" + block.location) + "，查看课程详情");
            course.setFocusable(true);
            UiStyle.pressable(course);
            course.setOnClickListener(view -> selected.accept(block));
            float columnWidth = block.allDay ? dayWidth[block.day] : unit;
            place(course, dayLeft[block.day] + (block.allDay ? 0 : placement.column * unit) + dp(2),
                    dp(placement.top) + dp(2), columnWidth - dp(4), height);
        }
        setLayoutParams(new FrameLayout.LayoutParams(width, dp(geometry.height())));
    }

    private void place(View view, float left, float top, float width, float height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.round(width), Math.round(height));
        params.leftMargin = Math.round(left);
        params.topMargin = Math.round(top);
        addView(view, params);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStrokeWidth(Math.max(1, density * .5f));
        paint.setColor(withAlpha(palette.outline, 90));
        for (float x : dayLeft) canvas.drawLine(x, dp(TimetableLayout.HEADER_HEIGHT), x, getHeight(), paint);
        float base = dp(TimetableLayout.HEADER_HEIGHT + geometry.allDayHeight);
        for (float y : geometry.offsets)
            canvas.drawLine(dp(TimetableLayout.TIME_GUTTER), base + y * density, getWidth(), base + y * density, paint);
        paint.setColor(palette.muted);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(dp(9) * Math.min(fontScale, 1.4f));
        float last = -Float.MAX_VALUE;
        for (int i = 0; i < geometry.minutes.length; i++) {
            float y = base + geometry.offsets[i] * density;
            // Dense boundaries remain geometrically accurate; labels never collide.
            if (y - last < dp(21) * Math.min(fontScale, 1.4f)) continue;
            canvas.drawText(Timetable.time(geometry.minutes[i]), dp(TimetableLayout.TIME_GUTTER / 2),
                    y - paint.ascent() + dp(1), paint);
            last = y;
        }
        if (geometry.allDayHeight > 0)
            canvas.drawText("全天", dp(TimetableLayout.TIME_GUTTER / 2),
                    dp(TimetableLayout.HEADER_HEIGHT + 18), paint);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    private int dp(float value) { return Math.round(value * density); }
}
