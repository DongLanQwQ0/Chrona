package com.donglan.chrona;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clips each occurrence to a local date and gives each overlap group equal-width lanes. */
final class CalendarTimelineLayout {
    static final class Block {
        final AgendaEntry entry;
        final double startMinute, endMinute;
        int lane, lanes;
        Block(AgendaEntry entry, double start, double end) {
            this.entry = entry; startMinute = start; endMinute = end;
        }
    }
    static List<Block> day(List<AgendaEntry> entries, LocalDate date, ZoneId zone) {
        long begin = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<Block> blocks = new ArrayList<>();
        for (AgendaEntry entry : entries) {
            if (entry.allDay || !entry.overlaps(begin, end)) continue;
            double start = entry.start <= begin ? 0 : minute(entry.start, zone);
            double finish = entry.finish() >= end ? 1440 : minute(entry.finish(), zone);
            // A fall-back clock hour can make wall-clock end precede start.
            blocks.add(new Block(entry, start, Math.min(1440, Math.max(start + 15, finish))));
        }
        blocks.sort(Comparator.comparingDouble((Block b) -> b.startMinute).thenComparingDouble(b -> b.endMinute));
        List<Block> group = new ArrayList<>();
        List<Double> laneEnds = new ArrayList<>();
        double groupEnd = -1;
        for (Block block : blocks) {
            if (block.startMinute >= groupEnd) {
                finishGroup(group, laneEnds.size()); group.clear(); laneEnds.clear();
            }
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > block.startMinute) lane++;
            if (lane == laneEnds.size()) laneEnds.add(block.endMinute);
            else laneEnds.set(lane, block.endMinute);
            block.lane = lane; group.add(block); groupEnd = Math.max(groupEnd, block.endMinute);
        }
        finishGroup(group, laneEnds.size());
        return blocks;
    }
    static double minute(long instant, ZoneId zone) {
        java.time.LocalTime time = Instant.ofEpochMilli(instant).atZone(zone).toLocalTime();
        return time.getHour() * 60 + time.getMinute() + time.getSecond() / 60.0;
    }
    private static void finishGroup(List<Block> group, int lanes) {
        for (Block block : group) block.lanes = Math.max(1, lanes);
    }
    private CalendarTimelineLayout() { }
}
