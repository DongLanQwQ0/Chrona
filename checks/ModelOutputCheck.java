package com.donglan.chrona.processing;

import com.donglan.chrona.JsonFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Desktop regression for actual preview files and formatted UTF-8 page boundaries. */
public final class ModelOutputCheck {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("chrona-output-check-");
        try {
            StreamingOutputStore store = new StreamingOutputStore(directory.toFile());
            String thought = "We need an answer. Keep every word and space.\nSecond line.\n";
            String answer = "{\"events\":[],\"note\":\"two words 中文\"}";
            String mixed = "[思考内容]\n" + thought + "\n[模型输出]\n" + answer;
            check(JsonFormat.formatModelOutput(mixed).startsWith("[思考内容]\n" + thought), "reasoning unchanged");
            check(JsonFormat.format("[思考内容]\nWe need an answer.").contains("We need an answer."), "prose keeps spaces");
            try (StreamingOutputStore.Writer writer = store.begin(1)) {
                writer.appendReasoning(thought.repeat(400));
            }
            check(store.readPreview(1).equals(StreamingOutputStore.THINKING_MESSAGE), "long thought never previewed as answer");
            try (StreamingOutputStore.Writer writer = store.begin(2)) {
                writer.appendReasoning(thought.repeat(500));
                writer.append(answer);
            }
            check(!store.readPreview(2).contains("We need") && store.readPreview(2).contains("two words 中文"), "preview final output");
            long size = store.renderedLength(2);
            StringBuilder joined = new StringBuilder();
            for (int page = 0; page * StreamingOutputStore.PAGE_BYTES < size; page++) joined.append(store.readPage(2, page));
            check(joined.toString().startsWith("[思考内容]\n" + thought.repeat(500)), "paged reasoning spaces intact");
            check(joined.toString().contains("two words 中文"), "JSON quoted spaces intact");
            Files.writeString(directory.resolve("task-3.txt"), answer, StandardCharsets.UTF_8);
            check(store.readPreview(3).contains("two words 中文"), "legacy answer-only file");
            for (int offset = 8150; offset < 8200; offset++) {
                String head = "[思考内容]\n" + "x".repeat(offset) + "\n[模型输出]\n";
                Files.writeString(directory.resolve("task-4.txt"), head + answer, StandardCharsets.UTF_8);
                check(store.readPreview(4).contains("two words 中文"), "marker across block boundary " + offset);
            }
            // UTF-8 characters may straddle page boundaries; concatenation must recover all text.
            String unicode = "[思考内容]\n" + "中文 words \n".repeat(6000);
            Files.writeString(directory.resolve("task-5.txt"), unicode, StandardCharsets.UTF_8);
            joined.setLength(0);
            size = store.renderedLength(5);
            for (int page = 0; page * StreamingOutputStore.PAGE_BYTES < size; page++) joined.append(store.readPage(5, page));
            check(joined.toString().equals(unicode), "UTF-8 pagination is lossless");
            System.out.println("ModelOutputCheck passed");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(directory);
        }
    }
    private static void check(boolean result, String label) { if (!result) throw new AssertionError(label); }
}
