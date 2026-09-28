package com.donglan.chrona;

import android.content.SharedPreferences;
import com.donglan.chrona.data.TaskFileAttachment;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs against production selectors/serialization, without an Android device. */
public final class WidgetDraftCheck {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static WidgetAgenda.Item item(long id, long start, long end, boolean allDay) {
        return new WidgetAgenda.Item(id, 3, start, end, "事项", "地点", "活动", "拾时", false, allDay);
    }
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long begin = LocalDate.of(2026, 9, 29).atStartOfDay(zone).toInstant().toEpochMilli();
        long tomorrow = begin + 86400000L;
        long now = begin + 12 * 3600000L;
        WidgetAgenda agenda = new WidgetAgenda(now, zone);
        List<WidgetAgenda.Item> items = new ArrayList<>();
        items.add(item(1, begin - 86400000L, begin, true));
        items.add(item(2, begin - 3600000L, begin, false));
        items.add(item(3, begin + 3600000L, begin + 7200000L, false));
        items.add(item(4, now - 3600000L, now + 3600000L, false));
        items.add(item(5, tomorrow, tomorrow + 3600000L, false));
        items.add(item(6, begin, begin, false));
        agenda.select(items, begin, tomorrow, tomorrow + 86400000L);
        check(agenda.today.size() == 3, "exclusive midnight and tomorrow excluded; point event retained");
        check(agenda.next.id == 4, "ongoing preferred over future, past excluded");
        check(agenda.today.get(0).id == 6, "stable chronological ordering");
        check(items.get(4).time(now, zone).equals("11:00 — 13:00"), "local clock rendering");
        check(item(7, begin, tomorrow, true).time(now, zone).equals("全天"), "all-day rendering");
        check(item(8, tomorrow - 3600000L, tomorrow + 3600000L, false).time(now, zone)
                .equals("23:00 — 9月30日 01:00"), "cross-day end date retained");
        WidgetAgenda tomorrowOnly = new WidgetAgenda(now, zone);
        tomorrowOnly.select(new ArrayList<>(List.of(item(9, tomorrow, tomorrow + 1, false))),
                begin, tomorrow, tomorrow + 86400000L);
        check(tomorrowOnly.today.isEmpty() && tomorrowOnly.next.id == 9, "future is next, not today");
        WidgetAgenda empty = new WidgetAgenda(now, zone);
        empty.select(new ArrayList<>(List.of(item(10, tomorrow + 86400000L,
                tomorrow + 86400001L, false))), begin, tomorrow, tomorrow + 86400000L);
        check(empty.next == null, "horizon exclusive");
        WidgetAgenda bounded = new WidgetAgenda(now, zone);
        List<WidgetAgenda.Item> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) many.add(item(i, now + i, now + i + 1, false));
        bounded.select(many, begin, tomorrow, tomorrow + 86400000L);
        check(bounded.today.size() == WidgetAgenda.MAX_ITEMS, "large agenda stays bounded");
        Map<String, String> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                WidgetDraftCheck.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("putString")) values.put((String) arguments[0], (String) arguments[1]);
                    if (method.getName().equals("remove")) values.remove((String) arguments[0]);
                    return method.getName().equals("apply") ? null : proxy;
                });
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                WidgetDraftCheck.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> method.getName().equals("edit") ? editor
                        : values.getOrDefault((String) arguments[0], (String) arguments[1]));
        CaptureDraftStore store = new CaptureDraftStore(preferences);
        check(!store.hasDraft(), "no draft initially");
        TaskFileAttachment file = new TaskFileAttachment(0, 0, "file.txt", "作业.txt", "text/plain", 42);
        store.save("草稿\n含空格 and words", List.of("photo.jpg"), List.of(file));
        CaptureDraftStore.Draft restored = new CaptureDraftStore(preferences).load();
        check(store.hasDraft(), "persisted draft preferred to older instance-state");
        check(restored.text.equals("草稿\n含空格 and words"), "draft preserves whitespace and Unicode");
        check(restored.images.equals(List.of("photo.jpg")), "draft preserves photo reference");
        check(restored.files.size() == 1 && restored.files.get(0).displayName.equals("作业.txt")
                && restored.files.get(0).sizeBytes == 42, "file metadata round trip");
        store.clear();
        check(store.load().text.isEmpty() && store.load().images.isEmpty()
                && store.load().files.isEmpty(), "successful submit/discard clears draft");
        values.put("content", "broken JSON");
        check(store.load().text.isEmpty(), "corrupt draft does not block capture");
        System.out.println("Widget/draft checks passed: " + checks);
    }
}
