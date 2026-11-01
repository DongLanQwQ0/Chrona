package com.donglan.chrona;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import com.donglan.chrona.ai.ChatCompletionClient;
import com.donglan.chrona.ai.SchedulePreferences;
import com.donglan.chrona.net.RequestControl;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** Per-exchange cellular lease; never binds the process or changes device network settings. */
final class CellularClassroomRoute implements ChatCompletionClient.ConnectionRoute {
    private static final int NETWORK_WAIT_MILLIS = 30_000;
    private static final int CANCEL_POLL_MILLIS = 100;
    private final Context app;

    CellularClassroomRoute(Context context) { app = context.getApplicationContext(); }

    @Override public ChatCompletionClient.OpenedConnection open(URL url, RequestControl control) throws IOException {
        RequestControl.check(control);
        if (!SchedulePreferences.load(app).classroomCellular)
            return new ChatCompletionClient.OpenedConnection((HttpURLConnection) url.openConnection(), () -> { });
        ConnectivityManager manager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) throw new IOException("无法获取移动数据连接");
        Network active = manager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            throw new IOException("拾时仍受VPN接管，请将拾时排除VPN或关闭AI总结使用移动数据");
        Lease lease = new Lease(manager);
        try {
            int timeout = control == null ? NETWORK_WAIT_MILLIS : control.timeoutMillis(NETWORK_WAIT_MILLIS);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            manager.requestNetwork(request, lease.callback, timeout);
            Network network = lease.await(control, timeout);
            // Network supplies its own DNS, socket factory and configured proxy; retain HTTPS checks.
            HttpURLConnection connection = (HttpURLConnection) network.openConnection(url);
            return new ChatCompletionClient.OpenedConnection(connection, lease::close);
        } catch (SecurityException denied) {
            lease.close();
            throw new IOException("系统不允许使用移动数据，请检查移动数据与VPN设置", denied);
        } catch (IOException | RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    private static final class Lease {
        private final ConnectivityManager manager;
        private Network network;
        private boolean unavailable, lost, closed;
        final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network available) {
                synchronized (Lease.this) {
                    if (closed || network != null) return;
                    network = available;
                    Lease.this.notifyAll();
                }
            }
            @Override public void onUnavailable() {
                synchronized (Lease.this) {
                    if (closed) return;
                    unavailable = true;
                    Lease.this.notifyAll();
                }
            }
            @Override public void onLost(Network missing) {
                synchronized (Lease.this) {
                    if (closed || !missing.equals(network)) return;
                    lost = true;
                    Lease.this.notifyAll();
                }
            }
        };
        Lease(ConnectivityManager manager) { this.manager = manager; }

        synchronized Network await(RequestControl control, int timeoutMillis) throws IOException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (network == null && !unavailable && !closed) {
                RequestControl.check(control);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("等待移动数据连接超时，请开启移动数据或关闭AI总结使用移动数据");
                try { wait(Math.max(1, Math.min(CANCEL_POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining)))); }
                catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("任务已停止");
                }
            }
            RequestControl.check(control);
            if (lost) throw new IOException("移动数据连接已断开，请手动继续");
            if (unavailable || closed || network == null)
                throw new IOException("移动数据不可用，请开启移动数据或关闭AI总结使用移动数据");
            return network;
        }

        void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                notifyAll();
            }
            try { manager.unregisterNetworkCallback(callback); }
            catch (IllegalArgumentException alreadyReleased) {
                // requestNetwork rejection/onUnavailable may already have released the callback.
            }
        }
    }
}
