package com.donglan.chrona;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ClassroomMonitorReceiver extends BroadcastReceiver {
    static final String STOP="com.donglan.chrona.CLASSROOM_MONITOR_STOP";
    @Override public void onReceive(Context context,Intent intent){
        if(STOP.equals(intent.getAction())){
            try{new ClassroomMonitorStore(context,new ZjuSettingsStore(context).account()).stopAll();}catch(Exception failure){android.util.Log.w("ChronaClassroomMonitor","Stop failed");return;}
            ClassroomMonitor.changed(context);
        }else if(ClassroomMonitor.ALARM.equals(intent.getAction()))ClassroomMonitor.promote(context);
        else ClassroomMonitor.schedule(context); // Boot/time changes restore scheduling, not a dataSync FGS.
    }
}
