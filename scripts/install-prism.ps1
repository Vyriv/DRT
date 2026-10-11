<#
.SYNOPSIS
  Installs built DRT jars from dist/ into the matching Prism Launcher instances, safely.

.DESCRIPTION
  A jar must never be replaced while Minecraft has it open. The JVM caches the zip central
  directory at startup, so classes loaded lazily after an overwrite are read from stale offsets
  and fail with "ZipFile invalid LOC header (bad signature)".

  For each version this script:
    1. validates the built jar (every entry readable, required classes present);
    2. refuses if a Java process launched for that instance is running, if any Prism-launched
       Java process cannot be attributed to an instance, or if the installed jar is locked;
    3. copies to a staging file in the mods folder, verifies its SHA-256 and zip integrity;
    4. re-checks the instance, then atomically replaces the installed jar and verifies it.

  Dot-source with -NoRun to load the functions without installing (used by the tests).

.EXAMPLE
  .\scripts\install-prism.ps1
  .\scripts\install-prism.ps1 -Version 26.2 -CheckOnly
#>
[CmdletBinding()]
param(
    [string[]] $Version = @('26.1.2', '26.2'),
    [string] $DistDir = '',
    [string] $PrismRoot = (Join-Path $env:APPDATA 'PrismLauncher'),
    [switch] $CheckOnly,
    [switch] $NoRun
)

Set-StrictMode -Version 2
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:InstanceByVersion = @{
    '26.1.2' = 'Skyblock 26.1.2(1)'
    '26.2'   = 'Skyblock 26.2'
}

$script:RequiredJarEntries = @(
    'fabric.mod.json',
    'dev/vy/drt/client/tracker/DungeonRunTrackerFeature.class',
    'dev/vy/drt/client/tracker/DungeonRunTrackerFeature$ScreenBounds.class'
)

function Test-DrtJar {
    param([Parameter(Mandatory)] [string] $Path, [string[]] $RequiredEntries = $script:RequiredJarEntries)
    $problems = New-Object System.Collections.Generic.List[string]
    $entries = 0
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        $problems.Add("missing file")
    } else {
        $zip = $null
        try {
            $zip = [IO.Compression.ZipFile]::OpenRead($Path)
            $names = New-Object 'System.Collections.Generic.HashSet[string]'
            $buffer = New-Object byte[] 65536
            foreach ($entry in $zip.Entries) {
                $entries++
                [void] $names.Add($entry.FullName)
                try {
                    $stream = $entry.Open()
                    try {
                        $read = 0
                        while (($n = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) { $read += $n }
                    } finally { $stream.Dispose() }
                    if ($read -ne $entry.Length) { $problems.Add("short read: $($entry.FullName)") }
                } catch {
                    $problems.Add("unreadable entry $($entry.FullName): $($_.Exception.Message)")
                }
            }
            foreach ($required in $RequiredEntries) {
                if (-not $names.Contains($required)) { $problems.Add("missing entry $required") }
            }
        } catch {
            $problems.Add("not a valid zip: $($_.Exception.Message)")
        } finally {
            if ($zip) { $zip.Dispose() }
        }
    }
    [pscustomobject]@{ Path = $Path; Valid = ($problems.Count -eq 0); Entries = $entries; Problems = @($problems) }
}

function Get-JavaCommandLines {
    $processes = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop
    foreach ($p in $processes) {
        [pscustomobject]@{ ProcessId = $p.ProcessId; CommandLine = $p.CommandLine }
    }
}

function Get-InstanceRunState {
    <# Returns 'Running', 'Unknown' or 'Stopped' for an instance, from Java process command lines. #>
    param([Parameter(Mandatory)] [string] $InstanceName, [object[]] $Processes = @(Get-JavaCommandLines))
    $needle = ('/instances/' + $InstanceName + '/').ToLowerInvariant()
    $state = 'Stopped'
    foreach ($p in $Processes) {
        if ($null -eq $p) { continue }
        $cmd = $p.CommandLine
        if ([string]::IsNullOrEmpty($cmd)) { $state = 'Unknown'; continue }
        $normalized = $cmd.Replace('\', '/').ToLowerInvariant()
        if ($normalized.Contains($needle)) { return 'Running' }
        if ($normalized.Contains('org.prismlauncher.entrypoint') -and -not $normalized.Contains('/instances/')) {
            $state = 'Unknown'
        }
    }
    $state
}

function Test-FileLocked {
    param([Parameter(Mandatory)] [string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    try {
        $handle = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $handle.Dispose()
        $false
    } catch {
        $true
    }
}

function Install-DrtJar {
    param(
        [Parameter(Mandatory)] [string] $Source,
        [Parameter(Mandatory)] [string] $ModsDir,
        [Parameter(Mandatory)] [string] $InstanceName,
        [scriptblock] $GetProcesses = { @(Get-JavaCommandLines) },
        [switch] $CheckOnly
    )
    $name = Split-Path -Leaf $Source
    $target = Join-Path $ModsDir $name
    $staging = Join-Path $ModsDir (".$name.drt-staging")
    $fail = { param($reason) [pscustomobject]@{ Instance = $InstanceName; Jar = $name; Installed = $false; Reason = $reason } }

    $check = Test-DrtJar -Path $Source
    if (-not $check.Valid) { return & $fail ("built jar failed validation: " + ($check.Problems -join '; ')) }
    if (-not (Test-Path -LiteralPath $ModsDir -PathType Container)) { return & $fail "mods folder not found: $ModsDir" }

    $state = Get-InstanceRunState -InstanceName $InstanceName -Processes (& $GetProcesses)
    if ($state -ne 'Stopped') { return & $fail "instance is $($state.ToLowerInvariant()); close Minecraft first" }
    if (Test-FileLocked -Path $target) { return & $fail "installed jar is open by another process" }

    $sourceHash = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
    if ((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $sourceHash) {
        return [pscustomobject]@{ Instance = $InstanceName; Jar = $name; Installed = $true; Reason = 'already up to date' }
    }
    if ($CheckOnly) { return [pscustomobject]@{ Instance = $InstanceName; Jar = $name; Installed = $false; Reason = 'check only: safe to install' } }

    try {
        Copy-Item -LiteralPath $Source -Destination $staging -Force
        if ((Get-FileHash -LiteralPath $staging -Algorithm SHA256).Hash -ne $sourceHash) { return & $fail "staging copy hash mismatch" }
        $stagedCheck = Test-DrtJar -Path $staging
        if (-not $stagedCheck.Valid) { return & $fail ("staging copy failed validation: " + ($stagedCheck.Problems -join '; ')) }

        $state = Get-InstanceRunState -InstanceName $InstanceName -Processes (& $GetProcesses)
        if ($state -ne 'Stopped') { return & $fail "instance started during install; nothing replaced" }
        if (Test-FileLocked -Path $target) { return & $fail "installed jar became locked during install; nothing replaced" }

        if (Test-Path -LiteralPath $target) {
            [IO.File]::Replace($staging, $target, [NullString]::Value)
        } else {
            [IO.File]::Move($staging, $target)
        }
    } catch {
        return & $fail "install failed: $($_.Exception.Message)"
    } finally {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Force -ErrorAction SilentlyContinue }
    }

    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $sourceHash) { return & $fail "installed jar hash mismatch after replace" }
    $others = @(Get-ChildItem -LiteralPath $ModsDir -Filter 'DungeonRunTracker-*.jar' | Where-Object Name -ne $name)
    $reason = 'installed and verified'
    if ($others.Count -gt 0) { $reason += '; other DRT jars present: ' + (($others | ForEach-Object Name) -join ', ') }
    [pscustomobject]@{ Instance = $InstanceName; Jar = $name; Installed = $true; Reason = $reason }
}

if ($NoRun) { return }

if (-not $DistDir) { $DistDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..\dist' }

$results = foreach ($v in $Version) {
    $instance = $script:InstanceByVersion[$v]
    if (-not $instance) { [pscustomobject]@{ Instance = "?"; Jar = $v; Installed = $false; Reason = "unknown version $v" }; continue }
    $source = Get-ChildItem -LiteralPath $DistDir -Filter "DungeonRunTracker-*+$v.jar" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $source) { [pscustomobject]@{ Instance = $instance; Jar = $v; Installed = $false; Reason = "no built jar in $DistDir" }; continue }
    $mods = Join-Path $PrismRoot "instances\$instance\minecraft\mods"
    Install-DrtJar -Source $source.FullName -ModsDir $mods -InstanceName $instance -CheckOnly:$CheckOnly
}
$results | Format-Table -AutoSize -Wrap | Out-String -Width 200 | Write-Host
if (@($results | Where-Object { -not $_.Installed -and $_.Reason -notlike 'check only*' }).Count -gt 0) { exit 1 }
