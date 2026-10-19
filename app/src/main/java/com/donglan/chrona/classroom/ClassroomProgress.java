package com.donglan.chrona.classroom;

/** Actual completed units for one stage; zero total means no measurable denominator yet. */
public final class ClassroomProgress {
    public final String stage, detail;
    public final int completed, total;
    public ClassroomProgress(String stage, int completed, int total, String detail) {
        this.stage = stage;
        this.total = Math.max(0, total);
        this.completed = Math.max(0, Math.min(completed, this.total));
        this.detail = detail == null ? "" : detail;
    }
    public static ClassroomProgress waiting(String stage, String detail) {
        return new ClassroomProgress(stage, 0, 0, detail);
    }
    public String text() {
        return stage + (total > 0 ? " · " + completed + " / " + total : "")
                + (detail.isEmpty() ? "" : "\n" + detail);
    }
}
