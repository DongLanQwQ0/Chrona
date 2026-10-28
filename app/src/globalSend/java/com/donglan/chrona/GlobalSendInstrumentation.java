package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real Android client/state checks; local fake Messenger never contacts a group or opens a URL. */
public final class GlobalSendInstrumentation extends Instrumentation {
    private Bundle args;private int assertions;private Activity screen,lastResumed;private GlobalSendLink link;
    private final List<JSONObject> requests=new ArrayList<>();private String replyMode="success";
    private final JSONArray evidence=new JSONArray();private String operation="";
    private Message delayedSend,delayedStatus;private String oldTheme;
    @Override public void onCreate(Bundle value){args=value==null?new Bundle():value;super.onCreate(value);start();}
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);lastResumed=activity;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        boolean protocol=args.getString("mode","protocol").equals("protocol");
        String expected=protocol?"com.donglan.chrona.lanbridge.validation":"com.donglan.chrona";
        if(!Build.HARDWARE.equals("ranchu")||!getTargetContext().getPackageName().equals(expected))throw new SecurityException("Owned shared AVD package only");
        Context app=getTargetContext();if(protocol&&(new ZjuSettingsStore(app).configured()||new WebDavSettingsStore(app).configured()))throw new SecurityException("Refusing saved school/sync identity");
        oldTheme=ThemeStore.mode(app);if(protocol)ThemeStore.setMode(app,args.getString("theme","light"));
        if(LanAccessService.running())throw new SecurityException("Existing LAN session");
        getUiAutomation().setRotation(args.getString("orientation","portrait").equals("landscape")?UiAutomation.ROTATION_FREEZE_90:UiAutomation.ROTATION_FREEZE_0);SystemClock.sleep(700);
        if(Build.VERSION.SDK_INT>=33){getUiAutomation().grantRuntimePermission(app.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);getUiAutomation().grantRuntimePermission(app.getPackageName(),android.Manifest.permission.NEARBY_WIFI_DEVICES);}
        screen=startActivitySync(new Intent(app,LanSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->lastResumed instanceof LanSettingsActivity&&!lastResumed.isDestroyed()&&lastResumed.hasWindowFocus());screen=lastResumed;
        if(args.getString("mode","protocol").equals("real")){real(app);}
        else{runOnMainSync(()->{try{((Handler)field(screen,"handler")).removeCallbacks((Runnable)field(screen,"poll"));((GlobalSendLink)field(screen,"globalSend")).close();LanAccessService.start(screen);}catch(Exception e){throw new AssertionError(e);}});await(LanAccessService::running);runOnMainSync(()->{try{invoke(screen,"refresh",new Class<?>[0]);}catch(Exception e){throw new AssertionError(e);}});protocol(app);}
        result.putString("report",new JSONObject().put("assertions",assertions).put("localFakeRequests",requests.size()).put("checks",evidence).toString());finish(-1,result);
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));try{capture("failure");}catch(Exception ignored){}finish(0,result);}
    finally{runOnMainSync(()->{if(link!=null)link.close();if(screen!=null)screen.finish();if(LanAccessService.running())LanAccessService.stop(getTargetContext());if(oldTheme!=null)ThemeStore.setMode(getTargetContext(),oldTheme);});}}
    private void protocol(Context app)throws Exception{
        Messenger server=new Messenger(new Handler(Looper.getMainLooper(),packet->{try{JSONObject request=new JSONObject(packet.getData().getString("request"));requests.add(request);String method=request.getString("method");JSONObject value;
            if(method.equals("hello"))value=new JSONObject().put("protocolVersion",1).put("associationState",replyMode.equals("denied")?"required":"authorized").put("connected",true).put("capabilities",new JSONArray().put("list_devices").put("send_open_url"));
            else if(method.equals("list_devices")){JSONArray list=new JSONArray().put(device("owned-receiver",replyMode.equals("changed")?"验收电脑（名称更新）":"验收电脑"));if(replyMode.equals("expanded"))list.put(device("second","另一台设备"));value=new JSONObject().put("devices",list);}
            else{if(method.equals("send_open_url")){operation=request.getString("requestId");if(replyMode.equals("timeout")){delayedSend=Message.obtain(packet);return true;}}else{if(!request.getString("requestId").equals(operation))throw new AssertionError("Status changed operation ID");if(replyMode.equals("hold_status")){delayedStatus=Message.obtain(packet);return true;}}value=new JSONObject().put("requestId",operation).put("state",method.equals("status")?"launch_accepted":"queued");}
            JSONObject response=new JSONObject().put("v",1).put("requestId",request.getString("requestId")).put("ok",true).put("result",value);Message reply=Message.obtain(null,1);reply.arg1=packet.arg1;Bundle data=new Bundle();data.putString("response",response.toString());reply.setData(data);packet.replyTo.send(reply);
        }catch(Exception failure){throw new AssertionError(failure);}return true;}));
        link=new GlobalSendLink(app,null);inject(link,server);runOnMainSync(()->{try{set(screen,"globalSend",link);link.observe(()->{try{invoke(screen,"renderDevices",new Class<?>[0]);}catch(Exception failure){throw new AssertionError(failure);}});((View)field(screen,"devicesArea")).setVisibility(View.VISIBLE);((HeaderActionGroup)field(screen,"headerActions")).setActionVisible((View)field(screen,"refreshDevices"),true);link.refresh();}catch(Exception failure){throw new AssertionError(failure);}});await(()->link.devices.size()==1&&items().size()==1);
        check(link.authorized&&requests.stream().noneMatch(r->r.optString("method").equals("send_open_url")),"opening and refreshing only queries; no unsolicited send");
        check(link.devices.get(0).supported()&&link.devices.get(0).sendable(),"v1 capability object and sendable field parsed");
        TextView note=(TextView)find(screen.getWindow().getDecorView(),"可通过手机确认/扫码/配对码授权");View area=(View)field(screen,"devicesArea");int[] n=new int[2],a=new int[2];runOnMainSync(()->{note.getLocationOnScreen(n);area.getLocationOnScreen(a);});
        check(a[1]>=n[1]+note.getHeight(),"device section follows the short authorization note");
        check(items().get("owned-receiver").getPaddingLeft()==Math.round(16*app.getResources().getDisplayMetrics().density),"device text retains the shared 16dp card inset");
        TextView row=items().get("owned-receiver");runOnMainSync(()->{row.setFocusableInTouchMode(true);row.requestFocus();replyMode="changed";link.refresh();});await(()->row.getText().toString().contains("名称更新"));
        check(items().get("owned-receiver")==row&&row.hasFocus(),"name/status refresh retains the actual row and keyboard focus");
        runOnMainSync(()->{replyMode="expanded";link.refresh();});await(()->items().size()==2);check(items().get("owned-receiver")==row&&row.hasFocus(),"inserting a peer preserves the existing focused row");
        runOnMainSync(()->{replyMode="success";link.refresh();});await(()->items().size()==1);check(items().get("owned-receiver")==row&&row.hasFocus()&&((LinearLayout)field(screen,"deviceRows")).getChildCount()==1,"removing a peer keeps other rows and removes orphan dividers");capture("devices");
        if(Boolean.parseBoolean(args.getString("visualOnly","false"))){if(args.getString("orientation","").equals("landscape")){
            ScrollView scroll=null;for(ViewParent p=row.getParent();p!=null;p=p.getParent())if(p instanceof ScrollView viewport){scroll=viewport;break;}if(scroll==null)throw new AssertionError("Missing page viewport");ScrollView viewport=scroll;
            runOnMainSync(()->{row.clearFocus();viewport.scrollTo(0,0);});capture("initial");
            Rect bounds=new Rect();runOnMainSync(()->viewport.getGlobalVisibleRect(bounds));float x=bounds.exactCenterX(),from=bounds.top+bounds.height()*.8f,to=bounds.top+bounds.height()*.2f;for(int gesture=0;gesture<2;gesture++){long start=SystemClock.uptimeMillis();inject(start,0,x,from);for(int i=1;i<=10;i++){SystemClock.sleep(20);inject(start,2,x,from+(to-from)*i/10f);}inject(start,1,x,to);SystemClock.sleep(500);}
            check(viewport.getScrollY()>0,"normal landscape upward touch scrolls the header/tools out with the full page");capture("scrolled");
        }return;}
        String url=LanAccessService.url();runOnMainSync(()->link.send(link.devices.get(0)));await(()->!link.operationId.isEmpty()&&!hasPending(link));
        JSONObject send=requests.stream().filter(r->r.optString("method").equals("send_open_url")).findFirst().orElseThrow();String id=link.operationId;
        check(send.getString("url").equals(url)&&!send.has("pair")&&!send.getString("url").contains("#")&&!send.getString("url").contains("?"),"only the actual plain LAN URL is sent, no pairing credential");
        check(send.getLong("expiresInMs")==120000&&send.getString("targetDeviceId").equals("owned-receiver"),"exact selected target and 120-second lifetime");
        runOnMainSync(()->link.foreground(true));await(()->link.operationState.equals("launch_accepted"));
        check(requests.stream().anyMatch(r->r.optString("method").equals("status")&&r.optString("requestId").equals(id)&&!r.has("operationRequestId")),"status uses the original sending requestId");
        check(link.operationText().contains("已请求打开浏览器")&&!link.operationText().contains("已加载"),"launch acceptance does not claim browser page loading");
        runOnMainSync(()->{link.foreground(false);link.operationId="";replyMode="timeout";link.send(link.devices.get(0));});String unknown=link.operationId;await(()->link.operationState.equals("timeout_unknown"));
        long sent=requests.stream().filter(r->r.optString("method").equals("send_open_url")).count();
        runOnMainSync(()->link.send(link.devices.get(0)));check(requests.stream().filter(r->r.optString("method").equals("send_open_url")).count()==sent,"duplicate clicks during unknown delivery do not resend");
        Bundle saved=new Bundle();runOnMainSync(()->{link.save(saved);replyMode="hold_status";link.foreground(true);});await(()->delayedStatus!=null);
        runOnMainSync(()->{try{respond(delayedSend,"launch_accepted");}catch(Exception failure){throw new AssertionError(failure);}});waitForIdleSync();
        check(link.operationState.equals("timeout_unknown")&&((Map<?,?>)field(link,"pending")).containsKey(delayedStatus.arg1),"late send reply cannot consume a newer status RPC with the same intent ID");
        runOnMainSync(()->{try{link.operationExpires=System.currentTimeMillis()-1;invoke(screen,"renderDevices",new Class<?>[0]);}catch(Exception failure){throw new AssertionError(failure);}});
        check(row.isEnabled(),"elapsed lifetime re-enables device actions without a terminal callback");
        runOnMainSync(()->link.close());link=new GlobalSendLink(app,saved);inject(link,server);
        runOnMainSync(()->{replyMode="success";link.foreground(true);link.refresh();});await(()->link.operationState.equals("launch_accepted"));
        check(link.operationId.equals(unknown)&&requests.stream().filter(r->r.optString("method").equals("send_open_url")).count()==sent,"restored unknown delivery only queries the same intent");
        runOnMainSync(()->{replyMode="denied";link.refresh();});await(()->!link.authorized);
        check(link.devices.isEmpty(),"authorization required clears device inventory");
        runOnMainSync(()->{try{Message fake=Message.obtain(null,1);fake.sendingUid=android.os.Process.myUid()+1;Bundle data=new Bundle();data.putString("response",new JSONObject().put("v",1).put("requestId",id).put("ok",true).put("result",new JSONObject().put("state","launch_accepted")).toString());fake.setData(data);invoke(link,"receive",new Class<?>[]{Message.class},fake);}catch(Exception e){throw new AssertionError(e);}});
        check(!link.authorized&&link.devices.isEmpty(),"reply from a different UID cannot authorize or populate inventory");
        runOnMainSync(()->link.activate(""));check(link.url.isEmpty()&&!link.connecting&&!link.loading,"LAN closure disconnects without resending");
    }
    private static JSONObject device(String id,String name)throws Exception{return new JSONObject().put("deviceId",id).put("name",name).put("platform","windows").put("online",true).put("sendable",true).put("capabilities",new JSONObject().put("openUrlIntentV1",true));}
    private void inject(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{getUiAutomation().injectInputEvent(event,true);}finally{event.recycle();}}
    private static View find(View root,String text){if(root instanceof TextView label&&text.contentEquals(label.getText()))return root;if(root instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}return null;}
    @SuppressWarnings("unchecked") private Map<String,TextView> items()throws Exception{return (Map<String,TextView>)field(screen,"deviceItems");}
    private void respond(Message request,String state)throws Exception{String id=new JSONObject(request.getData().getString("request")).getString("requestId");Message reply=Message.obtain(null,1);reply.arg1=request.arg1;Bundle data=new Bundle();data.putString("response",new JSONObject().put("v",1).put("requestId",id).put("ok",true).put("result",new JSONObject().put("requestId",id).put("state",state)).toString());reply.setData(data);request.replyTo.send(reply);}
    private void real(Context app)throws Exception{
        runOnMainSync(()->LanAccessService.start(screen));await(LanAccessService::running);
        await(()->{GlobalSendLink actual=(GlobalSendLink)field(screen,"globalSend");return !actual.connecting&&!actual.loading&&(actual.association!=null||actual.authorized||!actual.message.isEmpty());});
        check(GlobalSendLink.installed(app),"actual GlobalSend package visible through exact queries");capture("real-initial");
        GlobalSendLink actual=(GlobalSendLink)field(screen,"globalSend");
        if(actual.association!=null){
            check(GlobalSendLink.PACKAGE.equals(actual.association.getCreatorPackage()),"association action belongs to the actual provider");
            runOnMainSync(()->{try{invoke(screen,"associateGlobalSend",new Class<?>[0]);}catch(Exception failure){throw new AssertionError(failure);}});
            await(()->{var window=getUiAutomation().getRootInActiveWindow();return window!=null&&GlobalSendLink.PACKAGE.contentEquals(window.getPackageName());});capture("real-approval");
            long until=SystemClock.uptimeMillis()+100000;while(SystemClock.uptimeMillis()<until&&!actual.authorized)SystemClock.sleep(100);
            check(actual.authorized,"real cross-app user approval is returned to the original Chrona session");
        }
        check(actual.authorized,"real provider hello authorizes the exact Chrona caller");capture("real-associated");
    }
    private void inject(GlobalSendLink value,Messenger server)throws Exception{runOnMainSync(()->{try{value.url=LanAccessService.url();set(value,"remote",server);set(value,"serviceUid",android.os.Process.myUid());}catch(Exception e){throw new AssertionError(e);}});}
    private static boolean hasPending(GlobalSendLink value)throws Exception{return !((Map<?,?>)field(value,"pending")).isEmpty();}
    private interface Condition{boolean get()throws Exception;}
    private void await(Condition condition)throws Exception{long until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){boolean[] ready={false};Throwable[] error={null};runOnMainSync(()->{try{ready[0]=condition.get();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ready[0])return;SystemClock.sleep(30);}throw new AssertionError("Timed out");}
    private void check(boolean value,String name)throws Exception{assertions++;if(!value)throw new AssertionError(name);evidence.put(new JSONObject().put("check",name).put("pass",true));}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object... values)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,values);}
    private void capture(String name)throws Exception{SystemClock.sleep(400);Bitmap raw=getUiAutomation().takeScreenshot();float ratio=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap small=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*ratio),Math.round(raw.getHeight()*ratio),true);File dir=new File(getTargetContext().getExternalFilesDir(null),"global-send/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));dir.mkdirs();try(OutputStream output=new FileOutputStream(new File(dir,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,output);}finally{if(small!=raw)small.recycle();raw.recycle();}}
}
