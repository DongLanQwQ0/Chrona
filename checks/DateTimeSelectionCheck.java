package com.donglan.chrona;

import java.time.LocalDateTime;

public final class DateTimeSelectionCheck {
    public static void main(String[] args) {
        DateTimeSelection value = new DateTimeSelection(LocalDateTime.of(2028, 2, 29, 23, 59));
        value.move(true, 0, -1);
        check(value.value().getDayOfMonth() == 28, "leap day clamps when year changes");
        value.move(true, 1, 1);
        value.move(true, 2, 3);
        check(value.value().getDayOfMonth() == 31, "day wheel uses selected month's length");
        value.move(true, 1, 1);
        check(value.value().getDayOfMonth() == 30, "short month clamps invalid day");
        value.move(false, 0, 1); value.move(false, 1, 1);
        check(value.value().getHour() == 0 && value.value().getMinute() == 0, "24 hour wheels wrap");
        check(value.value().toLocalDate().toString().equals("2027-04-30"), "time wheel preserves date");
        value.move(true, 0, 9999);
        check(value.value().getYear() == 9999, "maximum year");
        value.move(true, 0, -20000);
        check(value.value().getYear() == 1, "minimum year");
        LocalDateTime committed = value.value();
        value.move(false, 1, 7);
        check(committed.getMinute() == 0, "selection does not mutate original value");
        check(value.offset(false, 1, -8) == 59, "negative wrap");
        System.out.println("DateTimeSelectionCheck passed");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
