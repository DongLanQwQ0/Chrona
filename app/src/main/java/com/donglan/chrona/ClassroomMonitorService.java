package com.donglan.chrona;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;

/** User-visible active observation. Waiting for future lessons never holds a foreground service. */
public final class ClassroomMonitorService extends Service {
    private static final int NOTICE=2045;
    private static final String CHANNEL="chrona_classroom_monitor";
    private final Handler main=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private ClassroomLiveTranscripts live;
    private boolean stopped;
    private final Runnable tick=()->{
        if(stopped)return;
        if(live!=null)live.refresh();
        if(!ClassroomMonitor.run(this,active->{
            if(stopped)return;
            ClassroomMonitor.schedule(this);
            if(live!=null)live.refresh();
            if(!active){stopSelf();return;}
            if(wake!=null&&!wake.isHeld())wake.acquire(6*60*60_000L);
            getSystemService(NotificationManager.class).notify(NOTICE,notice());
            main.postDelayed(this.tick,ClassroomMonitorStore.POLL_MILLIS);
        }))main.postDelayed(this.tick,ClassroomMonitorStore.POLL_MILLIS);
    };
    private Notification notice(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"课堂监视",NotificationManager.IMPORTANCE_LOW));
        String text="正在获取课堂资料";
        try{String value=new ClassroomMonitorStore(this,new ZjuSettingsStore(this).account()).read().optString("message");if(!value.isEmpty())text=value;}catch(Exception ignored){}
        PendingIntent open=PendingIntent.getActivity(this,NOTICE,new Intent(this,ClassroomSummaryActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getBroadcast(this,NOTICE,new Intent(this,ClassroomMonitorReceiver.class).setAction(ClassroomMonitorReceiver.STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_chrona_foreground)
                .setContentTitle("课堂监视中").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open).addAction(new Notification.Action.Builder(null,"停止监视",stop).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        try {
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notice(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTICE,notice());
        }catch(RuntimeException denied){ClassroomMonitor.schedule(this);stopSelf();return START_NOT_STICKY;}
        if(wake==null){wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Chrona:ClassroomMonitor");wake.setReferenceCounted(false);}
        if(live==null)live=new ClassroomLiveTranscripts(this);
        // Network and decoding must finish even if the screen turns off during this first pass.
        if(!wake.isHeld())wake.acquire(6*60*60_000L);
        main.removeCallbacks(tick);main.post(tick);return START_STICKY;
    }
    @Override public void onTimeout(int startId,int type){ClassroomMonitor.cancel();ClassroomMonitor.schedule(this);stopSelf();}
    @Override public void onDestroy(){stopped=true;main.removeCallbacks(tick);if(live!=null)live.close();ClassroomMonitor.cancel();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
