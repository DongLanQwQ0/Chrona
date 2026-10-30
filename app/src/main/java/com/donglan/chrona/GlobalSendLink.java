package com.donglan.chrona;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;
import java.util.*;

/** Activity-owned, retained local bridge. Sends only after a device click; recovery only queries. */
final class GlobalSendLink implements AutoCloseable {
    static final String PACKAGE="dev.globalsend.globalsend";
    private static final ComponentName SERVICE=new ComponentName(PACKAGE,PACKAGE+".IntegrationService");
    private static final long RESPONSE_TIMEOUT=8000,OPEN_LIFETIME=120000;
    record Device(String id,String name,String platform,boolean online,boolean sendable,boolean supported,boolean linkSupported) { }
    private record Reply(String requestId,String method,java.util.function.BiConsumer<JSONObject,PendingIntent> consumer,Runnable timeout) { }
    final Context app;
    final Handler main=new Handler(Looper.getMainLooper());
    final List<Device> devices=new ArrayList<>();
    private final Map<Integer,Reply> pending=new HashMap<>();
    private int rpcSequence;
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),this::receive));
    private Messenger remote;
    private ServiceConnection connection;
    private int serviceUid=-1;
    private Runnable observer;
    private boolean bound,closed,foreground,supportsOpen,supportsLinks;
    boolean connecting,loading,authorized;
    PendingIntent association;
    String url="",message="",operationId="",operationTarget="",operationTargetId="",operationState="",operationUrl="",deliveryMode="open";
    long operationExpires,statusPollUntil;
    private final Runnable poll=()->queryStatus();
    private final Runnable bindTimeout=()->{if(connecting)lost();};
    GlobalSendLink(Context context,Bundle saved){app=context.getApplicationContext();if(saved!=null){operationId=saved.getString("gs_id","");operationTarget=saved.getString("gs_target","");operationTargetId=saved.getString("gs_target_id","");operationState=saved.getString("gs_state","");operationUrl=saved.getString("gs_url","");deliveryMode=saved.getString("gs_mode","open");operationExpires=saved.getLong("gs_expires");}}
    static boolean installed(Context app){try{return app.getPackageManager().getApplicationInfo(PACKAGE,0).enabled;}catch(PackageManager.NameNotFoundException absent){return false;}}
    void observe(Runnable value){observer=value;}
    void foreground(boolean value){foreground=value;main.removeCallbacks(poll);if(value)statusPollUntil=System.currentTimeMillis()+OPEN_LIFETIME;if(value&&authorized&&inFlight())queryStatus();}
    void save(Bundle out){out.putString("gs_id",operationId);out.putString("gs_target",operationTarget);out.putString("gs_target_id",operationTargetId);out.putString("gs_state",operationState);out.putString("gs_url",operationUrl);out.putString("gs_mode",deliveryMode);out.putLong("gs_expires",operationExpires);}
    private void changed(){if(observer!=null)observer.run();}
    void activate(String value){
        if(closed||url.equals(value))return;
        boolean sameOperation=operationUrl.equals(value);disconnect();url=value;devices.clear();authorized=false;association=null;message="";
        if(!sameOperation){operationId="";operationState="";operationTarget="";operationTargetId="";deliveryMode="open";}
        if(!value.isEmpty())refresh();else changed();
    }
    void refresh(){
        if(closed||url.isEmpty()||connecting||loading)return;
        if(remote!=null){hello();return;}
        try{serviceUid=app.getPackageManager().getServiceInfo(SERVICE,0).applicationInfo.uid;}
        catch(PackageManager.NameNotFoundException absent){message="请更新 GlobalSend";changed();return;}
        connecting=true;message="正在连接 GlobalSend";changed();
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){if(closed||connection!=this||!SERVICE.equals(name))return;main.removeCallbacks(bindTimeout);remote=new Messenger(binder);connecting=false;hello();}
            @Override public void onServiceDisconnected(ComponentName name){if(connection==this)lost();}
            @Override public void onBindingDied(ComponentName name){if(connection==this)lost();}
            @Override public void onNullBinding(ComponentName name){if(connection==this)lost();}
        };
        try{bound=app.bindService(new Intent().setComponent(SERVICE),connection,Context.BIND_AUTO_CREATE);if(!bound)lost();else main.postDelayed(bindTimeout,RESPONSE_TIMEOUT);}
        catch(RuntimeException denied){lost();}
    }
    private void hello(){
        loading=true;changed();
        call("hello",new JSONObject(),null,(result,approval)->{
            loading=false;association=approval;
            if(result==null){changed();return;}
            supportsOpen=capability(result.optJSONArray("capabilities"),"send_open_url");supportsLinks=capability(result.optJSONArray("capabilities"),"send_link");
            if(result.optInt("protocolVersion")!=1||!capability(result.optJSONArray("capabilities"),"list_devices")||!supportsOpen&&!supportsLinks){authorized=false;devices.clear();association=null;message="请更新 GlobalSend";changed();return;}
            authorized="authorized".equals(result.optString("associationState"));
            if(!authorized){devices.clear();message=association!=null?"":"请在 GlobalSend 允许拾时联动";changed();return;}
            if(!result.optBoolean("connected")){devices.clear();message="请先连接 GlobalSend";changed();return;}
            listDevices();
        });
    }
    private void listDevices(){
        loading=true;changed();
        call("list_devices",object("refresh",true),null,(result,approval)->{
            loading=false;if(result==null){changed();return;}
            try{List<Device> next=new ArrayList<>();JSONArray values=result.getJSONArray("devices");for(int i=0;i<values.length();i++){JSONObject item=values.getJSONObject(i),capabilities=item.getJSONObject("capabilities");next.add(new Device(item.getString("deviceId"),item.getString("name"),item.getString("platform"),item.getBoolean("online"),item.getBoolean("sendable"),supportsOpen&&capabilities.getBoolean("openUrlIntentV1"),supportsLinks&&capabilities.optBoolean("sendLinkV1")));}
                devices.clear();devices.addAll(next);message=devices.isEmpty()?"暂无设备":"";
            }catch(JSONException invalid){devices.clear();message="GlobalSend 返回的设备列表无效";}
            changed();if(foreground&&inFlight())queryStatus();
        });
    }
    boolean inFlight(){return !operationId.isEmpty()&&!Set.of("sent","launch_accepted","needs_tap","failed","expired").contains(operationState);}
    private boolean linkDelivery(){return "link".equals(deliveryMode);}
    private boolean retryLink(Device device){return linkDelivery()&&!operationId.isEmpty()&&!operationState.equals("sent")&&operationTargetId.equals(device.id)&&operationUrl.equals(url);}
    boolean canSend(Device device){
        if(!authorized||!device.sendable||!device.supported&&!device.linkSupported)return false;
        if(!linkDelivery()&&inFlight()&&System.currentTimeMillis()>=operationExpires)return true;
        if(pending.values().stream().anyMatch(reply->reply.requestId.equals(operationId)))return false;
        if(retryLink(device))return device.linkSupported;
        return !inFlight()||System.currentTimeMillis()>=(linkDelivery()?statusPollUntil:operationExpires);
    }
    void send(Device device){
        String current=LanAccessService.running()?LanAccessService.url():"";
        if(remote==null||current.isEmpty()||!current.equals(url)||!devices.contains(device)||!canSend(device))return;
        boolean retry=retryLink(device);
        if(!retry){operationId=UUID.randomUUID().toString();deliveryMode=device.supported?"open":"link";}
        operationTarget=device.name;operationTargetId=device.id;operationState="queued";operationUrl=url;operationExpires=linkDelivery()?0:System.currentTimeMillis()+OPEN_LIFETIME;statusPollUntil=System.currentTimeMillis()+OPEN_LIFETIME;message="";
        String sendingId=operationId;
        JSONObject args=object("targetDeviceId",device.id);put(args,"url",url);if(!linkDelivery())put(args,"expiresInMs",OPEN_LIFETIME);
        call(linkDelivery()?"send_link":"send_open_url",args,sendingId,(result,approval)->{if(!operationId.equals(sendingId))return;if(result!=null)state(result);else if(!operationState.equals("failed"))operationState="timeout_unknown";changed();scheduleStatus();});changed();
    }
    private void queryStatus(){
        main.removeCallbacks(poll);if(closed||!foreground||!authorized||!inFlight()||remote==null)return;
        if(pending.values().stream().anyMatch(reply->reply.requestId.equals(operationId))){scheduleStatus();return;}
        String queriedId=operationId;JSONObject args=new JSONObject();if(linkDelivery())put(args,"deliveryMode","link");call("status",args,queriedId,(result,approval)->{if(!operationId.equals(queriedId))return;if(result!=null)state(result);changed();scheduleStatus();});changed();
    }
    private void scheduleStatus(){main.removeCallbacks(poll);if(foreground&&inFlight()&&System.currentTimeMillis()<(linkDelivery()?statusPollUntil:operationExpires))main.postDelayed(poll,1500);}
    private void state(JSONObject result){String value=result.optString("state");boolean valid=linkDelivery()?"link".equals(result.optString("deliveryMode"))&&Set.of("queued","sent","failed").contains(value):!"link".equals(result.optString("deliveryMode"))&&Set.of("queued","delivered","launch_accepted","needs_tap","failed","expired").contains(value);if(operationId.equals(result.optString("requestId"))&&valid){operationState=value;message="";}else{operationState="timeout_unknown";message=linkDelivery()?"发送结果未知，请点设备重试":"打开结果未知，请刷新查询";}}
    String operationText(){if(operationId.isEmpty())return "";String label=linkDelivery()?switch(operationState){case "sent"->"链接已发送";case "queued"->"待确认 · 点设备重试";case "failed"->"链接发送失败";default->"发送结果未知 · 点设备重试";}:switch(operationState){case "queued"->"正在发送";case "delivered"->"已送达，等待打开";case "launch_accepted"->"已请求打开浏览器";case "needs_tap"->"请在目标设备点击通知打开";case "failed"->"打开失败";case "expired"->"请求已过期";default->"打开结果未知";};return operationTarget+" · "+label;}
    private void call(String method,JSONObject args,String id,java.util.function.BiConsumer<JSONObject,PendingIntent> consumer){
        if(remote==null){message="GlobalSend 连接已断开";consumer.accept(null,null);return;}
        String request=id==null?UUID.randomUUID().toString():id;put(args,"v",1);put(args,"method",method);put(args,"requestId",request);
        int rpc=rpcSequence=rpcSequence==Integer.MAX_VALUE?1:rpcSequence+1;
        Runnable timeout=()->{Reply reply=pending.remove(rpc);if(reply!=null){message="GlobalSend 响应超时";reply.consumer.accept(null,null);}};
        pending.put(rpc,new Reply(request,method,consumer,timeout));main.postDelayed(timeout,RESPONSE_TIMEOUT);
        Message packet=Message.obtain(null,1);packet.arg1=rpc;Bundle data=new Bundle();data.putString("request",args.toString());packet.setData(data);packet.replyTo=receiver;
        try{remote.send(packet);}catch(RemoteException failure){Reply reply=pending.remove(rpc);main.removeCallbacks(timeout);message="GlobalSend 连接已断开";if(reply!=null)reply.consumer.accept(null,null);}
    }
    private boolean receive(Message packet){
        if(closed||packet.what!=1||packet.sendingUid!=serviceUid)return true;
        Reply reply=pending.remove(packet.arg1);if(reply==null)return true;main.removeCallbacks(reply.timeout);
        try{Bundle data=packet.getData();data.setClassLoader(PendingIntent.class.getClassLoader());JSONObject response=new JSONObject(data.getString("response",""));if(!reply.requestId.equals(response.getString("requestId")))throw new JSONException("Mismatched requestId");
            if(response.optInt("v")!=1||!response.optBoolean("ok")){JSONObject error=response.optJSONObject("error");String code=error==null?"unsupported_version":error.optString("code");message=errorText(code);if(response.optString("requestId").equals(operationId)&&Set.of("send_open_url","send_link").contains(reply.method)&&!code.equals("timeout_unknown"))operationState="failed";if(Set.of("caller_not_allowed","authorization_required").contains(code)){authorized=false;association=null;devices.clear();}reply.consumer.accept(null,null);}
            else{PendingIntent approval=data.getParcelable("association_intent");if(approval!=null&&(!PACKAGE.equals(approval.getCreatorPackage())||approval.getCreatorUid()!=serviceUid))throw new SecurityException("Unexpected association owner");reply.consumer.accept(response.getJSONObject("result"),approval);}
        }catch(RuntimeException|JSONException invalid){message="GlobalSend 响应无效";if(reply!=null)reply.consumer.accept(null,null);changed();}return true;
    }
    private String errorText(String code){return switch(code){case "not_connected"->"请先连接 GlobalSend";case "caller_not_allowed","authorization_required"->"请在 GlobalSend 允许拾时联动";case "target_unavailable"->"目标设备不可用";case "unsupported_target"->linkDelivery()?"目标设备不支持接收链接":"目标设备需要更新 GlobalSend";case "request_not_found","timeout_unknown"->linkDelivery()?"发送结果未知，请点设备重试":"打开结果未知，请刷新查询";case "invalid_url"->"局域网地址已失效";case "expired"->"请求已过期";case "launch_failed"->"目标设备无法打开浏览器";case "unsupported_version"->"请更新 GlobalSend";default->"GlobalSend 发送失败";};}
    private void lost(){disconnect();authorized=false;devices.clear();message="GlobalSend 连接已断开";changed();}
    private void disconnect(){remote=null;connecting=false;loading=false;main.removeCallbacks(poll);main.removeCallbacks(bindTimeout);for(Reply reply:pending.values())main.removeCallbacks(reply.timeout);pending.clear();if(bound){try{app.unbindService(connection);}catch(IllegalArgumentException ignored){}bound=false;}connection=null;}
    @Override public void close(){closed=true;observer=null;disconnect();}
    private static JSONObject object(String name,Object value){JSONObject object=new JSONObject();put(object,name,value);return object;}
    private static boolean capability(JSONArray values,String key){if(values!=null)for(int i=0;i<values.length();i++)if(key.equals(values.optString(i)))return true;return false;}
    private static void put(JSONObject object,String name,Object value){try{object.put(name,value);}catch(JSONException invalid){throw new IllegalArgumentException(invalid);}}
}
