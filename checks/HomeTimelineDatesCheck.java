package com.donglan.chrona;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class HomeTimelineDatesCheck {
    public static void main(String[] args) {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        long beforeMidnight = ZonedDateTime.of(2026, 9, 27, 23, 50, 0, 0, shanghai)
                .toInstant().toEpochMilli();
        long afterMidnight = ZonedDateTime.of(2026, 9, 28, 0, 20, 0, 0, shanghai)
                .toInstant().toEpochMilli();
        long previousDay = ZonedDateTime.of(2026, 9, 26, 23, 59, 0, 0, shanghai)
                .toInstant().toEpochMilli();

        if (HomeTimelineDates.dayOffset(afterMidnight, beforeMidnight, shanghai) != 1)
            throw new AssertionError("after midnight must be tomorrow");
        if (HomeTimelineDates.dayOffset(beforeMidnight, beforeMidnight, shanghai) != 0)
            throw new AssertionError("reference instant must be today");
        if (HomeTimelineDates.dayOffset(previousDay, beforeMidnight, shanghai) != -1)
            throw new AssertionError("previous date must remain outside the two-day timeline");
    }
}
