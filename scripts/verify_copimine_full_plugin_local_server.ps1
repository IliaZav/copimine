[CmdletBinding()]
param(
    [string] $ServerRoot = '',
    [string] $ServerLog = '',
    [string] $ManifestPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$validationRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'artifacts\local-validation'))

if ([string]::IsNullOrWhiteSpace($ServerRoot)) {
    $ServerRoot = Join-Path $validationRoot 'paper-full-plugins'
}
if ([string]::IsNullOrWhiteSpace($ServerLog)) {
    $latestLog = Join-Path $ServerRoot 'logs\latest.log'
    $consoleLog = Join-Path $ServerRoot 'logs\full-plugin.stdout.log'
    $ServerLog = $latestLog
    if (Test-Path -LiteralPath $consoleLog -PathType Leaf) {
        $latestText = if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            Get-Content -LiteralPath $latestLog -Raw
        } else {
            ''
        }
        # Paper rotates latest.log while the process keeps writing the
        # redirected console stream. Prefer that stream when startup evidence
        # is no longer present in the rotated file.
        if ($latestText -notmatch 'Done \([0-9\.,]+s\)!') {
            $ServerLog = $consoleLog
        }
    }
}
if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $ServerRoot 'full-plugin-manifest.json'
}

function Resolve-FullPath([string] $Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Assert-UnderValidation([string] $Path, [string] $Label) {
    $candidate = (Resolve-FullPath $Path).TrimEnd('\') + '\'
    $allowed = (Resolve-FullPath $validationRoot).TrimEnd('\') + '\'
    if (-not $candidate.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay below $validationRoot. Actual path: $Path"
    }
}

Assert-UnderValidation $ServerRoot 'The full-plugin runtime verifier server root'
Assert-UnderValidation $ServerLog 'The full-plugin runtime verifier log'
Assert-UnderValidation $ManifestPath 'The full-plugin runtime verifier manifest'

$resolvedServerRoot = Resolve-FullPath $ServerRoot
$resolvedServerLog = Resolve-FullPath $ServerLog
$resolvedManifestPath = Resolve-FullPath $ManifestPath

foreach ($path in @($resolvedServerRoot, $resolvedServerLog, $resolvedManifestPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf -ErrorAction SilentlyContinue) -and
        -not (Test-Path -LiteralPath $path -PathType Container -ErrorAction SilentlyContinue)) {
        throw "Required full-plugin runtime evidence is missing: $path"
    }
}

$manifest = Get-Content -LiteralPath $resolvedManifestPath -Raw | ConvertFrom-Json
if ($manifest.environment -ne 'staging') {
    throw 'Full-plugin runtime evidence must be marked environment=staging.'
}
$plugins = @($manifest.plugins)
$testPlugins = @(
    if ($null -ne $manifest.test_plugins) {
        $manifest.test_plugins
    }
)
if ($plugins.Count -lt 20) {
    throw "Full-plugin runtime manifest is unexpectedly small: $($plugins.Count)."
}
$expectedPluginCount = $plugins.Count + $testPlugins.Count

$logText = Get-Content -LiteralPath $resolvedServerLog -Raw
if ($logText -notmatch 'Done \([0-9\.,]+s\)!') {
    throw 'Paper startup evidence does not contain the Done marker.'
}
$logLines = @($logText -split "`r?`n")
$inventoryIndex = -1
for ($index = 0; $index -lt $logLines.Count; $index++) {
    if ($logLines[$index] -match 'Bukkit plugins \([0-9]+\):') {
        $inventoryIndex = $index
        break
    }
}
if ($inventoryIndex -lt 0) {
    throw 'Paper startup evidence does not contain the Bukkit plugin inventory line.'
}
$pluginListLine = [string]$logLines[$inventoryIndex]
# Paper may wrap the actual inventory onto the line immediately after the
# header. Include it so verification follows the real startup output format.
if ($inventoryIndex + 1 -lt $logLines.Count) {
    $pluginListLine += ' ' + [string]$logLines[$inventoryIndex + 1]
}
if ($pluginListLine -notmatch "Bukkit plugins \($expectedPluginCount\):") {
    throw "Paper reported a different enabled plugin count than the manifest: $expectedPluginCount."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-PluginName([string] $JarPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.Entries | Where-Object {
            $_.FullName -ieq 'plugin.yml' -or $_.FullName -ieq 'paper-plugin.yml'
        } | Select-Object -First 1
        if ($null -eq $entry) {
            throw "Plugin descriptor is missing from $JarPath."
        }
        $reader = [IO.StreamReader]::new($entry.Open())
        try {
            $descriptor = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
        $match = [regex]::Match($descriptor, '(?m)^name:\s*["'']?([^"''\r\n]+?)["'']?\s*$')
        if (-not $match.Success) {
            throw "Plugin name is missing from the descriptor in $JarPath."
        }
        return $match.Groups[1].Value.Trim()
    } finally {
        $archive.Dispose()
    }
}

$missing = [System.Collections.Generic.List[string]]::new()
$hashMismatches = [System.Collections.Generic.List[string]]::new()
$allManifestPlugins = @(
    $plugins | ForEach-Object { [pscustomobject]@{ Entry = $_; Label = 'main' } }
    $testPlugins | ForEach-Object { [pscustomobject]@{ Entry = $_; Label = 'test-only' } }
)
foreach ($manifestPlugin in $allManifestPlugins) {
    $plugin = $manifestPlugin.Entry
    if ($manifestPlugin.Label -eq 'test-only' -and $plugin.testOnly -ne $true) {
        $missing.Add("$($plugin.file) [test-only marker missing]")
        continue
    }
    $jarPath = Join-Path $resolvedServerRoot ('plugins\' + [string]$plugin.file)
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        $missing.Add([string]$plugin.file)
        continue
    }
    $hash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne ([string]$plugin.sha256).ToLowerInvariant()) {
        $hashMismatches.Add([string]$plugin.file)
    }
    $pluginName = Read-PluginName $jarPath
    $escapedName = [regex]::Escape($pluginName)
    if ($pluginListLine -notmatch "(?<![A-Za-z0-9_])$escapedName\s+\(") {
        $missing.Add("$($plugin.file) [$pluginName/$($manifestPlugin.Label)]")
    }
}

if ($missing.Count -gt 0) {
    throw "Plugins missing from the enabled Paper inventory: $($missing -join ', ')"
}
if ($hashMismatches.Count -gt 0) {
    throw "Plugin hash mismatches in the disposable server: $($hashMismatches -join ', ')"
}

$failureLines = @($logText -split "`r?`n" | Where-Object {
    $_ -match 'Could not load' -or $_ -match 'Disabling\s+[^ ]+\s+because'
})
if ($failureLines.Count -gt 0) {
    throw "Paper reported plugin load failures: $($failureLines -join ' | ')"
}

Write-Output "FULL_PLUGIN_RUNTIME_PLUGIN_COUNT=$($plugins.Count)"
Write-Output "FULL_PLUGIN_RUNTIME_TEST_PLUGIN_COUNT=$($testPlugins.Count)"
Write-Output "FULL_PLUGIN_RUNTIME_ENABLED=$expectedPluginCount"
Write-Output 'PRODUCTION_DATA_MODIFIED=NO'
Write-Output 'FULL_PLUGIN_RUNTIME=PASS'
