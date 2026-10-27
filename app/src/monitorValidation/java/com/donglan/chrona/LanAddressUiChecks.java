package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;

/** Real address text taps and system clipboard; an unstarted server opens no network socket. */
final class LanAddressUiChecks {
    private final Instrumentation test;private final Bundle args;private final Context app;
    private LanSettingsActivity screen;private LanAccessService fake;private ClipboardManager clipboard;private ClipData previousClip;
    private Object previousState;private int checks;
    private LanAddressUiChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;app=test.getTargetContext();}
    static int run(Instrumentation test,Bundle args)throws Exception{
        if(!"com.donglan.chrona.monitor.validation".equals(test.getTargetContext().getPackageName())||!Build.HARDWARE.contains("ranchu"))throw new SecurityException("Isolated emulator fixture required");
        if(LanAccessService.running()||LanAccessService.starting())throw new IllegalStateException("Existing LAN session must not be changed");
        LanAddressUiChecks c=new LanAddressUiChecks(test,args);
        SharedPreferences appearance=c.app.getSharedPreferences("appearance",0),school=c.app.getSharedPreferences("chrona_zju",0);
        boolean hadMode=appearance.contains("mode"),hadBackground=appearance.contains("background_enabled"),hadSchool=school.contains("enabled");
        String mode=appearance.getString("mode","system");boolean background=appearance.getBoolean("background_enabled",false),enabled=school.getBoolean("enabled",false);
        try{
            appearance.edit().putString("mode",args.getString("theme","light")).putBoolean("background_enabled",false).commit();school.edit().putBoolean("enabled",false).commit();
            c.execute();return c.checks;
        }finally{
            c.test.runOnMainSync(()->{if(c.clipboard!=null){if(c.previousClip!=null)c.clipboard.setPrimaryClip(c.previousClip);else c.clipboard.clearPrimaryClip();}if(c.screen!=null)c.screen.finish();});
            set(LanAccessService.class,null,"live",null);if(c.previousState!=null)set(LanAccessService.class,null,"state",c.previousState);
            SharedPreferences.Editor edit=appearance.edit();if(hadMode)edit.putString("mode",mode);else edit.remove("mode");if(hadBackground)edit.putBoolean("background_enabled",background);else edit.remove("background_enabled");edit.commit();
            SharedPreferences.Editor restore=school.edit();if(hadSchool)restore.putBoolean("enabled",enabled);else restore.remove("enabled");restore.commit();
        }
    }
    private void execute()throws Exception{
        previousState=get(LanAccessService.class,null,"state");
        screen=(LanSettingsActivity)test.startActivitySync(new Intent(app,LanSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();SystemClock.sleep(350);
        clipboard=screen.getSystemService(ClipboardManager.class);test.runOnMainSync(()->previousClip=clipboard.getPrimaryClip());
        TextView address=(TextView)get(LanSettingsActivity.class,screen,"address");
        check(address.getText().toString().equals("开启后显示访问地址")&&!address.isClickable(),"closed placeholder is not a copy action");
        test.runOnMainSync(()->{clipboard.setPrimaryClip(ClipData.newPlainText("fixture","unchanged"));fake=new LanAccessService();});
        set(LanAccessService.class,fake,"address","192.168.50.23");set(LanAccessService.class,fake,"server",new LanWebServer(app,"192.168.50.23",8765));set(LanAccessService.class,null,"live",fake);set(LanAccessService.class,null,"state","已开启");refresh();
        check(address.isClickable()&&address.isFocusable()&&address.getContentDescription().toString().contains("复制访问地址"),"running address is an accessible copy action");
        tap(address);check(value().equals("http://192.168.50.23:8765"),"actual text tap copies the full ordinary address");check(!value().contains("#")&&!value().contains("pair="),"copy excludes pairing secrets");
        String shown=address.getText().toString();refresh();check(address.getText().toString().equals(shown),"unchanged poll preserves displayed address");screenshot();
        set(LanAccessService.class,fake,"address","192.168.50.24");refresh();tap(address);check(value().equals("http://192.168.50.24:8765"),"next session address is copied instead of stale text");
        test.runOnMainSync(()->clipboard.setPrimaryClip(ClipData.newPlainText("fixture","keep-after-stop")));set(LanAccessService.class,fake,"server",null);
        tap(address);check(value().equals("keep-after-stop"),"stop racing a tap cannot copy a stale address");refresh();check(!address.isClickable()&&address.getText().toString().equals("开启后显示访问地址"),"stopped address returns to non-copy placeholder");
    }
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private void refresh()throws Exception{test.runOnMainSync(()->{try{Method m=LanSettingsActivity.class.getDeclaredMethod("refresh");m.setAccessible(true);m.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});test.waitForIdleSync();}
    private String value(){final String[] value={""};test.runOnMainSync(()->{ClipData data=clipboard.getPrimaryClip();if(data!=null&&data.getItemCount()>0)value[0]=data.getItemAt(0).coerceToText(screen).toString();});return value[0];}
    private void tap(View view){Rect bounds=new Rect();test.runOnMainSync(()->view.getGlobalVisibleRect(bounds));long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(now,now+(action==MotionEvent.ACTION_UP?80:0),action,bounds.centerX(),bounds.centerY(),0);try{test.sendPointerSync(event);}finally{event.recycle();}}test.waitForIdleSync();SystemClock.sleep(180);}
    private void screenshot()throws Exception{Bitmap raw=test.getUiAutomation().takeScreenshot();float ratio=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*ratio)),Math.max(1,Math.round(raw.getHeight()*ratio)),true);try(FileOutputStream out=new FileOutputStream(new File(app.getExternalFilesDir(null),"lan-address-"+args.getString("widthDp","400")+"-"+args.getString("theme","light")+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}}
    private static Object get(Class<?> type,Object target,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Class<?> type,Object target,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
