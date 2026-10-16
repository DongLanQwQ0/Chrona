package com.donglan.chrona;

import com.donglan.chrona.calendar.CalendarOccurrence;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Pure, conservative course identity rules. No source record is ever changed. */
public final class CourseSourceMatching {
    public static final long IMPORTED = -1, ZJU = -2;
    private static final Pattern CODE = Pattern.compile("(?i)(?:课程(?:编号|代码|号)|course\\s*(?:code|id))\\s*[:：]?\\s*([a-z0-9][a-z0-9._-]{3,})|(?<![a-z0-9])([a-z]{2,6}[- ]?\\d{3,8}|\\d{6,10})(?![a-z0-9])");
    public static String normalize(String title) {
        String value = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // NFKC converts Unicode Roman numerals; keep their level, as well as 甲/乙 and 实验/理论.
        value = value.replaceAll("[（(](?:教师|授课教师|任课教师|老师|地点|教室|教学地点|teacher|room)\\s*[:：][^()（）]*[)）]", "");
        return value.replaceAll("\\s+", "");
    }
    public static String code(String title) {
        Matcher matcher = CODE.matcher(Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKC));
        return matcher.find() ? (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)).toLowerCase(Locale.ROOT).replace(" ", "") : "";
    }
    public static String source(CalendarOccurrence c) {
        return c.calendarId == IMPORTED ? "imported" : c.calendarId == ZJU ? "zju" : "system:" + c.calendarId;
    }
    public static String identity(CalendarOccurrence c) { return source(c) + "\n" + normalize(c.title); }
    public static String pairKey(CalendarOccurrence a, CalendarOccurrence b) { return pairKey(identity(a), identity(b)); }
    public static String pairKey(String a, String b) { return a.compareTo(b) < 0 ? a + "\u001f" + b : b + "\u001f" + a; }
    public static boolean sameTime(CalendarOccurrence a, CalendarOccurrence b) {
        return a.startMillis == b.startMillis && a.endMillis == b.endMillis && a.allDay == b.allDay;
    }
    /** Exact display identity keeps teacher, group, course level and all other title metadata. */
    private static String exactText(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}\\u0085]+", "");
    }
    public static boolean exact(CalendarOccurrence a, CalendarOccurrence b) {
        return sameTime(a,b) && exactText(a.title).equals(exactText(b.title))
                && exactText(a.location).equals(exactText(b.location));
    }
    private static int sourceRank(CalendarOccurrence c) {
        return c.calendarId == ZJU ? 0 : c.calendarId == IMPORTED ? 1 : 2;
    }
    public static boolean conflict(CalendarOccurrence a, CalendarOccurrence b) {
        String al=normalize(a.location), bl=normalize(b.location);
        return (!al.isEmpty() && !bl.isEmpty() && !al.equals(bl)) || !group(a.title).equals(group(b.title));
    }
    private static String group(String title) {
        Matcher m=Pattern.compile("(?:班|组|分组|group)\\s*[:：]?\\s*[a-z0-9一二三四五六七八九十]+|[a-z0-9一二三四五六七八九十]+(?:班|组)").matcher(normalize(title));
        return m.find()?m.group():"";
    }
    private static String distinction(String title) {
        String n=normalize(title);
        Matcher m=Pattern.compile("甲|乙|丙|丁|实验|理论|(?:iii|ii|iv|i|v)(?=[(（0-9]|$)|[（(](?:i{1,3}|iv|v|1|2|3|4|5)[)）]").matcher(n);
        StringBuilder out=new StringBuilder(); while(m.find())out.append(m.group());return out.toString();
    }
    public static boolean strong(CalendarOccurrence a, CalendarOccurrence b) {
        if (source(a).equals(source(b)) || !sameTime(a,b) || conflict(a,b)) return false;
        String an=normalize(a.title),bn=normalize(b.title),ac=code(a.title),bc=code(b.title);
        return an.equals(bn) || (!ac.isEmpty() && ac.equals(bc) && distinction(a.title).equals(distinction(b.title)));
    }
    private static String suggestionName(String title) {
        return normalize(title).replaceAll("[（(][^()（）]*[)）]", "").replaceAll("[-_·:：]", "");
    }
    public static boolean suggested(CalendarOccurrence a, CalendarOccurrence b) {
        if(source(a).equals(source(b)) || a.allDay || b.allDay)return false;
        if(strong(a,b))return true;
        String x=suggestionName(a.title), y=suggestionName(b.title);
        boolean similar=x.equals(y)||(!code(a.title).isEmpty()&&code(a.title).equals(code(b.title)))
                ||(Math.min(x.length(),y.length())>=4&&(x.contains(y)||y.contains(x)));
        long week=7L*86400000L,delta=Math.abs(a.startMillis-b.startMillis);
        return similar && delta%week==0 && a.endMillis-a.startMillis==b.endMillis-b.startMillis;
    }
    /** Exact occurrences merge by default. Rules choose a source or "reject"; links apply across weeks. */
    public static List<CalendarOccurrence> filter(List<CalendarOccurrence> input,String primary,Map<String,String> rules) {
        // Stable traversal makes both exact representatives and contradictory manual rules
        // independent of provider/input order. A selected source wins only when present.
        List<CalendarOccurrence> ordered=new ArrayList<>(input);
        ordered.sort(Comparator.comparingInt((CalendarOccurrence c)->source(c).equals(primary)?0:1)
                .thenComparingInt(CourseSourceMatching::sourceRank).thenComparingLong(c->c.calendarId)
                .thenComparingLong(c->c.eventId).thenComparingLong(c->c.startMillis)
                .thenComparingLong(c->c.endMillis).thenComparing(c->c.title)
                .thenComparing(c->c.location).thenComparing(c->c.calendarName).thenComparing(c->c.accountName));
        Set<CalendarOccurrence> suppressed=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<CalendarOccurrence> protectedItems=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<CalendarOccurrence,CalendarOccurrence> preferredBy=new IdentityHashMap<>();
        Map<String,List<CalendarOccurrence>> slots=new HashMap<>();
        for(CalendarOccurrence c:ordered)slots.computeIfAbsent(c.startMillis+":"+c.endMillis+":"+c.allDay,k->new ArrayList<>()).add(c);
        // A third source must not indirectly swallow either side of an explicit rejection.
        for(List<CalendarOccurrence> slot:slots.values())for(int i=0;i<slot.size();i++)for(int j=i+1;j<slot.size();j++) {
            CalendarOccurrence a=slot.get(i),b=slot.get(j);
            if("reject".equals(rules.get(pairKey(a,b)))) {protectedItems.add(a);protectedItems.add(b);}
        }
        for(List<CalendarOccurrence> slot:slots.values())for(int i=0;i<slot.size();i++)for(int j=i+1;j<slot.size();j++) {
            CalendarOccurrence a=slot.get(i),b=slot.get(j);
            String decision=rules.get(pairKey(a,b));
            if("reject".equals(decision))continue;
            if(exact(a,b)) {
                CalendarOccurrence keep=a,drop=b;
                if(decision!=null && decision.equals(source(b)) && !decision.equals(source(a))) {keep=b;drop=a;}
                if(!protectedItems.contains(drop)){suppressed.add(drop);preferredBy.put(drop,keep);}
                continue;
            }
            if(source(a).equals(source(b)) || !sameTime(a,b))continue;
            String winner=decision;
            if(winner==null) {if(!strong(a,b)||"all".equals(primary))continue;winner=primary;}
            if(winner.equals(source(a))&&!protectedItems.contains(b)){suppressed.add(b);preferredBy.put(b,a);}
            else if(winner.equals(source(b))&&!protectedItems.contains(a)){suppressed.add(a);preferredBy.put(a,b);}
        }
        // A cycle of contradictory pair choices still leaves a visible representative.
        for(CalendarOccurrence c:ordered){
            Set<CalendarOccurrence> path=Collections.newSetFromMap(new IdentityHashMap<>());
            CalendarOccurrence current=c;
            while(suppressed.contains(current)){
                if(!path.add(current)){suppressed.remove(current);break;}
                current=preferredBy.get(current);
            }
        }
        List<CalendarOccurrence> result=new ArrayList<>();for(CalendarOccurrence c:input)if(!suppressed.contains(c))result.add(c);return result;
    }
    private CourseSourceMatching() { }
}
