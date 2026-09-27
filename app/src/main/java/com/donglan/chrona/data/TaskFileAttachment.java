package com.donglan.chrona.data;

/** Metadata for an ordinary file attached to a capture; file bytes stay in app-private storage. */
public final class TaskFileAttachment {
    public final long id;
    public final long taskId;
    public final String storedName;
    public final String displayName;
    public final String mimeType;
    public final long sizeBytes;

    public TaskFileAttachment(long id, long taskId, String storedName, String displayName,
            String mimeType, long sizeBytes) {
        this.id = id;
        this.taskId = taskId;
        this.storedName = storedName;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }
}
