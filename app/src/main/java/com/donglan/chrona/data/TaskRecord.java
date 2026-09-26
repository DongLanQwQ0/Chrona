package com.donglan.chrona.data;

/** An input saved before asynchronous processing begins. Times are Unix milliseconds. */
public final class TaskRecord {
    public static final String QUEUED = "queued";
    public static final String PROCESSING = "processing";
    public static final String NEEDS_REVIEW = "needs_review";
    public static final String READY = "ready";
    public static final String FAILED = "failed";

    public final long id;
    public final String rawText;
    /** Relative name of an attached image in app storage, or null. */
    public final String imagePath;
    public final String source;
    public final long createdAtMillis;
    public final String status;
    public final String errorMessage;
    public final Integer promptTokens;
    public final Integer completionTokens;
    public final Integer totalTokens;
    public final Integer cachedTokens;
    /** Text read from links in the input by the last fetch, or null when it read nothing. */
    public final String linkText;
    /** Unix millis of the last link fetch attempt, or null when links were never fetched. */
    public final Long linkFetchedAtMillis;

    public TaskRecord(long id, String rawText, String source, long createdAtMillis,
            String status, String errorMessage) {
        this(id, rawText, null, source, createdAtMillis, status, errorMessage, null, null, null,
                null, null, null);
    }

    public TaskRecord(long id, String rawText, String imagePath, String source, long createdAtMillis,
            String status, String errorMessage, Integer promptTokens,
            Integer completionTokens, Integer totalTokens, Integer cachedTokens, String linkText,
            Long linkFetchedAtMillis) {
        this.id = id;
        this.rawText = rawText;
        this.imagePath = imagePath;
        this.source = source;
        this.createdAtMillis = createdAtMillis;
        this.status = status;
        this.errorMessage = errorMessage;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
        this.linkText = linkText;
        this.linkFetchedAtMillis = linkFetchedAtMillis;
    }

    public static boolean isValidStatus(String status) {
        return QUEUED.equals(status) || PROCESSING.equals(status)
                || NEEDS_REVIEW.equals(status) || READY.equals(status)
                || FAILED.equals(status);
    }
}
