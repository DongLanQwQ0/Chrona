package com.donglan.chrona;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/** LAN source settings expose only saved schedules, never school account credentials. */
final class LanSourcesApi {
    static JSONObject read(Context context,Map<String,String> query) throws Exception {
        synchronized(ScheduleSources.class) {
            List<ScheduleSourceRepository.Source> sources=ScheduleSourceRepository.list(context);
            JSONObject result=new JSONObject().put("revision",ScheduleSources.revision(context));
            String key=query.get("source");
            if(key==null) {
                JSONArray entries=new JSONArray();
                for(ScheduleSourceRepository.Source source:sources)entries.put(new JSONObject().put("id",source.key)
                        .put("name",source.name).put("count",source.count).put("enabled",source.enabled(context)));
                return result.put("primary",ScheduleSourceRepository.primary(context))
                        .put("systemReadable",ScheduleSourceRepository.systemReadable(context)).put("sources",entries);
            }
            ScheduleSourceRepository.Source source=requireSource(context,sources,key);
            int page=query.containsKey("page")?Integer.parseInt(query.get("page")):0;
            if(page<0||page>100000||query.containsKey("size")&&!"50".equals(query.get("size")))throw new IllegalArgumentException();
            int offset=page*ScheduleSourceRepository.PAGE_SIZE;
            List<ScheduleSourceRepository.Item> items;
            if(key.startsWith("system:"))items=ScheduleSourceRepository.system(context,key,0,1,true,Long.MIN_VALUE,-1,offset);
            else {
                List<ScheduleSourceRepository.Item> all=ScheduleSourceRepository.local(context,key,0,0,ZoneId.systemDefault(),true);
                items=all.subList(Math.min(offset,all.size()),Math.min(offset+ScheduleSourceRepository.PAGE_SIZE+1,all.size()));
            }
            JSONArray entries=new JSONArray();
            for(int i=0;i<Math.min(items.size(),ScheduleSourceRepository.PAGE_SIZE);i++) {
                ScheduleSourceRepository.Item item=items.get(i);
                entries.put(new JSONObject().put("title",item.title).put("start",webTime(item,item.start)).put("end",webTime(item,item.end))
                        .put("allDay",item.allDay).put("location",item.location).put("description",item.description));
            }
            return result.put("source",key).put("name",source.name).put("items",entries)
                    .put("hasMore",items.size()>ScheduleSourceRepository.PAGE_SIZE).put("page",page);
        }
    }
    /** Web all-day values carry calendar dates at UTC midnight, matching provider all-day events. */
    private static long webTime(ScheduleSourceRepository.Item item,long value) {
        return item.allDay&&!item.utcDate?Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli():value;
    }
    static JSONObject update(Context context,JSONObject body) throws Exception {
        synchronized(ScheduleSources.class) {
            boolean primary=body.has("primary");
            Set<String> fields=new HashSet<>(Arrays.asList("revision",primary?"primary":"source"));
            if(!primary)fields.add("enabled");
            Iterator<String> names=body.keys();while(names.hasNext())if(!fields.contains(names.next()))throw new IllegalArgumentException();
            Object revision=body.get("revision");
            if(!(revision instanceof Integer)&&!(revision instanceof Long))throw new IllegalArgumentException();
            if(((Number)revision).longValue()<0)throw new IllegalArgumentException();
            if(((Number)revision).longValue()!=ScheduleSources.revision(context))throw new ConcurrentModificationException();
            Object value=body.get(primary?"primary":"source");if(!(value instanceof String))throw new IllegalArgumentException();
            String key=(String)value;
            List<ScheduleSourceRepository.Source> sources=ScheduleSourceRepository.list(context);
            if(primary) {
                if(!"all".equals(key))requireSource(context,sources,key);
                ScheduleSources.setPrimary(context,key);
            } else {
                requireSource(context,sources,key);
                Object enabled=body.get("enabled");if(!(enabled instanceof Boolean))throw new IllegalArgumentException();
                if("imported".equals(key))ScheduleSources.setImportedEnabled(context,(Boolean)enabled);
                else if("zju".equals(key))ScheduleSources.setZjuEnabled(context,(Boolean)enabled);
                else ScheduleSources.setCalendarEnabled(context,Long.parseLong(key.substring(7)),(Boolean)enabled);
            }
            return read(context,Collections.emptyMap()).put("ok",true);
        }
    }
    private static ScheduleSourceRepository.Source requireSource(Context context,List<ScheduleSourceRepository.Source> sources,String key) {
        if(key.startsWith("system:")&&!ScheduleSourceRepository.systemReadable(context))throw new SecurityException();
        for(ScheduleSourceRepository.Source source:sources)if(source.key.equals(key))return source;
        throw new IllegalArgumentException("Source unavailable");
    }
    private LanSourcesApi() {}
}
