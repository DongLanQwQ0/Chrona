package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.donglan.chrona.classroom.*;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

/** Service-owned continuous sockets. Polling PPT/materials never closes or recreates these sockets. */
final class ClassroomLiveTranscripts {
    // TLS pool eviction may write close_notify. Service destruction must never perform it on main.
    private static final java.util.concurrent.ExecutorService cleanup=Executors.newSingleThreadExecutor();
    private static void release(ClassroomClient client){if(client!=null)cleanup.execute(client::close);}
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService connector=Executors.newSingleThreadExecutor();
    private final Map<String,Slot> slots=new LinkedHashMap<>();
    private volatile boolean closed;
    private final Runnable verify=new Runnable(){public void run(){if(!closed){refresh();main.postDelayed(this,2000);}}};
    ClassroomLiveTranscripts(Context context){app=context.getApplicationContext();main.post(verify);}
    void refresh(){
        if(closed)return;
        try {
            String account=new ZjuSettingsStore(app).account();
            Map<String,ClassroomLesson> wanted=new LinkedHashMap<>();
            if(ClassroomMonitor.allowed(app,account))for(ClassroomLesson lesson:ClassroomMonitorStore.pending(new ClassroomMonitorStore(app,account).read()))
                if(lesson.startsAt<=System.currentTimeMillis()){
                    // PPT/replay state may lag behind the live service. It cannot authorize or block a socket.
                    wanted.put(account+":"+ClassroomMonitorStore.key(lesson),lesson);
                }
            for(String key:new ArrayList<>(slots.keySet()))if(!wanted.containsKey(key)){slots.remove(key).close();}
            for(var entry:wanted.entrySet()) {
                Slot old=slots.get(entry.getKey());
                int failures=0;
                if(old!=null&&old.failedAt>0&&System.currentTimeMillis()-old.failedAt>=old.retryDelay){failures=old.failures;old.close();slots.remove(entry.getKey());old=null;}
                if(old==null){Slot slot=new Slot(account,entry.getValue());slot.failures=failures;slots.put(entry.getKey(),slot);connector.execute(slot::connect);}
                else old.verifyCurrent();
            }
        }catch(Exception failure){android.util.Log.w("ChronaClassroomMonitor","live refresh: "+failure.getClass().getSimpleName());}
    }
    void close(){closed=true;main.removeCallbacks(verify);for(Slot slot:slots.values())slot.close();slots.clear();connector.shutdown();}
    static JSONObject read(File dir)throws Exception {
        JSONObject state=ClassroomStore.read(new File(dir,"live-transcript.json"));if(state==null)state=new JSONObject().put("segments",new JSONArray());
        JSONObject current=ClassroomStore.read(new File(dir,"live-current.json"));
        if(current!=null&&current.optInt("base_count",-1)==state.getJSONArray("segments").length()&&current.optJSONObject("current")!=null)state.put("current",current.getJSONObject("current"));
        return state;
    }
    private final class Slot extends WebSocketListener {
        final String account;final ClassroomLesson lesson;
        volatile boolean stopped;volatile long failedAt;
        int failures;long retryDelay=5_000;
        volatile ClassroomClient client;volatile WebSocket socket;
        File dir;JSONObject state;long savedAt;
        volatile long verifiedAt;volatile boolean checking;
        private final Runnable heartbeat=new Runnable(){public void run(){synchronized(Slot.this){if(!stopped&&failedAt==0&&socket!=null){socket.send("pi");main.postDelayed(this,30_000);}}}};
        Slot(String account,ClassroomLesson lesson){this.account=account;this.lesson=lesson;}
        void connect(){
            try {
                if(stopped||!ClassroomMonitor.allowed(app,account))return;
                dir=new ClassroomStore(app,account).localLesson(lesson);state=read(dir);status("连接中");
                String[] pair=new ZjuSettingsStore(app).credentials();ClassroomClient owner;try{owner=ClassroomMonitor.clients.apply(pair[0],pair[1]);}finally{Arrays.fill(pair,"");}
                synchronized(this){if(stopped){release(owner);return;}client=owner;}
                WebSocket connected=owner.liveTranscript(lesson,this);
                synchronized(this){
                    if(stopped||failedAt>0){if(connected!=null)connected.cancel();release(owner);return;}
                    socket=connected;verifiedAt=System.currentTimeMillis();
                    if(connected==null){failedAt=System.currentTimeMillis();status("等待实时字幕");client=null;release(owner);}
                }
            }catch(Exception failure){fail();}
        }
        void verifyCurrent(){
            if(stopped||failedAt>0||checking||socket==null||System.currentTimeMillis()-verifiedAt<30_000)return;
            checking=true;connector.execute(()->{try{ClassroomClient current=client;if(stopped||current==null)return;
                if(!current.liveNow(lesson)){fail();return;}verifiedAt=System.currentTimeMillis();
            }catch(Exception failure){fail();}finally{checking=false;}});
        }
        @Override public synchronized void onOpen(WebSocket ws,Response response){
            if(stopped||failedAt>0){ws.cancel();return;}socket=ws;ws.send("in{\"version\":\"1.9.1\"}");
        }
        @Override public synchronized void onMessage(WebSocket ws,String frame){
            if(failedAt>0)return;
            if(stopped||!ClassroomMonitor.allowed(app,account)){close();return;}
            try {
                if(frame.startsWith("pi")){ws.send("po");status("实时连接");return;}
                if(frame.startsWith("po")){status("实时连接");return;}
                if(frame.startsWith("in")){failures=0;status("实时连接");main.removeCallbacks(heartbeat);main.postDelayed(heartbeat,30_000);return;}
                JSONObject payload=ClassroomTranscript.payload(frame);if(payload==null)return;
                synchronized(this){
                    int previousCount=state.getJSONArray("segments").length();
                    state=ClassroomTranscript.append(state,payload,lesson.startsAt,System.currentTimeMillis());
                    long now=System.currentTimeMillis();boolean complete="1".equals(payload.optString("end_time"))||state.getJSONArray("segments").length()!=previousCount;
                    if(complete){ClassroomStore.write(new File(dir,"live-transcript.json"),state);savedAt=0;}
                    if(complete||now-savedAt>=250){
                        ClassroomStore.write(new File(dir,"live-current.json"),new JSONObject().put("base_count",state.getJSONArray("segments").length()).put("current",state.opt("current")));
                        savedAt=now;
                    }
                }
            }catch(Exception failure){fail();}
        }
        private synchronized void status(String value){
            try{if(dir!=null)ClassroomStore.write(new File(dir,"live-status.json"),new JSONObject().put("state",value).put("at",System.currentTimeMillis()));}catch(Exception ignored){}
        }
        private synchronized void fail(){
            if(stopped||failedAt>0)return;failedAt=System.currentTimeMillis();retryDelay=Math.min(60_000,5_000L<<Math.min(failures++,4));main.removeCallbacks(heartbeat);status("连接中断，正在重连");
            ClassroomClient current=client;if(current!=null)current.close();client=null;socket=null;
            // A live connection recovers independently of the slower PPT polling cadence.
            main.postDelayed(()->{if(!closed)refresh();},retryDelay);
        }
        @Override public void onFailure(WebSocket ws,Throwable failure,Response response){fail();}
        @Override public void onClosed(WebSocket ws,int code,String reason){fail();}
        synchronized void close(){
            if(stopped)return;stopped=true;main.removeCallbacks(heartbeat);
            try{if(state!=null&&dir!=null)ClassroomStore.write(new File(dir,"live-current.json"),new JSONObject().put("base_count",state.getJSONArray("segments").length()).put("current",state.opt("current")));}catch(Exception ignored){}
            WebSocket ws=socket;if(ws!=null)ws.close(1000,"monitor stopped");ClassroomClient current=client;client=null;socket=null;release(current);status("连接已暂停");
        }
    }
}
