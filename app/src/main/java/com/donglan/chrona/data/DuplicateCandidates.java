package com.donglan.chrona.data;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Business identity independent of capture metadata and calendar-provider identity. */
public final class DuplicateCandidates {
    private static final int MIN_CONTAINED_TITLE_LENGTH = 4;
    private static final double MIN_CONTAINMENT_COVERAGE = 0.65;
    private static final int MIN_SIMILAR_TITLE_LENGTH = 5;
    private static final int MIN_TITLE_SIMILARITY_PERCENT = 88;
    private static final Pattern NUMBERS = Pattern.compile("\\p{Nd}+|[〇零一二三四五六七八九十百千万亿两壹贰叁肆伍陆柒捌玖拾佰仟萬億]+");
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
        List<EventCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingLong((EventCandidate value) -> value.id)
                .thenComparingLong(value -> value.taskId));
        Map<List<Object>, List<List<EventCandidate>>> buckets = new LinkedHashMap<>();
        for (EventCandidate candidate : ordered) {
            List<Object> bucketKey = new ArrayList<>(key(candidate));
            if (datedSchedule(candidate)) { bucketKey.set(0, null); bucketKey.set(4, null); }
            List<List<EventCandidate>> bucket = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayList<>());
            List<EventCandidate> target = null;
            for (List<EventCandidate> group : bucket) {
                // Similarity is not transitive: a new member must agree with every existing member.
                if (group.stream().allMatch(existing -> matches(existing, candidate))) { target = group; break; }
            }
            if (target == null) { target = new ArrayList<>(); bucket.add(target); }
            target.add(candidate);
        }
        List<List<EventCandidate>> result = new ArrayList<>();
        for (List<List<EventCandidate>> bucket : buckets.values())
            for (List<EventCandidate> group : bucket) if (group.size() > 1) result.add(group);
        return result;
    }

    private static boolean datedSchedule(EventCandidate value) {
        return !value.isRecord && (value.startAtMillis != null || value.endAtMillis != null || value.dueAtMillis != null);
    }

    /** Called only within a bucket whose time, type, completion and repeat identity already agree. */
    private static boolean matches(EventCandidate first, EventCandidate second) {
        boolean dated = datedSchedule(first) && datedSchedule(second);
        if (dated && !numbers(first.title).equals(numbers(second.title))) return false;
        if (key(first).equals(key(second))) return true;
        if (!dated) return false;
        String firstLocation = compact(first.location), secondLocation = compact(second.location);
        if (!firstLocation.isEmpty() && !secondLocation.isEmpty() && !firstLocation.equals(secondLocation)) return false;
        String firstTitle = compact(first.title), secondTitle = compact(second.title);
        if (firstTitle.isEmpty() || secondTitle.isEmpty()) return false;
        if (firstTitle.equals(secondTitle)) return true;
        int[] a = firstTitle.codePoints().toArray(), b = secondTitle.codePoints().toArray();
        int shorter = Math.min(a.length, b.length), longer = Math.max(a.length, b.length);
        if (shorter >= MIN_CONTAINED_TITLE_LENGTH && (double) shorter / longer >= MIN_CONTAINMENT_COVERAGE
                && (firstTitle.contains(secondTitle) || secondTitle.contains(firstTitle))) return true;
        int maxEdits = (int) ((long) longer * (100 - MIN_TITLE_SIMILARITY_PERCENT) / 100);
        if (shorter < MIN_SIMILAR_TITLE_LENGTH || longer - shorter > maxEdits) return false;
        return distanceAtMost(a, b, maxEdits);
    }

    private static String compact(String value) {
        return normalized(value).replaceAll("\\p{P}+", "");
    }

    private static List<String> numbers(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = NUMBERS.matcher(Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static boolean distanceAtMost(int[] first, int[] second, int maxEdits) {
        int[] previous = new int[second.length + 1], current = new int[second.length + 1];
        int outside = maxEdits + 1;
        Arrays.fill(previous, outside);
        Arrays.fill(current, outside);
        for (int j = 0; j <= Math.min(second.length, maxEdits); j++) previous[j] = j;
        for (int i = 1; i <= first.length; i++) {
            current[0] = i <= maxEdits ? i : outside;
            int begin = Math.max(1, i - maxEdits), end = Math.min(second.length, i + maxEdits);
            if (begin > 1) current[begin - 1] = outside;
            if (end < second.length) current[end + 1] = outside;
            int rowMinimum = current[0];
            for (int j = begin; j <= end; j++) {
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + (first[i - 1] == second[j - 1] ? 0 : 1));
                rowMinimum = Math.min(rowMinimum, current[j]);
            }
            if (rowMinimum > maxEdits) return false;
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[second.length] <= maxEdits;
    }
}
