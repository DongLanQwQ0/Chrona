package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
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
        if ("backup".equals(args.getString("scope"))) {
            File archive = new File(context.getExternalFilesDir(null), "release-upgrade-" + installed + ".zip");
            ChronaDataBackup.write(context, android.net.Uri.fromFile(archive), false);
            if (!archive.isFile() || archive.length() == 0) throw new AssertionError("Upgrade backup missing");
            return "PASS: full local backup without API key; installed=" + installed + "; bytes=" + archive.length();
        }
        boolean newer=Boolean.parseBoolean(args.getString("newer","true"));
        // A browser handoff may have left an external activity above an old update task.
        // Require a fresh test-owned screen so startActivitySync observes a new Activity.
        Activity activity=test.startActivitySync(new Intent(context,UpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
            // Keep this metadata probe in the app. Browser handoff is accepted separately
            // against the actual external activity, not an IntentFilter-only monitor.
            return "PASS: installed="+installed+", latest="+args.getString("latest")+", newer="+newer+"; real UpdateActivity, public GitHub API, "+(newer?"download action present":"up-to-date state");
        } finally {test.runOnMainSync(activity::finish);}
    }
    private static TextView find(View view,String value){if(view instanceof TextView text&&value.contentEquals(text.getText()))return text;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static String labels(View view){StringBuilder result=new StringBuilder();if(view instanceof TextView text)result.append(text.getText()).append('\n');if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.append(labels(group.getChildAt(i)));return result.toString();}
}
