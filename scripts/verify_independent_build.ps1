param(
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = 'Stop'

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path

$pluginBuildScripts = @(
    'copimine-economy-core\build-plugin.ps1',
    'copimine-election-core\build-plugin.ps1',
    'copimine-artifacts\build-plugin.ps1',
    'copimine-narcotics\build-plugin.ps1',
    'copimine-world-core\build-plugin.ps1',
    'copimine-admin-plugin\build-plugin.ps1',
    'minecraft\server\plugins\AuthEffects\build-plugin.ps1'
)
$pluginArtifacts = @(
    'copimine-economy-core\CopiMineEconomyCore.jar',
    'copimine-election-core\CopiMineElectionCore.jar',
    'copimine-artifacts\CopiMineArtifacts.jar',
    'copimine-narcotics\CopiMineNarcotics.jar',
    'copimine-world-core\CopiMineWorldCore.jar',
    'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar',
    'minecraft\server\plugins\AuthEffects.jar'
)

if ($pluginBuildScripts.Count -ne $pluginArtifacts.Count) {
    throw 'Independent-build script and artifact lists must stay aligned.'
}

$proofRoot = Join-Path ([IO.Path]::GetTempPath()) ('copimine-independent-build-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $proofRoot | Out-Null

function Invoke-PluginBuilds {
    foreach ($relative in $pluginBuildScripts) {
        $scriptPath = Join-Path $ProjectRoot $relative
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
            throw "Missing plugin build script: $relative"
        }
        & powershell -ExecutionPolicy Bypass -File $scriptPath
        if ($LASTEXITCODE -ne 0) {
            throw "Plugin build failed: $relative"
        }
    }
}

function Get-ArtifactHashes {
    $hashes = [ordered]@{}
    foreach ($relative in $pluginArtifacts) {
        $artifactPath = Join-Path $ProjectRoot $relative
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            throw "Build did not produce required artifact: $relative"
        }
        $hashes[$relative.Replace('\', '/')] = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash.ToLowerInvariant()
    }
    return $hashes
}

try {
    Write-Host 'Independent build pass 1/2'
    Invoke-PluginBuilds
    $first = Get-ArtifactHashes

    Write-Host 'Independent build pass 2/2'
    Invoke-PluginBuilds
    $second = Get-ArtifactHashes

    $mismatches = @()
    foreach ($key in $first.Keys) {
        if ($first[$key] -ne $second[$key]) {
            $mismatches += "${key}: $($first[$key]) != $($second[$key])"
        }
    }
    if ($mismatches.Count -gt 0) {
        throw "Independent first-party build is not reproducible:`n - $($mismatches -join "`n - ")"
    }

    Write-Host ('Independent first-party build passed for {0} artifacts.' -f $first.Count)
}
finally {
    Remove-Item -LiteralPath $proofRoot -Recurse -Force -ErrorAction SilentlyContinue
}
