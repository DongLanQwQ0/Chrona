package com.donglan.chrona;

/** Committed dashboard visits, oldest first; returning never adds a new visit. */
final class SectionHistory {
    private static final int LIMIT = 10;
    private final java.util.ArrayDeque<Integer> previous = new java.util.ArrayDeque<>();

    void record(int from, int to) {
        if (from == to) return;
        if (previous.size() == LIMIT) previous.removeFirst();
        previous.addLast(from);
    }

    int previous() {
        return previous.isEmpty() ? -1 : previous.peekLast();
    }

    void commitBack(int destination) {
        if (previous() == destination && !previous.isEmpty()) previous.removeLast();
    }

    int[] snapshot() {
        return previous.stream().mapToInt(Integer::intValue).toArray();
    }

    void restore(int[] sections) {
        previous.clear();
        if (sections == null) return;
        for (int section : sections) {
            if (section < DashboardActivity.HOME || section > DashboardActivity.SCHEDULE) continue;
            if (previous.size() == LIMIT) previous.removeFirst();
            previous.addLast(section);
        }
    }
}
