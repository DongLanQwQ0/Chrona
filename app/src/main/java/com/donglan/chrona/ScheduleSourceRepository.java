package com.donglan.chrona;

import android.Manifest;
import android.content.Context;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.time.ZoneId;
import java.util.*;

/** Local source data shared by native and LAN browsers. Inclusion switches never filter contents. */
final class ScheduleSourceRepository {
    static final int PAGE_SIZE = 50;
    static final class Source {
        final long id;
        final String key, name, account;
        int count;
        Source(long id, String name, String account, int count) {
            this.id=id; this.key="system:"+id;
            this.name=name==null||name.isBlank()?"未命名日历":name;
            this.account=account==null||account.isBlank()?"本地账户":account; this.count=count;
        }
        Source(String key, String name, int count) {
            this.id=0; this.key=key; this.name=name; this.account=""; this.count=count;
        }
        boolean enabled(Context context) {
            return id>0?ScheduleSources.allowsCalendar(context,id):"imported".equals(key)
                    ?ScheduleSources.importedEnabled(context):ScheduleSources.zjuEnabled(context);
        }
    }
    static final class Item {
        final String title, location, description, sourceName, key;
        final long start, end, providerId;
        final boolean allDay, utcDate, series;
        final CourseAgenda.Item imported;
        Item(String title,String location,String description,long start,long end,boolean allDay,
                boolean utcDate,long providerId,boolean series,String sourceName,String key,CourseAgenda.Item imported) {
            this.title=title==null||title.isBlank()?"未命名日程":title;
            this.location=location==null?"":location; this.description=description==null?"":description;
            this.start=start; this.end=end; this.allDay=allDay; this.utcDate=utcDate;
            this.providerId=providerId; this.series=series; this.sourceName=sourceName; this.key=key; this.imported=imported;
        }
    }
    static boolean systemReadable(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }
    static int importedCount(Context context) throws Exception { return CourseAgenda.all(context,ZoneId.systemDefault()).size(); }
    static int zjuCount(Context context) throws Exception {
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        return settings.enabled()?new ZjuTimetableStore(context).load(settings.account()).size():0;
    }
    static String primary(Context context) {
        String key=ScheduleSources.primary(context);
        return "zju".equals(key)&&!new ZjuSettingsStore(context).enabled()?"all":key;
    }
    static List<Source> list(Context context) throws Exception {
        List<Source> result=new ArrayList<>(); int count=importedCount(context);
        if(count!=0)result.add(new Source("imported","导入课表",count));
        count=zjuCount(context); if(count!=0)result.add(new Source("zju","浙大课表",count));
        if(systemReadable(context))result.addAll(calendars(context));
        return result;
    }
    static List<Source> calendars(Context context) {
        if(!systemReadable(context))throw new SecurityException("Calendar permission required");
        List<Source> result=new ArrayList<>();
        String[] projection={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.Calendars.NAME};
        try(Cursor cursor=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,projection,null,null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME+" ASC")) {
            if(cursor==null)throw new IllegalStateException("Calendar provider returned no cursor");
            while(cursor.moveToNext()) {
                String account=cursor.getString(2);
                if((context.getPackageName()+".calendar").equals(account)
                        &&CalendarContract.ACCOUNT_TYPE_LOCAL.equals(cursor.getString(3))
                        &&com.donglan.chrona.calendar.CalendarStore.CALENDAR_NAME.equals(cursor.getString(4)))continue;
                result.add(new Source(cursor.getLong(0),cursor.getString(1),account,-1));
            }
        }
        Map<Long,Integer> counts=new HashMap<>();
        try(Cursor cursor=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events.CALENDAR_ID},activeEvents(),null,null)) {
            if(cursor==null)throw new IllegalStateException("No event count cursor");
            while(cursor.moveToNext())counts.merge(cursor.getLong(0),1,Integer::sum);
        }
        for(Source source:result)source.count=counts.getOrDefault(source.id,0);
        result.removeIf(source->source.count==0);
        return result;
    }
    private static String activeEvents() {
        return CalendarContract.Events.DELETED+"=0 AND ("+CalendarContract.Events.STATUS+" IS NULL OR "
                +CalendarContract.Events.STATUS+"<>"+CalendarContract.Events.STATUS_CANCELED+")";
    }
    static List<Item> local(Context context,String source,long begin,long end,ZoneId zone,boolean all) throws Exception {
        List<Item> result=new ArrayList<>();
        if("imported".equals(source)) {
            for(CourseAgenda.Item item:all?CourseAgenda.all(context,zone):CourseAgenda.load(context,begin,end,zone))
                result.add(new Item(item.title,item.location,item.description,item.start,item.end,item.allDay,false,0,false,"",item.key,item));
        } else if("zju".equals(source)) {
            ZjuSettingsStore settings=new ZjuSettingsStore(context);
            if(!settings.enabled())throw new IllegalArgumentException("Source unavailable");
            for(ZjuSnapshot.Lesson item:new ZjuTimetableStore(context).load(settings.account())) {
                if(!all&&(item.start>=end||item.end<=begin))continue;
                result.add(new Item(item.title,item.location,item.notes,item.start,item.end,false,false,0,false,"",item.key,null));
            }
        } else throw new IllegalArgumentException("Unknown source");
        result.sort(Comparator.comparingLong(item->item.start)); return result;
    }
    /** All lists stored series once; week mode lists occurrences. Native uses keysets; LAN uses page offsets. */
    static List<Item> system(Context context,String source,long begin,long end,boolean all,long after,long idAfter,int offset) {
        if(!systemReadable(context))throw new SecurityException("Calendar permission required");
        long calendarId=Long.parseLong(source.substring(7));
        if(calendarId<=0||!source.equals("system:"+calendarId))throw new IllegalArgumentException("Invalid source");
        android.net.Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri,begin); ContentUris.appendId(uri,end);
        String startColumn=all?CalendarContract.Events.DTSTART:CalendarContract.Instances.BEGIN;
        String idColumn=all?CalendarContract.Events._ID:CalendarContract.Instances.EVENT_ID;
        String[] projection={idColumn,startColumn,all?CalendarContract.Events.DTEND:CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,CalendarContract.Events.RRULE,CalendarContract.Events.DESCRIPTION};
        String selection=CalendarContract.Instances.CALENDAR_ID+" = ? AND "+activeEvents();
        List<String> args=new ArrayList<>();args.add(Long.toString(calendarId));
        if(after!=Long.MIN_VALUE) {
            selection+=" AND ("+startColumn+">? OR ("+startColumn+"=? AND "+idColumn+">?))";
            args.add(Long.toString(after));args.add(Long.toString(after));args.add(Long.toString(idAfter));
        }
        List<Item> result=new ArrayList<>();
        try(Cursor cursor=context.getContentResolver().query(all?CalendarContract.Events.CONTENT_URI:uri.build(),projection,
                selection,args.toArray(new String[0]),startColumn+" ASC,"+idColumn+" ASC")) {
            if(cursor==null)throw new IllegalStateException("No calendar cursor");
            int skipped=0;
            while(cursor.moveToNext()) {
                if(skipped++<offset)continue;
                long start=cursor.getLong(1),finish=cursor.isNull(2)?start:cursor.getLong(2);
                String rule=cursor.getString(7);
                result.add(new Item(cursor.getString(3),cursor.getString(5),cursor.getString(8),start,finish,cursor.getInt(4)!=0,
                        true,cursor.getLong(0),all&&rule!=null&&!rule.isBlank(),cursor.getString(6),"",null));
                if(result.size()>PAGE_SIZE)break;
            }
        }
        return result;
    }
    private ScheduleSourceRepository() {}
}
