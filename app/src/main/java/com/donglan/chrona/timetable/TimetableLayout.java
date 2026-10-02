package com.donglan.chrona.timetable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Pure geometry: shared time boundaries, compressed empty gaps and disjoint conflict columns. */
public final class TimetableLayout {
    public static final float MIN_BLOCK_HEIGHT = 52;
    public static final float MIN_COLUMN_WIDTH = 38;
    public static final float TIME_GUTTER = 42;
    public static final float HEADER_HEIGHT = 44;
    public static final float ALL_DAY_HEIGHT = 48;
    private static final float MIN_BOUNDARY_GAP = 12;
    public final int[] minutes;
    public final float[] offsets;
    public final List<Placement> placements = new ArrayList<>();
    public final int[] dayColumns = {1, 1, 1, 1, 1, 1, 1};
    public final float allDayHeight;
    public final float bodyHeight;

    public static final class Placement {
        public final Timetable.Block block;
        public final int column;
        public final float top, height;
        Placement(Timetable.Block block, int column, float top, float height) {
            this.block = block;
            this.column = column;
            this.top = top;
            this.height = height;
        }
    }

    public TimetableLayout(List<Timetable.Block> blocks) {
        TreeSet<Integer> boundaries = new TreeSet<>();
        int[] allDayRows = new int[7];
        for (Timetable.Block block : blocks) {
            if (block.allDay) allDayRows[block.day]++;
            else { boundaries.add(block.startMinute); boundaries.add(block.endMinute); }
        }
        allDayHeight = Arrays.stream(allDayRows).max().orElse(0) * ALL_DAY_HEIGHT;
        minutes = boundaries.stream().mapToInt(Integer::intValue).toArray();
        offsets = new float[minutes.length];
        for (int i = 1; i < minutes.length; i++) {
            boolean occupied = false;
            for (Timetable.Block b : blocks) {
                if (!b.allDay && b.startMinute < minutes[i] && b.endMinute > minutes[i - 1]) {
                    occupied = true;
                    break;
                }
            }
            float duration = minutes[i] - minutes[i - 1];
            float gap = occupied ? Math.min(110, Math.max(MIN_BOUNDARY_GAP, duration * .8f))
                    : Math.min(26, Math.max(MIN_BOUNDARY_GAP, duration * .2f));
            offsets[i] = offsets[i - 1] + gap;
            // A short class still has a full touch target, without overlapping the next boundary.
            for (Timetable.Block b : blocks) {
                if (!b.allDay && b.endMinute == minutes[i])
                    offsets[i] = Math.max(offsets[i], y(b.startMinute) + MIN_BLOCK_HEIGHT);
            }
        }
        bodyHeight = minutes.length == 0 ? 0 : offsets[offsets.length - 1] + 12;
        for (int day = 0; day < 7; day++) {
            ArrayList<Timetable.Block> timed = new ArrayList<>();
            int allDayRow = 0;
            for (Timetable.Block b : blocks) if (b.day == day) {
                if (b.allDay) placements.add(new Placement(b, 0,
                        HEADER_HEIGHT + ALL_DAY_HEIGHT * allDayRow++, ALL_DAY_HEIGHT));
                else timed.add(b);
            }
            timed.sort(Comparator.comparingInt((Timetable.Block b) -> b.startMinute)
                    .thenComparingInt(b -> -b.endMinute).thenComparing(b -> b.title));
            ArrayList<Integer> columnEnds = new ArrayList<>();
            for (Timetable.Block b : timed) {
                int column = 0;
                while (column < columnEnds.size() && columnEnds.get(column) > b.startMinute) column++;
                if (column == columnEnds.size()) columnEnds.add(b.endMinute);
                else columnEnds.set(column, b.endMinute);
                dayColumns[day] = Math.max(dayColumns[day], columnEnds.size());
                placements.add(new Placement(b, column, HEADER_HEIGHT + allDayHeight + y(b.startMinute),
                        y(b.endMinute) - y(b.startMinute)));
            }
        }
    }

    public float y(int minute) {
        int index = Arrays.binarySearch(minutes, minute);
        if (index < 0) throw new IllegalArgumentException("Missing time boundary");
        return offsets[index];
    }

    public float minimumWidth(float fontScale) {
        return TIME_GUTTER + Arrays.stream(dayColumns).sum() * MIN_COLUMN_WIDTH * Math.max(1, fontScale);
    }
    public float height() { return HEADER_HEIGHT + allDayHeight + bodyHeight + 8; }
}
