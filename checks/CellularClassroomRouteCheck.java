package com.donglan.chrona;

import android.content.Context;
import android.net.*;
import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Actual production route on deterministic Android callback doubles, never a carrier test. */
public final class CellularClassroomRouteCheck {
    private static int checks;
    private static final URL URL_VALUE;
    private static final AtomicInteger defaults = new AtomicInteger();
    static {
        try {
            java.net.URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
                protected URLConnection openConnection(URL u) {
                    if (!u.getHost().equals("offline.invalid")) throw new AssertionError("unexpected URL " + u);
                    defaults.incrementAndGet();
                    return new MemoryConnection(u, () -> { });
                }
            } : null);
            URL_VALUE = new URL("https://offline.invalid/v1/chat/completions");
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static void main(String[] args) throws Exception {
        happyAndToggle();
        unavailableAndDenied();
        cancellationAndDeadlines();
        vpnAndIndependentLeases();
        actualHttpExchange();
        System.out.println("CellularClassroomRouteCheck passed: " + checks + " checks (JVM doubles; no carrier/device acceptance)");
    }

    private static void happyAndToggle() throws Exception {
        Context c = new Context();
        check(SchedulePreferences.load(c).classroomCellular, "fresh preferences default cellular on");
        Network n = network(c);
        c.manager.requester = call -> call.callback.onAvailable(n);
        CellularClassroomRoute route = new CellularClassroomRoute(c);
        int before = defaults.get();
        ChatCompletionClient.OpenedConnection first = route.open(URL_VALUE, null);
        ConnectivityManager.Call call = c.manager.calls.get(0);
        check(call.request.networkCapabilities.transports.equals(java.util.Set.of(NetworkCapabilities.TRANSPORT_CELLULAR)),
                "request is explicitly cellular, never Wi-Fi");
        check(call.request.networkCapabilities.capabilities.contains(NetworkCapabilities.NET_CAPABILITY_INTERNET), "Internet capability required");
        check(call.timeout == 30_000 && c.manager.registered.size() == 1 && n.opens == 1, "30 second request lease survives open");
        setCellular(c, false);
        check(c.manager.registered.size() == 1 && c.manager.releases.isEmpty(), "toggle never interrupts an existing lease");
        try (ChatCompletionClient.OpenedConnection ignored = route.open(URL_VALUE, null)) {
            check(defaults.get() == before + 1 && c.manager.calls.size() == 1, "next exchange uses original default when off");
        }
        first.close(); first.close();
        check(c.manager.registered.isEmpty() && c.manager.releases.size() == 1, "cellular release is idempotent");
        Network late = network(c);
        call.callback.onAvailable(late); call.callback.onUnavailable(); call.callback.onLost(n);
        check(late.opens == 0 && c.manager.registered.isEmpty(), "closed callbacks never revive a lease");
        setCellular(c, true);
        try (ChatCompletionClient.OpenedConnection ignored = route.open(URL_VALUE, null)) {
            check(c.manager.calls.size() == 2 && defaults.get() == before + 1, "same route rereads on switch for next request");
        }
    }

    private static void unavailableAndDenied() throws Exception {
        int before = defaults.get();
        Context missing = new Context(); missing.manager = null;
        check(expect(IOException.class, () -> new CellularClassroomRoute(missing).open(URL_VALUE, null))
                .getMessage().contains("移动数据"), "missing manager is actionable failure");
        Context c = new Context();
        c.manager.requester = call -> {
            c.manager.registered.remove(call.callback); // Android may auto-release an unavailable request.
            call.callback.onUnavailable();
        };
        expect(IOException.class, () -> new CellularClassroomRoute(c).open(URL_VALUE, null));
        check(c.manager.calls.size() == 1 && c.manager.releases.size() == 1 && c.manager.registered.isEmpty(), "unavailable releases once without resend");
        Context denied = new Context();
        denied.manager.requester = call -> {
            denied.manager.registered.remove(call.callback); // Rejection need not register a callback.
            throw new SecurityException("denied");
        };
        IOException failure = expect(IOException.class, () -> new CellularClassroomRoute(denied).open(URL_VALUE, null));
        check(failure.getCause() instanceof SecurityException && denied.manager.registered.isEmpty(), "request permission failure remains visible and releases");
        Context openFail = new Context();
        Network n = new Network(); n.opener = url -> { throw new IOException("open failed"); };
        openFail.manager.requester = call -> call.callback.onAvailable(n);
        expect(IOException.class, () -> new CellularClassroomRoute(openFail).open(URL_VALUE, null));
        check(n.opens == 1 && openFail.manager.registered.isEmpty(), "network open failure releases without fallback");
        Context runtime = new Context();
        runtime.manager.requester = call -> { throw new IllegalStateException("runtime"); };
        expect(IllegalStateException.class, () -> new CellularClassroomRoute(runtime).open(URL_VALUE, null));
        check(runtime.manager.registered.isEmpty(), "unexpected request exception still releases");
        Context lost = new Context(); Network l = network(lost);
        lost.manager.requester = call -> { call.callback.onAvailable(l); call.callback.onLost(l); };
        expect(IOException.class, () -> new CellularClassroomRoute(lost).open(URL_VALUE, null));
        check(l.opens == 0 && lost.manager.registered.isEmpty(), "lost before opening fails with no HTTP request");
        check(defaults.get() == before, "all cellular failure paths make zero default connection attempts");
    }

    private static void cancellationAndDeadlines() throws Exception {
        Context before = new Context();
        try (RequestControl control = new RequestControl(2000)) {
            control.cancel();
            expect(InterruptedIOException.class, () -> new CellularClassroomRoute(before).open(URL_VALUE, control));
            check(before.manager.calls.isEmpty(), "pre-cancel never acquires network");
        }
        Context waiting = new Context();
        try (RequestControl control = new RequestControl(4000)) {
            Worker worker = start(() -> new CellularClassroomRoute(waiting).open(URL_VALUE, control));
            awaitCalls(waiting, 1);
            long start = System.nanoTime(); control.cancel(); worker.join();
            check(worker.failure.get() instanceof InterruptedIOException, "waiting cancellation propagated");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500 && waiting.manager.registered.isEmpty(),
                    "100ms polling releases promptly during no-network wait");
            Network late = network(waiting); waiting.manager.calls.get(0).callback.onAvailable(late);
            check(late.opens == 0 && waiting.manager.registered.isEmpty(), "late available after cancellation cannot open");
            Object callback = waiting.manager.calls.get(0).callback;
            java.lang.reflect.Field ownerField = callback.getClass().getDeclaredField("this$0");
            ownerField.setAccessible(true);
            Object owner = ownerField.get(callback);
            java.lang.reflect.Field networkField = owner.getClass().getDeclaredField("network");
            networkField.setAccessible(true);
            check(networkField.get(owner) == null, "late available cannot mutate the closed lease state");
        }
        Context deadline = new Context();
        try (RequestControl control = new RequestControl(180)) {
            long start = System.nanoTime();
            expect(IOException.class, () -> new CellularClassroomRoute(deadline).open(URL_VALUE, control));
            check(deadline.manager.calls.get(0).timeout <= 180 && deadline.manager.calls.get(0).timeout > 0,
                    "network timeout uses remaining total request budget");
            check(deadline.manager.registered.isEmpty() && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500,
                    "total deadline releases rather than waiting full network timeout");
        }
        Context interrupt = new Context();
        Worker worker = start(() -> new CellularClassroomRoute(interrupt).open(URL_VALUE, null));
        awaitCalls(interrupt, 1); worker.thread.interrupt(); worker.join();
        check(worker.failure.get() instanceof InterruptedIOException && worker.interrupted.get(), "thread interruption preserved");
        check(interrupt.manager.registered.isEmpty(), "interrupted waiter releases network");
        Context timed = new Context();
        // Exercise the no-control 30s route deadline once; Android onUnavailable is a separate path.
        long start = System.nanoTime();
        expect(IOException.class, () -> new CellularClassroomRoute(timed).open(URL_VALUE, null));
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        check(elapsed >= 29_000 && elapsed < 35_000 && timed.manager.registered.isEmpty(), "standalone network deadline is bounded to 30 seconds");
    }

    private static void vpnAndIndependentLeases() throws Exception {
        Context vpn = new Context(); vpn.manager.active = new Network();
        vpn.manager.activeCapabilities = new NetworkCapabilities();
        vpn.manager.activeCapabilities.transports.add(NetworkCapabilities.TRANSPORT_VPN);
        expect(IOException.class, () -> new CellularClassroomRoute(vpn).open(URL_VALUE, null));
        check(vpn.manager.calls.isEmpty(), "current App VPN network is never bypassed");
        setCellular(vpn, false);
        try (ChatCompletionClient.OpenedConnection ignored = new CellularClassroomRoute(vpn).open(URL_VALUE, null)) {
            check(vpn.manager.calls.isEmpty(), "off preserves default VPN routing");
        }
        Context excluded = new Context(); excluded.manager.active = new Network();
        excluded.manager.activeCapabilities = new NetworkCapabilities();
        excluded.manager.activeCapabilities.transports.add(NetworkCapabilities.TRANSPORT_WIFI);
        Network n = network(excluded); excluded.manager.requester = call -> call.callback.onAvailable(n);
        try (ChatCompletionClient.OpenedConnection ignored = new CellularClassroomRoute(excluded).open(URL_VALUE, null)) {
            check(n.opens == 1, "non-VPN active network permits per-App VPN exclusion case");
        }
        Context concurrent = new Context();
        CellularClassroomRoute route = new CellularClassroomRoute(concurrent);
        Worker one = start(() -> route.open(URL_VALUE, null));
        Worker two = start(() -> route.open(URL_VALUE, null));
        awaitCalls(concurrent, 2);
        Network a = network(concurrent), b = network(concurrent);
        concurrent.manager.calls.get(0).callback.onAvailable(a);
        concurrent.manager.calls.get(1).callback.onAvailable(b);
        one.join(); two.join();
        check(one.failure.get() == null && two.failure.get() == null && a.opens == 1 && b.opens == 1,
                "concurrent opens each use their own callback/network");
        check(concurrent.manager.registered.size() == 2, "two concurrent exchanges hold independent leases");
        one.opened.get().close();
        check(concurrent.manager.registered.size() == 1, "one release leaves peer lease active");
        two.opened.get().close();
        check(concurrent.manager.registered.isEmpty() && concurrent.manager.releases.size() == 2, "both leases cleanly released");
    }

    private static void actualHttpExchange() throws Exception {
        Context c = new Context();
        AtomicReference<MemoryConnection> connection = new AtomicReference<>();
        Network n = new Network();
        n.opener = url -> {
            check(url.toString().equals(URL_VALUE.toString()), "selected network receives original HTTPS endpoint");
            MemoryConnection value = new MemoryConnection(url,
                    () -> check(c.manager.registered.size() == 1, "network lease held through HTTP I/O and disconnect"));
            connection.set(value); return value;
        };
        c.manager.requester = call -> call.callback.onAvailable(n);
        JSONObject request = new JSONObject().put("model", "offline").put("stream", false)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "original")));
        String frozen = request.toString();
        ChatCompletionClient client = new ChatCompletionClient(new AiSettings("https://offline.invalid/v1", "offline", "test-only"),
                null, SchedulePrompt.SYSTEM, new CellularClassroomRoute(c));
        client.completeConversation(request);
        MemoryConnection value = connection.get();
        check(c.manager.registered.isEmpty() && c.manager.releases.size() == 1 && value.disconnected, "actual client disconnects before releasing cellular lease");
        check(value.body.toString(StandardCharsets.UTF_8).equals(frozen) && request.toString().equals(frozen), "route does not alter frozen body/options");
        check(value.getRequestMethod().equals("POST") && !value.getInstanceFollowRedirects(), "POST and no-redirect policy retained");
        check(value.getRequestProperty("Authorization").equals("Bearer test-only")
                && value.getRequestProperty("Content-Type").equals("application/json; charset=utf-8"), "headers retained");
        check(value.getSSLSocketFactory() == value.initialTlsFactory && value.getHostnameVerifier() == value.initialVerifier,
                "selected network/client never replace platform TLS factory or hostname verifier");
        check(n.opens == 1 && c.manager.calls.size() == 1, "one successful exchange sends once");
    }

    private static Network network(Context c) {
        Network n = new Network(); n.opener = url -> new MemoryConnection(url, () -> { }); return n;
    }
    private static void setCellular(Context c, boolean enabled) {
        SchedulePreferences p = SchedulePreferences.load(c);
        new SchedulePreferences(p.workStartMinutes, p.workEndMinutes, p.customPrompt, p.classroomPrompt,
                p.enhancedClassroom, p.classroomConcurrency, p.classroomNoteStrength, enabled).save(c);
    }
    private static final class MemoryConnection extends javax.net.ssl.HttpsURLConnection {
        final ByteArrayOutputStream body = new ByteArrayOutputStream(); final Runnable during;
        final javax.net.ssl.SSLSocketFactory initialTlsFactory;
        final javax.net.ssl.HostnameVerifier initialVerifier;
        boolean disconnected;
        MemoryConnection(URL u, Runnable during) {
            super(u); this.during = during;
            initialTlsFactory = getSSLSocketFactory(); initialVerifier = getHostnameVerifier();
        }
        public String getCipherSuite() { return "offline-fixture"; }
        public java.security.cert.Certificate[] getLocalCertificates() { return null; }
        public java.security.cert.Certificate[] getServerCertificates() { return null; }
        public void connect() { during.run(); }
        public void disconnect() { during.run(); disconnected = true; }
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() { during.run(); return body; }
        public int getResponseCode() { during.run(); return 200; }
        public InputStream getInputStream() { during.run(); return new ByteArrayInputStream("{\"choices\":[]}".getBytes(StandardCharsets.UTF_8)) {
            public synchronized int read(byte[] b, int off, int len) { during.run(); return super.read(b, off, len); }
            public void close() { during.run(); }
        }; }
    }
    private interface Operation { ChatCompletionClient.OpenedConnection run() throws Exception; }
    private static final class Worker {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<ChatCompletionClient.OpenedConnection> opened = new AtomicReference<>();
        final AtomicBoolean interrupted = new AtomicBoolean(); final Thread thread;
        Worker(Operation operation) { thread = new Thread(() -> {
            try { opened.set(operation.run()); } catch (Throwable e) { failure.set(e); }
            finally { interrupted.set(Thread.currentThread().isInterrupted()); }
        }); thread.start(); }
        void join() throws Exception { thread.join(2000); check(!thread.isAlive(), "worker exits within bound"); }
    }
    private static Worker start(Operation operation) { return new Worker(operation); }
    private static void awaitCalls(Context c, int count) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (c.manager.calls.size() < count && System.nanoTime() < end) Thread.sleep(5);
        check(c.manager.calls.size() == count, "network request registered");
    }
    private interface Throwing { void run() throws Exception; }
    private static <T extends Throwable> T expect(Class<T> type, Throwing operation) throws Exception {
        try { operation.run(); } catch (Throwable failure) {
            check(type.isInstance(failure), "expected " + type.getSimpleName() + ", got " + failure);
            return type.cast(failure);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
