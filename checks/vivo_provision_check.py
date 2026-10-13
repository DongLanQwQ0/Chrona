"""Exercise the actual setup script with a fake ADB command; never accesses a phone."""
import os
import shutil
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/vivo-provision-check'
out.mkdir(parents=True, exist_ok=True)
apk = out / 'fixture.apk'
apk.write_bytes(b'offline fixture')
fake = out / 'fake-adb.ps1'
fake.write_text(r'''
$ErrorActionPreference = 'Stop'
$global:LASTEXITCODE = 0
if ($args[2] -eq 'install') {
    if ($env:CHRONA_FIXTURE -eq 'install_fail') { $global:LASTEXITCODE = 1; 'installation rejected'; return }
    'Success'; return
}
$enabled = if (($args -join ' ') -match 'disable_registered_scene') { 'false' } else { 'true' }
$scene = if ($env:CHRONA_FIXTURE -eq 'wrong_scene') { 'OTHER_SCENE' } else { 'CHRONA_AGENDA' }
if ($env:CHRONA_FIXTURE -eq 'wrong_enabled') { $enabled = 'false' }
'INSTRUMENTATION_RESULT: ui_report={"scene":"' + $scene + '","enabled":' + $enabled + '}'
if ($env:CHRONA_FIXTURE -eq 'unconfirmed') { 'INSTRUMENTATION_CODE: 0' } else { 'INSTRUMENTATION_CODE: -1' }
''', encoding='utf-8')
pwsh = shutil.which('pwsh')
assert pwsh, 'PowerShell 7 is required'
for mode, fixture, expected in [('Enable', 'ok', True), ('Disable', 'ok', True),
        ('Enable', 'install_fail', False), ('Enable', 'wrong_scene', False),
        ('Enable', 'wrong_enabled', False), ('Enable', 'unconfirmed', False)]:
    env = dict(os.environ, CHRONA_FIXTURE=fixture)
    result = subprocess.run([pwsh, '-NoProfile', '-NonInteractive', '-File',
        str(root / 'tools/vivo/Configure-VivoAtomicScene.ps1'), '-Device', 'OFFLINE_FIXTURE',
        '-Mode', mode, '-TestApk', str(apk), '-Adb', str(fake)], capture_output=True, text=True,
        encoding='utf-8', errors='replace', env=env)
    assert (result.returncode == 0) == expected, (mode, fixture, result.stdout, result.stderr)
    if expected:
        assert 'CHRONA_AGENDA' in result.stdout
print('Vivo provisioning script: 6 offline cases passed; explicit enable/disable, install failure, scene/state mismatch and unconfirmed instrumentation')

# Execute the actual enable branch with registration/readback failure doubles.
source = (root / 'app/src/androidTest/java/com/donglan/chrona/DeviceUiChecks.java').read_text(encoding='utf-8')
branch = source[source.index('        if ("enable_registered_scene"'):source.index('        if ("registered_atomic"')]
harness = r'''
class SceneCleanupCheck {
    static int scenario, calls;
    static final Object test = new Object();
    static class Arguments { String getString(String key) { return "enable_registered_scene"; } }
    static class JSONObject {
        boolean enabled;
        JSONObject put(String key, Object value) { if (key.equals("enabled")) enabled = (Boolean)value; return this; }
        public String toString() { return Boolean.toString(enabled); }
    }
    static boolean setAtomicScene(Object test, boolean enabled) throws Exception {
        calls++;
        if (calls == 1 && scenario >= 2) throw new Exception("readback");
        if (calls == 2 && scenario == 3) throw new Exception("cleanup");
        return enabled && scenario != 1;
    }
    static String run() throws Exception { Arguments arguments = new Arguments();
''' + branch + r'''
        throw new AssertionError("unexpected branch");
    }
    public static void main(String[] args) throws Exception {
        for (scenario = 0; scenario < 4; scenario++) {
            calls = 0;
            try {
                String result = run();
                if (scenario >= 2 || !result.equals(Boolean.toString(scenario == 0))) throw new AssertionError("result");
            } catch (Exception failure) {
                if (scenario < 2 || !failure.getMessage().equals("readback")
                    || failure.getSuppressed().length != (scenario == 3 ? 1 : 0)) throw new AssertionError("exception", failure);
            }
            if (calls != (scenario == 0 ? 1 : 2)) throw new AssertionError("cleanup count");
        }
        System.out.println("Scene enable: 4 production-branch cleanup cases passed");
    }
}
'''
(out / 'SceneCleanupCheck.java').write_text(harness, encoding='utf-8')
java_home = Path(os.environ.get('JAVA_HOME', r'D:\Minecraft\java21'))
subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-d', str(out),
    str(out / 'SceneCleanupCheck.java')], check=True)
subprocess.run([str(java_home / 'bin/java.exe'), '-cp', str(out), 'SceneCleanupCheck'], check=True)
