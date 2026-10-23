package com.donglan.chrona.ai;

import com.donglan.chrona.data.EventCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed entries and the provider's reported usage; null token counts mean unreported. */
public final class ParseResult {
    public final List<EventCandidate> candidates;
    public final Integer promptTokens;
    public final Integer completionTokens;
    public final Integer totalTokens;
    public final Integer cachedTokens;
    /** Immutable JSON snapshot; includes model evidence and the actual normalized values. */
    public final String timeAuditJson;
    public final String parseRunId;

    public ParseResult(List<EventCandidate> candidates, Integer promptTokens,
            Integer completionTokens, Integer totalTokens, Integer cachedTokens) {
        this(candidates, promptTokens, completionTokens, totalTokens, cachedTokens, null);
    }

    public ParseResult(List<EventCandidate> candidates, Integer promptTokens,
            Integer completionTokens, Integer totalTokens, Integer cachedTokens, String timeAuditJson) {
        this(candidates, promptTokens, completionTokens, totalTokens, cachedTokens, timeAuditJson, null);
    }

    private ParseResult(List<EventCandidate> candidates, Integer promptTokens,
            Integer completionTokens, Integer totalTokens, Integer cachedTokens, String timeAuditJson, String parseRunId) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
        this.timeAuditJson = timeAuditJson;
        this.parseRunId = parseRunId;
    }

    public ParseResult withRunId(String runId) {
        return new ParseResult(candidates, promptTokens, completionTokens, totalTokens, cachedTokens, timeAuditJson, runId);
    }
}
