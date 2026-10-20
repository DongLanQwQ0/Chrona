package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityNodeInfo;
import com.donglan.chrona.classroom.ClassroomLesson;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;

/** Emulator-only export checks. No target is selected and no file leaves the device. */
final class ExportShareDeviceCheck {
    private static int checks;
    private static final class Shares extends Instrumentation.ActivityMonitor {
        final LinkedBlockingQueue<Intent> intents=new LinkedBlockingQueue<>();
        @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
            if(!Intent.ACTION_CHOOSER.equals(intent.getAction()))return null;
            intents.add(new Intent(intent));return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
        }
        Intent await()throws Exception{Intent intent=intents.poll(30,TimeUnit.SECONDS);require(intent!=null,"export opens chooser after successful write");return intent;}
    }
    private static void isolated(Instrumentation test){
        if(!test.getTargetContext().getPackageName().equals("com.donglan.chrona.followup.validation")
                ||!android.os.Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator only");
    }
    static String classroom(Instrumentation test,Bundle args,ClassroomLesson lesson)throws Exception{
        isolated(test);Shares monitor=new Shares();test.addMonitor(monitor);
        Activity page=null;Uri target=null;
        try{
            page=test.startActivitySync(new Intent(test.getTargetContext(),ClassroomLessonActivity.class)
                    .putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity screen=page;Object session=field(page,"s");
            waitFor(test,()->Boolean.TRUE.equals(field(session,"localInitialized"))&&!Boolean.TRUE.equals(field(session,"busy")),"classroom loaded");
            target=create(test,"Chrona-share-check.zip","application/zip");Uri destination=target;
            Method result=ClassroomLessonActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);result.setAccessible(true);
            // Cancelling the save picker must neither write nor launch sharing.
            test.runOnMainSync(()->{try{set(session,"pendingExport",lesson);result.invoke(screen,2041,Activity.RESULT_CANCELED,null);}catch(Exception e){throw new IllegalStateException(e);}});
            require(monitor.intents.poll(350,TimeUnit.MILLISECONDS)==null,"cancelled save does not share");
            test.runOnMainSync(()->{try{result.invoke(screen,2041,Activity.RESULT_OK,new Intent().setData(destination)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception e){throw new IllegalStateException(e);}});
            Intent chooser=monitor.await();validate(chooser,"application/zip",destination);
            try(ZipInputStream zip=new ZipInputStream(test.getTargetContext().getContentResolver().openInputStream(destination))){
                java.util.Set<String> entries=new java.util.HashSet<>();java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){entries.add(entry.getName());zip.readAllBytes();}
                require(entries.contains("课堂总结.md")&&entries.contains("images/image-0.jpg")&&entries.contains("sources.md"),"shared ZIP is complete and readable");
            }
            require(field(session,"shareUri")==null,"share is consumed before opening chooser");
            test.removeMonitor(monitor);showAndDismiss(test,page,chooser,"zip",args);
            test.addMonitor(monitor);
            test.runOnMainSync(()->{try{set(session,"pendingExport",lesson);result.invoke(screen,2041,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://chrona.invalid-export/destination")));}catch(Exception e){throw new IllegalStateException(e);}});
            waitFor(test,()->!Boolean.TRUE.equals(field(session,"busy")),"failed write completes");
            require(monitor.intents.poll(350,TimeUnit.MILLISECONDS)==null&&field(session,"shareUri")==null,"failed export never shares a partial file");
            try(android.os.ParcelFileDescriptor file=test.getTargetContext().getContentResolver().openFileDescriptor(destination,"r")){require(file!=null&&file.getStatSize()>0,"cancelling share preserves saved ZIP");}
            return report("zip");
        }finally{test.removeMonitor(monitor);if(page!=null){Activity close=page;test.runOnMainSync(close::finish);}if(target!=null)test.getTargetContext().getContentResolver().delete(target,null,null);}
    }
    static String week(Instrumentation test,Bundle args)throws Exception{
        isolated(test);ThemeStore.setMode(test.getTargetContext(),args.getString("theme","light"));
        Shares monitor=new Shares();test.addMonitor(monitor);Activity page=null;Uri saved=null;
        java.util.concurrent.CountDownLatch release=null;
        boolean recreate=Boolean.parseBoolean(args.getString("recreate","false")),saf=Boolean.parseBoolean(args.getString("saf","false"));
        try{
            page=test.startActivitySync(new Intent(test.getTargetContext(),CalendarOverviewActivity.class).putExtra("open_week",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ScheduleWorkspaceActivity host=(ScheduleWorkspaceActivity)page;
            waitFor(test,()->host.currentPage() instanceof CalendarOverviewPage,"week page created");Object calendar=host.currentPage();
            waitFor(test,()->!Boolean.TRUE.equals(field(calendar,"loading"))&&!String.valueOf(field(calendar,"renderedKey")).isEmpty(),"calendar snapshot ready");
            if(recreate){
                Field executor=CalendarOverviewPage.class.getDeclaredField("EXPORTS");executor.setAccessible(true);
                java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1);release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.CountDownLatch gate=release;
                ((java.util.concurrent.ExecutorService)executor.get(null)).execute(()->{started.countDown();try{gate.await();}catch(InterruptedException error){Thread.currentThread().interrupt();}});
                require(started.await(10,TimeUnit.SECONDS),"export executor held for deterministic recreation");
            }
            if(saf){
                File source=new File(test.getTargetContext().getCacheDir(),"week-export-"+java.util.UUID.randomUUID()+".png");
                Bitmap bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                Object job=field(calendar,"weekExport");set(job,"imageName",source.getName());saved=create(test,"Chrona-SAF-share-check.png","image/png");Uri destination=saved;
                Method result=CalendarOverviewPage.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);result.setAccessible(true);
                test.runOnMainSync(()->{try{result.invoke(calendar,811,Activity.RESULT_OK,new Intent().setData(destination));}catch(Exception e){throw new IllegalStateException(e);}});
            }else{
                Method write=CalendarOverviewPage.class.getDeclaredMethod("writeWeekImage",boolean.class);write.setAccessible(true);
                test.runOnMainSync(()->{try{write.invoke(calendar,false);}catch(Exception e){throw new IllegalStateException(e);}});
            }
            Object exportJob=field(calendar,"weekExport");Object current=calendar;
            if(recreate){
                require(Boolean.TRUE.equals(field(exportJob,"busy")),"export is in progress before recreation");
                Instrumentation.ActivityMonitor recreated=test.addMonitor(CalendarOverviewActivity.class.getName(),null,false);
                Activity original=page;test.runOnMainSync(original::recreate);Activity replacement=test.waitForMonitorWithTimeout(recreated,15000);test.removeMonitor(recreated);
                require(replacement instanceof ScheduleWorkspaceActivity&&replacement!=original,"workspace recreated during export");page=replacement;
                ScheduleWorkspaceActivity next=(ScheduleWorkspaceActivity)replacement;waitFor(test,()->next.currentPage() instanceof CalendarOverviewPage,"replacement calendar");current=next.currentPage();
                require(field(current,"weekExport")==exportJob&&Boolean.TRUE.equals(field(exportJob,"busy")),"replacement owns retained in-progress export");
                release.countDown();
            }
            Intent chooser=monitor.await();Intent send=chooser.getParcelableExtra(Intent.EXTRA_INTENT);saved=send.getParcelableExtra(Intent.EXTRA_STREAM);validate(chooser,"image/png",saved);
            require(saved.toString().contains("/downloads/"),"PNG is saved through Downloads collection");
            try(android.database.Cursor row=test.getTargetContext().getContentResolver().query(saved,new String[]{MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.IS_PENDING},null,null,null)){
                require(row!=null&&row.moveToFirst()&&"Download/Chrona/".equals(row.getString(0))&&row.getInt(1)==0,"published PNG is in Download/Chrona, not Pictures");
            }
            try(java.io.InputStream input=test.getTargetContext().getContentResolver().openInputStream(saved)){
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeStream(input,null,bounds);require(bounds.outWidth>100&&bounds.outHeight>100,"shared PNG decodes after output stream closes");
            }
            String kind=(saf?"week-saf":"week")+(recreate?"-recreate":"");
            test.removeMonitor(monitor);showAndDismiss(test,page,chooser,kind,args);
            test.addMonitor(monitor);
            require(field(field(current,"weekExport"),"shareUri")==null&&monitor.intents.poll(350,TimeUnit.MILLISECONDS)==null,"returning from share does not reopen chooser");
            try(android.os.ParcelFileDescriptor file=test.getTargetContext().getContentResolver().openFileDescriptor(saved,"r")){require(file!=null&&file.getStatSize()>0,"cancelling share preserves Downloads image");}
            return report(kind);
        }finally{if(release!=null)release.countDown();test.removeMonitor(monitor);if(page!=null){Activity close=page;test.runOnMainSync(close::finish);}if(saved!=null)test.getTargetContext().getContentResolver().delete(saved,null,null);}
    }
    private static void validate(Intent chooser,String mime,Uri expected){
        require(Intent.ACTION_CHOOSER.equals(chooser.getAction()),"uses Android Sharesheet");Intent send=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        require(send!=null&&Intent.ACTION_SEND.equals(send.getAction())&&mime.equals(send.getType()),"share carries precise MIME type");
        require(expected!=null&&expected.equals(send.getParcelableExtra(Intent.EXTRA_STREAM))&&"content".equals(expected.getScheme()),"share contains saved content URI");
        require((send.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(send.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0,"receiver gets read-only access");
        require(send.getClipData()!=null&&expected.equals(send.getClipData().getItemAt(0).getUri()),"ClipData carries URI for permission propagation");
    }
    private static void showAndDismiss(Instrumentation test,Activity page,Intent chooser,String kind,Bundle args)throws Exception{
        test.runOnMainSync(()->page.startActivity(chooser));
        waitFor(test,()->{AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();return root!=null&&String.valueOf(root.getPackageName()).contains("intentresolver");},"native system share window");
        SystemClock.sleep(400);
        Bitmap capture=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"export-share-"+kind+"-"+args.getString("theme","light")+".png"))){capture.compress(Bitmap.CompressFormat.PNG,100,out);}finally{capture.recycle();}
        require(test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),"native share sheet dismisses");
        waitFor(test,page::hasWindowFocus,"returns to export page");
    }
    private static Uri create(Instrumentation test,String name,String mime)throws Exception{ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/Chrona");Uri uri=test.getTargetContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new java.io.IOException("test destination");return uri;}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void waitFor(Instrumentation test,BooleanSupplier ready,String label)throws Exception{long until=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<until){test.waitForIdleSync();if(ready.getAsBoolean())return;SystemClock.sleep(100);}throw new AssertionError(label);}
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static String report(String kind)throws Exception{return new JSONObject().put("assertions",checks).put("kind",kind).put("nativeSharesheet",true).toString();}
}
