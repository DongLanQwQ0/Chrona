[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$Device,
    [Parameter(Mandatory)][ValidateSet('Enable', 'Disable')][string]$Mode,
    [Parameter(Mandatory)][string]$TestApk,
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$scene = 'CHRONA_AGENDA'
$testPath = (Resolve-Path -LiteralPath $TestApk).Path
if (-not $PSCmdlet.ShouldProcess($Device, "$Mode Chrona's own $scene scene")) { return }

function Invoke-DeviceAdb {
    param([string[]]$Arguments)
    $output = & $Adb -s $Device @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    return $output
}

Invoke-DeviceAdb -Arguments @('install', '-r', $testPath) | Out-Host
$phase = if ($Mode -eq 'Enable') { 'enable_registered_scene' } else { 'disable_registered_scene' }
$result = Invoke-DeviceAdb -Arguments @('shell', 'am', 'instrument', '-w', '-e', 'check', 'ui',
    '-e', 'phase', $phase, 'com.donglan.chrona.test/com.donglan.chrona.LanAccessInstrumentation')
$reportLines = @($result | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: ui_report=' })
if ($reportLines.Count -ne 1 -or -not ($result -match '^INSTRUMENTATION_CODE: -1$')) {
    throw "Configuration was not confirmed. $($result -join [Environment]::NewLine)"
}
$report = ($reportLines[0] -replace '^INSTRUMENTATION_RESULT: ui_report=', '') | ConvertFrom-Json
$expected = $Mode -eq 'Enable'
if ($report.scene -ne $scene -or $report.enabled -isnot [bool] -or $report.enabled -ne $expected) {
    throw "The device did not confirm the requested Chrona scene state: $($reportLines[0])"
}
[pscustomobject]@{ Scene = $scene; Enabled = $report.enabled; Confirmed = $true }
