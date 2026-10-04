package com.donglan.chrona;

import android.app.*;
import android.content.Intent;
import android.net.*;
import android.os.*;
import java.net.*;

/** Explicit user-started LAN session. Process death/reboot never reopens access. */
public final class LanAccessService extends Service {
    static final int PORT = 8765;
    private static final String CHANNEL = "chrona_lan";
    private static volatile LanAccessService live;
    private static volatile String state = "已关闭";
    private static volatile boolean starting;
    private LanWebServer server;
    private String address;
    private LanNetworkIdentity binding;
    private ConnectivityManager.NetworkCallback networks;
    static boolean running() { LanAccessService current = live; return current != null && current.server != null; }
    static boolean starting() { return starting; }
    static String status() { return state; }
    static String url() { LanAccessService current = live; return current == null || current.server == null ? "" : "http://" + current.address + ":" + PORT; }
    static String pairing() { LanAccessService current = live; return current == null || current.server == null ? "" : current.server.security.pairing(); }
    static void start(android.content.Context context) {
        if (running() || starting) return;
        starting = true; state = "正在开启…";
        try { context.startForegroundService(new Intent(context, LanAccessService.class)); }
        catch (RuntimeException error) { starting = false; state = "无法开启，请检查通知和附近设备权限后重试"; }
    }
    static void stop(android.content.Context context) {
        LanAccessService current = live;
        if (current != null) current.shutdown();
        context.stopService(new Intent(context, LanAccessService.class)); starting = false; state = "已关闭";
    }
    @Override public void onCreate() { super.onCreate(); live = this; }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || "stop".equals(intent.getAction())) { shutdown(); stopSelf(); return START_NOT_STICKY; }
        if (server != null) return START_NOT_STICKY;
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "局域网访问", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, LanSettingsActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, LanAccessService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_chrona_foreground)
                    .setContentTitle("拾时 · 局域网访问").setContentText("电脑浏览器可访问手机，点此管理连接")
                    .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "关闭", stop).build()).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(8756, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(8756, notification);
            binding = lanBinding();
            if (binding == null) throw new IllegalStateException("请先连接 Wi-Fi 或以太网局域网");
            address = binding.address;
            server = new LanWebServer(this, address, PORT); server.start(10_000, false);
            state = "已开启"; starting = false;
            networks = new ConnectivityManager.NetworkCallback() {
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) { checkAddress(); }
                @Override public void onLost(Network network) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (server != null && binding.lost(network.getNetworkHandle())) closeForNetworkChange();
                    });
                }
            };
            getSystemService(ConnectivityManager.class).registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networks);
        } catch (Exception error) {
            String message = error instanceof IllegalStateException ? error.getMessage() : "无法开启，请检查局域网、附近设备权限和端口后重试";
            shutdown(); state = message; starting = false; stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void checkAddress() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (server != null && !binding.matches(lanBinding())) closeForNetworkChange();
        });
    }
    private void closeForNetworkChange() { shutdown(); state = "网络已变化，请重新开启局域网访问"; stopSelf(); }
    private LanNetworkIdentity lanBinding() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        if (manager == null) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    || !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (LinkAddress link : properties.getLinkAddresses()) {
                InetAddress ip = link.getAddress();
                if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) return new LanNetworkIdentity(network.getNetworkHandle(), ip.getHostAddress());
            }
        }
        return null;
    }
    private synchronized void shutdown() {
        if (server != null) { server.closeAccess(); server = null; }
        if (networks != null) { try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networks); } catch (RuntimeException ignored) { } networks = null; }
        stopForeground(STOP_FOREGROUND_REMOVE); starting = false;
    }
    @Override public void onDestroy() { shutdown(); if (live == this) live = null; if ("已开启".equals(state)) state = "已关闭"; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
