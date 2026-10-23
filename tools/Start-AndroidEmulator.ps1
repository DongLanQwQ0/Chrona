#Requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$RestartHeadless,
    [switch]$CheckOnly,
    [ValidateRange(320, 4320)][int]$Width = 1080,
    [ValidateRange(480, 7680)][int]$Height = 2376
)
$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:ANDROID_USER_HOME = 'F:\Android\user-home'
$env:ANDROID_AVD_HOME = 'F:\Android\Avd'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$avd = 'Chrona_Classroom_36'
$config = Join-Path $env:ANDROID_AVD_HOME "$avd.avd\config.ini"
$serial = 'emulator-5580'
foreach ($path in @($emulator, $adb, $config)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "找不到所需文件：$path" }
}

$running = @(Get-CimInstance Win32_Process -Filter "Name = 'emulator.exe'" |
    Where-Object { $_.CommandLine -match "-avd\s+$([regex]::Escape($avd))(?:\s|$)" })
$headless = @($running | Where-Object { $_.CommandLine -match '(?:^|\s)-no-window(?:\s|$)' })
if ($CheckOnly) {
    [pscustomobject]@{ Device = $avd; Resolution = "${Width}x${Height}"; Running = $running.Count -gt 0
        Headless = $headless.Count -gt 0; Keyboard = '启动时开启'; Data = Split-Path $config }
    return
}
if ($running.Count -gt 0 -and $headless.Count -eq 0) {
    Write-Host '安卓虚拟机已经在窗口模式运行，请在任务栏中打开它。'
    return
}
if ($headless.Count -gt 0) {
    if (-not $RestartHeadless) {
        $answer = Read-Host '虚拟机正在后台运行。保留数据并重启为可见窗口？输入 Y 确认'
        if ($answer -notmatch '^(?i:y|yes)$') { Write-Host '已保留后台实例。'; return }
    }
    $devices = @(& $adb devices | ForEach-Object { $_.Trim() })
    if (-not ($devices -match "^$serial\s+device$")) { throw '后台实例未连接就绪，未关闭它；请稍后重试。' }
    $name = & $adb -s $serial emu avd name
    if ($LASTEXITCODE -ne 0 -or $avd -notin @($name | ForEach-Object { $_.Trim() })) {
        throw '设备身份与目标虚拟机不一致，未关闭任何实例。'
    }
    & $adb -s $serial emu kill
    if ($LASTEXITCODE -ne 0) { throw '后台实例未正常退出，已停止启动流程。' }
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    do {
        $alive = @($headless | Where-Object { Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue })
        if ($alive.Count -eq 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($alive.Count -gt 0) { throw '后台实例仍在退出中；未强制终止，请稍后重试。' }
}

$text = [IO.File]::ReadAllText($config)
$updated = $text
foreach ($setting in @{
    'hw.keyboard' = 'yes'; 'hw.keyboard.lid' = 'no'; 'hw.initialOrientation' = 'portrait'
    'hw.lcd.width' = "$Width"; 'hw.lcd.height' = "$Height"; 'showDeviceFrame' = 'no'
}.GetEnumerator()) {
    $pattern = '(?m)^' + [regex]::Escape($setting.Key) + '=[^\r\n]*'
    $line = $setting.Key + '=' + $setting.Value
    if ([regex]::IsMatch($updated, $pattern)) { $updated = [regex]::Replace($updated, $pattern, $line) }
    else { $updated = $updated.TrimEnd() + "`r`n$line`r`n" }
}
if ($updated -cne $text) {
    Copy-Item -LiteralPath $config -Destination "$config.before-window-$(Get-Date -Format yyyyMMdd-HHmmss).bak"
    [IO.File]::WriteAllText($config, $updated, [Text.UTF8Encoding]::new($false))
}
$logs = Join-Path $env:ANDROID_USER_HOME 'emulator-logs'
[IO.Directory]::CreateDirectory($logs) | Out-Null
$stamp = Get-Date -Format yyyyMMdd-HHmmss
$process = Start-Process -FilePath $emulator -ArgumentList @('-avd', $avd, '-port', '5580',
    '-skin', "${Width}x${Height}", '-no-snapshot-load') -WindowStyle Normal -PassThru `
    -RedirectStandardOutput (Join-Path $logs "$stamp-out.log") -RedirectStandardError (Join-Path $logs "$stamp-error.log")
Write-Host "正在打开安卓虚拟机（${Width}×${Height}），首次启动请稍候……"
$deadline = [DateTime]::UtcNow.AddMinutes(3)
do {
    $process.Refresh()
    if ($process.HasExited) { throw "模拟器提前退出，请查看 $logs\$stamp-error.log" }
    $state = & $adb -s $serial get-state 2>$null
    if ($state -eq 'device') {
        $boot = [string](& $adb -s $serial shell getprop sys.boot_completed 2>$null)
        if ($boot.Trim() -eq '1') { break }
    }
    Start-Sleep -Seconds 1
} while ([DateTime]::UtcNow -lt $deadline)
if ([DateTime]::UtcNow -ge $deadline) { throw '启动尚未完成，窗口保留；请查看模拟器窗口及F盘日志。' }
& $adb -s $serial shell wm size "${Width}x${Height}"
if ($LASTEXITCODE -ne 0) { throw '虚拟机已启动，但分辨率设置失败。' }
Write-Host '已就绪。点击输入框后使用电脑键盘；所有原有应用和数据均保留。'
