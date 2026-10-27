[CmdletBinding()]
param(
    [ValidateSet('Preview','Move','Remove')][string]$Action = 'Preview',
    [ValidateSet('A1','A2','A3','A4','A5','A6','A7')][string[]]$Id = @(),
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\','/')
$destinationRoot = Join-Path $project 'build/imported-external-20261027'
function Assert-NoLinkedAncestor([string]$Path) {
    $current = $Path
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "路径经过目录链接：$current" }
        }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ($parent -eq $current) { break }
        $current = $parent
    }
}
$catalog = [ordered]@{
    A1 = @('F:\Android\ChronaBuildCheck\capsule-host')
    A2 = @('F:\Android\ChronaBuildCheck\com')
    A3 = @('F:\Android\ChronaHistoricalBuilds')
    A4 = @('F:\Android\temp\reparse-web-evidence')
    A5 = @('F:\Android\temp\settings-exit-evidence')
    A6 = @('C:\Users\DongLan\AppData\Local\Temp\chrona-stores-review.txt')
    A7 = @(
        'F:\Android\temp\settings-exit-build.log',
        'F:\Android\temp\settings-exit-dark-320-1.5.log',
        'F:\Android\temp\settings-exit-dark-400-1.0.log',
        'F:\Android\temp\settings-exit-emulator-err.log',
        'F:\Android\temp\settings-exit-emulator-out.log',
        'F:\Android\temp\settings-exit-light-320-1.5.log',
        'F:\Android\temp\settings-exit-light.log',
        'F:\Android\temp\settings-exit-test-build.log'
    )
}
if ($Action -ne 'Preview' -and $Id.Count -eq 0) { throw '请显式选择要处理的Id。' }
if ($Action -eq 'Preview' -and $Execute) { throw 'Preview不接受Execute。' }
$selected = if ($Id.Count) { @($Id | Select-Object -Unique) } else { @($catalog.Keys) }
$plan = @()
foreach ($key in $selected) {
    foreach ($literal in $catalog[$key]) {
        $source = [IO.Path]::GetFullPath($literal)
        $destination = [IO.Path]::GetFullPath((Join-Path (Join-Path $destinationRoot $key) ([IO.Path]::GetFileName($source))))
        if (-not $destination.StartsWith($project + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw '目标超出项目。' }
        if ($source.StartsWith($project + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $source -eq [IO.Path]::GetPathRoot($source)) { throw '源边界无效。' }
        Assert-NoLinkedAncestor $source
        Assert-NoLinkedAncestor $destination
        if (-not (Test-Path -LiteralPath $source)) {
            $plan += [pscustomobject]@{ Id=$key;Source=$source;Destination=$destination;Files=0;Bytes=0;State='Missing';Action=$Action }
            continue
        }
        $item = Get-Item -LiteralPath $source -Force
        if (-not $item.FullName.Equals($source,[StringComparison]::OrdinalIgnoreCase) -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "源路径改变或为链接：$source" }
        $children = if ($item.PSIsContainer) { @(Get-ChildItem -LiteralPath $source -Recurse -Force) } else { @() }
        if ($children | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw "源内有目录链接：$source" }
        $files = if ($item.PSIsContainer) { @($children | Where-Object { -not $_.PSIsContainer }) } else { @($item) }
        if ($Action -eq 'Move' -and (Test-Path -LiteralPath $destination)) { throw "目标已存在，停止而不合并：$destination" }
        if ($Execute) {
            foreach ($file in $files) {
                $stream = [IO.File]::Open($file.FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
                $stream.Dispose()
            }
        }
        $bytes = ($files | Measure-Object -Property Length -Sum).Sum
        $plan += [pscustomobject]@{ Id=$key;Source=$source;Destination=$destination;Files=$files.Count;Bytes=[long]$bytes;State='Ready';Action=$Action }
    }
}
$plan | Format-Table Id,Action,State,Files,Bytes,Source -AutoSize
if (-not $Execute) { Write-Host '仅预览；未移动或删除。实际操作必须指定-Id、-Action及-Execute。'; return }
# Preflight above validates every selected path before the first filesystem change.
$recordRoot = Join-Path $project 'build/external-artifact-actions'
New-Item -ItemType Directory -Path $recordRoot -Force | Out-Null
$record = Join-Path $recordRoot ((Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.json')
$results = @()
try {
    foreach ($entry in $plan | Where-Object State -eq 'Ready') {
        if ($Action -eq 'Move') {
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($entry.Destination)) -Force | Out-Null
            Move-Item -LiteralPath $entry.Source -Destination $entry.Destination
        } else {
            Remove-Item -LiteralPath $entry.Source -Recurse -Force
        }
        $results += [pscustomobject]@{ Id=$entry.Id;Action=$Action;Source=$entry.Source;Destination=$(if($Action -eq 'Move'){$entry.Destination}else{$null});State='Completed';At=(Get-Date).ToString('o') }
    }
} catch {
    $results += [pscustomobject]@{ Id=$entry.Id;Action=$Action;Source=$entry.Source;State='Failed';Error=$_.Exception.Message;At=(Get-Date).ToString('o') }
    throw
} finally {
    ConvertTo-Json -InputObject @($results) -Depth 5 | Set-Content -LiteralPath $record -Encoding utf8
    Write-Host "操作记录：$record"
}
