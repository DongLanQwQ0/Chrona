package com.donglan.chrona;

import com.donglan.chrona.data.EventCandidate;
import java.util.List;

/** A schedule ID is stable even when the list and capture use different sort orders. */
final class CandidateSelection {
    static int indexOf(List<EventCandidate> candidates, long id, int fallback) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).id == id) return i;
        }
        return candidates.isEmpty() ? 0 : Math.max(0, Math.min(fallback, candidates.size() - 1));
    }

    private CandidateSelection() { }
}
