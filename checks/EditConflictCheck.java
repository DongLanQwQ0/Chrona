package com.donglan.chrona;
import com.donglan.chrona.data.EventCandidate;
public final class EditConflictCheck {
    static EventCandidate row(String note, Long calendar) {
        return new EventCandidate(1, 2, "标题", 100L, 200L, "UTC", "地点", note,
                5, false, calendar, "event", false, false, 0);
    }
    static void rejected(EventCandidate row, String expected) {
        try { CandidateEditBaseline.require(row, expected); throw new AssertionError("stale accepted"); }
        catch (IllegalStateException correct) { }
    }
    public static void main(String[] args) {
        EventCandidate phoneDraft = row("旧备注", null);
        String baseline = CandidateEditBaseline.of(phoneDraft);
        CandidateEditBaseline.require(row("旧备注", null), baseline);
        rejected(row("电脑新备注", null), baseline);
        rejected(row("旧备注", 33L), baseline);
        rejected(null, baseline);
        rejected(phoneDraft, "");
        String committed = CandidateEditBaseline.of(row("手机新备注", null));
        rejected(row("手机新备注", null), baseline);
        CandidateEditBaseline.require(row("手机新备注", null), committed);
        rejected(row(null, null), CandidateEditBaseline.of(row("", null)));
        LanNetworkIdentity bound = new LanNetworkIdentity(100, "192.168.1.2");
        if (!bound.matches(new LanNetworkIdentity(100, "192.168.1.2"))
                || bound.matches(new LanNetworkIdentity(101, "192.168.1.2"))
                || bound.matches(new LanNetworkIdentity(100, "192.168.1.3"))
                || bound.matches(null) || !bound.lost(100) || bound.lost(101))
            throw new AssertionError("network identity");
        System.out.println("EditConflictCheck passed: bidirectional stale baselines, notes, calendar link, deletion, successful new baseline, null distinction and same-IP network switch");
    }
}
