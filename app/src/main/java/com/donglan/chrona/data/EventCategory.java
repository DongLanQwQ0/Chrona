package com.donglan.chrona.data;

/** Stable values stored with each event; labels may change without migrating the database. */
public final class EventCategory {
    public static final String EVENT = "event";
    public static final String TASK = "task";
    public static final String REMINDER = "reminder";
    public static final String DEADLINE = "deadline";
    public static final String NOTE = "note";

    public static final String[] VALUES = {EVENT, TASK, REMINDER, DEADLINE, NOTE};
    public static final String[] LABELS = {"活动", "待办", "提醒", "截止", "备忘"};

    private EventCategory() { }

    public static String normalize(String value) {
        for (String item : VALUES) if (item.equals(value)) return item;
        return EVENT;
    }

    public static int indexOf(String value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(value)) return i;
        return 0;
    }

    public static String label(String value) {
        return LABELS[indexOf(value)];
    }
}
