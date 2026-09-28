package com.donglan.chrona.processing;

import com.donglan.chrona.data.TaskFileAttachment;
import java.lang.reflect.Method;
import java.util.List;

/** Runs against the compiled service without constructing an Android component. */
public final class FileMetadataCheck {
    public static void main(String[] args) throws Exception {
        Method format = ProcessingJobService.class.getDeclaredMethod("formatFileMetadata", List.class);
        format.setAccessible(true);
        for (Object files : new Object[]{null, List.of()}) {
            String metadata = (String) format.invoke(null, files);
            if (metadata == null || !metadata.isEmpty())
                throw new AssertionError("Missing files must produce a non-null empty string");
        }
        TaskFileAttachment file = new TaskFileAttachment(1, 1, "stored", "meeting\nnotes.txt", "text/plain", 42);
        String metadata = (String) format.invoke(null, List.of(file));
        if (!metadata.contains("meeting notes.txt") || !metadata.contains("text/plain")
                || !metadata.contains("42 bytes")) throw new AssertionError(metadata);
        System.out.println("FileMetadataCheck passed");
    }
}
