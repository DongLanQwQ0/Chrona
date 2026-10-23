package com.donglan.chrona;

import android.os.Bundle;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.widget.RemoteViews;

/** Nonvisual identity on our own title, preserved through RemoteViews and accessibility. */
final class AtomicCapsuleIdentity {
    private static final String KEY = "chrona.capsule";
    final int id;
    final String identity;
    private AtomicCapsuleIdentity(int id, String identity) { this.id = id; this.identity = identity; }

    static void attach(Bundle island, int id, String identity, String title) {
        RemoteViews original = island.getParcelable("island.superx.customTemplate");
        if (original == null || title.isEmpty()) return;
        RemoteViews card = original.clone();
        SpannableString text = new SpannableString(title);
        text.setSpan(new Annotation(KEY, id + "\n" + identity), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        card.setTextViewText(identity.startsWith("homework:") ? R.id.homework_title : R.id.atomic_island_title, text);
        island.putParcelable("island.superx.customTemplate", card);
    }

    static AtomicCapsuleIdentity read(CharSequence text) {
        if (!(text instanceof Spanned spans)) return null;
        for (Annotation annotation : spans.getSpans(0, spans.length(), Annotation.class)) {
            if (!KEY.equals(annotation.getKey())) continue;
            String value = annotation.getValue(); int separator = value.indexOf('\n');
            if (separator <= 0 || separator == value.length() - 1) return null;
            try { return new AtomicCapsuleIdentity(Integer.parseInt(value.substring(0, separator)), value.substring(separator + 1)); }
            catch (NumberFormatException invalid) { return null; }
        }
        return null;
    }
}
