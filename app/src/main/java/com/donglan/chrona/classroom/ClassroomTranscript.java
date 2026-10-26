package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;

/** Glue 1.9.1 text frames, as used by the official classroom's live subtitle component. */
public final class ClassroomTranscript {
    private ClassroomTranscript() { }
    public static JSONObject payload(String frame)throws Exception {
        if(frame.length()>256*1024)throw new IllegalArgumentException("实时字幕消息过大");
        if(!frame.startsWith("cd"))return null;
        int delimiter=frame.indexOf('&',2);if(delimiter<0)return null;
        int length=Integer.parseInt(frame.substring(2,delimiter));int from=delimiter+1;
        if(length<0||length>frame.length()-from)throw new IllegalArgumentException("实时字幕消息无效");
        if(!frame.substring(from,from+length).equals("m"))return null;
        return new JSONObject(frame.substring(from+length));
    }
    /** Epoch-millisecond times are supplied by the actual live stream; missing clocks use explicit receipt timing. */
    public static JSONObject append(JSONObject previous,JSONObject payload,long lessonStarts,long receivedAt)throws Exception {
        JSONObject state=previous==null?new JSONObject().put("segments",new JSONArray()):new JSONObject(previous.toString());
        String text=payload.optString("sourcetext").trim();if(text.isEmpty())return state;
        JSONObject current=state.optJSONObject("current");double now=Math.max(0,(receivedAt-lessonStarts)/1000.0);
        double begin=payload.optDouble("text_begin_time",Double.NaN),end=payload.optDouble("text_end_time",Double.NaN);
        boolean aligned=Double.isFinite(begin)&&Double.isFinite(end)&&begin>0&&end>=begin;
        String identity=aligned?Double.toString(begin):current==null?"receipt:"+receivedAt:current.optString("live_id");
        JSONArray rows=state.getJSONArray("segments");
        for(int i=0;i<rows.length();i++)if(identity.equals(rows.getJSONObject(i).optString("live_id"))){
            JSONObject correction=rows.getJSONObject(i);correction.put("text",text).put("end",Math.max(correction.getDouble("start"),aligned?(end-lessonStarts)/1000:now));
            rows.put(i,correction);return state;
        }
        if(current!=null&&!current.optString("live_id").equals(identity))rows.put(current);
        if(current==null||!current.optString("live_id").equals(identity))current=new JSONObject().put("start",aligned?Math.max(0,(begin-lessonStarts)/1000):now)
                .put("origin","live").put("timing",aligned?"source":"received").put("live_id",identity);
        current.put("end",Math.max(current.getDouble("start"),aligned?(end-lessonStarts)/1000:now)).put("text",text);
        if("1".equals(payload.optString("end_time"))){rows.put(current);state.remove("current");}
        else state.put("current",current);
        return state;
    }
    public static JSONArray snapshot(JSONObject state)throws Exception {
        JSONArray rows=new JSONArray(state.getJSONArray("segments").toString());
        if(state.optJSONObject("current")!=null)rows.put(new JSONObject(state.getJSONObject("current").toString()));
        return rows;
    }
}
