package com.donglan.chrona;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import com.donglan.chrona.calendar.CalendarOccurrence;
import java.time.*;
import java.util.*;

/** Local, read-only evidence over the previous two and next six weeks, including disabled sources. */
final class CourseSourceProbe {
    static final int LIMIT=2400;
    static final class Result {
        final List<CalendarOccurrence> rows=new ArrayList<>();
        boolean limited;
    }
    static Result load(Context context) throws Exception {
        Result result=new Result();ZoneId zone=ZoneId.systemDefault();
        long begin=LocalDate.now(zone).minusWeeks(2).atStartOfDay(zone).toInstant().toEpochMilli();
        long end=LocalDate.now(zone).plusWeeks(6).atStartOfDay(zone).toInstant().toEpochMilli();
        // Direct provider enumeration deliberately bypasses CalendarStore's enabled-source filter.
        if(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED){
            // Account columns belong to Calendars, not to the Instances query contract.
            Set<Long> ownedCalendars=new HashSet<>();
            try(Cursor calendars=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID},
                    CalendarContract.Calendars.ACCOUNT_NAME+" = ? AND "+CalendarContract.Calendars.ACCOUNT_TYPE+" = ? AND "+CalendarContract.Calendars.NAME+" = ?",
                    new String[]{context.getPackageName()+".calendar",CalendarContract.ACCOUNT_TYPE_LOCAL,
                        com.donglan.chrona.calendar.CalendarStore.CALENDAR_NAME},null)){
                if(calendars==null)throw new IllegalStateException("无法读取系统日历来源");
                while(calendars.moveToNext())ownedCalendars.add(calendars.getLong(0));
            }
            android.net.Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri,begin);ContentUris.appendId(uri,end);
            String[] projection={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,CalendarContract.Instances.TITLE,CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ID};
            try(Cursor cursor=context.getContentResolver().query(uri.build(),projection,null,null,CalendarContract.Instances.BEGIN+" ASC")){
                if(cursor==null)throw new IllegalStateException("无法读取系统日历实例");
                while(cursor.moveToNext()){
                    if(ownedCalendars.contains(cursor.getLong(7)))continue;
                    if(result.rows.size()>=LIMIT){result.limited=true;break;}
                    result.rows.add(new CalendarOccurrence(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),cursor.getString(3),
                        cursor.getInt(4)!=0,cursor.getString(5),cursor.getString(6),null,cursor.getLong(7),""));
                }
            }
        }
        int sourceCount=0;
        for(CourseAgenda.Item c:CourseAgenda.load(context,begin,end,zone)){
            if(sourceCount++>=LIMIT){result.limited=true;break;}
            result.rows.add(new CalendarOccurrence(-1,c.start,c.end,c.title,c.allDay,c.location,"导入课表",null,CourseSourceMatching.IMPORTED,""));
        }
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        sourceCount=0;
        if(settings.enabled())for(com.donglan.chrona.zju.ZjuSnapshot.Lesson c:new ZjuTimetableStore(context).load(settings.account())){
            if(c.start>=end||c.end<=begin)continue;
            if(sourceCount++>=LIMIT){result.limited=true;break;}
            result.rows.add(new CalendarOccurrence(-2,c.start,c.end,c.title,false,c.location,"折姜星辰",null,CourseSourceMatching.ZJU,""));
        }
        return result;
    }
    /** Weekly structure is evidence for a course group, never permission to disable a calendar. */
    static Map<String,Integer> weeklyGroups(List<CalendarOccurrence> rows){
        Map<String,Set<LocalDate>> dates=new HashMap<>();Map<String,String> sources=new HashMap<>();
        ZoneId zone=ZoneId.systemDefault();
        for(CalendarOccurrence c:rows){
            if(c.allDay)continue;ZonedDateTime start=Instant.ofEpochMilli(c.startMillis).atZone(zone);
            String key=CourseSourceMatching.identity(c)+"/"+start.getDayOfWeek()+"/"+start.toLocalTime()+"/"+(c.endMillis-c.startMillis);
            dates.computeIfAbsent(key,k->new HashSet<>()).add(start.toLocalDate());sources.put(key,CourseSourceMatching.source(c));
        }
        Map<String,Set<String>> groups=new HashMap<>();
        for(Map.Entry<String,Set<LocalDate>> entry:dates.entrySet())if(entry.getValue().size()>=2){
            groups.computeIfAbsent(sources.get(entry.getKey()),k->new HashSet<>()).add(entry.getKey().split("/")[0]);
        }
        Map<String,Integer> result=new HashMap<>();for(Map.Entry<String,Set<String>> e:groups.entrySet())result.put(e.getKey(),e.getValue().size());return result;
    }
    private CourseSourceProbe(){}
}
