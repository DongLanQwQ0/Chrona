package com.donglan.chrona;

import java.time.Instant;
import java.time.ZoneId;

/** A local calendar day, rather than a rolling 24-hour interval. */
final class ReleaseCheckPolicy {
    static boolean due(long now, long previousAttempt, ZoneId zone) {
        return previousAttempt <= 0 || !Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .equals(Instant.ofEpochMilli(previousAttempt).atZone(zone).toLocalDate());
    }

    private ReleaseCheckPolicy() { }
}
