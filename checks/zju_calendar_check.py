"""Offline checks of the production ZJU calendar using synthetic course data.

Run with Python 3. Requires an existing JDK 17+ and cached org.json jar; downloads
nothing and never reads account files. Only Android Html is replaced for JVM use.
"""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-calendar-check'
out.mkdir(parents=True, exist_ok=True)
cached_json = root / 'build/ai-checks/json.jar'
if not cached_json.is_file():
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    jars = sorted((cache / 'caches/modules-2/files-2.1/org.json/json').glob('*/*/*.jar'))
    if not jars:
        raise RuntimeError('An existing org.json Gradle cache or build/ai-checks/json.jar is required')
    cached_json = jars[-1]

stub = out / 'android/text/Html.java'
stub.parent.mkdir(parents=True, exist_ok=True)
stub.write_text(r'''package android.text;
public final class Html {
    public static final int FROM_HTML_MODE_LEGACY = 0;
    public static String fromHtml(String html, int flags) {
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "");
        java.util.regex.Matcher numeric = java.util.regex.Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (numeric.find()) {
            String value = numeric.group(1);
            int code = Integer.parseInt(value.startsWith("x") ? value.substring(1) : value, value.startsWith("x") ? 16 : 10);
            numeric.appendReplacement(decoded, java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        numeric.appendTail(decoded);
        return decoded.toString().replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
''', encoding='utf-8')

def executable(name):
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = os.environ.get('JAVA_HOME')
    candidate = Path(java_home) / 'bin' / (name + suffix) if java_home else None
    if candidate and candidate.is_file():
        return str(candidate)
    located = shutil.which(name)
    if not located:
        raise RuntimeError(f'Existing {name} is required; no tools will be installed')
    return located

src = root / 'app/src/main/java/com/donglan/chrona/zju'
subprocess.run([executable('javac'), '--release', '17', '-encoding', 'UTF-8',
                '-cp', str(cached_json), '-d', str(out), str(stub),
                str(src / 'ZjuSnapshot.java'), str(src / 'ZjuAcademicCalendar.java'),
                str(root / 'checks/ZjuCalendarCheck.java')], check=True)
subprocess.run([executable('java'), '-cp', str(out) + os.pathsep + str(cached_json),
                'com.donglan.chrona.zju.ZjuCalendarCheck'], check=True)
