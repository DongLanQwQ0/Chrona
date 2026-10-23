import java.util.*;

/** The two production classes are inserted without rewriting their methods. */
// PRODUCTION_SOURCES

public final class AtomicCapsuleObserverCheck {
    static int checks;
    static final String OWN = "com.donglan.chrona", HOST = "com.vivo.systemuiplugin", HOME = "com.bbk.launcher2";
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static AtomicCapsuleAccessibilityService fresh() {
        Handler.pending.clear(); AtomicCapsuleController.clear();
        SystemClock.elapsed = 100_000; SystemClock.uptime = 50_000;
        VivoAtomicNotification.vivo = true;
        AtomicCapsuleAccessibilityService service = new AtomicCapsuleAccessibilityService();
        service.windows.add(new AccessibilityWindowInfo(1, 1, true, node(HOME, "home", null)));
        service.onServiceConnected(); return service;
    }
    static AccessibilityNodeInfo node(String pkg, String id, CharSequence text) {
        return new AccessibilityNodeInfo(pkg, id, text);
    }
    static CharSequence marker(int id, String identity) {
        Bundle data = new Bundle(); data.putParcelable("island.superx.customTemplate", new RemoteViews());
        AtomicCapsuleIdentity.attach(data, id, identity, "课程完整标题");
        RemoteViews views = data.getParcelable("island.superx.customTemplate");
        return views.text.get(identity.startsWith("homework:") ? R.id.homework_title : R.id.atomic_island_title);
    }
    static AccessibilityNodeInfo card(AtomicCapsuleAccessibilityService service, String id, CharSequence text) {
        AccessibilityNodeInfo root = node(HOST, "host-root", null);
        root.children.add(node(HOST, "unrelated-host-title", "Do not read this content"));
        AccessibilityNodeInfo title = node(OWN, id, text); root.children.add(title);
        service.windows.add(new AccessibilityWindowInfo(9, 3, false, root)); return title;
    }
    static AccessibilityEvent event(int type, String pkg, String sourceId) {
        return new AccessibilityEvent(type, pkg, 9, SystemClock.uptime - 200,
                sourceId == null ? null : node(pkg, sourceId, null));
    }
    static void home(AtomicCapsuleAccessibilityService service) {
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, HOME, null));
    }
    static void tap(AtomicCapsuleAccessibilityService service) {
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED, HOST, HOST + ":id/main_island_layout_right"));
    }
    static void content(AtomicCapsuleAccessibilityService service) {
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, HOST, null));
    }
    public static void main(String[] args) {
        var service = fresh();
        check(AtomicCapsuleController.desktops.equals(List.of(100_000L)), "connect on unlocked desktop starts one window");
        check(service.info.notificationTimeout == 100 && (service.info.flags & AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS) != 0,
                "real observer requests ids and a bounded event delivery interval");
        home(service); home(service); check(AtomicCapsuleController.desktops.size() == 1, "repeated launcher updates do not renew window");
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null, null));
        home(service); check(AtomicCapsuleController.desktops.size() == 1, "missing package cannot invent a desktop transition");
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "another.app", null));
        SystemClock.elapsed += 5000; home(service);
        check(AtomicCapsuleController.desktops.equals(List.of(100_000L,105_000L)), "returning from another app gets new desktop window");
        for (String pkg : List.of(HOST,"com.android.systemui","com.vivo.upslide"))
            service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,pkg,null));
        home(service); check(AtomicCapsuleController.desktops.size() == 2, "system overlays do not simulate leaving home");
        service.registeredScreen.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF)); service.keyguard.locked = true;
        home(service); check(AtomicCapsuleController.desktops.size() == 2,"locked home does not start reading window");
        service.keyguard.locked = false; service.power.awake = false;
        home(service); check(AtomicCapsuleController.desktops.size() == 2,"noninteractive home does not start reading window");
        service.power.awake = true; service.registeredScreen.onReceive(service,new Intent(Intent.ACTION_USER_PRESENT));
        check(AtomicCapsuleController.desktops.size() == 3,"unlocking to the actual desktop starts window");
        service.windows.set(0,new AccessibilityWindowInfo(1,1,true,node("another.app","root",null)));
        service.registeredScreen.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
        service.registeredScreen.onReceive(service,new Intent(Intent.ACTION_USER_PRESENT));
        check(AtomicCapsuleController.desktops.size() == 3,"unlocking into another app is not desktop entry");

        service = fresh();
        AccessibilityNodeInfo title = card(service,OWN+":id/atomic_island_title",marker(42,"entry-A"));
        tap(service);
        check(AtomicCapsuleController.clicks.equals(List.of("42:entry-A:99800")), "native tap is matched through host tree and carries original event time");
        check(title.textReads == 1,"only selected own title text is read");
        content(service); Handler.drain(); content(service);
        check(AtomicCapsuleController.clicks.size() == 1,"content updates and delayed scan never renew consumed click");
        SystemClock.elapsed += 1000; SystemClock.uptime += 1000; tap(service);
        check(AtomicCapsuleController.clicks.size() == 1,"clicks while the same card stays expanded do not renew deadline");
        var expanded=service.windows.remove(1);content(service);service.windows.add(expanded);tap(service);
        check(AtomicCapsuleController.clicks.size() == 2,"a new capsule expansion after actual close starts another window");
        check(title.recycles > 0,"traversed child nodes are released");

        service=fresh();card(service,OWN+":id/homework_title",marker(99,"homework:source-null"));
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,HOST,null));
        check(AtomicCapsuleController.clicks.size()==1,"ordinary vivo null-source click is matched only through own visible card");
        content(service);Handler.drain();service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,HOST,null));
        check(AtomicCapsuleController.clicks.size()==1,"null-source updates and close click cannot renew expanded card");
        expanded=service.windows.remove(1);
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOWS_CHANGED,null,null));
        service.windows.add(expanded);
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,HOST,null));
        check(AtomicCapsuleController.clicks.size()==2,"package-less close event releases expansion before the next null-source click");
        service=fresh();service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,HOST,null));
        check(AtomicCapsuleController.clicks.isEmpty(),"null-source click without Chrona marker does nothing");
        card(service,OWN+":id/atomic_island_title",marker(42,"different-window"));
        service.onAccessibilityEvent(new AccessibilityEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED,HOST,17,SystemClock.uptime,null));
        check(AtomicCapsuleController.clicks.size()==1,"expanded card may use a different window than original click event");

        service = fresh(); title = card(service,OWN+":id/homework_title",marker(99,"homework:expiry"));
        tap(service); check(AtomicCapsuleController.clicks.size() == 1 && AtomicCapsuleController.clicks.get(0).startsWith("99:homework:expiry:"),
                "homework title uses its own immutable marker without touching homework state");
        service = fresh(); title = card(service,OWN+":id/atomic_island_title",marker(42,"delayed")); title.visible=false;
        tap(service); check(AtomicCapsuleController.clicks.isEmpty(),"hidden title is not mistaken for expanded card");
        title.visible=true; SystemClock.elapsed+=500; SystemClock.uptime+=500; content(service);
        check(AtomicCapsuleController.clicks.equals(List.of("42:delayed:99800")),"animation delivery keeps the initial click time");

        service = fresh(); title = card(service,OWN+":id/atomic_island_title",marker(42,"late")); title.visible=false;
        tap(service); SystemClock.elapsed+=2500; title.visible=true; content(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"expired tap cannot attach to later unrelated card");
        service = fresh(); card(service,"foreign.app:id/title",marker(42,"foreign")); tap(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"foreign app card marker is ignored without reading its text");
        service = fresh(); card(service,OWN+":id/atomic_island_title","ordinary title without marker"); tap(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"plain title cannot impersonate marker");
        service = fresh(); card(service,OWN+":id/atomic_island_title",marker(42,"ours"));
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,"foreign.app","foreign.app:id/button"));
        check(AtomicCapsuleController.clicks.isEmpty(),"foreign app click is ignored");
        service.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED,HOST,HOST+":id/other_button"));
        check(AtomicCapsuleController.clicks.isEmpty(),"other system button is not a capsule click");
        service.windows.get(1).root.pkg="foreign.app"; tap(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"window id reuse cannot expose another package tree");
        service = fresh(); title=card(service,OWN+":id/atomic_island_title",marker(42,"cancelled"));title.visible=false;
        tap(service);service.onInterrupt();title.visible=true;Handler.drain();content(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"interrupt clears delayed click recognition");
        service.onDestroy(); check(service.unregistered==1,"destroy releases screen observer");

        service=fresh(); AccessibilityNodeInfo root=node(HOST,"root",null);
        for(int i=0;i<260;i++)root.children.add(node(HOST,"nontext-"+i,null));
        root.children.add(node(OWN,OWN+":id/atomic_island_title",marker(42,"past-limit")));
        service.windows.add(new AccessibilityWindowInfo(9,3,false,root));tap(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"host traversal is bounded by node budget");
        check(root.children.get(255).recycles==0,"nodes beyond budget are not obtained");
        service=fresh();root=node(HOST,"root",null);AccessibilityNodeInfo parent=root;
        for(int i=0;i<35;i++){var child=node(HOST,"nest-"+i,null);parent.children.add(child);parent=child;}
        parent.children.add(node(OWN,OWN+":id/atomic_island_title",marker(42,"too-deep")));
        service.windows.add(new AccessibilityWindowInfo(9,3,false,root));tap(service);
        check(AtomicCapsuleController.clicks.isEmpty(),"host traversal has a depth bound");

        var decoded=AtomicCapsuleIdentity.read(marker(77,"entry:with:separators"));
        check(decoded.id==77 && decoded.identity.equals("entry:with:separators"),"codec preserves full notification occurrence identity");
        check(AtomicCapsuleIdentity.read("not annotated")==null,"non-spanned title ignored");
        for(String value:List.of("bad","not-an-id\nidentity","42\n","\nidentity")){
            var bad=new SpannableString("title");bad.setSpan(new Annotation("chrona.capsule",value),0,5,0);
            check(AtomicCapsuleIdentity.read(bad)==null,"malformed marker ignored: "+value);
        }
        var unknown=new SpannableString("title");unknown.setSpan(new Annotation("other.key","42\nidentity"),0,5,0);
        check(AtomicCapsuleIdentity.read(unknown)==null,"unrelated annotation ignored");
        Bundle bundle=new Bundle();RemoteViews original=new RemoteViews();bundle.putParcelable("island.superx.customTemplate",original);
        AtomicCapsuleIdentity.attach(bundle,42,"x","课程标题");RemoteViews clone=bundle.getParcelable("island.superx.customTemplate");
        check(clone!=original && original.text.isEmpty(),"marker attachment clones template instead of mutating original");
        check(clone.text.get(R.id.atomic_island_title).toString().equals("课程标题"),"marker leaves visible title unchanged");
        Bundle missing=new Bundle();AtomicCapsuleIdentity.attach(missing,42,"x","title");check(missing.items.isEmpty(),"no invented custom template");

        service=fresh();check(!AtomicCapsuleAccessibilityService.enabled(service),"ungranted service is not enabled");
        service.manager.enabled.add(new AccessibilityServiceInfo(OWN+"/.AtomicCapsuleAccessibilityService"));
        check(AtomicCapsuleAccessibilityService.enabled(service),"enabled matching component recognized");
        VivoAtomicNotification.vivo=false;service=new AtomicCapsuleAccessibilityService();service.onServiceConnected();
        check(service.disabled && service.registeredScreen==null,"non-vivo service disables itself without observation");
        System.out.println("Atomic observer: PASS ("+checks+" production observer/marker checks; framework doubles)");
    }
}

class Context {
    static final int RECEIVER_NOT_EXPORTED=4;
    PowerManager power=new PowerManager();KeyguardManager keyguard=new KeyguardManager();AccessibilityManager manager=new AccessibilityManager();
    <T>T getSystemService(Class<T> type){return type.cast(type==PowerManager.class?power:type==KeyguardManager.class?keyguard:manager);}
    String getPackageName(){return "com.donglan.chrona";}
}
class DiagLog {static void add(Context context,String message){}}
class AccessibilityService extends Context {
    List<AccessibilityWindowInfo>windows=new ArrayList<>();AccessibilityServiceInfo info=new AccessibilityServiceInfo("");
    PackageManager pm=new PackageManager();BroadcastReceiver registeredScreen;int unregistered;boolean disabled;
    protected void onServiceConnected(){}public void onAccessibilityEvent(AccessibilityEvent e){}public void onInterrupt(){}public void onDestroy(){}
    void disableSelf(){disabled=true;}PackageManager getPackageManager(){return pm;}
    AccessibilityServiceInfo getServiceInfo(){return info;}void setServiceInfo(AccessibilityServiceInfo value){info=value;}
    List<AccessibilityWindowInfo>getWindows(){return windows;}
    void registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){registeredScreen=receiver;}
    void unregisterReceiver(BroadcastReceiver receiver){unregistered++;}
}
class AccessibilityServiceInfo {
    static final int FEEDBACK_ALL_MASK=-1,FLAG_REPORT_VIEW_IDS=16,FLAG_RETRIEVE_INTERACTIVE_WINDOWS=64,FLAG_INCLUDE_NOT_IMPORTANT_VIEWS=2;
    int eventTypes,flags;long notificationTimeout;String id;AccessibilityServiceInfo(String id){this.id=id;}String getId(){return id;}
}
class AccessibilityManager {List<AccessibilityServiceInfo>enabled=new ArrayList<>();List<AccessibilityServiceInfo>getEnabledAccessibilityServiceList(int feedback){return enabled;}}
class ComponentName {
    String id;ComponentName(Context c,Class<?>cls){id=c.getPackageName()+"/"+c.getPackageName()+"."+cls.getSimpleName();}
    static ComponentName unflattenFromString(String id){if(id==null||!id.contains("/"))return null;ComponentName c=new ComponentName(new Context(),AtomicCapsuleAccessibilityService.class);c.id=id.replace("/.","/"+id.substring(0,id.indexOf('/'))+".");return c;}
    @Override public boolean equals(Object other){return other instanceof ComponentName c&&id.equals(c.id);}
    @Override public int hashCode(){return id.hashCode();}
}
class Intent {static final String ACTION_MAIN="main",CATEGORY_HOME="home",ACTION_SCREEN_OFF="off",ACTION_USER_PRESENT="present";String action;Intent(String action){this.action=action;}Intent addCategory(String c){return this;}String getAction(){return action;}}
class IntentFilter {IntentFilter(String action){}void addAction(String action){}}
abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}
class PackageManager {ResolveInfo resolveActivity(Intent i,int flags){return new ResolveInfo();}}
class ResolveInfo {ActivityInfo activityInfo=new ActivityInfo();}class ActivityInfo {String packageName="com.bbk.launcher2";}
class Looper {static Looper getMainLooper(){return new Looper();}}
class Handler {static List<Runnable>pending=new ArrayList<>();Handler(Looper l){}void removeCallbacks(Runnable r){pending.remove(r);}void postDelayed(Runnable r,long delay){pending.add(r);}static void drain(){var copy=new ArrayList<>(pending);pending.clear();copy.forEach(Runnable::run);}}
class PowerManager {boolean awake=true;boolean isInteractive(){return awake;}}class KeyguardManager {boolean locked;boolean isKeyguardLocked(){return locked;}}
class SystemClock {static long elapsed,uptime;static long elapsedRealtime(){return elapsed;}static long uptimeMillis(){return uptime;}}
class AccessibilityEvent {
    static final int TYPE_WINDOW_STATE_CHANGED=1,TYPE_WINDOWS_CHANGED=2,TYPE_VIEW_CLICKED=4,TYPE_WINDOW_CONTENT_CHANGED=8;
    int type,window;String pkg;long time;AccessibilityNodeInfo source;
    AccessibilityEvent(int type,String pkg,int window,long time,AccessibilityNodeInfo source){this.type=type;this.pkg=pkg;this.window=window;this.time=time;this.source=source;}
    CharSequence getPackageName(){return pkg;}int getEventType(){return type;}int getWindowId(){return window;}long getEventTime(){return time;}AccessibilityNodeInfo getSource(){return source;}
}
class AccessibilityWindowInfo {static final int TYPE_APPLICATION=1;int id,type;boolean active;AccessibilityNodeInfo root;AccessibilityWindowInfo(int id,int type,boolean active,AccessibilityNodeInfo root){this.id=id;this.type=type;this.active=active;this.root=root;}int getType(){return type;}int getId(){return id;}boolean isActive(){return active;}AccessibilityNodeInfo getRoot(){return root;}}
class AccessibilityNodeInfo {
    String pkg,id;CharSequence text;boolean visible=true;int textReads,recycles;List<AccessibilityNodeInfo>children=new ArrayList<>();
    AccessibilityNodeInfo(String pkg,String id,CharSequence text){this.pkg=pkg;this.id=id;this.text=text;}
    String getViewIdResourceName(){return id;}CharSequence getPackageName(){return pkg;}boolean isVisibleToUser(){return visible;}
    CharSequence getText(){textReads++;if(!"com.donglan.chrona:id/atomic_island_title".equals(id)&&!"com.donglan.chrona:id/homework_title".equals(id))throw new AssertionError("Read unrelated content: "+id);return text;}
    int getChildCount(){return children.size();}AccessibilityNodeInfo getChild(int i){return children.get(i);}void recycle(){recycles++;}
}
class VivoAtomicNotification {static boolean vivo=true;static boolean supportedManufacturer(){return vivo;}}
class AtomicCapsuleController {static List<Long>desktops=new ArrayList<>();static List<String>clicks=new ArrayList<>();static void desktopEntered(Context c,long at){desktops.add(at);}static boolean capsuleClicked(Context c,int id,String identity,long at){clicks.add(id+":"+identity+":"+at);return true;}static void clear(){desktops.clear();clicks.clear();}}
class R {static class id {static int homework_title=1,atomic_island_title=2;}}
class Bundle {Map<String,Object>items=new HashMap<>();@SuppressWarnings("unchecked")<T>T getParcelable(String key){return (T)items.get(key);}void putParcelable(String key,Object value){items.put(key,value);}}
class RemoteViews {Map<Integer,CharSequence>text=new HashMap<>();public RemoteViews clone(){RemoteViews copy=new RemoteViews();copy.text.putAll(text);return copy;}void setTextViewText(int id,CharSequence value){text.put(id,value);}}
class Annotation {String key,value;Annotation(String key,String value){this.key=key;this.value=value;}String getKey(){return key;}String getValue(){return value;}}
interface Spanned extends CharSequence {int SPAN_EXCLUSIVE_EXCLUSIVE=33;<T>T[]getSpans(int start,int end,Class<T>type);}
class SpannableString implements Spanned {
    String value;List<Object>spans=new ArrayList<>();SpannableString(String text){value=text;}void setSpan(Object span,int start,int end,int flags){spans.add(span);}
    @SuppressWarnings("unchecked")public <T>T[]getSpans(int start,int end,Class<T>type){return spans.stream().filter(type::isInstance).map(type::cast).toArray(n->(T[])java.lang.reflect.Array.newInstance(type,n));}
    public int length(){return value.length();}public char charAt(int i){return value.charAt(i);}public CharSequence subSequence(int s,int e){return value.substring(s,e);}@Override public String toString(){return value;}
}
