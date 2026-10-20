package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real production service, consent Activity and notification touches; isolated emulator only. */
final class LanApprovalDeviceCheck {
    private final Instrumentation test;
    private final Context context;
    private final List<Activity> opened = new ArrayList<>();
    private final JSONArray screenshots = new JSONArray();
    private URI endpoint;
    private boolean ownsService;
    private boolean shadeOpened;
    private final String theme;
    private LanApprovalDeviceCheck(Instrumentation test, String theme) {
        this.test = test; context = test.getTargetContext(); this.theme = theme;
    }
    static String run(Instrumentation test, Bundle args) throws Exception {
        return new LanApprovalDeviceCheck(test, args.getString("theme", "light")).run(args);
    }
    private String run(Bundle args) throws Exception {
        require("com.donglan.chrona.followup.validation.v33".equals(context.getPackageName()), "Isolated followup v33 package only");
        require(("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))
                && (Build.MODEL.startsWith("sdk_gphone") || Build.MODEL.startsWith("Android SDK built for"))
                && (Build.FINGERPRINT.contains("google/") || Build.FINGERPRINT.contains("generic/")
                || Build.FINGERPRINT.startsWith("Android/sdk_phone64_")), "Official Android emulator only");
        require(theme.equals("light") || theme.equals("dark"), "Explicit light or dark theme");
        require(!LanAccessService.running() && !LanAccessService.starting(), "Existing LAN session; fixture refused");
        ZjuSettingsStore school = new ZjuSettingsStore(context);
        require(!school.enabled() && ((!school.configured() && school.account().isEmpty())
                || "00000000-0000-0000-0000-000000000036".equals(school.account())),
                "Only disabled unconfigured school integration or the known synthetic fixture account");
        WebDavSettingsStore sync = new WebDavSettingsStore(context);
        require(!sync.configured() && !sync.automatic(), "No configured or automatic external sync");
        require(context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)
                != android.content.pm.PackageManager.PERMISSION_GRANTED, "Calendar Provider writes must be denied");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        require(manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED), "Grant test package notifications before running");
        require(!hasApprovalNotification(), "Existing approval notification; fixture refused");
        SharedPreferences appearance = context.getSharedPreferences("appearance", 0);
        SharedPreferences guide = context.getSharedPreferences("usage_guide", 0);
        Object oldMode = appearance.getAll().get("mode"), oldRevision = appearance.getAll().get("revision");
        Object oldGuide = guide.getAll().get("version");
        Instrumentation.ActivityMonitor monitor = test.addMonitor(LanPairingActivity.class.getName(), null, false);
        try {
            java.lang.reflect.Field guideVersion = GuideActivity.class.getDeclaredField("GUIDE_VERSION");
            guideVersion.setAccessible(true);
            require(guide.edit().putInt("version", guideVersion.getInt(null)).commit(), "Suppress first-use guide in fixture");
            ThemeStore.setMode(context, theme);
            Activity home = test.startActivitySync(new Intent(context, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
            opened.add(home);
            await(() -> home.hasWindowFocus(), 10000, "dashboard foreground");
            startService();
            JSONObject first = create();
            Activity consent = consent(monitor);
            verifyIdentity(consent, first);
            expect(http("GET", "/api/session", null, null), 401);
            state(first, "pending");
            checkGeometry(consent);
            capture("foreground");
            if ("expiry".equals(args.getString("scope"))) {
                long began = SystemClock.elapsedRealtime();
                SystemClock.sleep(first.getLong("expiresIn") * 1000L + 1100);
                state(first, "expired");
                await(consent::isDestroyed, 5000, "expired consent closes");
                require(!hasApprovalNotification(), "Expired request leaves no notification");
                return report("expiry").put("elapsedMillis", SystemClock.elapsedRealtime() - began).toString();
            }
            touchButton(consent, "允许访问");
            Reply accepted = state(first, "approved");
            String cookie = cookie(accepted);
            expect(http("GET", "/api/session", null, cookie), 200);
            Reply replay = state(first, "expired");
            require(replay.setCookie == null, "Claim replay cannot issue another cookie");
            await(consent::isDestroyed, 5000, "accepted consent closes");
            await(home::hasWindowFocus, 5000, "home resumes after consent");

            JSONObject denied = create();
            Activity denyScreen = consent(monitor); verifyIdentity(denyScreen, denied);
            touchButton(denyScreen, "拒绝");
            require(state(denied, "denied").setCookie == null, "Denial issues no cookie");
            expect(http("GET", "/api/session", null, null), 401);
            await(denyScreen::isDestroyed, 5000, "denied consent closes");
            await(home::hasWindowFocus, 5000, "home resumes after denial");

            JSONObject cancelled = create();
            Activity cancelScreen = consent(monitor);
            expect(http("POST", "/api/approval/cancel", handle(cancelled), null), 200);
            state(cancelled, "expired");
            await(cancelScreen::isDestroyed, 5000, "browser cancellation closes native consent");
            await(home::hasWindowFocus, 5000, "home resumes after cancel");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
            await(() -> !home.hasWindowFocus(), 5000, "launcher visible");
            int priorHits = monitor.getHits();
            JSONObject background = create();
            await(this::hasApprovalNotification, 5000, "8757 approval notification posted");
            SystemClock.sleep(1400);
            require(monitor.getHits() == priorHits && !home.hasWindowFocus(), "Background request cannot steal foreground");
            state(background, "pending");
            shadeOpened = true;
            shell("cmd statusbar expand-notifications");
            touchNotification();
            Activity notificationScreen = consent(monitor); verifyIdentity(notificationScreen, background);
            capture("notification");
            touchButton(notificationScreen, "允许访问");
            String backgroundCookie = cookie(state(background, "approved"));
            expect(http("GET", "/api/session", null, backgroundCookie), 200);
            await(notificationScreen::isDestroyed, 5000, "notification consent closes");

            // The final pending request is deliberately backgrounded so shutdown also exercises notification removal.
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
            SystemClock.sleep(500);
            JSONObject stopped = create();
            await(this::hasApprovalNotification, 5000, "shutdown fixture has pending notification");
            stopService();
            require(LanAccessService.pendingApproval() == null && !hasApprovalNotification(), "Shutdown clears pending approval and notification");
            boolean disconnected = false;
            try { http("GET", "/api/session", null, cookie); } catch (IOException expected) { disconnected = true; }
            require(disconnected, "Shutdown closes listening socket");
            // Restart only the service created here; old credentials must not survive its lifecycle.
            test.runOnMainSync(() -> home.startActivity(new Intent(context, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await(home::hasWindowFocus, 5000, "foreground before restart");
            startService();
            expect(http("GET", "/api/session", null, cookie), 401);
            expect(http("GET", "/api/session", null, backgroundCookie), 401);
            state(stopped, "expired");
            return report("main").put("foregroundConsent", true).put("denied", true).put("browserCancel", true)
                    .put("notificationRealTouch", true).put("noBackgroundLaunch", true)
                    .put("singleCookieClaim", true).put("stopRevokes", true).toString();
        } finally {
            try { if (shadeOpened) shell("cmd statusbar collapse"); }
            finally {
                try {
                    if (ownsService) stopService();
                } finally {
                    try {
                        test.runOnMainSync(() -> { for (Activity screen : opened) if (!screen.isDestroyed() && !screen.isFinishing()) screen.finish(); });
                    } finally {
                        test.removeMonitor(monitor);
                        restore(guide, "version", oldGuide);
                        restore(appearance, "mode", oldMode); restore(appearance, "revision", oldRevision);
                    }
                }
            }
        }
    }
    private JSONObject report(String scope) throws Exception {
        return new JSONObject().put("passed", true).put("scope", scope).put("theme", theme)
                .put("productionService", true).put("realHttp", true).put("screenshots", screenshots)
                .put("fontScale", context.getResources().getConfiguration().fontScale)
                .put("screenWidthDp", context.getResources().getConfiguration().screenWidthDp);
    }
    private void startService() throws Exception {
        require(!LanAccessService.running() && !LanAccessService.starting(), "No unrelated service before fixture start");
        ownsService = true;
        test.runOnMainSync(() -> LanAccessService.start(context));
        await(LanAccessService::running, 10000, "production LAN startup: " + LanAccessService.status());
        endpoint = new URI(LanAccessService.url());
        require("http".equals(endpoint.getScheme()) && endpoint.getHost() != null && endpoint.getPort() == LanAccessService.PORT,
                "Use actual production LAN binding");
    }
    private void stopService() throws Exception {
        test.runOnMainSync(() -> LanAccessService.stop(context));
        await(() -> !LanAccessService.running() && !LanAccessService.starting(), 5000, "fixture LAN shutdown");
        SystemClock.sleep(300); ownsService = false;
    }
    private JSONObject create() throws Exception {
        Reply reply = http("POST", "/api/approval/request", new JSONObject(), null); expect(reply, 200);
        require(reply.setCookie == null && reply.body.getString("id").matches("[0-9a-f]{64}")
                && reply.body.getString("verification").matches("[0-9]{4}") && reply.body.getLong("expiresIn") == 120,
                "Request returns only a pending 256-bit handle and comparison code");
        return reply.body;
    }
    private static JSONObject handle(JSONObject request) throws Exception { return new JSONObject().put("id", request.getString("id")); }
    private Reply state(JSONObject request, String expected) throws Exception {
        Reply reply = http("POST", "/api/approval/poll", handle(request), null); expect(reply, 200);
        require(expected.equals(reply.body.getString("state")), "Expected approval state " + expected + ": " + reply.body);
        return reply;
    }
    private Activity consent(Instrumentation.ActivityMonitor monitor) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        Activity candidate = null;
        do {
            Activity observed = monitor.getLastActivity();
            if (observed instanceof LanPairingActivity && !opened.contains(observed)) candidate = observed;
            if (candidate != null) {
                Activity screen = candidate;
                boolean[] ready = {false};
                test.runOnMainSync(() -> ready[0] = !screen.isDestroyed() && !screen.isFinishing()
                        && screen.hasWindowFocus() && !opened.contains(screen));
                if (ready[0]) {
                    opened.add(screen);
                    SystemClock.sleep(600);
                    return screen;
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out: new live LanPairingActivity with window focus from production lifecycle");
    }
    private void verifyIdentity(Activity screen, JSONObject request) throws Exception {
        String code = request.getString("verification");
        test.runOnMainSync(() -> {
            View decor = screen.getWindow().getDecorView();
            require(findText(decor, "Chrome · Windows") != null, "Consent displays browser device identity");
            require(findText(decor, endpoint.getHost()) != null, "Consent displays actual socket peer IP");
            require(findText(decor, code) != null && findText(decor, "核对码") != null, "Consent comparison code matches HTTP");
        });
    }
    private void checkGeometry(Activity screen) {
        test.runOnMainSync(() -> {
            TextView title = findText(screen.getWindow().getDecorView(), "浏览器授权");
            require(title != null, "Shared settings header title");
            LinearLayout header = (LinearLayout)title.getParent(), content = (LinearLayout)header.getParent();
            float density = screen.getResources().getDisplayMetrics().density;
            int gutter = Math.round(20 * density), gap = Math.round(16 * density);
            require(content.getPaddingLeft() == gutter && content.getPaddingRight() == gutter, "Shared 20dp content gutters");
            View first = content.getChildAt(content.indexOfChild(header) + 1);
            require(first.getTop() - header.getBottom() >= gap, "Shared header leaves at least 16dp before identity");
            for (String label : new String[]{"拒绝", "允许访问"}) {
                TextView button = findText(content, label);
                require(button instanceof Button && button.getWidth() > 0 && button.getHeight() > 0, "Button laid out: " + label);
                require(button.getLeft() >= 0 && button.getRight() <= ((View)button.getParent()).getWidth(), "Button remains within content: " + label);
            }
        });
    }
    private void touchButton(Activity screen, String label) throws Exception {
        final View[] target = {null};
        test.runOnMainSync(() -> {
            target[0] = findText(screen.getWindow().getDecorView(), label);
            require(target[0] instanceof Button, "Actual consent button " + label);
            target[0].requestRectangleOnScreen(new Rect(0, 0, target[0].getWidth(), target[0].getHeight()), true);
        });
        SystemClock.sleep(400);
        Rect bounds = new Rect();
        test.runOnMainSync(() -> require(target[0].getGlobalVisibleRect(bounds)
                && bounds.height() == target[0].getHeight() && bounds.width() == target[0].getWidth(), "Complete button scrolls into viewport: " + label));
        touch(bounds.centerX(), bounds.centerY());
    }
    private void touchNotification() throws Exception {
        AccessibilityServiceInfo info = test.getUiAutomation().getServiceInfo();
        int originalFlags = info.flags;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        test.getUiAutomation().setServiceInfo(info);
        try {
            long deadline = SystemClock.elapsedRealtime() + 7000;
            do {
                // An expanded shade is an interactive SystemUI window even when launcher stays active.
                for (AccessibilityWindowInfo window : test.getUiAutomation().getWindows()) {
                    AccessibilityNodeInfo root = window.getRoot();
                    try {
                        if (root != null && "com.android.systemui".equals(String.valueOf(root.getPackageName()))) {
                            Rect target = notificationBounds(root);
                            if (target != null) { touchSystem(target.centerX(), target.centerY()); return; }
                        }
                    } finally { if (root != null) root.recycle(); window.recycle(); }
                }
                AccessibilityNodeInfo active = test.getUiAutomation().getRootInActiveWindow();
                try {
                    if (active != null) {
                        Rect target = notificationBounds(active);
                        if (target != null) { touchSystem(target.centerX(), target.centerY()); return; }
                    }
                } finally { if (active != null) active.recycle(); }
                SystemClock.sleep(150);
            } while (SystemClock.elapsedRealtime() < deadline);
            capture("shade-failure");
            File diagnostic = shadeDiagnostics();
            throw new AssertionError("Approval notification title is not touch-reachable in actual system shade; diagnostics: " + diagnostic);
        } finally {
            AccessibilityServiceInfo restore = test.getUiAutomation().getServiceInfo();
            restore.flags = originalFlags; test.getUiAutomation().setServiceInfo(restore);
        }
    }
    private Rect notificationBounds(AccessibilityNodeInfo root) {
        return notificationBounds(root, new int[]{0}, 0);
    }
    private Rect notificationBounds(AccessibilityNodeInfo node, int[] visited, int depth) {
        if (++visited[0] > 1000 || depth > 64) return null;
        if (("浏览器请求连接".equals(String.valueOf(node.getText()))
                || "浏览器请求连接".equals(String.valueOf(node.getContentDescription()))) && node.isVisibleToUser()) {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) return bounds;
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < 1000; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) try {
                Rect result = notificationBounds(child, visited, depth + 1);
                if (result != null) return result;
            } finally { child.recycle(); }
        }
        return null;
    }
    private File shadeDiagnostics() throws Exception {
        JSONArray windows = new JSONArray();
        for (AccessibilityWindowInfo window : test.getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            try {
                JSONObject entry = new JSONObject().put("id", window.getId()).put("type", window.getType())
                        .put("active", window.isActive()).put("focused", window.isFocused());
                JSONArray nodes = new JSONArray();
                if (root != null) {
                    entry.put("package", String.valueOf(root.getPackageName()));
                    collectShadeNodes(root, nodes, new int[]{0});
                }
                windows.put(entry.put("nodes", nodes));
            } finally { if (root != null) root.recycle(); window.recycle(); }
        }
        File file = new File(context.getExternalFilesDir(null), "lan-approval-" + theme + "-shade-failure.json");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { out.write(windows.toString(2)); }
        android.util.Log.e("ChronaDeviceCheck", "LAN approval shade windows: " + windows);
        return file;
    }
    private static void collectShadeNodes(AccessibilityNodeInfo node, JSONArray result, int[] visited) throws Exception {
        if (++visited[0] > 1000 || result.length() >= 100) return;
        CharSequence text = node.getText(), description = node.getContentDescription();
        if (text != null || description != null) {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            String label = String.valueOf(text == null ? description : text);
            result.put(new JSONObject().put("text", label.substring(0, Math.min(160, label.length())))
                    .put("bounds", bounds.toShortString()).put("visible", node.isVisibleToUser())
                    .put("class", String.valueOf(node.getClassName())));
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < 1000 && result.length() < 100; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) try { collectShadeNodes(child, result, visited); } finally { child.recycle(); }
        }
    }
    private void touch(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        try { test.sendPointerSync(down); SystemClock.sleep(70); test.sendPointerSync(up); } finally { down.recycle(); up.recycle(); }
        test.waitForIdleSync();
    }
    private void touchSystem(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            require(test.getUiAutomation().injectInputEvent(down, true), "System notification touch down injected");
            SystemClock.sleep(70);
            require(test.getUiAutomation().injectInputEvent(up, true), "System notification touch up injected");
        } finally { down.recycle(); up.recycle(); }
        test.waitForIdleSync();
    }
    private boolean hasApprovalNotification() {
        for (android.service.notification.StatusBarNotification notification : context.getSystemService(NotificationManager.class).getActiveNotifications())
            if (notification.getId() == 8757 && context.getPackageName().equals(notification.getPackageName())) return true;
        return false;
    }
    private void capture(String stage) throws Exception {
        File folder = context.getExternalFilesDir(null); require(folder != null, "External screenshot folder");
        File image = new File(folder, "lan-approval-" + theme + "-" + stage + ".png");
        Bitmap bitmap = test.getUiAutomation().takeScreenshot(); require(bitmap != null, "Actual display screenshot");
        try (FileOutputStream output = new FileOutputStream(image)) { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "Screenshot saved"); }
        finally { bitmap.recycle(); }
        screenshots.put(image.getAbsolutePath());
    }
    private void shell(String command) throws IOException {
        try (ParcelFileDescriptor fd = test.getUiAutomation().executeShellCommand(command);
                InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buffer = new byte[1024]; while (input.read(buffer) >= 0) { }
        }
    }
    private void await(BooleanSupplier condition, long timeout, String label) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeout; boolean[] ready = {false};
        do { test.runOnMainSync(() -> ready[0] = condition.getAsBoolean()); if (ready[0]) return; SystemClock.sleep(50); }
        while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out: " + label);
    }
    private static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView)root).getText())) return (TextView)root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) {
            TextView result = findText(((ViewGroup)root).getChildAt(i), text); if (result != null) return result;
        }
        return null;
    }
    private static String cookie(Reply reply) {
        require(reply.setCookie != null && reply.setCookie.contains("HttpOnly") && reply.setCookie.contains("SameSite=Strict")
                && reply.setCookie.contains("Path=/"), "Approved response has protected session cookie");
        String result = reply.setCookie.split(";", 2)[0];
        require(result.matches("chrona_session=[0-9a-f]{64}"), "High-entropy session cookie"); return result;
    }
    private Reply http(String method, String path, JSONObject body, String cookie) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 5000); socket.setSoTimeout(5000);
            byte[] payload = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
            StringBuilder headers = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: " + endpoint.getRawAuthority()
                    + "\r\nConnection: close\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0) Chrome/130.0\r\n");
            if (body != null) headers.append("Origin: ").append(endpoint.toString()).append("\r\nContent-Type: application/json\r\nContent-Length: ").append(payload.length).append("\r\n");
            if (cookie != null) headers.append("Cookie: ").append(cookie).append("\r\n");
            headers.append("\r\n"); OutputStream out = socket.getOutputStream();
            out.write(headers.toString().getBytes(StandardCharsets.US_ASCII)); out.write(payload); out.flush();
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            int status = Integer.parseInt(line(input).split(" ")[1]), length = -1; String header, setCookie = null;
            while (!(header = line(input)).isEmpty()) {
                String lower = header.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("content-length:")) length = Integer.parseInt(header.substring(15).trim());
                if (lower.startsWith("set-cookie:")) setCookie = header.substring(11).trim();
            }
            require(length >= 0 && length <= 1024 * 1024, "Bounded HTTP response");
            byte[] bytes = new byte[length]; int offset = 0;
            while (offset < length) { int count = input.read(bytes, offset, length - offset); if (count < 0) throw new EOFException(); offset += count; }
            return new Reply(status, new JSONObject(new String(bytes, StandardCharsets.UTF_8)), setCookie);
        }
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(); int next;
        while ((next = input.read()) != -1 && next != '\n') { if (next != '\r') result.write(next); if (result.size() > 8192) throw new IOException("HTTP header too long"); }
        if (next == -1) throw new EOFException(); return result.toString("US-ASCII");
    }
    private static final class Reply {
        final int status; final JSONObject body; final String setCookie;
        Reply(int status, JSONObject body, String setCookie) { this.status = status; this.body = body; this.setCookie = setCookie; }
    }
    private static void restore(SharedPreferences preferences, String key, Object value) {
        SharedPreferences.Editor edit = preferences.edit();
        if (value == null) edit.remove(key); else if (value instanceof String) edit.putString(key, (String)value);
        else if (value instanceof Integer) edit.putInt(key, (Integer)value); else throw new AssertionError("Unexpected preference type");
        require(edit.commit(), "Restore fixture preference " + key);
    }
    private static void expect(Reply reply, int expected) { require(reply.status == expected, "HTTP " + expected + " expected, got " + reply.status); }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
