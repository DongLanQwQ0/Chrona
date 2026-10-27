package com.donglan.chrona.ai;

import com.donglan.chrona.data.EventCandidate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Checkable time evidence, separate from provider reasoning and user-facing notes. */
public final class TimeAssessment {
    public static final String[] FIELDS = {"start", "end", "due", "suggested_start", "plan_until", "duration_minutes", "recurrence"};
    private TimeAssessment() { }

    static JSONObject schema() throws JSONException {
        JSONObject properties = new JSONObject();
        JSONObject reason = new JSONObject().put("type", "object").put("additionalProperties", false);
        JSONObject rp = new JSONObject()
                .put("certainty", new JSONObject().put("type", "string").put("enum", new JSONArray()
                        .put("explicit").put("rule_based").put("uncertain")))
                .put("basis", new JSONObject().put("type", "string"))
                .put("rule", new JSONObject().put("type", new JSONArray().put("string").put("null")))
                .put("reason", new JSONObject().put("type", "string"));
        reason.put("properties", rp).put("required", rp.names());
        for (String field : FIELDS) properties.put(field, new JSONObject().put("anyOf", new JSONArray()
                .put(reason).put(new JSONObject().put("type", "null"))));
        return new JSONObject().put("type", "object").put("additionalProperties", false)
                .put("properties", properties).put("required", properties.names());
    }

    /** Network responses must explain both certain and doubtful values; never fabricate evidence. */
    static void validate(JSONArray events) throws JSONException {
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            JSONObject assessment = event.getJSONObject("time_assessment");
            if (assessment.length() != FIELDS.length) throw new JSONException("Invalid time assessment fields");
            for (String field : FIELDS) {
                if (!assessment.has(field)) throw new JSONException("Missing time assessment: " + field);
                if (assessment.isNull(field)) {
                    if (!event.isNull(field)) throw new JSONException("Missing time evidence: " + field);
                    continue;
                }
                JSONObject item = assessment.getJSONObject(field);
                if (item.length() != 4) throw new JSONException("Invalid time evidence: " + field);
                String certainty = item.getString("certainty");
                if (!certainty.equals("explicit") && !certainty.equals("rule_based") && !certainty.equals("uncertain"))
                    throw new JSONException("Invalid time certainty");
                for (String key : new String[]{"basis", "reason"})
                    if (!(item.opt(key) instanceof String) || item.getString(key).trim().isEmpty())
                        throw new JSONException("Empty time evidence: " + key);
                if (!item.has("rule") || (!item.isNull("rule") && !(item.opt("rule") instanceof String)))
                    throw new JSONException("Invalid time evidence rule");
                if (certainty.equals("rule_based") && (item.isNull("rule") || item.getString("rule").trim().isEmpty()))
                    throw new JSONException("Rule-based time requires the applied preference");
            }
        }
    }

    static boolean uncertain(JSONObject event) {
        JSONObject assessment = event.optJSONObject("time_assessment");
        if (assessment == null) return false; // Direct normalizer callers retain their existing contract.
        for (String field : FIELDS) {
            JSONObject item = assessment.optJSONObject(field);
            if (item != null && "uncertain".equals(item.optString("certainty"))) return true;
        }
        return false;
    }

    static boolean preferenceSuggestion(JSONObject event) {
        JSONObject assessment = event.optJSONObject("time_assessment");
        JSONObject item = assessment == null ? null : assessment.optJSONObject("suggested_start");
        return item != null && "rule_based".equals(item.optString("certainty"));
    }

    static boolean dateOnlyDeadline(JSONObject event) {
        JSONObject due = event.optJSONObject("due");
        return due != null && due.isNull("time") && due.isNull("offset_minutes");
    }

    public static JSONObject snapshot(EventCandidate item) throws JSONException {
        return new JSONObject().put("title", item.title).put("category", item.category)
                .put("start", nullable(item.startAtMillis)).put("end", nullable(item.endAtMillis))
                .put("due", nullable(item.dueAtMillis)).put("time_zone_id", nullable(item.timeZoneId))
                .put("all_day", item.allDay).put("uncertainty_level", item.uncertaintyLevel)
                .put("needs_confirmation", item.needsConfirmation).put("end_auto_generated", item.endAutoGenerated)
                .put("recurrence", item.repeatRule == null ? JSONObject.NULL : item.repeatRule.toJson());
    }

    static JSONArray audit(JSONArray events, java.util.List<EventCandidate> candidates, long reference, String zone) throws JSONException {
        JSONArray entries = new JSONArray();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            EventCandidate candidate = candidates.get(i);
            JSONArray adjustments = new JSONArray();
            JSONObject validation = SemanticEventNormalizer.sourceTimeValidation(event, reference, zone);
            if (dateOnlyDeadline(event) && candidate.dueAtMillis != null)
                adjustments.put("日期截止按产品规则补为该日23:59，原始钟点未提供。");
            if (event.isNull("start") && candidate.startAtMillis != null) {
                adjustments.put(preferenceSuggestion(event) && candidate.uncertaintyLevel == 0
                        ? "使用已保存偏好的确定执行建议。"
                        : "按整个可用周期、工作量、阶段依赖及工作窗口生成可编辑执行建议，保留高存疑。");
            }
            JSONObject sourceStart = event.optJSONObject("start");
            if (sourceStart != null && sourceStart.isNull("time") && sourceStart.isNull("offset_minutes")
                    && candidate.startAtMillis != null && !candidate.allDay)
                adjustments.put("起点日期明确但未给钟点，程序暂选15:00，保留高存疑待确认。");
            JSONObject sourceEnd=event.optJSONObject("end");
            if(sourceEnd!=null&&sourceEnd.isNull("time")&&sourceEnd.isNull("offset_minutes")
                    &&(!sourceEnd.isNull("date")||!sourceEnd.isNull("day_offset")||!sourceEnd.isNull("weekday"))
                    &&candidate.endAtMillis!=null&&!candidate.allDay)
                adjustments.put("结束日期明确但未给钟点，保留该日期并按起点钟点与时长建议结束钟点，待确认。");
            if (event.isNull("end") && candidate.endAtMillis != null)
                adjustments.put(candidate.endAutoGenerated ? "程序按事项类别补全默认结束时间。" : "程序按执行时长补全结束时间。");
            if (candidate.uncertaintyLevel > event.optInt("uncertainty_level", 2))
                adjustments.put("时间依据或本地时间合法性与安排校验将存疑等级提升为2；以实际结果为准。");
            if (!event.isNull("due") && event.isNull("start") && candidate.startAtMillis == null)
                adjustments.put("未能生成可执行起点；保留原始截止与待确认草稿，原因见归一化说明。");
            entries.put(new JSONObject().put("position", i).put("plan_group", event.opt("plan_group"))
                    .put("model", new JSONObject(event.toString())).put("resolved", snapshot(candidate))
                    .put("source_time_validation", validation)
                    .put("resolved_time_assessment", resolvedAssessment(event, candidate, validation))
                    .put("adjustments", adjustments).put("normalization_note", nullable(candidate.description)));
        }
        return entries;
    }

    private static JSONObject resolvedAssessment(JSONObject event, EventCandidate item, JSONObject validation) throws JSONException {
        JSONObject result = new JSONObject();
        JSONObject assessment = event.getJSONObject("time_assessment");
        String startCertainty = "uncertain";
        for (String field : new String[]{"start", "end", "due"}) {
            Long value = field.equals("start") ? item.startAtMillis : field.equals("end") ? item.endAtMillis : item.dueAtMillis;
            if (value == null) {
                result.put(field, event.isNull(field) ? JSONObject.NULL : localEvidence("uncertain", null, "未通过本地时间转换或合法性校验，等待修改。"));
                continue;
            }
            JSONObject source = assessment.optJSONObject(field);
            JSONObject raw = event.optJSONObject(field);
            JSONObject evidence;
            JSONObject checked = validation.getJSONObject(field);
            if (checked.getBoolean("requires_review")) {
                evidence = localEvidence("uncertain", "local_time_validation", checked.getJSONArray("notes").getString(0));
            } else if (source != null && raw != null && (!raw.isNull("time") || !raw.isNull("offset_minutes") || item.allDay)) {
                evidence = new JSONObject(source.toString());
            } else if (field.equals("due")) {
                evidence = localEvidence(source != null && "uncertain".equals(source.optString("certainty")) ? "uncertain" : "rule_based",
                        "date_only_deadline", "保留原截止日期，按产品规则补23:59。");
            } else if (field.equals("start")) {
                String rule = raw != null ? "missing_start_clock" : preferenceSuggestion(event) ? "saved_preference" : "whole_period_scheduling";
                evidence = localEvidence(item.uncertaintyLevel == 0 ? "rule_based" : "uncertain", rule,
                        raw != null ? "缺少原文钟点，暂填具体时间待确认。" : "按对应执行规则补全，以实际归一化时间为准。");
            } else {
                JSONObject duration = assessment.optJSONObject("duration_minutes");
                boolean certain = !startCertainty.equals("uncertain")
                        && (duration == null || !duration.optString("certainty").equals("uncertain"));
                evidence = localEvidence(certain ? "rule_based" : "uncertain", "duration_completion",
                        "按开始时刻及明确时长、偏好区间或事项默认时长补全结束。");
            }
            if (field.equals("start")) startCertainty = evidence.getString("certainty");
            result.put(field, evidence);
        }
        return result;
    }

    private static JSONObject localEvidence(String certainty, String rule, String reason) throws JSONException {
        return new JSONObject().put("certainty", certainty).put("rule", nullable(rule)).put("reason", reason);
    }

    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
}
