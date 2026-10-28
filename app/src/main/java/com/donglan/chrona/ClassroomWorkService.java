package com.donglan.chrona;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import com.donglan.chrona.classroom.ClassroomLesson;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/** Main-thread registry. Work starts only after foreground promotion; never redelivers AI calls. */
public final class ClassroomWorkService extends Service {
    private static final int NOTIFICATION_ID = 2043;
    private static final java.util.Map<ClassroomLessonActivity.Session, Runnable> jobs = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
    private static volatile ClassroomWorkService instance;
    private static int pendingStarts;
    private static final LinkedHashMap<Object, BatchHold> batches = new LinkedHashMap<>();
    private static final class BatchHold {
        Runnable ready; final Runnable lost;
        BatchHold(Runnable ready,Runnable lost){this.ready=ready;this.lost=lost;}
    }
    private final java.util.Set<ClassroomLessonActivity.Session> owned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean foregroundReady;

    /** Only an already-promoted manual/batch owner may replace a lesson's running notice. */
    static boolean updateProgress(ClassroomNotifications source) {
        ClassroomWorkService service = instance;
        if (service == null || !service.foregroundReady) return false;
        for (ClassroomLessonActivity.Session session : sessions()) {
            if (!session.monitorOwned && session.notifications == source && service.owned.contains(session)) {
                service.main.post(() -> {
                    if (instance == service && service.foregroundReady && service.owned.contains(session)
                            && jobs.containsKey(session)) service.refreshOrStop();
                });
                return true;
            }
        }
        return false;
    }

    private void own(ClassroomLessonActivity.Session session) {
        owned.add(session);
        // Session emits its first progress before submission. Remove that former tag now.
        if (session.notifications != null) session.notifications.repost();
    }

    static ClassroomLessonActivity.Session find(String account, ClassroomLesson lesson) {
        for (ClassroomLessonActivity.Session session : sessions())
            if (session.account.equals(account) && session.selected.courseId.equals(lesson.courseId)
                    && session.selected.id.equals(lesson.id)) return session;
        return null;
    }
    private static ArrayList<ClassroomLessonActivity.Session> sessions(){synchronized(jobs){return new ArrayList<>(jobs.keySet());}}

    static void submit(ClassroomLessonActivity.Session session, Runnable work) {
        if(find(session.account,session.selected)!=null){session.startFailed();return;}
        jobs.put(session, work);
        pendingStarts++;
        try { session.app.startForegroundService(new Intent(session.app, ClassroomWorkService.class)); }
        catch (RuntimeException unavailable) {
            pendingStarts--;
            jobs.remove(session);
            session.startFailed();
        }
    }

    /** A sequential batch keeps one foreground owner alive between its lesson Sessions. */
    static void holdBatch(Object owner,android.content.Context app,Runnable ready,Runnable lost){
        if(batches.containsKey(owner))throw new IllegalStateException("批量任务已在运行");
        batches.put(owner,new BatchHold(ready,lost));pendingStarts++;
        try{app.startForegroundService(new Intent(app,ClassroomWorkService.class));}
        catch(RuntimeException unavailable){pendingStarts--;BatchHold hold=batches.remove(owner);if(hold!=null)hold.lost.run();}
    }
    static void releaseBatch(Object owner){batches.remove(owner);if(instance!=null)instance.refreshOrStop();}
    static void submitBatch(Object owner,ClassroomLessonActivity.Session session,Runnable work){
        if(instance==null||!batches.containsKey(owner)||find(session.account,session.selected)!=null){session.startFailed();return;}
        jobs.put(session,null);instance.own(session);work.run();
    }
    private static void loseBatches(){
        ArrayList<BatchHold> lost=new ArrayList<>(batches.values());batches.clear();
        for(BatchHold hold:lost)hold.lost.run();
    }

    /** The monitor already owns a visible dataSync FGS; do not start another service. */
    static void submitMonitor(ClassroomLessonActivity.Session session,Runnable work){
        if(!ClassroomAutoSummary.hasForegroundOwner()){session.startFailed();return;}
        if(find(session.account,session.selected)!=null){session.startFailed();return;}
        jobs.put(session,null);work.run();
    }
    static void cancelMonitored(){
        for(ClassroomLessonActivity.Session session:sessions())if(session.monitorOwned)session.cancel();
    }
    static void reconcileMonitored(android.content.Context app){
        for(ClassroomLessonActivity.Session session:sessions())if(session.monitorOwned){
            try{if(!session.allowed()||ClassroomMonitorStore.ruleFor(new ClassroomMonitorStore(app,session.account).read(),session.selected)==null)session.cancel();}
            catch(Exception failure){session.cancel();}
        }
    }

    static void finished(ClassroomLessonActivity.Session session) {
        jobs.remove(session);
        if (instance != null) { instance.owned.remove(session); instance.refreshOrStop(); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        if (pendingStarts > 0) pendingStarts--;
        try {
            // Required even when notifications are denied, or a queued task was cancelled.
            android.app.Notification notice = ClassroomNotifications.foreground(this, activeSessions());
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION_ID, notice);
            foregroundReady = true;
        } catch (RuntimeException unavailable) {
            foregroundReady = false;
            if (instance == this) instance = null;
            loseBatches();
            for (ClassroomLessonActivity.Session session : sessions()) {
                if (session.monitorOwned) continue;
                if (jobs.get(session) != null) { jobs.remove(session); session.startFailed(); }
                else session.cancel();
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        for (ClassroomLessonActivity.Session session : sessions()) {
            Runnable work = jobs.get(session);
            if (work != null) { jobs.put(session, null); own(session); work.run(); }
        }
        for(BatchHold hold:new ArrayList<>(batches.values()))if(hold.ready!=null){Runnable ready=hold.ready;hold.ready=null;ready.run();}
        refreshOrStop();
        return START_NOT_STICKY;
    }

    private void refreshOrStop() {
        boolean pendingWork = false;
        for (ClassroomLessonActivity.Session session : sessions()) if (!session.monitorOwned) { pendingWork = true; break; }
        if (!pendingWork && batches.isEmpty() && pendingStarts == 0) {
            foregroundReady = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            if (instance == this) instance = null;
            stopSelf();
        } else {
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                try { manager.notify(NOTIFICATION_ID, ClassroomNotifications.foreground(this, activeSessions())); }
                catch (RuntimeException unavailable) { /* Foreground state remains active without drawer permission. */ }
            }
        }
    }

    private ArrayList<ClassroomLessonActivity.Session> activeSessions() {
        ArrayList<ClassroomLessonActivity.Session> active = new ArrayList<>();
        for (ClassroomLessonActivity.Session session : sessions())
            if (owned.contains(session) && !session.monitorOwned && session.allowed()) active.add(session);
        return active;
    }

    @Override public void onTimeout(int startId, int fgsType) {
        foregroundReady = false;
        if (instance == this) instance = null;
        loseBatches();
        for (ClassroomLessonActivity.Session session : new ArrayList<>(owned)) session.cancel();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        foregroundReady = false;
        main.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        loseBatches();
        for (ClassroomLessonActivity.Session session : new ArrayList<>(owned)) session.cancel();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
