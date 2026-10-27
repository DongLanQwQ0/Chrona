package com.donglan.chrona.zju;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Official published exam slot; unrecognised time text is retained rather than guessed. */
public final class ZjuExam {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern TIME = Pattern.compile(
            "(\\d{4})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?\\s*[（(]?\\s*(\\d{1,2}):(\\d{2})\\s*[-—–~至]+\\s*(\\d{1,2}):(\\d{2})\\s*[）)]?");
    public final String courseKey, title, kind, time, room, seat;
    public final Long start, end;

    public ZjuExam(String courseKey, String title, String kind, String time, String room, String seat) {
        this.courseKey=courseKey;this.title=title;this.kind=kind;this.time=time;this.room=room;this.seat=seat;
        Long begin=null,finish=null;
        Matcher m=TIME.matcher(time.replace('：', ':').trim());
        if(m.matches())try{
            LocalDate day=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
            long a=day.atTime(LocalTime.of(Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)))).atZone(ZONE).toInstant().toEpochMilli();
            long b=day.atTime(LocalTime.of(Integer.parseInt(m.group(6)),Integer.parseInt(m.group(7)))).atZone(ZONE).toInstant().toEpochMilli();
            if(b>a){begin=a;finish=b;}
        }catch(java.time.DateTimeException ignored){}
        start=begin;end=finish;
    }

    public String displayTime() {
        if(start==null)return time;
        DateTimeFormatter first=DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm",Locale.CHINA).withZone(ZONE);
        return first.format(java.time.Instant.ofEpochMilli(start))+"–"+
                DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE).format(java.time.Instant.ofEpochMilli(end));
    }
    public boolean ended(long now){return end!=null&&end<=now;}
    public JSONObject json()throws JSONException{return new JSONObject().put("courseKey",courseKey).put("title",title)
            .put("kind",kind).put("time",time).put("room",room).put("seat",seat);}
    public static ZjuExam fromJson(JSONObject row)throws JSONException{return new ZjuExam(row.getString("courseKey"),
            row.getString("title"),row.getString("kind"),row.getString("time"),row.getString("room"),row.getString("seat"));}

    public static List<ZjuExam> parse(JSONArray rows)throws JSONException {
        Map<String,ZjuExam> exams=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            for(boolean midterm:new boolean[]{false,true}){
                String time=text(row,midterm?"qzkssj":"kssj");if(time.isEmpty())continue;
                String title=text(row,"kcmc");if(title.isEmpty())throw new JSONException("Missing exam course title");
                String room=text(row,midterm?"qzjsmc":"jsmc"),seat=text(row,midterm?"qzzwxh":"zwxh");
                String course=text(row,"xkkh");
                ZjuExam exam=new ZjuExam(course,title,midterm?"期中":"考试",time,room,seat);
                String when=exam.start==null?time:exam.start+"/"+exam.end;
                // Never merge distinct teaching groups, seats or conflicting rooms.
                String key=(course.isEmpty()?title:course)+"\0"+when+"\0"+room+"\0"+seat;
                exams.putIfAbsent(key,exam);
            }
        }
        return List.copyOf(exams.values());
    }
    public static List<ZjuExam> ordered(List<ZjuExam> exams,long now){
        List<ZjuExam> result=new ArrayList<>(exams);
        result.sort(Comparator.comparing((ZjuExam e)->e.ended(now))
                .thenComparingLong(e->e.start==null?Long.MAX_VALUE:e.ended(now)?-e.start:e.start)
                .thenComparing(e->e.title).thenComparing(e->e.courseKey).thenComparing(e->e.kind));
        return result;
    }
    private static String text(JSONObject row,String key)throws JSONException{
        if(!row.has(key)||row.isNull(key))return "";
        Object value=row.get(key);
        if(!(value instanceof String||value instanceof Number))throw new JSONException("Invalid exam field "+key);
        return value.toString().trim();
    }
}
