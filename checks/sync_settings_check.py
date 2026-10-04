"""Execute production settings callbacks with offline UI/storage/network stand-ins."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/sync-settings-check"
OUT.mkdir(parents=True, exist_ok=True)
source = (ROOT / "app/src/main/java/com/donglan/chrona/SyncSettingsActivity.java").read_text(encoding="utf-8")


def method(marker):
    start = source.index(marker)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end]


methods = "\n".join(method(marker) for marker in (
    "private AndroidSync.Completion completion()",
    "private AndroidSync.Completion completion(String savedPassword)",
    "private void save(boolean test)",
    "private void resolve(AndroidSync.Pending record, int index, boolean removeCalendar)",
))
fixture = r'''
import java.lang.ref.WeakReference;
class SyncSettingsActivity {
    Field url = new Field("https://new.example/dav/"), user = new Field("new-user"), password = new Field("new-secret"), status = new Field("");
    boolean destroyed, finishing; int reloads, buttonUpdates;
    boolean isDestroyed() { return destroyed; } boolean isFinishing() { return finishing; }
    void updatePendingButtons() { buttonUpdates++; } void loadPending() { reloads++; }
    METHODS
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    static SyncSettingsActivity fresh() {
        AndroidSync.busy=false; AndroidSync.next=null; AndroidSync.callback=null;
        WebDavSettingsStore.saves=0; WebDavSettingsStore.fail=false; WebDavClient.fail=false;
        WebDavClient.tests=0; WebDavClient.secret=null; SyncJobService.schedules=0; Toast.shown=0;
        return new SyncSettingsActivity();
    }
    public static void main(String[] args) throws Exception {
        SyncSettingsActivity a=fresh(); a.save(true); AndroidSync.finish();
        check(WebDavSettingsStore.saves==0 && SyncJobService.schedules==0,"test never writes settings or schedules");
        check(WebDavClient.tests==1 && a.password.value.equals("new-secret"),"test preserves input for later save");
        a=fresh(); WebDavClient.fail=true; a.save(true); AndroidSync.finish();
        check(WebDavSettingsStore.saves==0 && SyncJobService.schedules==0 && a.password.value.equals("new-secret"),"failed test preserves settings and input");
        a=fresh(); a.password.setText(""); a.save(true); AndroidSync.finish();
        check("old-secret".equals(WebDavClient.secret) && WebDavSettingsStore.saves==0,"blank test password reuses saved secret");
        a=fresh(); a.user.setText(""); a.save(true); AndroidSync.finish();
        check(WebDavClient.tests==0 && a.status.value.equals("请输入账号和应用密码"),"empty account rejected");
        a=fresh(); a.save(false); check(a.password.value.equals("new-secret"),"save keeps password until completion"); AndroidSync.finish();
        check(WebDavSettingsStore.saves==1 && SyncJobService.schedules==1 && a.password.value.isEmpty(),"successful save persists and clears submitted password");
        a=fresh(); WebDavSettingsStore.fail=true; a.save(false); AndroidSync.finish();
        check(SyncJobService.schedules==0 && a.password.value.equals("new-secret"),"failed save retains password for retry");
        a=fresh(); a.save(false); a.password.setText("newer-secret"); AndroidSync.finish();
        check(a.password.value.equals("newer-secret"),"completion preserves edits made during save");
        a=fresh(); AndroidSync.busy=true; a.save(true);
        check(AndroidSync.next==null && a.status.value.contains("稍后测试") && a.password.value.equals("new-secret"),"busy test reports and retains input");
        a=fresh(); AndroidSync.busy=true; a.resolve(new AndroidSync.Pending(),-1,false);
        check(Toast.shown==1 && a.reloads==0 && a.buttonUpdates==1,"busy resolve reports without removing records");
        for(int index : new int[]{-1,0}) for(boolean calendar : new boolean[]{false,true}) {
            a=fresh(); a.resolve(new AndroidSync.Pending(),index,calendar);
            check(AndroidSync.index==index && AndroidSync.calendar==calendar && a.buttonUpdates==1,"resolve forwards choice and updates controls");
            AndroidSync.finish(); check(a.reloads==1 && a.buttonUpdates==2,"resolve completion reloads pending records");
        }
        a=fresh(); a.resolve(new AndroidSync.Pending(),0,true); AndroidSync.callback.done("操作失败");
        check(a.reloads==1 && a.status.value.equals("操作失败"),"failed resolve refreshes from authoritative persisted records");
        a=fresh(); a.save(false); a.destroyed=true; AndroidSync.finish();
        check(a.reloads==0 && a.password.value.equals("new-secret"),"destroyed activity ignored");
        a=fresh(); a.save(false); a.finishing=true; AndroidSync.finish(); check(a.reloads==0,"finishing activity ignored");
        System.out.println("Sync settings: 16 offline scenarios passed (production methods; UI/storage/network stand-ins)");
    }
}
class Field { String value; Field(String s){value=s;} Object getText(){return value;} void setText(String s){value=s;} }
class AndroidSync {
    interface Completion { void done(String result); } interface Work { String run(Object app) throws Exception; }
    static class Pending {} static boolean busy,calendar; static int index; static Work next; static Completion callback;
    static boolean work(Object owner,Completion completion,Work work) { if(busy)return false;busy=true;next=work;callback=completion;return true; }
    static boolean resolve(Object owner,Pending record,int i,boolean c,Completion completion) { index=i;calendar=c;return work(owner,completion,app -> "已确认，点击立即同步上传选择"); }
    static void finish() {String result;try{result=next.run(new Object());}catch(Exception ex){result=ex.getMessage();}busy=false;callback.done(result);}
}
class WebDavSettingsStore {
    static int saves; static boolean fail; WebDavSettingsStore(Object app){} boolean configured(){return true;} String password(){return "old-secret";}
    void save(String b,String u,String s)throws Exception {if(fail)throw new Exception("保存失败");saves++;}
}
class WebDavClient implements AutoCloseable {
    static boolean fail;static int tests; static String secret; WebDavClient(String b,String u,String s){secret=s;}
    void testConnection()throws Exception {tests++;if(fail)throw new Exception("测试失败");}public void close(){}
}
class SyncJobService {static int schedules;static void schedule(Object app){schedules++;}}
class Toast {static final int LENGTH_SHORT=0;static int shown;static Toast makeText(Object a,String s,int d){return new Toast();}void show(){shown++;}}
'''.replace("METHODS", methods)
target = OUT / "SyncSettingsActivity.java"
target.write_text(fixture, encoding="utf-8")
java = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(OUT), str(target)], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(OUT), "SyncSettingsActivity"], check=True)
