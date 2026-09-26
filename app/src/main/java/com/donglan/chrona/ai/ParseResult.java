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

    public ParseResult(List<EventCandidate> candidates, Integer promptTokens,
            Integer completionTokens, Integer totalTokens) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }
}
