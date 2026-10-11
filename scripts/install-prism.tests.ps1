<#
  Self-contained tests for install-prism.ps1. Run: powershell -NoProfile -File scripts\install-prism.tests.ps1
  Uses a temporary fake Prism layout; never touches real instances.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'install-prism.ps1') -NoRun

$failures = 0
function Assert($condition, $message) {
    if ($condition) { Write-Host "PASS $message" } else { Write-Host "FAIL $message" -ForegroundColor Red; $script:failures++ }
}

function New-TestJar([string] $path, [string[]] $entries) {
    $zip = [IO.Compression.ZipFile]::Open($path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($e in $entries) {
            $writer = New-Object IO.StreamWriter(($zip.CreateEntry($e)).Open())
            $writer.Write(("payload for $e " * 200))
            $writer.Dispose()
        }
    } finally { $zip.Dispose() }
}

$root = Join-Path ([IO.Path]::GetTempPath()) ("drt-install-test-" + [Guid]::NewGuid().ToString('N'))
$mods = Join-Path $root 'instances\Skyblock 26.2\minecraft\mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null
$stopped = { @([pscustomobject]@{ ProcessId = 1; CommandLine = 'C:\jdk\bin\java.exe -jar gradle-daemon.jar' }) }
$running = { @([pscustomobject]@{ ProcessId = 2; CommandLine = 'javaw.exe -Djava.library.path=C:/Users/x/AppData/Roaming/PrismLauncher/instances/Skyblock 26.2/natives -cp NewLaunch.jar org.prismlauncher.EntryPoint' }) }
$otherInstance = { @([pscustomobject]@{ ProcessId = 3; CommandLine = 'javaw.exe -Djava.library.path=C:\PrismLauncher\instances\Skyblock 26.1.2(1)\natives org.prismlauncher.EntryPoint' }) }
$unattributed = { @([pscustomobject]@{ ProcessId = 4; CommandLine = 'javaw.exe -cp NewLaunch.jar org.prismlauncher.EntryPoint' }) }
$hidden = { @([pscustomobject]@{ ProcessId = 5; CommandLine = $null }) }

try {
    $good = Join-Path $root 'DungeonRunTracker-1.0.11+26.2.jar'
    New-TestJar $good $script:RequiredJarEntries
    $target = Join-Path $mods 'DungeonRunTracker-1.0.11+26.2.jar'

    Assert ((Test-DrtJar $good).Valid) 'valid jar passes validation'

    $missing = Join-Path $root 'missing-class.jar'
    New-TestJar $missing @('fabric.mod.json')
    Assert (-not (Test-DrtJar $missing).Valid) 'jar without ScreenBounds class fails validation'

    $corrupt = Join-Path $root 'corrupt.jar'
    $bytes = [IO.File]::ReadAllBytes($good)
    $bytes[0] = 0; $bytes[1] = 0
    [IO.File]::WriteAllBytes($corrupt, $bytes)
    Assert (-not (Test-DrtJar $corrupt).Valid) 'jar with bad local header signature fails validation'

    $truncated = Join-Path $root 'truncated.jar'
    [IO.File]::WriteAllBytes($truncated, $bytes[0..([int]($bytes.Length / 2))])
    Assert (-not (Test-DrtJar $truncated).Valid) 'truncated jar fails validation'

    Assert ((Get-InstanceRunState 'Skyblock 26.2' (& $running)) -eq 'Running') 'running instance detected from command line'
    Assert ((Get-InstanceRunState 'Skyblock 26.2' (& $otherInstance)) -eq 'Stopped') 'other instance does not block'
    Assert ((Get-InstanceRunState 'Skyblock 26.1.2(1)' (& $otherInstance)) -eq 'Running') 'backslash paths and parentheses are matched'
    Assert ((Get-InstanceRunState 'Skyblock 26.2' (& $unattributed)) -eq 'Unknown') 'unattributed Prism launch is treated as unknown'
    Assert ((Get-InstanceRunState 'Skyblock 26.2' (& $hidden)) -eq 'Unknown') 'unreadable command line is treated as unknown'
    Assert ((Get-InstanceRunState 'Skyblock 26.2' (& $stopped)) -eq 'Stopped') 'unrelated java process does not block'

    $r = Install-DrtJar -Source $good -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped
    Assert ($r.Installed -and (Get-FileHash $target).Hash -eq (Get-FileHash $good).Hash) 'fresh install copies and verifies'
    Assert (@(Get-ChildItem -LiteralPath $mods -Force -Filter '*.drt-staging').Count -eq 0) 'no staging file left behind'

    New-TestJar (Join-Path $root 'v2.jar') ($script:RequiredJarEntries + 'extra.txt')
    $v2 = Join-Path $root 'v2\DungeonRunTracker-1.0.11+26.2.jar'
    New-Item -ItemType Directory -Path (Split-Path $v2) | Out-Null
    Move-Item (Join-Path $root 'v2.jar') $v2
    $before = (Get-FileHash $target).Hash

    $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $running
    Assert ((-not $r.Installed) -and (Get-FileHash $target).Hash -eq $before) 'running instance refuses and leaves jar untouched'

    $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $unattributed
    Assert ((-not $r.Installed) -and (Get-FileHash $target).Hash -eq $before) 'unknown process state refuses'

    $handle = [IO.File]::Open($target, 'Open', 'Read', 'ReadWrite, Delete')
    try {
        $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped
        Assert (-not $r.Installed) 'jar held open (as by a JVM) refuses'
    } finally { $handle.Dispose() }
    Assert ((Get-FileHash $target).Hash -eq $before) 'locked jar left untouched'

    $badSource = Join-Path $root 'bad\DungeonRunTracker-1.0.11+26.2.jar'
    New-Item -ItemType Directory -Path (Split-Path $badSource) | Out-Null
    Copy-Item $corrupt $badSource
    $r = Install-DrtJar -Source $badSource -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped
    Assert ((-not $r.Installed) -and (Get-FileHash $target).Hash -eq $before) 'corrupt build is never installed'

    $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped -CheckOnly
    Assert ((-not $r.Installed) -and (Get-FileHash $target).Hash -eq $before) 'check only does not modify'

    $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped
    Assert ($r.Installed -and (Get-FileHash $target).Hash -eq (Get-FileHash $v2).Hash) 'stopped instance upgrade replaces atomically'

    $r = Install-DrtJar -Source $v2 -ModsDir $mods -InstanceName 'Skyblock 26.2' -GetProcesses $stopped
    Assert ($r.Installed -and $r.Reason -eq 'already up to date') 'identical jar is not rewritten'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) { Write-Host "$failures test(s) failed" -ForegroundColor Red; exit 1 }
Write-Host 'All install-prism tests passed'
