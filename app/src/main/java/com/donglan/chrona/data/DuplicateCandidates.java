package com.donglan.chrona.data;

import java.text.Normalizer;
import java.util.*;

/** Business identity independent of capture metadata and calendar-provider identity. */
public final class DuplicateCandidates {
    private DuplicateCandidates() { }
    public static List<Object> key(EventCandidate value) {
        boolean protectDescription = value.isRecord
                || (value.startAtMillis == null && value.endAtMillis == null && value.dueAtMillis == null);
        return Arrays.asList(normalized(value.title), value.startAtMillis, value.endAtMillis,
                value.dueAtMillis, normalized(value.location), value.category, value.allDay,
                value.isRecord, value.completed(), protectDescription ? value.description : null,
                value.repeatRule == null ? null : value.repeatRule.toJson().toString(),
                value.repeatRule == null ? null : value.timeZoneId, value.repeatExceptions, value.repeatStoppedAtMillis);
    }
    private static String normalized(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))
                .forEach(result::appendCodePoint);
        return result.toString();
    }
    public static List<List<EventCandidate>> groups(List<EventCandidate> candidates) {
        Map<List<Object>, List<EventCandidate>> groups = new LinkedHashMap<>();
        for (EventCandidate candidate : candidates)
            groups.computeIfAbsent(key(candidate), ignored -> new ArrayList<>()).add(candidate);
        List<List<EventCandidate>> result = new ArrayList<>();
        for (List<EventCandidate> group : groups.values()) if (group.size() > 1) result.add(group);
        return result;
    }
}
