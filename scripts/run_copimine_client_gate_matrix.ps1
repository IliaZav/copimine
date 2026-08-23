[CmdletBinding()]
param(
    [ValidateSet('all', 'managed-plus-extra', 'no-copimine-client', 'no-mods', 'copimine-client-plus-fabric-api')]
    [string]$Scenario = 'all',
    [string]$InstanceRoot = 'D:\Desktop\Copimine\copimine-main\.worktrees\site-launcher-audit\artifacts\local-validation\msi-install-1.0.3-20260823\CopiMine Launcher\Minecraft',
    [string]$ServerHost = '127.0.0.1',
    [int]$ServerPort = 25567,
    [string]$ServerLog = 'D:\Desktop\Copimine\copimine-main\.worktrees\site-launcher-audit\artifacts\local-validation\paper\logs\latest.log'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedInstanceRoot = [IO.Path]::GetFullPath($InstanceRoot)
$resolvedValidationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\artifacts\local-validation'))
if (-not $resolvedInstanceRoot.StartsWith($resolvedValidationRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The matrix may only mutate an instance below $resolvedValidationRoot."
}

$modsDirectory = Join-Path $resolvedInstanceRoot 'mods'
$localSkinPath = Join-Path $resolvedInstanceRoot 'LocalSkin\skins\SmokePlayer.png'
$localCapePath = Join-Path $resolvedInstanceRoot 'LocalSkin\capes\SmokePlayer.png'
$testProject = Join-Path $PSScriptRoot '..\CopiMineLauncher\tests\CopiMineLauncher.IntegrationTests\CopiMineLauncher.IntegrationTests.csproj'
$javaPath = Join-Path $resolvedInstanceRoot '.copimine\java\21.0.10\bin\java.exe'
$copimineClientName = 'CopiMineClient-0.1.0.jar'

foreach ($path in @($resolvedInstanceRoot, $modsDirectory, $testProject, $javaPath, $ServerLog)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required matrix path is missing: $path"
    }
}

$initialFiles = @(Get-ChildItem -LiteralPath $modsDirectory -File | Sort-Object Name)
if (-not ($initialFiles.Name -contains $copimineClientName)) {
    throw "The baseline instance does not contain $copimineClientName."
}

$scenarios = switch ($Scenario) {
    'all' {
        @(
            @{ Name = 'managed-plus-extra'; ExpectedGate = 'ACCEPT'; Keep = @($initialFiles.Name) },
            @{ Name = 'no-copimine-client'; ExpectedGate = 'REJECT'; Keep = @($initialFiles.Name | Where-Object { $_ -ne $copimineClientName }) },
            @{ Name = 'no-mods'; ExpectedGate = 'REJECT'; Keep = @() },
            @{ Name = 'copimine-client-plus-fabric-api'; ExpectedGate = 'ACCEPT'; Keep = @($copimineClientName, 'fabric-api-0.116.11+1.21.1.jar') }
        )
    }
    'managed-plus-extra' { @(@{ Name = 'managed-plus-extra'; ExpectedGate = 'ACCEPT'; Keep = @($initialFiles.Name) }) }
    'no-copimine-client' { @(@{ Name = 'no-copimine-client'; ExpectedGate = 'REJECT'; Keep = @($initialFiles.Name | Where-Object { $_ -ne $copimineClientName }) }) }
    'no-mods' { @(@{ Name = 'no-mods'; ExpectedGate = 'REJECT'; Keep = @() }) }
    'copimine-client-plus-fabric-api' { @(@{ Name = 'copimine-client-plus-fabric-api'; ExpectedGate = 'ACCEPT'; Keep = @($copimineClientName, 'fabric-api-0.116.11+1.21.1.jar') }) }
}

function Invoke-GateScenario([hashtable]$definition) {
    $scenarioName = [string]$definition.Name
    $expectedGate = [string]$definition.ExpectedGate
    $keep = @($definition.Keep)
    $quarantine = Join-Path $resolvedInstanceRoot ('.validation-quarantine\' + $scenarioName)
    if (Test-Path -LiteralPath $quarantine) {
        $leftovers = @(Get-ChildItem -LiteralPath $quarantine -Force -ErrorAction SilentlyContinue)
        if ($leftovers.Count -gt 0) {
            throw "Scenario quarantine is not empty: $quarantine"
        }
    }
    New-Item -ItemType Directory -Path $quarantine -Force | Out-Null

    $baselineNames = @($initialFiles.Name)
    $moved = @()
    try {
        foreach ($file in @(Get-ChildItem -LiteralPath $modsDirectory -File)) {
            if ($keep -notcontains $file.Name) {
                Move-Item -LiteralPath $file.FullName -Destination $quarantine
                $moved += $file.Name
            }
        }

        $currentNames = @(Get-ChildItem -LiteralPath $modsDirectory -File | Select-Object -ExpandProperty Name)
        $currentNames = @($currentNames | Sort-Object)
        $expectedNames = @($keep | Sort-Object)
        if (($currentNames -join "`n") -ne ($expectedNames -join "`n")) {
            throw "Scenario $scenarioName produced an unexpected mod profile. Current=$($currentNames -join ', ') expected=$($expectedNames -join ', ')"
        }

        Write-Host "`n=== CLIENT GATE SCENARIO: $scenarioName ===" -ForegroundColor Cyan
        Write-Host ("mods={0}" -f ($(if ($currentNames.Count -eq 0) { '<none>' } else { $currentNames -join ', ' })))
        Write-Host "expected_gate=$expectedGate"

        $env:COPIMINE_SERVER_SMOKE_INSTANCE = $resolvedInstanceRoot
        $env:COPIMINE_SERVER_SMOKE_HOST = $ServerHost
        $env:COPIMINE_SERVER_SMOKE_PORT = [string]$ServerPort
        $env:COPIMINE_SERVER_SMOKE_LOG = [IO.Path]::GetFullPath($ServerLog)
        $env:COPIMINE_SERVER_SMOKE_EXPECTED_GATE = $expectedGate
        $expectLocalCosmetics = ($scenarioName -eq 'managed-plus-extra') -and
            (Test-Path -LiteralPath $localSkinPath -PathType Leaf) -and
            (Test-Path -LiteralPath $localCapePath -PathType Leaf)
        if ($scenarioName -eq 'managed-plus-extra' -and -not $expectLocalCosmetics) {
            Write-Host 'local_cosmetics_fixture=ABSENT (optional on a clean install)'
        }
        $env:COPIMINE_SERVER_SMOKE_EXPECT_LOCAL_COSMETICS = if ($expectLocalCosmetics) { '1' } else { '0' }
        # A cold full-plugin Paper boot can consume over 30 seconds before
        # the client reaches the world. Rejection evidence appears only after
        # the 15-second READY timeout, so leave enough budget for both phases.
        $env:COPIMINE_SERVER_SMOKE_WAIT_SECONDS = if ($expectedGate -eq 'REJECT') { '90' } else { '90' }

        & dotnet test $testProject --no-restore --filter 'FullyQualifiedName~MinecraftServerJoinSmokeTests' --logger 'console;verbosity=minimal'
        if ($LASTEXITCODE -ne 0) {
            throw "Scenario $scenarioName failed with exit code $LASTEXITCODE."
        }
        Write-Host "scenario_result=PASS" -ForegroundColor Green
    }
    finally {
        foreach ($file in @(Get-ChildItem -LiteralPath $quarantine -File -ErrorAction SilentlyContinue)) {
            Move-Item -LiteralPath $file.FullName -Destination $modsDirectory
        }
        $restoredNames = @(Get-ChildItem -LiteralPath $modsDirectory -File | Select-Object -ExpandProperty Name | Sort-Object)
        if (($restoredNames -join "`n") -ne (($baselineNames | Sort-Object) -join "`n")) {
            throw "Scenario $scenarioName did not restore the baseline mod set."
        }
        if (Test-Path -LiteralPath $quarantine) {
            $leftovers = @(Get-ChildItem -LiteralPath $quarantine -Force -ErrorAction SilentlyContinue)
            if ($leftovers.Count -eq 0) {
                Remove-Item -LiteralPath $quarantine -Force
            } else {
                throw "Scenario quarantine still contains files after restore: $quarantine"
            }
        }
        Write-Host ("restored_mod_count={0}" -f $restoredNames.Count)
    }
}

foreach ($definition in $scenarios) {
    Invoke-GateScenario $definition
}

Write-Host "`nCLIENT_GATE_MATRIX=PASS" -ForegroundColor Green
