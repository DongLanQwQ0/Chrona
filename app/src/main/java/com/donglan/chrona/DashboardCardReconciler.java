package com.donglan.chrona;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps business rows alive while applying a new, detached local-data presentation. */
final class DashboardCardReconciler {
    private record Identity(String key, String state, boolean branch) { }
    private final Map<View, Identity> identities = new WeakHashMap<>();

    void branch(View view, String key) { identities.put(view, new Identity(key, "", true)); }
    void row(View view, String key, String state) {
        identities.put(view, new Identity(key, state, false));
    }

    void apply(ViewGroup target, ViewGroup incoming, int preservedPrefix) {
        Map<String, View> previous = new LinkedHashMap<>();
        for (int i = preservedPrefix; i < target.getChildCount(); i++) {
            View child = target.getChildAt(i);
            Identity identity = identities.get(child);
            if (identity != null) previous.put(identity.key, child);
        }
        List<View> desired = new ArrayList<>();
        while (incoming.getChildCount() > 0) {
            View next = incoming.getChildAt(0);
            incoming.removeViewAt(0);
            Identity identity = identities.get(next);
            View retained = identity == null ? null : previous.remove(identity.key);
            if (retained != null && retained.getClass() == next.getClass()) {
                Identity old = identities.get(retained);
                if (identity.branch || !identity.state.equals(old.state)) {
                    apply((ViewGroup) retained, (ViewGroup) next, 0);
                    identities.put(retained, identity);
                }
                retained.setLayoutParams(next.getLayoutParams());
                retained.setVisibility(next.getVisibility());
                desired.add(retained);
            } else desired.add(next);
        }
        // Do not detach a surviving row at its existing position. ChangeBounds can then
        // animate its real layout movement and accessibility focus keeps the same node.
        for (int i = target.getChildCount() - 1; i >= preservedPrefix; i--) {
            if (!desired.contains(target.getChildAt(i))) target.removeViewAt(i);
        }
        for (int i = 0; i < desired.size(); i++) {
            View child = desired.get(i);
            int position = preservedPrefix + i;
            if (position < target.getChildCount() && target.getChildAt(position) == child) continue;
            if (child.getParent() == target) target.removeView(child);
            target.addView(child, position);
        }
    }
}
