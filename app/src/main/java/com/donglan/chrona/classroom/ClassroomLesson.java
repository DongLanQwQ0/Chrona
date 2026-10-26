package com.donglan.chrona.classroom;

import org.json.JSONException;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** One actual recording, identified independently of its title and calendar position. */
public final class ClassroomLesson {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public final String courseId, id, course, title, room, status;
    public final long startsAt;
    public ClassroomLesson(String courseId, String id, String course, String title, String room, String status, long startsAt) {
        this.courseId=courseId; this.id=id; this.course=course; this.title=title;
        this.room=room; this.status=status; this.startsAt=startsAt;
    }
    public boolean selectable(long now) { return startsAt > 0 && startsAt <= now && "6".equals(status); }
    public String state(long now) { return startsAt > now ? "未开始" : "6".equals(status) ? "可回看" : "1".equals(status)?"上课中":"准备中"; }
    public LocalDate date() { return Instant.ofEpochMilli(startsAt).atZone(ZONE).toLocalDate(); }
    public JSONObject json() throws JSONException {
        return new JSONObject().put("course_id",courseId).put("id",id).put("course",course).put("title",title)
                .put("room",room).put("status",status).put("starts_at",startsAt);
    }
    public static ClassroomLesson fromJson(JSONObject o) throws JSONException {
        return new ClassroomLesson(o.getString("course_id"),o.getString("id"),o.getString("course"),
                o.getString("title"),o.optString("room"),o.getString("status"),o.getLong("starts_at"));
    }
}
