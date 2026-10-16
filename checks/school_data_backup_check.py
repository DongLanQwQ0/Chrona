"""Execute portable school backup/binding logic with synthetic identities and Android storage doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/school-data-backup-check'
out.mkdir(parents=True, exist_ok=True)
files = {
'android/content/SharedPreferences.java': '''package android.content;import java.util.*;
public class SharedPreferences {final Map<String,Object> map=new HashMap<>();public Map<String,?> getAll(){return new HashMap<>(map);}
public String getString(String k,String d){return (String)map.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)map.getOrDefault(k,d);}
public Editor edit(){return new Editor();}public class Editor {public Editor putString(String k,String v){map.put(k,v);return this;}public Editor putBoolean(String k,boolean v){map.put(k,v);return this;}public Editor remove(String k){map.remove(k);return this;}public boolean commit(){return true;}}}''',
'android/content/Context.java': '''package android.content;import java.util.*;import java.io.*;public class Context {
public static int MODE_PRIVATE=0;private final Map<String,SharedPreferences> prefs=new HashMap<>();final File dir;
public Context(File f){dir=f;f.mkdirs();}public File getFilesDir(){return dir;}public SharedPreferences getSharedPreferences(String n,int m){return prefs.computeIfAbsent(n,k->new SharedPreferences());}}''',
'android/util/AtomicFile.java': '''package android.util;import java.io.*;public class AtomicFile {final File f;public AtomicFile(File f){this.f=f;}public File getBaseFile(){return f;}
public byte[] readFully()throws IOException{return java.nio.file.Files.readAllBytes(f.toPath());}public FileOutputStream startWrite()throws IOException{return new FileOutputStream(f);}
public void finishWrite(FileOutputStream s)throws IOException{s.close();}public void failWrite(FileOutputStream s)throws IOException{s.close();}public void delete(){f.delete();}}''',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;import android.content.*;import java.util.*;public class ZjuSettingsStore {
final SharedPreferences prefs;ZjuSettingsStore(Context c){prefs=c.getSharedPreferences("school",0);}boolean configured(){return !prefs.getString("user","").isEmpty();}
String[] credentials(){return new String[]{prefs.getString("user",""),"synthetic-secret"};}String account(){return prefs.getString("account","");}
static String identityKey(String salt,String user){return "identity:"+UUID.nameUUIDFromBytes((salt+"\\0"+user.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}''',
'com/donglan/chrona/SchoolBackupCheck.java': '''package com.donglan.chrona;import android.content.*;import java.io.*;import java.util.*;import org.json.*;import com.donglan.chrona.zju.ZjuSnapshot;
public class SchoolBackupCheck {static int checks;static void check(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
public static void main(String[] args)throws Exception {
Context original=new Context(new File(args[0],"original")),fresh=new Context(new File(args[0],"fresh"));
String account=UUID.randomUUID().toString(),salt=UUID.randomUUID().toString(),identity=ZjuSettingsStore.identityKey(salt,"synthetic-user");
ZjuSettingsStore settings=new ZjuSettingsStore(original);settings.prefs.edit().putString("identity_salt",salt).putString(identity,account).putString("user","synthetic-user").putString("password","private-password").putBoolean("enabled",true).putBoolean("homework",true).putBoolean("timetable",true).commit();
new ZjuTimetableStore(original).save(account,List.of(new ZjuSnapshot.Lesson("lesson","课程","教室","term",100,200)),List.of("term"));
JSONObject data=SchoolDataBackup.export(original);String text=data.toString();
check(!text.contains("synthetic-user")&&!text.contains("private-password")&&!text.contains("synthetic-secret"),"credentials excluded");
SchoolDataBackup.apply(fresh,data);check(new ZjuSettingsStore(fresh).prefs.getString(identity,"").equals(account),"new-device login can reuse restored capture namespace");
check(new ZjuTimetableStore(fresh).load(account).size()==1,"cached lessons restored");
check(!new ZjuSettingsStore(fresh).prefs.getBoolean("enabled",true),"automatic sync remains off");
check(new ZjuSettingsStore(fresh).prefs.getBoolean("homework",false),"homework option retained");
new ZjuSettingsStore(fresh).prefs.edit().putString("user","synthetic-user").putString("account",UUID.randomUUID().toString()).commit();
SchoolDataBackup.apply(fresh,data);check(new ZjuSettingsStore(fresh).account().equals(account),"existing same-user credentials rebind to restored capture");
String other=UUID.randomUUID().toString();new ZjuSettingsStore(fresh).prefs.edit().putString("user","other-user").putString("account",other).commit();
SchoolDataBackup.apply(fresh,data);check(new ZjuSettingsStore(fresh).account().equals(other),"different user does not inherit restored account");
JSONObject broken=new JSONObject(text).put("homework","true");try{SchoolDataBackup.apply(fresh,broken);throw new AssertionError("invalid option accepted");}catch(JSONException expected){}
check(new ZjuSettingsStore(fresh).account().equals(other)&&new ZjuTimetableStore(fresh).load(account).size()==1,"invalid input makes no writes");
new ZjuTimetableStore(fresh).restore(null);check(new ZjuTimetableStore(fresh).load(account).isEmpty(),"empty cache clears previous cache");
new ZjuTimetableStore(fresh).restore(data.getJSONObject("timetableCache"));check(new ZjuTimetableStore(fresh).load(account).size()==1,"cache rollback restores previous snapshot");
System.out.println("School backup: "+checks+" checks passed; synthetic accounts, no credentials accessed");}}
'''
}
paths=[]
for name, text in files.items():
    path=out/name
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(text,encoding='utf-8')
    paths.append(path)
paths += [src/'SchoolDataBackup.java',src/'ZjuTimetableStore.java',src/'zju/ZjuSnapshot.java']
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
cp=str(root/'build/ai-checks/json.jar')
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out)+os.pathsep+cp,'com.donglan.chrona.SchoolBackupCheck',str(out)],check=True)
backup=(src/'ChronaDataBackup.java').read_text(encoding='utf-8')
assert 'SchoolDataBackup.validate(manifest.getJSONObject("schoolData"))' in backup
assert 'SchoolDataBackup.apply(context,previousSchoolData)' in backup
assert 'options(previousSchoolEnabled, previousSchoolData.getBoolean("timetable"))' in backup
