package com.donglan.chrona.zju;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of one read-only school synchronization. Unknown submission state is null. */
public final class ZjuSnapshot {
    public final List<Homework> homework;
    public final List<Course> courses;
    public final List<Lesson> lessons;
    public final List<String> warnings;
    /** Fully read regular-course terms, including successfully read empty terms. */
    public final List<String> completeTimetableTerms;

    public ZjuSnapshot(List<Homework> homework, List<Course> courses, List<Lesson> lessons,
                       List<String> warnings) {
        this(homework, courses, lessons, warnings, Collections.emptyList());
    }

    public ZjuSnapshot(List<Homework> homework, List<Course> courses, List<Lesson> lessons,
                       List<String> warnings, List<String> completeTimetableTerms) {
        this.homework = immutable(homework);
        this.courses = immutable(courses);
        this.lessons = immutable(lessons);
        this.warnings = immutable(warnings);
        this.completeTimetableTerms = immutable(completeTimetableTerms);
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public static final class Homework {
        public final String key, courseId, title, description, url, updatedAt;
        public final Long deadline;
        public final Boolean submitted;
        public Homework(String key, String courseId, String title, String description, String url,
                        String updatedAt, Long deadline, Boolean submitted) {
            this.key = key; this.courseId = courseId; this.title = title;
            this.description = description; this.url = url; this.updatedAt = updatedAt;
            this.deadline = deadline; this.submitted = submitted;
        }
    }

    public static final class Course {
        public final String id, title;
        public final boolean activitiesComplete, statusComplete;
        public Course(String id, String title, boolean activitiesComplete, boolean statusComplete) {
            this.id = id; this.title = title; this.activitiesComplete = activitiesComplete;
            this.statusComplete = statusComplete;
        }
    }

    public static final class Lesson {
        public final String key, title, location, term, notes;
        public final long start, end;
        public Lesson(String key, String title, String location, String term, long start, long end) {
            this(key, title, location, term, start, end, "");
        }
        public Lesson(String key, String title, String location, String term, long start, long end, String notes) {
            this.key = key; this.title = title; this.location = location; this.term = term;
            this.start = start; this.end = end;
            this.notes = notes == null ? "" : notes;
        }
    }
}
