package com.donglan.chrona;

import java.util.List;

/** Keeps notification identity stable when chronological priority changes. */
final class ReminderSlots {
    static final int COUNT = 3;
    static int find(String[] keys, String key) {
        if (key == null || key.isEmpty()) return -1;
        for (int i = 0; i < keys.length; i++) if (key.equals(keys[i])) return i;
        return -1;
    }
    static AgendaEntry[] assign(List<AgendaEntry> entries, String[] previous) {
        AgendaEntry[] slots = new AgendaEntry[COUNT];
        for (AgendaEntry entry : entries) {
            int slot = find(previous, entry.key());
            if (slot >= 0 && slot < COUNT) slots[slot] = entry;
        }
        for (AgendaEntry entry : entries) {
            boolean retained = false;
            for (AgendaEntry slot : slots) if (slot != null && slot.key().equals(entry.key())) retained = true;
            if (retained) continue;
            for (int i = 0; i < COUNT; i++) if (slots[i] == null) { slots[i] = entry; break; }
        }
        return slots;
    }
    private ReminderSlots() { }
}
