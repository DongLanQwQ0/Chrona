package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Read-only release-page acceptance against the public API; official package on emulator only. */
final class ReleaseDeviceCheck {
    static String run(Instrumentation test,Bundle args)throws Exception {
        var context=test.getTargetContext();
        if(!context.getPackageName().equals("com.donglan.chrona")
                || !(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish")))
            throw new IllegalStateException("Official package on emulator only");
        String expected=args.getString("installed");
        String installed=ReleaseUpdates.installedVersion(context);
        if(!installed.equals(expected))throw new AssertionError("Installed version "+installed+" != "+expected);
        boolean newer=Boolean.parseBoolean(args.getString("newer","true"));
        Activity activity=test.startActivitySync(new Intent(context,UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            long until=SystemClock.elapsedRealtime()+40000;boolean[] done={false};
            do {test.runOnMainSync(()->done[0]=find(activity.getWindow().getDecorView(),"重新检查")!=null);if(done[0])break;SystemClock.sleep(120);}while(SystemClock.elapsedRealtime()<until);
            if(!done[0])throw new AssertionError("Manual release check did not finish");
            test.runOnMainSync(()-> {
                View root=activity.getWindow().getDecorView();
                if(find(root,newer?"发现新版本":"已是最新版本")==null||find(root,"v"+args.getString("latest"))==null)
                    throw new AssertionError("Unexpected update result: "+labels(root));
                if(newer && !(find(root,"下载更新") instanceof Button))throw new AssertionError("APK download action missing");
            });
            Bitmap image=test.getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"release-check-"+installed+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            if(newer) {
                Instrumentation.ActivityMonitor monitor=test.addMonitor(new IntentFilter(Intent.ACTION_VIEW),new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
                try {test.runOnMainSync(()->find(activity.getWindow().getDecorView(),"下载更新").performClick());test.waitForIdleSync();if(monitor.getHits()!=1)throw new AssertionError("Download action did not delegate to system browser");}
                finally {test.removeMonitor(monitor);}
            }
            return "PASS: installed="+installed+", latest="+args.getString("latest")+", newer="+newer+"; real UpdateActivity, public GitHub API, "+(newer?"download ACTION_VIEW intercepted":"up-to-date state");
        } finally {test.runOnMainSync(activity::finish);}
    }
    private static TextView find(View view,String value){if(view instanceof TextView text&&value.contentEquals(text.getText()))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static String labels(View view){StringBuilder result=new StringBuilder();if(view instanceof TextView text)result.append(text.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.append(labels(group.getChildAt(i)));return result.toString();}
}
