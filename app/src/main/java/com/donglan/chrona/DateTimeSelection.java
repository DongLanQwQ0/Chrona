package com.donglan.chrona;

import java.time.LocalDateTime;

/** Local selection only; the caller commits it after confirmation. */
final class DateTimeSelection {
    private LocalDateTime value;
    DateTimeSelection(LocalDateTime initial) { value = initial.withSecond(0).withNano(0); }
    LocalDateTime value() { return value; }
    int number(boolean date, int column) {
        if (!date) return column == 0 ? value.getHour() : value.getMinute();
        return column == 0 ? value.getYear() : column == 1 ? value.getMonthValue() : value.getDayOfMonth();
    }
    int minimum(boolean date, int column) { return date ? 1 : 0; }
    int maximum(boolean date, int column) {
        if (!date) return column == 0 ? 23 : 59;
        return column == 0 ? 9999 : column == 1 ? 12 : value.toLocalDate().lengthOfMonth();
    }
    int offset(boolean date, int column, int steps) {
        int min = minimum(date, column), max = maximum(date, column);
        int next = number(date, column) + steps;
        if (date && column == 0) return Math.max(min, Math.min(max, next));
        return min + Math.floorMod(next - min, max - min + 1);
    }
    void move(boolean date, int column, int steps) {
        int next = offset(date, column, steps);
        if (!date) value = column == 0 ? value.withHour(next) : value.withMinute(next);
        else if (column == 0) value = value.withYear(next);
        else if (column == 1) value = value.withMonth(next);
        else value = value.withDayOfMonth(next);
    }
}
