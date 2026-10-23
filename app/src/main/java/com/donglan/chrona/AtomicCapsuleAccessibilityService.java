package com.donglan.chrona;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

/** Observes presentation only. Never performs gestures/actions or opens/closes an OEM card. */
public final class AtomicCapsuleAccessibilityService extends AccessibilityService {
    private static final String HOST = "com.vivo.systemuiplugin";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String launcher = "", foreground = "";
    private long pendingTap = -1;
    private AtomicCapsuleIdentity expandedCard;
    private int expandedWindow = -1;
    private boolean registered;
    private final Runnable inspectAfterTap = this::inspectIsland;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) { foreground = ""; pendingTap = -1; expandedCard = null; }
            else inspectDesktop();
        }
    };

    static boolean enabled(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        ComponentName ours = new ComponentName(context, AtomicCapsuleAccessibilityService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if (ours.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        return false;
    }

    @Override protected void onServiceConnected() {
        if (!VivoAtomicNotification.supportedManufacturer()) { disableSelf(); return; }
        var home = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (home == null) return;
        launcher = home.activityInfo.packageName;
        // Window-state package metadata tracks leaving home; content is inspected only in HOST.
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED); registered = true;
        inspectDesktop();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED && expandedCard != null)
            inspectIsland();
        if (event.getPackageName() == null) return;
        String pkg = String.valueOf(event.getPackageName());
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !HOST.equals(pkg)
                && !"com.android.systemui".equals(pkg) && !"com.vivo.upslide".equals(pkg)) {
            if (launcher.equals(pkg)) enterDesktop();
            else foreground = pkg;
        }
        if (!HOST.equals(pkg)) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            boolean capsule = source == null;
            if (source != null) {
                String id = source.getViewIdResourceName(); source.recycle();
                capsule = id != null && id.startsWith(HOST + ":id/main_island_layout");
            }
            // Vivo's ordinary service delivers native capsule clicks with a null source.
            // The visible, marked Chrona card below is the identity check, not the source node.
            if (capsule && expandedCard == null) {
                pendingTap = SystemClock.elapsedRealtime() - Math.max(0, SystemClock.uptimeMillis() - event.getEventTime());
                handler.removeCallbacks(inspectAfterTap); handler.postDelayed(inspectAfterTap, 100);
            }
        }
        if (pendingTap >= 0 || expandedCard != null) inspectIsland();
    }

    private void enterDesktop() {
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (power == null || !power.isInteractive() || keyguard != null && keyguard.isKeyguardLocked()) return;
        if (!launcher.equals(foreground)) {
            foreground = launcher;
            AtomicCapsuleController.desktopEntered(this, SystemClock.elapsedRealtime());
        }
    }

    private void inspectDesktop() {
        for (var window : getWindows()) {
            if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive()) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            String pkg = String.valueOf(root.getPackageName()); root.recycle();
            if (launcher.equals(pkg)) enterDesktop(); else foreground = pkg;
            return;
        }
    }

    private void inspectIsland() {
        if (pendingTap >= 0 && SystemClock.elapsedRealtime() - pendingTap > 2000) pendingTap = -1;
        if (pendingTap < 0 && expandedCard == null) return;
        int[] remaining = {256};
        boolean unknownRoot = false;
        for (var window : getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) { if (window.getId() == expandedWindow) unknownRoot = true; continue; }
            try {
                if (!HOST.contentEquals(root.getPackageName())) continue;
                AtomicCapsuleIdentity identity = findOwnCard(root, 0, remaining);
                if (identity != null) {
                    if (expandedCard == null && pendingTap >= 0) {
                        long at = pendingTap; pendingTap = -1;
                        boolean accepted = AtomicCapsuleController.capsuleClicked(this, identity.id, identity.identity, at);
                        com.donglan.chrona.debug.DiagLog.add(this, "CAPSULE matched id=" + identity.id + " accepted=" + accepted);
                    }
                    expandedCard = identity;
                    expandedWindow = window.getId();
                    return;
                }
            } finally { root.recycle(); }
        }
        // Do not mistake a temporarily unavailable tree for a completed close/reopen cycle.
        if (!unknownRoot) { expandedCard = null; expandedWindow = -1; }
    }

    // RemoteViews ids belong to Chrona, not the host's Resources. Walk the observed
    // host tree rather than asking the host to resolve another package's resource id.
    private AtomicCapsuleIdentity findOwnCard(AccessibilityNodeInfo node, int depth, int[] remaining) {
        if (depth > 32 || remaining[0]-- <= 0) return null;
        String id = node.getViewIdResourceName();
        if (node.isVisibleToUser() && (getPackageName().concat(":id/atomic_island_title").equals(id)
                || getPackageName().concat(":id/homework_title").equals(id)))
            return AtomicCapsuleIdentity.read(node.getText());
        for (int i = 0; i < node.getChildCount() && remaining[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AtomicCapsuleIdentity identity;
            try { identity = findOwnCard(child, depth + 1, remaining); }
            finally { child.recycle(); }
            if (identity != null) return identity;
        }
        return null;
    }

    @Override public void onInterrupt() { pendingTap = -1; expandedCard = null; handler.removeCallbacks(inspectAfterTap); }
    @Override public void onDestroy() {
        onInterrupt();
        if (registered) unregisterReceiver(screen);
        super.onDestroy();
    }
}
