package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.*;
import java.io.File;
import java.util.*;

/** Shared review rules and completion transitions against production TaskStore and real SQLite. */
public final class ReviewStatusCheck {
    static int checks;
    static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    static EventCandidate candidate(long task, String category, boolean uncertain,
            Long linked, Long done, boolean record) {
        boolean event = EventCategory.EVENT.equals(category) && !record;
        return new EventCandidate(0, task, "Review status", event ? 1000L : null,
                event ? 2000L : null, "UTC", "", "", null, uncertain, linked,
                category, false, false, uncertain ? EventCandidate.DOUBTFUL : EventCandidate.CERTAIN,
                null, done, record);
    }
    static void status(TaskStore store, long id, String expected, String label) {
        check(store.updateReviewStatus(id), label + " updates existing capture");
        check(expected.equals(store.getTask(id).status), label + " persisted status");
        check(expected.equals(TaskStore.reviewStatus(store.getCandidates(id))), label + " shared rule");
    }
    public static void main(String[] args) {
        Context context = new Context(new File(args[0], "review-status-" + System.nanoTime()));
        try (TaskStore store = new TaskStore(context)) {
            long id = store.insertTask("Review status source", null, "test", 1L);
            status(store, id, TaskRecord.NEEDS_REVIEW, "empty capture");
            store.appendCandidate(candidate(id, EventCategory.EVENT, false, null, null, false));
            status(store, id, TaskRecord.NEEDS_REVIEW, "certain unlinked event");
            EventCandidate event = store.getCandidates(id).get(0);
            check(store.setCalendarEventIdIfUnlinked(event.id, id, 123L), "link event");
            status(store, id, TaskRecord.READY, "linked event");
            long unlinked = store.appendCandidate(candidate(id, EventCategory.EVENT, false, null, null, false));
            status(store, id, TaskRecord.NEEDS_REVIEW, "unlinked sibling prevents ready");
            check(store.deleteCandidate(unlinked, id), "delete unlinked sibling");
            status(store, id, TaskRecord.READY, "all remaining candidates settled");
            check(store.deleteCandidate(event.id, id), "delete last event");
            status(store, id, TaskRecord.NEEDS_REVIEW, "delete final candidate");

            for (String category : List.of(EventCategory.TASK, EventCategory.HOMEWORK)) {
                store.replaceCandidates(id, List.of(candidate(id, category, false, null, null, false)));
                status(store, id, TaskRecord.READY, category + " without time");
                store.replaceCandidates(id, List.of(candidate(id, category, true, null, null, false)));
                status(store, id, TaskRecord.NEEDS_REVIEW, category + " awaiting confirmation");
                long candidateId = store.getCandidates(id).get(0).id;
                check(store.setCandidateCompleted(candidateId, id, true), category + " completion accepted");
                check(TaskRecord.READY.equals(store.getTask(id).status), category + " completion recomputes ready");
                check(store.setCandidateCompleted(candidateId, id, false), category + " restore accepted");
                check(TaskRecord.NEEDS_REVIEW.equals(store.getTask(id).status), category + " restore recomputes review");
            }
            store.replaceCandidates(id, List.of(candidate(id, EventCategory.EVENT, true, null, null, true)));
            status(store, id, TaskRecord.READY, "pure record");
            check(!store.updateReviewStatus(Long.MAX_VALUE), "missing capture is not updated");
        }
        System.out.println("Shared review status: " + checks + " checks passed; production TaskStore and real SQLite");
    }
}
