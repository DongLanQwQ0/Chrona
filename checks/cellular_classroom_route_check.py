"""Run actual cellular route/client on thin Android JVM doubles. No device, AI or network I/O.

Callbacks model Android's contract; these checks do not establish carrier/VPN/device behavior.
Uses only the existing JDK and cached model/JSON dependencies.
"""
from pathlib import Path
import os
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/cellular-classroom-route-check'
fixtures = {
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 String getString(String k,String d); boolean getBoolean(String k,boolean d); Editor edit();
 interface Editor {Editor putString(String k,String v);Editor putBoolean(String k,boolean v);void apply();}
}''',
    'android/content/Context.java': '''package android.content;
import java.util.*;import android.net.ConnectivityManager;
public class Context {
 public static final int MODE_PRIVATE=0;public static final String CONNECTIVITY_SERVICE="connectivity";
 public ConnectivityManager manager=new ConnectivityManager();private final Map<String,Prefs> stores=new java.util.concurrent.ConcurrentHashMap<>();
 public Context getApplicationContext(){return this;}
 public Object getSystemService(String s){if(!CONNECTIVITY_SERVICE.equals(s))throw new AssertionError(s);return manager;}
 public SharedPreferences getSharedPreferences(String n,int m){return stores.computeIfAbsent(n,k->new Prefs());}
 static class Prefs implements SharedPreferences,SharedPreferences.Editor {
  private final Map<String,Object> values=new java.util.concurrent.ConcurrentHashMap<>();
  public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
  public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
  public Editor edit(){return this;}public Editor putString(String k,String v){values.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public void apply(){}
 }
}''',
    'android/net/NetworkCapabilities.java': '''package android.net;
public class NetworkCapabilities {
 public static final int TRANSPORT_CELLULAR=0,TRANSPORT_WIFI=1,TRANSPORT_VPN=4,NET_CAPABILITY_INTERNET=12;
 public final java.util.Set<Integer> transports=new java.util.HashSet<>(),capabilities=new java.util.HashSet<>();
 public boolean hasTransport(int t){return transports.contains(t);}
}''',
    'android/net/NetworkRequest.java': '''package android.net;
public class NetworkRequest {
 public final NetworkCapabilities networkCapabilities=new NetworkCapabilities();
 public static class Builder {private final NetworkRequest r=new NetworkRequest();
  public Builder addTransportType(int t){r.networkCapabilities.transports.add(t);return this;}
  public Builder addCapability(int c){r.networkCapabilities.capabilities.add(c);return this;}
  public NetworkRequest build(){return r;}}
}''',
    'android/net/Network.java': '''package android.net;
public class Network {
 public interface Opener {java.net.URLConnection open(java.net.URL u)throws java.io.IOException;}
 public Opener opener;public int opens;
 public java.net.URLConnection openConnection(java.net.URL u)throws java.io.IOException{opens++;return opener.open(u);}
}''',
    'android/net/ConnectivityManager.java': '''package android.net;
import java.util.*;
public class ConnectivityManager {
 public static class NetworkCallback {public void onAvailable(Network n){}public void onUnavailable(){}public void onLost(Network n){}}
 public static class Call {public final NetworkRequest request;public final NetworkCallback callback;public final int timeout;
  Call(NetworkRequest r,NetworkCallback c,int t){request=r;callback=c;timeout=t;}}
 public interface Requester {void request(Call c);}
 public final List<Call> calls=new java.util.concurrent.CopyOnWriteArrayList<>();
 public final Set<NetworkCallback> registered=java.util.concurrent.ConcurrentHashMap.newKeySet();
 public final List<NetworkCallback> releases=new java.util.concurrent.CopyOnWriteArrayList<>();
 public Requester requester=c->{};public Network active;public NetworkCapabilities activeCapabilities;
 public Network getActiveNetwork(){return active;}
 public NetworkCapabilities getNetworkCapabilities(Network n){return activeCapabilities;}
 public void requestNetwork(NetworkRequest r,NetworkCallback c,int timeout){Call call=new Call(r,c,timeout);calls.add(call);registered.add(c);requester.request(call);}
 public void unregisterNetworkCallback(NetworkCallback c){releases.add(c);if(!registered.remove(c))throw new IllegalArgumentException("not registered");}
}''',
}
paths = []
for relative, content in fixtures.items():
    path = out / 'stubs' / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths += [src / relative for relative in (
    'CellularClassroomRoute.java', 'ai/SchedulePreferences.java', 'ai/AiSettings.java',
    'ai/ChatCompletionClient.java', 'ai/ParseResult.java', 'ai/SchedulePrompt.java',
    'ai/SemanticEventNormalizer.java', 'ai/TimeAssessment.java', 'ai/RequestOptions.java',
    'ai/StreamChunkContent.java', 'ai/ResponsesProtocol.java', 'data/EventTimeDefaults.java', 'data/CandidateRules.java',
    'calendar/AllDayDates.java', 'net/RequestControl.java', 'net/NetworkFailure.java')]
paths += [root / 'checks' / name for name in (
    'CellularClassroomRouteCheck.java', 'AiRequestCheck.java', 'TimeAssessmentCheck.java')]
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar required; nothing is downloaded')

def executable(name):
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) if home else None
    if candidate and candidate.is_file():
        return str(candidate)
    found = shutil.which(name)
    if not found:
        raise RuntimeError(f'Existing {name} required')
    return found

classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run(recurrence_java([executable('javac'), '--release', '17', '-encoding', 'UTF-8',
               '-cp', str(jar), '-d', str(classes), *map(str, paths)]), check=True)
classpath = str(classes) + os.pathsep + str(jar)
for main in ('com.donglan.chrona.CellularClassroomRouteCheck', 'com.donglan.chrona.ai.AiRequestCheck'):
    subprocess.run(recurrence_java([executable('java'), '-cp', classpath, main]), check=True)
