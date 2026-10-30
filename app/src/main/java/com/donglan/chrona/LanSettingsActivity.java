package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.widget.*;

/** Same backdrop, grouped surfaces, inputs and safe area as the native settings. */
public final class LanSettingsActivity extends Activity {
    private Switch enabled;
    private TextView status, address, code;
    private ImageView qr;
    private FrameLayout qrFrame;
    private boolean refreshing;
    private GlobalSendLink globalSend;
    private LinearLayout devicesArea, deviceRows;
    private TextView deviceStatus, sendStatus;
    private Button associate;
    private ImageButton refreshDevices;
    private HeaderActionGroup headerActions;
    private String shownDevices="";
    private boolean shownDeviceContent;
    private final java.util.Map<android.view.View,GlobalSendLink.Device> deviceViews=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,TextView> deviceItems=new java.util.LinkedHashMap<>();
    private final java.util.Map<TextView,android.view.View> deviceSeparators=new java.util.HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 1000); } };
    @Override protected void onCreate(Bundle saved) {
        ThemeStore.apply(this); super.onCreate(saved);
        globalSend=getLastNonConfigurationInstance() instanceof GlobalSendLink retained?retained:new GlobalSendLink(this,saved);
        LinearLayout root = SettingsPageLayout.content(this);
        headerActions=new HeaderActionGroup(this,48);
        refreshDevices=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新设备",()->globalSend.refresh());
        headerActions.addAction(refreshDevices,48);headerActions.finish();headerActions.setActionVisible(refreshDevices,false);
        SettingsPageLayout.header(this, root, "局域网访问",headerActions);
        LinearLayout session = group(root, "连接",0);
        enabled = new Switch(this); enabled.setText("允许电脑浏览器访问"); enabled.setTextSize(16);
        enabled.setTextColor(UiStyle.colors(this).text); enabled.setPadding(dp(16), dp(12), dp(16), dp(12)); UiStyle.toggle(enabled);
        session.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
        enabled.setOnCheckedChangeListener((button, checked) -> { if (refreshing) return; if (!checked) LanAccessService.stop(this); else enable(); refresh(); });
        status = text("", 14); UiStyle.muted(status); add(session, status);
        LinearLayout access = group(root, "浏览器配对"); address = UiStyle.selectableActionText(this);
        address.setTextSize(16); address.setTextColor(UiStyle.colors(this).text);
        address.setGravity(android.view.Gravity.CENTER); add(access, address);
        address.setOnClickListener(v -> copyAddress());
        qr = new ImageView(this); qr.setContentDescription("扫码连接手机");
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrFrame = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable qrSurface = new android.graphics.drawable.GradientDrawable();
        qrSurface.setColor(Color.WHITE);
        qrSurface.setCornerRadius(dp(UiStyle.radius(this, UiStyle.RADIUS_FIELD)));
        qrSurface.setStroke(dp(1), UiStyle.colors(this).outline);
        qrFrame.setBackground(qrSurface);
        qrFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        qrFrame.addView(qr, new FrameLayout.LayoutParams(-1, -1));
        qrFrame.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams qrLayout = new LinearLayout.LayoutParams(dp(172), dp(172));
        qrLayout.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        qrLayout.setMargins(dp(16), dp(4), dp(16), dp(12));
        access.addView(qrFrame, qrLayout);
        code = text("", 16); code.setTextIsSelectable(true); code.setTypeface(android.graphics.Typeface.MONOSPACE); add(access, code);
        code.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        Button rotate = new Button(this); rotate.setText("重新配对所有浏览器"); UiStyle.button(rotate, false);
        rotate.setOnClickListener(v -> { LanAccessService.stop(this); enable(); }); UiStyle.addSpaced(root, rotate, 16, 0);
        TextView note = text("可通过手机确认/扫码/配对码授权", 13);
        UiStyle.muted(note); UiStyle.addSpaced(root, note, 18, 0);
        devicesArea=new LinearLayout(this);devicesArea.setOrientation(LinearLayout.VERTICAL);devicesArea.setVisibility(android.view.View.GONE);root.addView(devicesArea,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout devices=group(devicesArea,"发送到设备");
        deviceStatus=text("",14);UiStyle.muted(deviceStatus);add(devices,deviceStatus);
        associate=new Button(this);associate.setText("关联 GlobalSend");UiStyle.button(associate,true);associate.setOnClickListener(v->associateGlobalSend());devices.addView(associate,new LinearLayout.LayoutParams(-1,-2));
        deviceRows=new LinearLayout(this);deviceRows.setOrientation(LinearLayout.VERTICAL);devices.addView(deviceRows,new LinearLayout.LayoutParams(-1,-2));
        sendStatus=text("",14);UiStyle.muted(sendStatus);add(devices,sendStatus);
        SettingsPageLayout.show(this, root, saved == null);
        globalSend.observe(this::renderDevices);
    }
    private void enable() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 81);
        else LanAccessService.start(this);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != 81) return;
        boolean allowed = grants.length == permissions.length && grants.length > 0;
        for (int grant : grants) allowed &= grant == PackageManager.PERMISSION_GRANTED;
        if (allowed) LanAccessService.start(this);
        else android.widget.Toast.makeText(this, "请允许通知和附近设备权限后开启", android.widget.Toast.LENGTH_LONG).show();
        refresh();
    }
    private void refresh() {
        refreshing = true; enabled.setChecked(LanAccessService.running() || LanAccessService.starting()); refreshing = false;
        status.setText(LanAccessService.status());
        String url=LanAccessService.running()?LanAccessService.url():"";
        boolean available=!url.isEmpty();String label=available?url:"开启后显示访问地址";
        boolean showDevices=available&&GlobalSendLink.installed(this);
        devicesArea.setVisibility(showDevices?android.view.View.VISIBLE:android.view.View.GONE);
        headerActions.setActionVisible(refreshDevices,showDevices);
        globalSend.activate(showDevices?url:"");
        if(!label.contentEquals(address.getText()))address.setText(label);
        address.setClickable(available);address.setFocusable(available);
        address.setContentDescription(available?"复制访问地址，"+url:null);
        String pairing = LanAccessService.pairing();
        code.setText(!LanAccessService.running() ? "开启后生成临时配对码"
                : pairing.isEmpty() ? "配对码已过期，请重新配对" : pairing);
        try {
            LanAddressQr.update(qr, LanAccessService.running() ? LanAccessService.url() : "", pairing);
            qrFrame.setVisibility(qr.getVisibility());
        }
        catch (com.google.zxing.WriterException error) {
            qr.setImageDrawable(null); qr.setTag(null); qr.setVisibility(android.view.View.GONE);
            qrFrame.setVisibility(android.view.View.GONE);
            status.setText("无法生成二维码，请复制访问地址");
        }
        renderDevices();
    }
    private void renderDevices(){
        if(isDestroyed())return;
        refreshDevices.setEnabled(!globalSend.connecting&&!globalSend.loading);
        String message=globalSend.message.isEmpty()?globalSend.deviceMessage:globalSend.message;
        deviceStatus.setText(message);deviceStatus.setVisibility(message.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
        associate.setVisibility(globalSend.association!=null?android.view.View.VISIBLE:android.view.View.GONE);
        String result=globalSend.operationText();sendStatus.setText(result);sendStatus.setVisibility(result.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
        String next=globalSend.authorized+"|"+globalSend.devices.toString();
        if(!next.equals(shownDevices)){
            shownDevices=next;java.util.List<GlobalSendLink.Device> snapshot=java.util.List.copyOf(globalSend.devices);
            java.util.Set<String> ids=new java.util.HashSet<>();if(globalSend.authorized)for(GlobalSendLink.Device device:snapshot)ids.add(device.id());
            boolean membershipChanged=!deviceItems.keySet().equals(ids);
            Runnable update=()->updateDeviceRows(globalSend.authorized?snapshot:java.util.List.of());
            if(shownDeviceContent&&membershipChanged)UiMotion.swap(deviceRows,update);else{update.run();if(!shownDeviceContent&&!snapshot.isEmpty()){shownDeviceContent=true;UiMotion.enterPage(deviceRows);}}
        }
        for(java.util.Map.Entry<android.view.View,GlobalSendLink.Device> entry:deviceViews.entrySet())setDeviceEnabled(entry.getKey(),entry.getValue());
    }
    private void updateDeviceRows(java.util.List<GlobalSendLink.Device> values){
        java.util.Set<String> ids=new java.util.HashSet<>();for(GlobalSendLink.Device device:values)ids.add(device.id());
        for(String id:new java.util.ArrayList<>(deviceItems.keySet()))if(!ids.contains(id)){TextView row=deviceItems.remove(id);android.view.View line=deviceSeparators.remove(row);if(line!=null)deviceRows.removeView(line);deviceRows.removeView(row);deviceViews.remove(row);}
        if(!deviceItems.isEmpty()){TextView first=deviceItems.values().iterator().next();android.view.View line=deviceSeparators.remove(first);if(line!=null)deviceRows.removeView(line);}
        for(GlobalSendLink.Device device:values){
            String platform=switch(device.platform()){case "windows"->"Windows";case "android"->"Android";case "ios"->"iOS";default->device.platform();};String detail=platform+(device.online()?"":" · 离线")+(device.supported()?"":device.linkSupported()?" · 发送链接":" · 不支持接收网页");String label=device.name()+"\n"+detail;
            TextView row=deviceItems.get(device.id());
            if(row==null){int before=deviceRows.getChildCount();row=UiDialogs.addMenuItem(deviceRows,label,false,()->globalSend.send(device));deviceItems.put(device.id(),row);if(deviceRows.getChildAt(before)!=row)deviceSeparators.put(row,deviceRows.getChildAt(before));}
            else if(!label.contentEquals(row.getText()))row.setText(label);
            row.setPadding(dp(16),row.getPaddingTop(),dp(16),row.getPaddingBottom());row.setOnClickListener(v->globalSend.send(device));deviceViews.put(row,device);setDeviceEnabled(row,device);
        }
    }
    private void setDeviceEnabled(android.view.View view,GlobalSendLink.Device device){view.setEnabled(globalSend.canSend(device));view.setAlpha(view.isEnabled()?1f:.5f);}
    private void associateGlobalSend(){
        android.app.PendingIntent approval=globalSend.association;if(approval==null)return;
        globalSend.association=null;globalSend.deviceMessage="等待 GlobalSend 授权";renderDevices();
        android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
        if(Build.VERSION.SDK_INT>=36)options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
        else if(Build.VERSION.SDK_INT>=34)options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        try{approval.send(this,0,null,null,null,null,options.toBundle());}catch(android.app.PendingIntent.CanceledException unavailable){Feedback.show(this,"关联已失效，请刷新设备");globalSend.refresh();}
    }
    private void copyAddress() {
        String url=LanAccessService.running()?LanAccessService.url():"";
        if(url.isEmpty())return;
        android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
        if(clipboard==null){Feedback.show(this,"剪贴板暂时不可用");return;}
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("局域网访问地址",url));
        Feedback.show(this,"已复制");
    }
    @Override protected void onResume() { super.onResume(); globalSend.foreground(true);handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll);globalSend.foreground(false);super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out){globalSend.save(out);super.onSaveInstanceState(out);}
    @Override public Object onRetainNonConfigurationInstance(){return globalSend;}
    @Override protected void onDestroy(){handler.removeCallbacks(poll);globalSend.observe(null);if(!isChangingConfigurations())globalSend.close();super.onDestroy();}
    private LinearLayout group(LinearLayout root, String name) {
        return group(root,name,16);
    }
    private LinearLayout group(LinearLayout root,String name,int top) {
        TextView title = text(name, 12); UiStyle.muted(title); title.setPadding(dp(4),0,dp(4),0); if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true); UiStyle.addSpaced(root,title,top,7);
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(group); root.addView(group,new LinearLayout.LayoutParams(-1,-2)); return group;
    }
    private void add(LinearLayout root, TextView view) { view.setPadding(dp(16),dp(12),dp(16),dp(12)); root.addView(view,new LinearLayout.LayoutParams(-1,-2)); }
    private TextView text(String value, int size) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(UiStyle.colors(this).text); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
