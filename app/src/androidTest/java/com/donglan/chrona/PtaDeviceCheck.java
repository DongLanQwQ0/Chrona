package com.donglan.chrona;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.donglan.chrona.data.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Explicit device-only import of the user's supplied PTA cookie. Never outputs credentials or account IDs. */
final class PtaDeviceCheck {
    private static int[] scrollMetrics(Instrumentation test){
        int[][] result={{-1,-1}};
        test.runOnMainSync(()->{for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){
            android.widget.ScrollView scroll=findScroll(root);
            if(scroll!=null&&root.isShown()&&root.getWindowToken()!=null){
                var body=scroll.getChildCount()==0?null:scroll.getChildAt(0);
                int count=body instanceof android.view.ViewGroup group?group.getChildCount():0;
                result[0]=new int[2+count];result[0][0]=scroll.getScrollY();result[0][1]=body==null?0:Math.max(0,body.getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom()-scroll.getHeight());
                if(body instanceof android.view.ViewGroup group)for(int i=0;i<count;i++)result[0][i+2]=group.getChildAt(i).getMeasuredHeight();
            }
        }});return result[0];
    }
    private static android.widget.ScrollView findScroll(android.view.View view){
        if(view instanceof android.widget.ScrollView scroll)return scroll;
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            var found=findScroll(group.getChildAt(i));if(found!=null)return found;
        }return null;
    }
    static String run(Instrumentation test,Bundle args)throws Exception{
        var context=test.getTargetContext();
        if("interaction".equals(args.getString("phase")))return PtaUiInteractionCheck.run(test);
        if("ui".equals(args.getString("phase"))){
            test.getUiAutomation(); // Subscribe before the target window emits its accessibility events.
            String oldMode=ThemeStore.mode(context);String theme=args.getString("theme",oldMode);
            if(!theme.equals(oldMode))test.runOnMainSync(()->ThemeStore.setMode(context,theme));
            try{
            String page=args.getString("page","hub");Class<? extends android.app.Activity> target=switch(page){
                case "pta" -> PtaSettingsActivity.class;case "scope","courses" -> PtaScopeActivity.class;
                case "account","homework","timetable" -> SchoolSettingsActivity.class;case "settings" -> SettingsHubActivity.class;
                default -> ZjuSettingsActivity.class;
            };
            var intent=new android.content.Intent(context,target).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("page",page).putExtra("courses","courses".equals(page));
            android.app.Activity activity=test.startActivitySync(intent);test.waitForIdleSync();Thread.sleep(1200);
            boolean landscape=args.getBoolean("landscape",false)||"true".equals(args.getString("landscape"));
            if(landscape){test.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_90);Thread.sleep(1800);}
            Activity current=((LanAccessInstrumentation)test).ptaActivity;
            if("filters".equals(page))test.runOnMainSync(()->ScheduleFilterSheet.show(current,
                    new ScheduleFilterState(0,0,0,0,3,java.time.LocalDate.now(),java.time.LocalDate.now()),
                    (title,date,callback)->{},ignored->{}));
            if("true".equals(args.getString("scroll"))){
                test.waitForIdleSync();Thread.sleep(500);
                android.graphics.Bitmap before=test.getUiAutomation().takeScreenshot();int w=before.getWidth(),h=before.getHeight();before.recycle();
                android.graphics.Rect region="filters".equals(page)?PtaUiInteractionCheck.scrollBounds(test.getUiAutomation().getRootInActiveWindow()):null;
                for(int swipe=0;swipe<(region==null?1:6);swipe++){
                long now=android.os.SystemClock.uptimeMillis();
                for(int i=0;i<=20;i++){
                    float x=region==null?w*.7f:region.left+20;float y=region==null?h*.85f-h*.6f*i/20:region.top+region.height()*(.8f-.6f*i/20);
                    android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now+i*16,i==0?0:i==20?1:2,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    test.getUiAutomation().injectInputEvent(event,true);event.recycle();
                }Thread.sleep(150);
                }Thread.sleep(700);
            }
            Thread.sleep(6000); // Allow unrelated system heads-up notifications to leave the screen.
            if("true".equals(args.getString("compact_select"))){
                var all=test.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("全部");
                android.graphics.Rect lower=new android.graphics.Rect();
                for(var node:all){var r=new android.graphics.Rect();node.getBoundsInScreen(r);if(node.isVisibleToUser()&&r.bottom>lower.bottom)lower.set(r);}
                long now=android.os.SystemClock.uptimeMillis();PtaUiInteractionCheck.touch(test,now,now,0,lower.exactCenterX(),lower.exactCenterY());PtaUiInteractionCheck.touch(test,now,now+50,1,lower.exactCenterX(),lower.exactCenterY());
                Thread.sleep(300);PtaUiInteractionCheck.tap(test,"PTA");Thread.sleep(500);
                PtaUiInteractionCheck.tap(test,"PTA");Thread.sleep(300);test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(500);
                if(test.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("PTA").isEmpty())throw new AssertionError("Cancel lost selection");
            }
            long heldAt=0;float heldX=0,heldY=0;
            int[] scrollBefore=scrollMetrics(test),scrollAfter=null;
            String motion=args.getString("motion","");
            if(!motion.isEmpty()){
                var automation=test.getUiAutomation();var info=automation.getServiceInfo();
                info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                automation.setServiceInfo(info);Thread.sleep(300);
                var root=automation.getRootInActiveWindow();
                if(root==null)for(var activeWindow:automation.getWindows())if(activeWindow.isFocused()&&activeWindow.getRoot()!=null){root=activeWindow.getRoot();break;}
                if(root==null)throw new AssertionError("AccessibilityWindowUnavailable");
                if(root.findAccessibilityNodeInfosByText("筛选日程").isEmpty())throw new AssertionError("NotFilterDialog");
                var nodes=root.findAccessibilityNodeInfosByText("first".equals(motion)?"全部":"PTA");
                android.graphics.Rect targetBounds=new android.graphics.Rect();
                for(var node:nodes){var r=new android.graphics.Rect();node.getBoundsInScreen(r);if(node.isVisibleToUser()&&node.getText()!=null&&node.getText().toString().equals("first".equals(motion)?"全部":"PTA")&&r.bottom>targetBounds.bottom)targetBounds.set(r);}
                if(targetBounds.isEmpty())throw new AssertionError("Missing source option");
                heldAt=android.os.SystemClock.uptimeMillis();heldX=targetBounds.exactCenterX();heldY=targetBounds.exactCenterY();
                PtaUiInteractionCheck.touch(test,heldAt,heldAt,0,heldX,heldY);Thread.sleep(700);
                if("last".equals(motion)){heldX+=65;PtaUiInteractionCheck.touch(test,heldAt,android.os.SystemClock.uptimeMillis(),2,heldX,heldY);Thread.sleep(200);}
                if("fling".equals(motion)){
                    float start=heldX;
                    for(int i=1;i<=5;i++){heldX=start-350*i/5;PtaUiInteractionCheck.touch(test,heldAt,android.os.SystemClock.uptimeMillis(),2,heldX,heldY);Thread.sleep(10);}
                    PtaUiInteractionCheck.touch(test,heldAt,android.os.SystemClock.uptimeMillis(),1,heldX,heldY);heldAt=0;Thread.sleep(120);
                    scrollAfter=scrollMetrics(test);
                }
            }
            android.graphics.Rect window=new android.graphics.Rect();test.runOnMainSync(()->current.getWindow().getDecorView().getWindowVisibleDisplayFrame(window));
            android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();
            int width=bitmap.getWidth(),height=bitmap.getHeight();float factor=Math.min(1f,1200f/Math.max(width,height));
            android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.round(width*factor),Math.round(height*factor),true);
            java.io.File dir=new java.io.File(context.getExternalCacheDir(),"pta-ui");dir.mkdirs();
            String name=page+(landscape?"-landscape":"")+("true".equals(args.getString("scroll"))?"-scrolled":"")
                    +(args.containsKey("font")?"-font"+args.getString("font"):"")+("light".equals(theme)?"-light":"")+(motion.isEmpty()?"":"-"+motion)
                    +("true".equals(args.getString("compact_select"))?"-selected-cancelled":"")+".jpg";
            try(var output=new java.io.FileOutputStream(new java.io.File(dir,name))){small.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,output);}
            if(small!=bitmap)small.recycle();bitmap.recycle();
            if(heldAt>0)PtaUiInteractionCheck.touch(test,heldAt,android.os.SystemClock.uptimeMillis(),1,heldX,heldY);
            boolean canvasChecked=false;
            if("true".equals(args.getString("check_canvas"))){
                Thread.sleep(500);
                var heading=test.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("筛选日程");
                var rect=new android.graphics.Rect();heading.get(0).getBoundsInScreen(rect);
                long now=android.os.SystemClock.uptimeMillis();
                PtaUiInteractionCheck.touch(test,now,now,0,rect.exactCenterX(),rect.exactCenterY());
                PtaUiInteractionCheck.touch(test,now,now+50,1,rect.exactCenterX(),rect.exactCenterY());Thread.sleep(200);
                if(test.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("筛选日程").isEmpty())throw new AssertionError("Inside panel dismissed");
                float outside=rect.left-32*context.getResources().getDisplayMetrics().density;
                now=android.os.SystemClock.uptimeMillis();PtaUiInteractionCheck.touch(test,now,now,0,outside,rect.exactCenterY());
                PtaUiInteractionCheck.touch(test,now,now+50,1,outside,rect.exactCenterY());Thread.sleep(700);
                if(!test.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("筛选日程").isEmpty())throw new AssertionError("Outside canvas did not dismiss");
                canvasChecked=true;
            }
            if(landscape)test.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE);
            int[] settled=scrollMetrics(test);
            return new JSONObject().put("page",page).put("window_width",window.width()).put("window_height",window.height()).put("image",name)
                    .put("scroll_before",new JSONArray(scrollBefore)).put("scroll_120ms",scrollAfter==null?JSONObject.NULL:new JSONArray(scrollAfter))
                    .put("scroll_settled",new JSONArray(settled)).put("canvas_checked",canvasChecked).toString();
            }finally{if(!theme.equals(oldMode)){test.runOnMainSync(()->ThemeStore.setMode(context,oldMode));context.getSharedPreferences("appearance",0).edit().putString("mode",oldMode).commit();}}
        }
        String schoolBefore=new ZjuSettingsStore(context).status();
        if("refresh".equals(args.getString("phase"))||"recover_ui".equals(args.getString("phase"))||"school_status".equals(args.getString("phase"))){
            if("recover_ui".equals(args.getString("phase"))){var state=new ZjuHomeworkStore(context,2).state();new PtaSettingsStore(context).prefs.edit().putBoolean("enabled",state.enabled).commit();}
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);ZjuSync.requestPta(context,ignored->done.countDown());
            if(!done.await(8,java.util.concurrent.TimeUnit.MINUTES))throw new IllegalStateException("SyncStillRunning");
        }
        if("import".equals(args.getString("phase"))){
            String session=null;
            try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("cat /data/local/tmp/chrona/pta-integration/PTACookies.txt");
                var input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
                String text=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\uFEFF","").trim();
                if(text.startsWith("Cookie:"))text=text.substring(7).trim();
                for(String part:text.split(";")){String value=part.trim();if(value.startsWith("PTASession="))session=value.substring(11);}
            }finally{
                try(var fd=test.getUiAutomation().executeShellCommand("rm -f /data/local/tmp/chrona/pta-integration/PTACookies.txt")){ }
            }
            if(session==null||session.isBlank())throw new IllegalArgumentException("MissingSession");
            try(PtaClient client=new PtaClient(session)){
                PtaClient.User user=client.whoAmI();PtaSettingsStore settings=new PtaSettingsStore(context);
                synchronized(AndroidSync.LOCK){settings.save(session,user.id(),user.nickname());new ZjuHomeworkStore(context,2).configure(settings.account(),true);}
            }
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
            if("school_status".equals(args.getString("phase")))ZjuSync.requestSchool(context,ignored->done.countDown());else ZjuSync.requestPta(context,ignored->done.countDown());
            if(!done.await(8,java.util.concurrent.TimeUnit.MINUTES))throw new IllegalStateException("SyncStillRunning");
        }
        PtaSettingsStore settings=new PtaSettingsStore(context);ZjuHomeworkStore.State state=new ZjuHomeworkStore(context,2).state();
        JSONObject synthetic=new JSONObject().put("id","123").put("name","同日作业").put("startAt","2026-11-03T00:00:00Z").put("endAt","2026-11-03T15:59:00Z");
        var basic=PtaClient.parse(synthetic,null,"");
        var exam=PtaClient.parse(synthetic,new JSONObject().put("id","777").put("endAt","2026-11-04T15:59:00Z"),"程序设计");
        if(!basic.key.equals(exam.key)||basic.deadline!=java.time.Instant.parse("2026-11-03T15:59:00Z").toEpochMilli()
                ||exam.deadline<=basic.deadline||basic.submitted!=null||!exam.title.equals("程序设计 · 同日作业"))throw new AssertionError("PTA mapping");
        synthetic.remove("endAt");if(PtaClient.parse(synthetic,null,"").deadline!=null)throw new AssertionError("No fabricated deadline");
        JSONObject result=new JSONObject().put("configured",settings.configured()).put("enabled",state.enabled)
                .put("status",settings.status()).put("success",settings.prefs.getLong("last_success",0)>0).put("parser_checks",6);
        if("refresh".equals(args.getString("phase")))result.put("school_status_preserved",schoolBefore.equals(new ZjuSettingsStore(context).status()));
        try(TaskStore store=new TaskStore(context)){
            List<EventCandidate> items=store.getCandidates(state.captureId);
            result.put("items",items.size()).put("pta_search",store.queryScheduleIds(new ScheduleQuery(3,"homework",0,"PTA",null,null,System.currentTimeMillis(),java.time.ZoneId.systemDefault())).size());
            int school=store.queryScheduleIds(new ScheduleQuery(3,"homework",0,"学在浙大",null,null,System.currentTimeMillis(),java.time.ZoneId.systemDefault())).size();
            result.put("school_search",school).put("unread",new ZjuHomeworkStore(context).newestUnread()>0);
            java.util.Set<String> identities=new java.util.HashSet<>();for(var item:items)identities.add(store.stableCandidateId(item.id));
            result.put("unique",identities.size()==items.size());
        }
        // Instrumentation ends the process immediately; flush the adapter's normal async writes.
        settings.prefs.edit().putString("status",settings.status()).commit();
        return result.toString();
    }
}
