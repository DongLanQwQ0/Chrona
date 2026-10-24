#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Device,
    [ValidateSet('Enable', 'Disable', 'Status')][string]$Mode = 'Enable',
    [string]$TestApk = (Join-Path $PSScriptRoot 'Chrona-vivo-setup.apk'),
    [string]$Adb
)
$ErrorActionPreference = 'Stop'
$Mode = @{ enable = 'Enable'; disable = 'Disable'; status = 'Status' }[$Mode.ToLowerInvariant()]
$package = 'com.donglan.chrona'
$helper = 'com.donglan.chrona.vivo.setup'
$scene = 'CHRONA_AGENDA'
$testPath = (Resolve-Path -LiteralPath $TestApk).Path
if (-not $PSCmdlet.ShouldProcess('连接的 vivo / iQOO 手机', "$Mode 拾时专属原子岛通知场景")) { return }

if (-not $Adb) {
    $bundled = Join-Path $PSScriptRoot 'platform-tools/adb.exe'
    $installed = Get-Command adb -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $bundled) { $Adb = $bundled }
    elseif ($installed) { $Adb = $installed.Source }
    else {
        throw '未找到 ADB。请从 https://developer.android.com/tools/releases/platform-tools 下载 Windows 工具，将 platform-tools 文件夹放在此脚本旁，再双击运行。'
    }
}
function Invoke-Adb {
    param([string[]]$Arguments)
    $global:LASTEXITCODE = 0
    $output = @(& $Adb @Arguments 2>&1 | ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    return $output
}
function Invoke-DeviceAdb {
    param([string[]]$Arguments)
    return Invoke-Adb -Arguments (@('-s', $Device) + $Arguments)
}

$devices = @(Invoke-Adb -Arguments @('devices', '-l') | ForEach-Object {
    if ($_ -match '^([^\s]+)\s+(device|offline|unauthorized)(?:\s|$)') {
        [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
    }
} | Where-Object { -not $_.Serial.StartsWith('emulator-') })
if ($Device) { $devices = @($devices | Where-Object { $_.Serial -eq $Device }) }
if (-not $devices.Count) { throw '未找到手机。请用数据线连接，开启 USB 调试；无线设备请先自行 adb connect，再用 -Device 指定。' }
if ($devices.Count -ne 1) { throw "检测到多台手机，请只连接一台，或用 -Device 指定：$($devices.Serial -join ', ')" }
$Device = $devices[0].Serial
if ($devices[0].State -eq 'unauthorized') { throw '手机尚未授权。请解锁手机，在 USB 调试提示中选择允许后重新运行。' }
if ($devices[0].State -ne 'device') { throw '手机处于离线状态，请重新连接后运行。' }
$manufacturer = (Invoke-DeviceAdb -Arguments @('shell', 'getprop', 'ro.product.manufacturer') | Out-String).Trim()
if ($manufacturer -ine 'vivo') { throw '此工具只支持 vivo / iQOO；当前手机未作任何配置。' }
$target = @(Invoke-DeviceAdb -Arguments @('shell', 'pm', 'list', 'packages', $package))
if ($target -notcontains "package:$package") { throw '请先安装 GitHub 正式版拾时，再运行配置。不要卸载原应用或清除数据。' }
$removeHelper = $target -notcontains "package:$helper"
$installedHelper = $false
Write-Host "设备：$Device。请先完成拾时正在进行的解析或同步；手机出现辅助程序安装提示时选择允许。"
try {
    $install = @(Invoke-DeviceAdb -Arguments @('install', '-r', '-t', $testPath))
    if (-not ($install -match '^Success\s*$')) { throw "辅助程序未安装成功：$($install -join ' ')" }
    $installedHelper = $true
    $result = @(Invoke-DeviceAdb -Arguments @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode,
        "$helper/com.donglan.chrona.VivoAtomicSetupInstrumentation"))
    $lines = @($result | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: setup_report=' })
    if ($lines.Count -ne 1 -or -not ($result -match '^INSTRUMENTATION_CODE: -1\s*$')) {
        throw "系统未确认配置；此固件可能不支持该接口。`n$($result -join [Environment]::NewLine)"
    }
    $report = ($lines[0] -replace '^INSTRUMENTATION_RESULT: setup_report=', '') | ConvertFrom-Json
    if ($report.package -ne $package -or $report.scene -ne $scene -or $report.mode -cne $Mode -or
        $report.enabled -isnot [bool] -or $report.before -isnot [bool] -or $report.changed -isnot [bool] -or
        ($Mode -ne 'Status' -and $report.enabled -ne ($Mode -eq 'Enable'))) {
        throw '配置回执不匹配，不能认定成功。'
    }
} finally {
    if ($installedHelper -and $removeHelper) {
        try {
            $removed = @(Invoke-DeviceAdb -Arguments @('uninstall', $helper))
            if (-not ($removed -match '^Success\s*$')) { throw '未确认辅助程序移除' }
        } catch { Write-Warning "辅助程序未能移除，可稍后在手机应用管理中卸载（不要卸载拾时）：$_" }
    }
}
if ($Mode -eq 'Enable') {
    Write-Host '配置已读回确认。可断开电脑，回到拾时开启原子岛提醒；仍需允许手机系统通知。'
} elseif ($Mode -eq 'Disable') { Write-Host '拾时专属原子岛场景已关闭，日程数据保留。' }
else { Write-Host "当前原子岛场景启用状态：$($report.enabled)" }
$report
