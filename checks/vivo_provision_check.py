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
Add-Content -LiteralPath $env:CHRONA_TRACE -Value ($args -join ' ') -Encoding utf8
$fixture = $env:CHRONA_FIXTURE
if ($args[0] -eq 'devices') {
    'List of devices attached'
    if ($fixture -eq 'no_device') { return }
    if ($fixture -eq 'emulator_only') { 'emulator-5554 device product:fixture'; return }
    $state = if ($fixture -in @('unauthorized','offline')) { $fixture } else { 'device' }
    "OFFLINE_FIXTURE $state product:fixture"
    if ($fixture -eq 'multi') { 'OTHER_PHONE device product:fixture' }
    return
}
if ($args[2] -eq 'install') {
    if ($fixture -eq 'install_fail') { $global:LASTEXITCODE = 1; 'installation rejected'; return }
    if ($fixture -eq 'install_unconfirmed') { 'pending'; return }
    'Success'; return
}
if ($args[2] -eq 'uninstall') {
    if ($fixture -eq 'cleanup_fail') { $global:LASTEXITCODE = 1; 'cleanup failed'; return }
    'Success'; return
}
if ($args[3] -eq 'getprop') { if ($fixture -eq 'non_vivo') { 'other' } else { 'vivo' }; return }
if ($args[3] -eq 'pm') {
    if ($fixture -ne 'missing_target') { 'package:com.donglan.chrona' }
    if ($fixture -eq 'existing_helper') { 'package:com.donglan.chrona.vivo.setup' }
    return
}
$mode = $args[[array]::IndexOf($args, 'mode') + 1]
$enabled = $mode -ne 'Disable'
$report = @{ package='com.donglan.chrona'; scene='CHRONA_AGENDA'; mode=$mode; before=$true; enabled=$enabled; changed=($mode -eq 'Disable') }
if ($fixture -eq 'wrong_scene') { $report.scene = 'OTHER_SCENE' }
if ($fixture -eq 'wrong_package') { $report.package = 'another.app' }
if ($fixture -eq 'wrong_mode') { $report.mode = 'Disable' }
if ($fixture -eq 'wrong_enabled') { $report.enabled = $false }
if ($fixture -eq 'string_enabled') { $report.enabled = 'true' }
if ($fixture -eq 'malformed') { 'INSTRUMENTATION_RESULT: setup_report=invalid'; return }
if ($fixture -ne 'missing_report') { 'INSTRUMENTATION_RESULT: setup_report=' + ($report | ConvertTo-Json -Compress) }
if ($fixture -eq 'unconfirmed') { 'INSTRUMENTATION_CODE: 0' } else { 'INSTRUMENTATION_CODE: -1' }
''', encoding='utf-8')
pwsh = shutil.which('pwsh')
assert pwsh, 'PowerShell 7 is required'
cases = [('Enable','ok',True), ('Disable','ok',True), ('Status','ok',True), ('enable','ok',True),
         ('Enable','existing_helper',True), ('Enable','cleanup_fail',True)]
preflight = ('no_device','emulator_only','unauthorized','offline','non_vivo','missing_target')
failures = preflight + ('install_fail','install_unconfirmed','wrong_scene','wrong_package','wrong_mode',
                       'wrong_enabled','string_enabled','malformed','missing_report','unconfirmed')
cases += [('Enable', fixture, False) for fixture in failures]
cases += [('Enable','multi',False), ('Enable','multi_selected',True), ('Enable','whatif',True)]
for i, (mode, fixture, expected) in enumerate(cases):
    trace = out / f'trace-{i}.txt'
    trace.write_text('', encoding='utf-8')
    env = dict(os.environ, CHRONA_FIXTURE='multi' if fixture=='multi_selected' else fixture, CHRONA_TRACE=str(trace))
    args = [pwsh, '-NoProfile', '-NonInteractive', '-File',
            str(root / 'tools/vivo/Configure-VivoAtomicScene.ps1'),
            '-Mode', mode, '-TestApk', str(apk), '-Adb', str(fake)]
    if fixture=='multi_selected': args += ['-Device','OFFLINE_FIXTURE']
    if fixture=='whatif': args += ['-WhatIf']
    result = subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace', env=env)
    assert (result.returncode == 0) == expected, (mode, fixture, result.stdout, result.stderr)
    calls = trace.read_text(encoding='utf-8-sig')
    if fixture in preflight or fixture=='multi': assert ' install ' not in calls, (fixture,calls)
    if fixture=='whatif': assert not calls
    elif fixture=='existing_helper': assert 'uninstall' not in calls
    elif fixture not in preflight + ('multi','install_fail','install_unconfirmed'):
        assert 'uninstall com.donglan.chrona.vivo.setup' in calls, (fixture,calls)
    if fixture in ('install_fail','install_unconfirmed'): assert 'instrument' not in calls
    assert 'uninstall com.donglan.chrona\n' not in calls
print(f'Vivo setup script: {len(cases)} offline cases passed; preflight, auto-device, modes, strict readback, cleanup and WhatIf')

# Exercise the shipped double-click entrypoint under Windows, including this repository's spaced path.
launcher = root / 'tools/vivo/Enable-VivoAtomic.cmd'
for fixture, expected in [('ok',0),('install_fail',1)]:
    trace = out / f'launcher-{fixture}.txt'
    trace.write_text('',encoding='utf-8')
    env = dict(os.environ,CHRONA_FIXTURE=fixture,CHRONA_TRACE=str(trace))
    command = f'cmd.exe /d /c ""{launcher}" -TestApk "{apk}" -Adb "{fake}""'
    result = subprocess.run(command,input='\n',capture_output=True,text=True,
                            encoding='utf-8',errors='replace',env=env)
    assert result.returncode==expected,(fixture,result.stdout,result.stderr)
    assert 'install' in trace.read_text(encoding='utf-8-sig')
print('Double-click launcher: 2 offline runs passed from a spaced Chinese path; success and failure exit codes')

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
