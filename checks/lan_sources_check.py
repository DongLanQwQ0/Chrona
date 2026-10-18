"""Run production LAN source validation/serialization with deterministic local adapters."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/lan-sources-check'
out.mkdir(parents=True, exist_ok=True)
files = {
'android/content/Context.java': 'package android.content; public class Context {}',
'com/donglan/chrona/ScheduleSources.java': '''package com.donglan.chrona;
import android.content.Context;
class ScheduleSources {
static long version;static String preference="all";static boolean enabled=true;
static long revision(Context c){return version;}static String primary(Context c){return preference;}
static void setPrimary(Context c,String s){preference=s;version++;}
static void setImportedEnabled(Context c,boolean b){enabled=b;version++;}
static void setZjuEnabled(Context c,boolean b){enabled=b;version++;}
static void setCalendarEnabled(Context c,long id,boolean b){enabled=b;version++;}
}''',
'com/donglan/chrona/ScheduleSourceRepository.java': '''package com.donglan.chrona;
import android.content.Context;import java.util.*;import java.time.ZoneId;
class ScheduleSourceRepository {
static final int PAGE_SIZE=50;static boolean readable=true,school=true,allDay=false;
static class Source {String key,name;int count;Source(String s){key=s;name=s;count=101;}boolean enabled(Context c){return ScheduleSources.enabled;}}
static class Item {String title="Course",location="Room",description="Notes";long start=123,end=456;boolean allDay=false,utcDate=false;}
static List<Source> list(Context c){List<Source> a=new ArrayList<>();a.add(new Source("imported"));if(school)a.add(new Source("zju"));if(readable)a.add(new Source("system:7"));return a;}
static String primary(Context c){return !school&&ScheduleSources.preference.equals("zju")?"all":ScheduleSources.preference;}
static boolean systemReadable(Context c){return readable;}
static List<Item> local(Context c,String s,long b,long e,ZoneId z,boolean a){List<Item> r=new ArrayList<>();for(int i=0;i<101;i++){Item item=new Item();if(allDay){item.allDay=true;item.start=java.time.Instant.parse("2026-10-17T16:00:00Z").toEpochMilli();item.end=item.start+86400000L;}r.add(item);}return r;}
static List<Item> system(Context c,String s,long b,long e,boolean a,long after,long id,int offset){List<Item> r=local(c,s,b,e,null,a);for(Item item:r){item.utcDate=true;if(allDay){item.start=java.time.Instant.parse("2026-10-18T00:00:00Z").toEpochMilli();item.end=item.start+86400000L;}}return r.subList(Math.min(offset,r.size()),Math.min(offset+51,r.size()));}
}''',
'com/donglan/chrona/LanSourcesCheck.java': '''package com.donglan.chrona;
import android.content.Context;import org.json.*;import java.util.*;
public class LanSourcesCheck {
static Context context=new Context();static int count;
static void check(boolean b){count++;if(!b)throw new AssertionError("check "+count);}
interface Action{void run()throws Exception;}
static void fails(Class<?> type,Action action)throws Exception{try{action.run();throw new AssertionError("not rejected");}catch(Exception e){check(type.isInstance(e));}}
public static void main(String[] args)throws Exception {
JSONObject list=LanSourcesApi.read(context,Map.of());check(list.getJSONArray("sources").length()==3);check(!list.toString().contains("account"));
JSONObject page=LanSourcesApi.read(context,Map.of("source","imported","page","0"));check(page.getJSONArray("items").length()==50&&page.getBoolean("hasMore"));
page=LanSourcesApi.read(context,Map.of("source","system:7","page","2"));check(page.getJSONArray("items").length()==1&&!page.getBoolean("hasMore"));
check(page.getJSONArray("items").getJSONObject(0).getString("description").equals("Notes"));
TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));ScheduleSourceRepository.allDay=true;
long day=java.time.Instant.parse("2026-10-18T00:00:00Z").toEpochMilli();
JSONObject importedDay=LanSourcesApi.read(context,Map.of("source","imported")).getJSONArray("items").getJSONObject(0);
check(importedDay.getLong("start")==day&&importedDay.getLong("end")==day+86400000L);
JSONObject systemDay=LanSourcesApi.read(context,Map.of("source","system:7")).getJSONArray("items").getJSONObject(0);
check(systemDay.getLong("start")==day&&systemDay.getLong("end")==day+86400000L);
ScheduleSourceRepository.allDay=false;
fails(IllegalArgumentException.class,()->LanSourcesApi.read(context,Map.of("source","imported","page","-1")));
fails(IllegalArgumentException.class,()->LanSourcesApi.read(context,Map.of("source","imported","size","51")));
fails(IllegalArgumentException.class,()->LanSourcesApi.read(context,Map.of("source","system:8")));
LanSourcesApi.update(context,new JSONObject().put("revision",0).put("source","imported").put("enabled",false));check(!ScheduleSources.enabled&&ScheduleSources.version==1);
fails(ConcurrentModificationException.class,()->LanSourcesApi.update(context,new JSONObject().put("revision",0).put("primary","all")));
fails(IllegalArgumentException.class,()->LanSourcesApi.update(context,new JSONObject().put("revision",1).put("source","imported").put("enabled","true")));
fails(IllegalArgumentException.class,()->LanSourcesApi.update(context,new JSONObject().put("revision",1).put("primary","all").put("source","imported")));
LanSourcesApi.update(context,new JSONObject().put("revision",1).put("primary","zju"));ScheduleSourceRepository.school=false;
check(LanSourcesApi.read(context,Map.of()).getString("primary").equals("all"));
fails(IllegalArgumentException.class,()->LanSourcesApi.read(context,Map.of("source","zju")));
fails(IllegalArgumentException.class,()->LanSourcesApi.update(context,new JSONObject().put("revision",2).put("primary","zju")));
ScheduleSourceRepository.readable=false;check(!LanSourcesApi.read(context,Map.of()).getBoolean("systemReadable"));
fails(SecurityException.class,()->LanSourcesApi.read(context,Map.of("source","system:7")));
fails(SecurityException.class,()->LanSourcesApi.update(context,new JSONObject().put("revision",2).put("source","system:7").put("enabled",true)));
System.out.println("LAN sources: "+count+" production API checks passed");
}}
'''
}
paths = []
for name, source in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source, encoding='utf-8')
    paths.append(path)
paths.append(root / 'app/src/main/java/com/donglan/chrona/LanSourcesApi.java')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
cp = str(root / 'build/ai-checks/json.jar')
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp, '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out)+os.pathsep+cp, 'com.donglan.chrona.LanSourcesCheck'], check=True)
