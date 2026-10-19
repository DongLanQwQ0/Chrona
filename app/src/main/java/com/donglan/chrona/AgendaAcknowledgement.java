package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import com.donglan.chrona.data.EventCandidate;

/** Local acknowledgement belongs to one reminder occurrence, never the calendar event. */
final class AgendaAcknowledgement {
    static String key(EventCandidate candidate) {
        Long anchor = candidate.reminderAnchor();
        return anchor == null ? "" : "chrona:" + candidate.id + ":"
                + (candidate.occurrenceAtMillis == null ? anchor : candidate.occurrenceAtMillis);
    }

    static String key(long systemId, long start) { return "system:" + systemId + ":" + start; }

    static boolean isAcknowledged(Context context, String key) {
        return key != null && !key.isEmpty()
                && AgendaReminderPreferences.prefs(context).getLong("hidden:" + key, 0L) > System.currentTimeMillis();
    }

    static synchronized void set(Context context, String key, long until, boolean confirmed) {
        if (key == null || key.isEmpty()) return;
        SharedPreferences prefs = AgendaReminderPreferences.prefs(context);
        SharedPreferences.Editor edit = prefs.edit();
        if (confirmed) edit.putLong("hidden:" + key, until);
        else edit.remove("hidden:" + key).remove("snooze_until:" + key).remove("snooze_end:" + key);
        edit.putLong("acknowledgement_revision", prefs.getLong("acknowledgement_revision", 0L) + 1L).apply();
        AgendaReminder.requestRefresh(context);
    }

    private AgendaAcknowledgement() { }
}
