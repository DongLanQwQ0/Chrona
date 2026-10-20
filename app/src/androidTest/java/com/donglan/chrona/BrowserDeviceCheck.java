package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import com.donglan.chrona.data.*;
import java.io.File;
import java.util.List;

/** Holds a real validation-app LAN session for an external browser, then removes its own fixture. */
final class BrowserDeviceCheck {
    static String run(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        FixtureIsolation.require(context);
        long task=0; Activity screen=null; LanWebServer server=null;
        File stop=new File(context.getFilesDir(),"browser-check-stop");
        if(stop.exists()&&!stop.delete())throw new IllegalStateException("Cannot reset browser check");
        try {
            try(android.os.ParcelFileDescriptor command=test.getUiAutomation().executeShellCommand(
                    "am start -W -n "+context.getPackageName()+"/com.donglan.chrona.DashboardActivity");
                    java.io.FileInputStream output=new java.io.FileInputStream(command.getFileDescriptor())) {
                while(output.read()!=-1) { }
            }
            try(TaskStore store=new TaskStore(context)) {
                task=store.insertTask("网页真机验收",null,"browser-device-check",System.currentTimeMillis());
                store.updateStatus(task,TaskRecord.READY,null);
                long first=java.time.LocalDate.now().plusDays(1).atTime(9,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                for(int index=0;index<15;index++)store.appendCandidate(new EventCandidate(0,task,"浏览器验收 "+(index+1),
                        first+index*3600000L,first+(index+1)*3600000L,java.time.ZoneId.systemDefault().getId(),"","",null,false,null,"event",false));
                store.appendCandidate(new EventCandidate(0,task,"每日重复验收",first,first+3600000L,
                        java.time.ZoneId.systemDefault().getId(),"","",null,false,null,"event",false)
                        .withRepeat(new RepeatRule("daily",1,List.of(),null,5)));
            }
            screen=test.startActivitySync(new Intent(context,LanSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            server=new LanWebServer(context,"127.0.0.1",18765);
            server.start(3000,false);
            LanWebServer session=server;
            Activity settings=screen;
            test.runOnMainSync(()->{
                android.widget.LinearLayout panel=SettingsPageLayout.content(settings);
                SettingsPageLayout.header(settings,panel,"浏览器真机验收");
                android.widget.TextView address=new android.widget.TextView(settings);
                address.setText("http://127.0.0.1:18765");address.setTextSize(20);panel.addView(address);
                android.widget.TextView pairing=new android.widget.TextView(settings);
                pairing.setText(session.security.pairing());pairing.setTextSize(32);panel.addView(pairing);
                SettingsPageLayout.show(settings,panel,false);
            });
            long deadline=SystemClock.uptimeMillis()+600000;
            while(!stop.exists()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(250);
            return "Real browser fixture closed";
        } finally {
            if(server!=null)server.closeAccess();
            if(screen!=null){Activity close=screen;test.runOnMainSync(close::finish);}
            if(task>0)try(TaskStore store=new TaskStore(context)){store.deleteTask(task);}
            if(stop.exists())stop.delete();
        }
    }
}
