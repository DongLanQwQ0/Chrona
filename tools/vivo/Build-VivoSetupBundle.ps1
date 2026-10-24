#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PlatformTools,
    [string]$HelperApk,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$config = Get-Content -LiteralPath (Join-Path $root 'app/build.gradle') -Raw
if ($config -notmatch "versionName\s+'(\d+\.\d+\.\d+)'") { throw 'Cannot determine the source version.' }
$version = $Matches[1]
if (-not $HelperApk) { $HelperApk = Join-Path $root 'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $root 'build/distributions' }
$helper = (Resolve-Path -LiteralPath $HelperApk).Path
$tools = (Resolve-Path -LiteralPath $PlatformTools).Path
$name = "Chrona-vivo-setup-$version"
$output = [IO.Path]::GetFullPath($OutputDirectory)
$archive = Join-Path $output "$name.zip"
if (Test-Path -LiteralPath $archive) { throw "Output already exists; choose another OutputDirectory: $archive" }
$runtime = @('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll', 'NOTICE.txt', 'source.properties')
foreach ($file in $runtime) {
    if (-not (Test-Path -LiteralPath (Join-Path $tools $file) -PathType Leaf)) { throw "Missing platform tool: $file" }
}
$stage = Join-Path $root ("build/vivo-setup-bundles/" + [guid]::NewGuid().ToString('N') + "/$name")
New-Item -ItemType Directory -Path (Join-Path $stage 'platform-tools') -Force | Out-Null
New-Item -ItemType Directory -Path $output -Force | Out-Null
foreach ($file in @('Configure-VivoAtomicScene.ps1', 'Enable-VivoAtomic.cmd', 'README.md')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $stage
}
Copy-Item -LiteralPath $helper -Destination (Join-Path $stage 'Chrona-vivo-setup.apk')
foreach ($file in $runtime) {
    Copy-Item -LiteralPath (Join-Path $tools $file) -Destination (Join-Path $stage 'platform-tools')
}
$commit = (& git -C $root rev-parse HEAD | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source commit.' }
@("Target Chrona: $version", "Setup source commit: $commit",
    'Helper: com.donglan.chrona.vivo.setup / VivoAtomicSetupInstrumentation',
    'Only CHRONA_AGENDA for com.donglan.chrona is configured.',
    'Android platform-tools notices: platform-tools/NOTICE.txt') |
    Set-Content -LiteralPath (Join-Path $stage 'SOURCE.txt') -Encoding utf8
Compress-Archive -LiteralPath $stage -DestinationPath $archive
Get-Item -LiteralPath $archive | Select-Object FullName, Length
