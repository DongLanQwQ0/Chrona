package com.donglan.chrona.zju;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Credential-free, injectable academic calendar. Dates are in Asia/Shanghai. */
public final class ZjuCalendarConfig {
    public String campus = "ordinary", selectedYear = "", selectedTerm = "";
    public final List<Term> terms = new ArrayList<>();
    public final List<String> starts = new ArrayList<>(List.of("08:00","08:50","10:00","10:50","11:40","13:25","14:15","15:05","16:15","17:05","18:50","19:40","20:30"));
    public final List<String> ends = new ArrayList<>(List.of("08:45","09:35","10:45","11:35","12:25","14:10","15:00","15:50","17:00","17:50","19:35","20:25","21:15"));
    public final Set<LocalDate> closed = new TreeSet<>();
    /** Actual date -> original teaching date; a moved class replaces both original slots. */
    public final Map<LocalDate,LocalDate> makeup = new TreeMap<>();
    public static final class Term {
        public String year, name;
        public LocalDate start, end, teachingEnd;
        public Term(String year,String name,String start,String end,String teachingEnd){
            this.year=year;this.name=name;this.start=LocalDate.parse(start);this.end=LocalDate.parse(end);this.teachingEnd=LocalDate.parse(teachingEnd);
        }
    }
    public Term term(String year,String name){for(Term t:terms)if(t.year.equals(year)&&t.name.equals(name))return t;return null;}
    public LocalDate actualDate(LocalDate teaching){
        for(Map.Entry<LocalDate,LocalDate> e:makeup.entrySet())if(e.getValue().equals(teaching))return e.getKey();
        return closed.contains(teaching)||makeup.containsKey(teaching)?null:teaching;
    }
    public void validate(){
        if(!List.of("ordinary","haining","custom").contains(campus))throw new IllegalArgumentException("请选择校区");
        if(!selectedYear.isEmpty()&&(!selectedYear.matches("\\d{4}-\\d{4}")||Integer.parseInt(selectedYear.substring(5))!=Integer.parseInt(selectedYear.substring(0,4))+1))throw new IllegalArgumentException("学年格式应为 2026-2027");
        Set<String> ids=new HashSet<>();
        for(Term t:terms){
            if(!t.year.matches("\\d{4}-\\d{4}")||Integer.parseInt(t.year.substring(5))!=Integer.parseInt(t.year.substring(0,4))+1)throw new IllegalArgumentException("学年格式应为 2026-2027");
            if(t.name.isBlank()||!ids.add(t.year+"/"+t.name))throw new IllegalArgumentException("学期名称重复或为空");
            if(t.start.getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException(t.name+"学期须从周一开始");
            int academicYear=Integer.parseInt(t.year.substring(0,4));
            if(t.start.isBefore(LocalDate.of(academicYear,1,1))||t.end.isAfter(LocalDate.of(academicYear+1,12,31)))throw new IllegalArgumentException("日期须位于所选学年内");
            if(t.end.isBefore(t.start)||t.teachingEnd.isBefore(t.start)||t.teachingEnd.isAfter(t.end)||t.end.isAfter(t.start.plusYears(1)))throw new IllegalArgumentException(t.name+"学期日期范围无效");
        }
        if(starts.isEmpty()||starts.size()!=ends.size()||starts.size()>30)throw new IllegalArgumentException("节次需成对填写，最多30节");
        LocalTime previous=null;
        for(int i=0;i<starts.size();i++){LocalTime s=LocalTime.parse(starts.get(i)),e=LocalTime.parse(ends.get(i));if(!e.isAfter(s)||(previous!=null&&s.isBefore(previous)))throw new IllegalArgumentException("节次需按时间排列且结束晚于开始");previous=e;}
        Set<LocalDate> targets=new HashSet<>();
        for(Map.Entry<LocalDate,LocalDate> e:makeup.entrySet()){
            LocalDate a=e.getKey(),b=e.getValue();
            if(a.equals(b)||!targets.add(b))throw new IllegalArgumentException("补课日期重复或指向自身");
            Set<LocalDate> visited=new HashSet<>();LocalDate at=a;
            while(makeup.containsKey(at)){if(!visited.add(at))throw new IllegalArgumentException("补课映射存在循环");at=makeup.get(at);}
            boolean inTerm=false;for(Term t:terms)if(!b.isBefore(t.start)&&!b.isAfter(t.teachingEnd)&&!a.isBefore(t.start.minusMonths(1))&&!a.isAfter(t.end.plusMonths(1)))inTerm=true;
            if(!inTerm)throw new IllegalArgumentException("补课映射超出已配置学期");
            if(makeup.containsKey(b))throw new IllegalArgumentException("补课映射须直接指向原授课日期");
        }
        for(LocalDate d:closed){boolean within=false;for(Term t:terms)if(!d.isBefore(t.start.minusMonths(1))&&!d.isAfter(t.end.plusMonths(1)))within=true;if(!within)throw new IllegalArgumentException("停课日超出已配置学期");}
    }
    public JSONObject json(){try{
        JSONObject o=new JSONObject().put("version",1).put("campus",campus).put("selectedYear",selectedYear).put("selectedTerm",selectedTerm);
        JSONArray ts=new JSONArray();for(Term t:terms)ts.put(new JSONObject().put("year",t.year).put("name",t.name).put("start",t.start.toString()).put("end",t.end.toString()).put("teachingEnd",t.teachingEnd.toString()));
        JSONArray cs=new JSONArray();for(LocalDate d:closed)cs.put(d.toString());JSONObject ms=new JSONObject();for(Map.Entry<LocalDate,LocalDate>e:makeup.entrySet())ms.put(e.getKey().toString(),e.getValue().toString());
        return o.put("terms",ts).put("starts",new JSONArray(starts)).put("ends",new JSONArray(ends)).put("closed",cs).put("makeup",ms);
    }catch(Exception e){throw new IllegalStateException(e);}}
    public static ZjuCalendarConfig parse(String raw){try{
        JSONObject o=new JSONObject(raw);if(o.getInt("version")!=1)throw new IllegalArgumentException("校历文件版本不支持");
        // Reject unknown fields so account data can never enter calendar exports.
        Set<String> allowed=Set.of("version","campus","selectedYear","selectedTerm","terms","starts","ends","closed","makeup");for(Iterator<String>it=o.keys();it.hasNext();)if(!allowed.contains(it.next()))throw new IllegalArgumentException("校历包含未知字段");
        ZjuCalendarConfig c=new ZjuCalendarConfig();c.campus=o.getString("campus");c.selectedYear=o.optString("selectedYear");c.selectedTerm=o.optString("selectedTerm");
        JSONArray ts=o.getJSONArray("terms");for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);c.terms.add(new Term(t.getString("year"),t.getString("name"),t.getString("start"),t.getString("end"),t.getString("teachingEnd")));}
        c.starts.clear();c.ends.clear();for(int i=0;i<o.getJSONArray("starts").length();i++)c.starts.add(o.getJSONArray("starts").getString(i));for(int i=0;i<o.getJSONArray("ends").length();i++)c.ends.add(o.getJSONArray("ends").getString(i));
        JSONArray cs=o.getJSONArray("closed");for(int i=0;i<cs.length();i++)c.closed.add(LocalDate.parse(cs.getString(i)));JSONObject ms=o.getJSONObject("makeup");for(Iterator<String>it=ms.keys();it.hasNext();){String k=it.next();c.makeup.put(LocalDate.parse(k),LocalDate.parse(ms.getString(k)));}c.validate();return c;
    }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("校历JSON或日期格式无效");}}
    private void term(String n,String s,String e,String stop){terms.add(new Term("2026-2027",n,s,e,stop));}
    private void close(String a,String b){for(LocalDate d=LocalDate.parse(a);!d.isAfter(LocalDate.parse(b));d=d.plusDays(1))closed.add(d);}
    public static ZjuCalendarConfig ordinary(){
        ZjuCalendarConfig c=new ZjuCalendarConfig();c.term("秋","2026-09-14","2026-11-08","2026-11-08");c.term("冬","2026-11-09","2027-02-21","2027-01-05");c.term("春","2027-02-22","2027-04-18","2027-04-18");c.term("夏","2027-04-19","2027-06-26","2027-06-14");
        c.close("2026-09-25","2026-09-27");c.close("2026-10-01","2026-10-07");c.close("2026-10-24","2026-10-25");c.close("2027-01-01","2027-01-01");c.close("2027-03-27","2027-03-28");c.close("2027-04-05","2027-04-05");c.close("2027-04-17","2027-04-18");c.close("2027-04-24","2027-04-25");c.close("2027-05-01","2027-05-02");c.close("2027-06-09","2027-06-09");
        c.makeup.put(LocalDate.parse("2026-09-20"),LocalDate.parse("2026-10-06"));c.makeup.put(LocalDate.parse("2026-10-10"),LocalDate.parse("2026-10-07"));c.makeup.put(LocalDate.parse("2026-10-17"),LocalDate.parse("2026-10-02"));c.makeup.put(LocalDate.parse("2027-01-04"),LocalDate.parse("2026-12-31"));c.validate();return c;
    }
    /** Haining publishes a continuous autumn/winter semester, with its own makeup. */
    public static ZjuCalendarConfig haining(){ZjuCalendarConfig c=new ZjuCalendarConfig();c.campus="haining";c.term("秋冬","2026-09-14","2027-01-15","2026-12-25");c.close("2026-09-25","2026-09-27");c.close("2026-10-01","2026-10-07");c.close("2026-10-24","2026-10-25");c.makeup.put(LocalDate.parse("2026-09-20"),LocalDate.parse("2026-09-25"));c.validate();return c;}
}
