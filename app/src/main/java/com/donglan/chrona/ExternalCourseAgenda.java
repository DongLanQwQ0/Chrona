package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared read-only course feed; negative IDs cannot collide with CalendarProvider IDs. */
public final class ExternalCourseAgenda {
    public static boolean isExternal(long id){return id<0;}
    private static long id(String key){return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits()|Long.MIN_VALUE;}
    static String revision(Context context){ZjuSettingsStore settings=new ZjuSettingsStore(context);return ScheduleSources.revision(context)+":"+CourseAgenda.revision()+":"+ZjuTimetableStore.revision()+":"+settings.account()+":"+settings.enabled()+":"+settings.timetable();}
    static List<CalendarOccurrence> merge(Context context,List<CalendarOccurrence> system,long begin,long end){
        List<CalendarOccurrence> courses=new ArrayList<>();
        if(ScheduleSources.importedEnabled(context))try{
            for(CourseAgenda.Item c:CourseAgenda.load(context,begin,end,ZoneId.systemDefault())){
                long start=c.allDay?utcDate(c.start):c.start,endAt=c.allDay?utcDate(c.end):c.end;
                courses.add(new CalendarOccurrence(id("imported:"+c.termId+":"+c.key),start,endAt,c.title,c.allDay,c.location,"导入课表"));
            }
        }catch(Exception e){throw new IllegalStateException("无法读取导入课表",e);}
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        if(settings.timetable()&&ScheduleSources.zjuEnabled(context))try{
            for(ZjuSnapshot.Lesson c:new ZjuTimetableStore(context).load(settings.account()))if(c.start<end&&c.end>begin)
                courses.add(new CalendarOccurrence(id("zju:"+settings.account()+":"+c.key),c.start,c.end,c.title,false,c.location,"折姜星辰"));
        }catch(Exception e){throw new IllegalStateException("无法读取浙大课表",e);}
        List<CalendarOccurrence> all=new ArrayList<>(system);all.addAll(courses);
        String primary=ScheduleSources.primary(context);
        if(!"all".equals(primary)){
            // The explicit primary-source choice authorizes exact title/time matches;
            // unrelated events and merely similar titles remain visible.
            Map<String,CalendarOccurrence> preferred=new LinkedHashMap<>();
            for(CalendarOccurrence c:all)if(preferred(c,primary))preferred.put(fingerprint(c),c);
            all.removeIf(c->!preferred(c,primary)&&preferred.containsKey(fingerprint(c))
                    &&(isExternal(c.eventId)||isExternal(preferred.get(fingerprint(c)).eventId)));
        }
        all.sort(Comparator.comparingLong((CalendarOccurrence c)->c.startMillis).thenComparingLong(c->c.eventId));
        return all;
    }
    private static boolean preferred(CalendarOccurrence c,String key){return "imported".equals(key)&&"导入课表".equals(c.calendarName)
            ||"zju".equals(key)&&"折姜星辰".equals(c.calendarName)||key.equals("system:"+c.calendarId);}
    private static String fingerprint(CalendarOccurrence c){return c.title.trim().replaceAll("\\s+","")+"/"+c.startMillis+"/"+c.endMillis+"/"+c.allDay;}
    private static long utcDate(long local){return java.time.Instant.ofEpochMilli(local).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}
    public static Intent details(Context context,long id,long start,long end){return new Intent(context,ExternalCourseDetailActivity.class)
            .putExtra("event_id",id).putExtra("begin",start).putExtra("end",end);}
    private ExternalCourseAgenda(){}
}
