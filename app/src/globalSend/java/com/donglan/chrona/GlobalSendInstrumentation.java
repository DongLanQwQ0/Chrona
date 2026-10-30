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
            if(method.equals("hello")){JSONArray caps=new JSONArray().put("list_devices");if(!replyMode.equals("link_only"))caps.put("send_open_url");if(replyMode.startsWith("link")||replyMode.equals("open_failure"))caps.put("send_link");value=new JSONObject().put("protocolVersion",1).put("associationState",replyMode.equals("denied")?"required":"authorized").put("connected",true).put("capabilities",caps);}
            else if(method.equals("list_devices")){JSONArray list=new JSONArray().put(device("owned-receiver",replyMode.equals("changed")?"验收电脑（名称更新）":"验收电脑"));if(replyMode.equals("expanded"))list.put(device("second","另一台设备"));if(replyMode.startsWith("link")||replyMode.equals("legacy_links")||replyMode.equals("open_failure"))list.put(device("owned-ipad","验收 iPad").put("platform","ios").put("capabilities",new JSONObject().put("openUrlIntentV1",false).put("sendLinkV1",true)));value=new JSONObject().put("devices",list);}
            else if(method.equals("send_link")||method.equals("status")&&request.optString("deliveryMode").equals("link")){if(method.equals("send_link")){operation=request.getString("requestId");if(replyMode.equals("link_timeout")){delayedSend=Message.obtain(packet);return true;}}value=new JSONObject().put("requestId",operation).put("deliveryMode","link").put("state",replyMode.equals("link_queued")?"queued":replyMode.equals("link_failed")?"failed":"sent");if(replyMode.equals("link_failed"))value.put("error",new JSONObject().put("code","target_unavailable"));}
            else if(method.equals("send_open_url")&&replyMode.equals("open_failure")){respondError(packet,"unsupported_target");return true;}
            else{if(method.equals("send_open_url")){operation=request.getString("requestId");if(replyMode.equals("timeout")){delayedSend=Message.obtain(packet);return true;}}else{if(!request.getString("requestId").equals(operation))throw new AssertionError("Status changed operation ID");if(replyMode.equals("hold_status")){delayedStatus=Message.obtain(packet);return true;}}value=new JSONObject().put("requestId",operation).put("state",method.equals("status")?"launch_accepted":"queued");}
            JSONObject response=new JSONObject().put("v",1).put("requestId",request.getString("requestId")).put("ok",true).put("result",value);Message reply=Message.obtain(null,1);reply.arg1=packet.arg1;Bundle data=new Bundle();data.putString("response",response.toString());reply.setData(data);packet.replyTo.send(reply);
        }catch(Exception failure){throw new AssertionError(failure);}return true;}));
        if(args.getString("case","").equals("link-failed")){
            replyMode="link_failed";attach(app,server,null);runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("failed"));View status=(View)field(screen,"sendStatus");waitForIdleSync();SystemClock.sleep(400);runOnMainSync(()->status.requestRectangleOnScreen(new Rect(0,0,status.getWidth(),status.getHeight()),true));waitForIdleSync();capture("link-failed-visible");Rect bounds=new Rect();runOnMainSync(()->status.getGlobalVisibleRect(bounds));check(status.getHeight()>0&&bounds.height()==status.getHeight(),"link failure status is entirely visible after normal page scrolling");return;
        }
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
        linkProtocol(app,server);
    }
    private void attach(Context app,Messenger server,Bundle saved)throws Exception{
        runOnMainSync(()->{if(link!=null)link.close();});link=new GlobalSendLink(app,saved);inject(link,server);
        runOnMainSync(()->{try{set(screen,"globalSend",link);((View)field(screen,"devicesArea")).setVisibility(View.VISIBLE);((HeaderActionGroup)field(screen,"headerActions")).setActionVisible((View)field(screen,"refreshDevices"),true);link.observe(()->{try{invoke(screen,"renderDevices",new Class<?>[0]);}catch(Exception e){throw new AssertionError(e);}});link.refresh();}catch(Exception e){throw new AssertionError(e);}});
        await(()->link.authorized&&link.devices.size()==2&&!hasPending(link));
    }
    private GlobalSendLink.Device ipad(){return link.devices.stream().filter(d->d.id().equals("owned-ipad")).findFirst().orElseThrow();}
    private long count(String method){return requests.stream().filter(r->r.optString("method").equals(method)).count();}
    private void linkProtocol(Context app,Messenger server)throws Exception{
        replyMode="link_sent";attach(app,server,null);
        TextView pad=items().get("owned-ipad");check(pad.isEnabled()&&pad.getText().toString().contains("发送链接")&&!ipad().supported(),"iPad without browser-opening capability remains enabled for explicit link delivery");
        runOnMainSync(()->pad.requestRectangleOnScreen(new Rect(0,0,pad.getWidth(),pad.getHeight()),true));capture("ipad-link");
        Rect hit=new Rect();runOnMainSync(()->pad.getGlobalVisibleRect(hit));check(hit.height()==pad.getHeight()&&hit.width()>0,"iPad link row is fully reachable in the actual viewport");
        long opens=count("send_open_url"),down=SystemClock.uptimeMillis();inject(down,MotionEvent.ACTION_DOWN,hit.exactCenterX(),hit.exactCenterY());SystemClock.sleep(70);inject(down,MotionEvent.ACTION_UP,hit.exactCenterX(),hit.exactCenterY());await(()->link.operationState.equals("sent"));
        JSONObject sent=requests.stream().filter(r->r.optString("method").equals("send_link")).findFirst().orElseThrow();
        check(!sent.has("expiresInMs")&&!sent.has("expiresAt")&&sent.getString("url").equals(LanAccessService.url())&&sent.getString("targetDeviceId").equals("owned-ipad"),"plain link sends the exact LAN URL and selected iPad without an opening expiry");
        check(link.operationText().contains("链接已发送")&&!link.operationText().contains("打开")&&!link.inFlight()&&count("send_open_url")==opens,"sent is terminal center acceptance, never browser opening");View sentStatus=(View)field(screen,"sendStatus");runOnMainSync(()->sentStatus.requestRectangleOnScreen(new Rect(0,0,sentStatus.getWidth(),sentStatus.getHeight()),true));capture("link-sent");
        long statuses=count("status");runOnMainSync(()->link.foreground(true));SystemClock.sleep(1700);check(count("status")==statuses,"sent link does not poll for a browser-opening result");
        runOnMainSync(()->link.foreground(false));replyMode="link_queued";runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("queued")&&!hasPending(link));
        String queuedId=link.operationId;Bundle queuedSaved=new Bundle();runOnMainSync(()->link.save(queuedSaved));attach(app,server,queuedSaved);
        runOnMainSync(()->link.foreground(true));await(()->requests.stream().anyMatch(r->r.optString("method").equals("status")&&r.optString("requestId").equals(queuedId)&&r.optString("deliveryMode").equals("link"))&&!hasPending(link));
        runOnMainSync(()->{link.foreground(false);link.statusPollUntil=System.currentTimeMillis()-1;});
        check(link.deliveryMode.equals("link")&&link.operationId.equals(queuedId)&&link.operationTargetId.equals("owned-ipad")&&link.operationExpires==0&&link.canSend(ipad()),"restoration retains link kind, target and original ID; ending a polling window does not expire the message");
        long linkSends=count("send_link");replyMode="link_sent";runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("sent"));
        JSONObject retried=requests.stream().filter(r->r.optString("method").equals("send_link")).reduce((a,b)->b).orElseThrow();
        check(count("send_link")==linkSends+1&&retried.getString("requestId").equals(queuedId)&&!retried.has("expiresInMs"),"explicit queued retry reuses the original link ID and content without an expiry");
        replyMode="link_timeout";runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("timeout_unknown"));String unknown=link.operationId;linkSends=count("send_link");Bundle unknownSaved=new Bundle();runOnMainSync(()->link.save(unknownSaved));
        replyMode="link_queued";attach(app,server,unknownSaved);runOnMainSync(()->link.foreground(true));await(()->!hasPending(link)&&link.operationState.equals("queued"));runOnMainSync(()->link.foreground(false));
        check(link.operationId.equals(unknown)&&count("send_link")==linkSends,"restoring unknown link delivery only queries; no automatic resend");
        runOnMainSync(()->{try{invoke(link,"state",new Class<?>[]{JSONObject.class},new JSONObject().put("requestId",unknown).put("deliveryMode","open").put("state","launch_accepted"));}catch(Exception e){throw new AssertionError(e);}});
        check(link.operationState.equals("timeout_unknown")&&!link.operationText().contains("已请求打开"),"an opening reply cannot be mistaken for a link-delivery result");
        replyMode="legacy_links";attach(app,server,null);check(!ipad().linkSupported()&&!items().get("owned-ipad").isEnabled()&&link.devices.get(0).supported(),"old provider without send_link keeps opening support but does not invent iPad link capability");
        replyMode="link_only";attach(app,server,null);check(link.authorized&&ipad().linkSupported()&&items().get("owned-ipad").isEnabled(),"link-only provider is valid without declaring browser-opening support");
        replyMode="link_failed";runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("failed"));String rejectedId=link.operationId;
        check(!link.inFlight()&&link.operationText().contains("链接发送失败"),"explicit link rejection is terminal and does not claim delivery");capture("link-failed");replyMode="link_sent";runOnMainSync(()->link.send(ipad()));await(()->link.operationState.equals("sent"));
        check(link.operationId.equals(rejectedId),"explicit retry after a definitive rejection also preserves the original link ID");
        replyMode="open_failure";attach(app,server,null);long linksBefore=count("send_link");runOnMainSync(()->link.send(link.devices.get(0)));await(()->link.operationState.equals("failed"));check(count("send_link")==linksBefore&&link.deliveryMode.equals("open"),"failed browser opening never silently falls back to an ordinary link");
    }
    private void respondError(Message packet,String code)throws Exception{Message reply=Message.obtain(null,1);reply.arg1=packet.arg1;String id=new JSONObject(packet.getData().getString("request")).getString("requestId");Bundle data=new Bundle();data.putString("response",new JSONObject().put("v",1).put("requestId",id).put("ok",false).put("error",new JSONObject().put("code",code)).toString());reply.setData(data);packet.replyTo.send(reply);}
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
    private void capture(String name)throws Exception{
        SystemClock.sleep(400);Bitmap raw=getUiAutomation().takeScreenshot();float ratio=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));Bitmap scaled=Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*ratio),Math.round(raw.getHeight()*ratio),true),small=scaled.copy(Bitmap.Config.ARGB_8888,true);if(scaled!=raw)scaled.recycle();raw.recycle();
        Canvas canvas=new Canvas(small);Paint mask=new Paint();mask.setColor(Color.GRAY);for(String field:List.of("qrFrame","code")){View privateView=(View)field(screen,field);Rect bounds=new Rect();if(privateView.getGlobalVisibleRect(bounds))canvas.drawRect(bounds.left*ratio,bounds.top*ratio,bounds.right*ratio,bounds.bottom*ratio,mask);}
        File dir=new File(getTargetContext().getExternalFilesDir(null),"global-send/"+args.getString("orientation","portrait")+"-"+args.getString("theme","light"));dir.mkdirs();try(OutputStream output=new FileOutputStream(new File(dir,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,75,output);}finally{small.recycle();}
    }
}
