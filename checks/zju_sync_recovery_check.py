"""Exercise production school fetch and credential recovery with isolated JVM fixtures.

All HTTP responses are intercepted; no account files, school network or device are used.
"""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-sync-recovery-check'
src = out / 'src'

def write(path, content):
    target = src / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')

write('android/text/Html.java', '''package android.text;public class Html {public static final int FROM_HTML_MODE_LEGACY=0;public static String fromHtml(String s,int flags){return s.replaceAll("<[^>]*>","");}}''')
write('android/content/SharedPreferences.java', '''package android.content;public interface SharedPreferences {boolean contains(String k);boolean getBoolean(String k,boolean d);String getString(String k,String d);long getLong(String k,long d);Editor edit();interface Editor {Editor putString(String k,String v);Editor putBoolean(String k,boolean v);Editor putLong(String k,long v);Editor remove(String k);boolean commit();void apply();}}''')
write('android/content/Context.java', '''package android.content;public class Context {public static final int MODE_PRIVATE=0;public final Prefs prefs=new Prefs();public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}
public static class Prefs implements SharedPreferences,SharedPreferences.Editor {public java.util.Map<String,Object> values=new java.util.HashMap<>();public boolean contains(String k){return values.containsKey(k);}public boolean getBoolean(String k,boolean d){return (boolean)values.getOrDefault(k,d);}public String getString(String k,String d){return (String)values.getOrDefault(k,d);}public long getLong(String k,long d){return (long)values.getOrDefault(k,d);}public Editor edit(){return this;}public Editor putString(String k,String v){values.put(k,v);return this;}public Editor putLong(String k,long v){values.put(k,v);return this;}public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public Editor remove(String k){values.remove(k);return this;}public boolean commit(){return true;}public void apply(){}}}''')
write('android/util/Base64.java', '''package android.util;public class Base64 {public static final int NO_WRAP=2;public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}public static String encodeToString(byte[] b,int flags){return java.util.Base64.getEncoder().encodeToString(b);}}''')
write('android/security/keystore/KeyProperties.java', '''package android.security.keystore;public class KeyProperties {public static final String KEY_ALGORITHM_AES="AES",BLOCK_MODE_GCM="GCM",ENCRYPTION_PADDING_NONE="NoPadding";public static final int PURPOSE_ENCRYPT=1,PURPOSE_DECRYPT=2;}''')
write('android/security/keystore/KeyGenParameterSpec.java', '''package android.security.keystore;public class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {public static class Builder {public Builder(String a,int p){}public Builder setBlockModes(String...v){return this;}public Builder setEncryptionPaddings(String...v){return this;}public KeyGenParameterSpec build(){return new KeyGenParameterSpec();}}}''')

cache = Path(os.environ.get('GRADLE_USER_HOME', 'F:/Android/GradleCache')) / 'caches/modules-2/files-2.1'
jars = [root / 'build/ai-checks/json.jar']
for group, module in [('com.squareup.okhttp3', 'okhttp'), ('com.squareup.okio', 'okio-jvm'), ('org.jetbrains.kotlin', 'kotlin-stdlib')]:
    jars += sorted(p for p in (cache / group / module).glob('*/*/*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1:]
if len(jars) != 4 or not all(p.is_file() for p in jars):
    raise RuntimeError('Existing JSON/OkHttp/Okio/Kotlin jars required; downloads nothing')
classpath = os.pathsep.join(map(str, jars))
production = root / 'app/src/main/java/com/donglan/chrona'
sources = list(src.rglob('*.java')) + [production / p for p in
    ['ZjuSettingsStore.java', 'zju/ZjuClient.java', 'zju/ZjuCalendarConfig.java', 'zju/ZjuAcademicCalendar.java', 'zju/ZjuSnapshot.java']]
sources += [root / 'checks/ZjuSyncRecoveryCheck.java']
sources += [production / 'net/NetworkFailure.java']
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(out), *map(str, sources)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out) + os.pathsep + classpath, 'com.donglan.chrona.ZjuSyncRecoveryCheck'], check=True)
