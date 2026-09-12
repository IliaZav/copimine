$ErrorActionPreference = 'Stop'

# Current End Rift gate. This runner is deliberately local/staging-only and
# names only the schema-4, seven-wave, real-health encounter. Production is
# never started or mutated by this script.
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$config = Get-Content -LiteralPath $configPath -Raw
if ($config -notmatch '(?m)^environment:\s*(local|staging)\s*$') {
  throw 'End Rift gate refuses to run: environment must be local or staging.'
}
if ($config -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
  throw 'End Rift gate refuses to run: schema 4 is required.'
}

function Invoke-GateStep {
  param([Parameter(Mandatory)][string]$Label, [Parameter(Mandatory)][scriptblock]$Action)
  Write-Host "== $Label =="
  & $Action
  if ($LASTEXITCODE -ne 0) {
    throw "$Label failed with exit code $LASTEXITCODE"
  }
}

function Invoke-JavaTest {
  param([Parameter(Mandatory)][string]$Classpath, [Parameter(Mandatory)][string]$MainClass)
  & java -cp $Classpath $MainClass
  if ($LASTEXITCODE -ne 0) {
    throw "Java test $MainClass failed with exit code $LASTEXITCODE"
  }
}

$firstPartyBuilds = @(
  @{ Label = 'WorldCore'; Directory = 'copimine-world-core' },
  @{ Label = 'Artifacts'; Directory = 'copimine-artifacts' },
  @{ Label = 'End Event'; Directory = 'copimine-end-event' },
  @{ Label = 'EconomyCore'; Directory = 'copimine-economy-core' },
  @{ Label = 'ElectionCore'; Directory = 'copimine-election-core' },
  @{ Label = 'Narcotics'; Directory = 'copimine-narcotics' },
  @{ Label = 'UltimateAdminPlus'; Directory = 'copimine-admin-plugin' },
  @{ Label = 'AuthEffects'; Directory = 'minecraft\server\plugins\AuthEffects' }
)
foreach ($build in $firstPartyBuilds) {
  $buildPath = Join-Path $root $build.Directory
  Invoke-GateStep ("$($build.Label) build") {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $buildPath 'build-plugin.ps1')
  }
}
Invoke-GateStep 'CopiMineClient build' {
  Push-Location (Join-Path $root 'CopiMineClient')
  try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File '.\build-client.ps1'
  } finally {
    Pop-Location
  }
}
Invoke-GateStep 'Resource pack build' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'resourcepacks\build-resourcepack.ps1') -SkipServerProperties
}

Invoke-GateStep 'Current Python contract' {
  Push-Location $root
  try {
    & python -m pytest -q '.\tests\test_end_event_current_contract.py'
  } finally {
    Pop-Location
  }
}

$testBuild = Join-Path $root 'tests\build\end-event-current'
New-Item -ItemType Directory -Path $testBuild -Force | Out-Null
$domainSources = @(Get-ChildItem (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\domain') -Filter '*.java' |
  ForEach-Object FullName)
$runtimeSources = @(
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\AttemptLifecycleController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\CombatTraceService.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EncounterContext.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EndRiftSession.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EndRiftEncounterCoordinator.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\RealitySplitChamberController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\TentacleController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\TransitionRuneController.java')
)
$runtimeSources += @(Get-ChildItem (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\encounter') -Filter '*.java' |
  ForEach-Object FullName)
$pureTests = @(
  'AbyssAnchorPolicyTest',
  'AttemptLifecycleControllerTest',
  'BossDamagePolicyTest',
  'BossAnimationIdTest',
  'CreativeTestAdmissionPolicyTest',
  'BossAiSimulationTest',
  'BossCastTimelineTest',
  'BossDefeatCinematicPolicyTest',
  'BossFinalStrikePolicyTest',
  'BossMovementPolicyTest',
  'BossRealHealthDamagePolicyTest',
  'BossStatsPolicyTest',
  'BossTargetPolicyTest',
  'BossVisualCuePolicyTest',
  'ChamberIsolationPolicyTest',
  'ChamberScalingPolicyTest',
  'CollapseRingEncounterPolicyTest',
  'CombatMovementPolicyTest',
  'CombatTacticsPolicyTest',
  'CombatTraceDiagnosisTest',
  'CombatTraceRecordTest',
  'EndEventDomainTest',
  'EndEventStateMachineTest',
  'EndRiftEncounterCoordinatorTest',
  'EndRiftAiPolicyTest',
  'EventCombatScalingPolicyTest',
  'EventMobDamagePolicyTest',
  'EventRealHealthDamagePolicyTest',
  'GateOpeningPlanTest',
  'HazardPlannerTest',
  'NightCloakRollPolicyTest',
  'PortalCapturePolicyTest',
  'PressureBudgetControllerTest',
  'ResourceProgressFormatterTest',
  'RiftCarrierPolicyTest',
  'RiftFireballCollisionPolicyTest',
  'RiftFireballScalingPolicyTest',
  'RiftFracturePolicyTest',
  'RiftObeliskScalingPolicyTest',
  'RiftObeliskTimingPolicyTest',
  'ShardPassivePolicyTest',
  'SkeletonArrowPolicyTest',
  'SkeletonCombatPolicyTest',
  'SpellVisualPolicyTest',
  'TargetPressurePolicyTest',
  'TentacleAnimationPolicyTest',
  'TentacleControllerTest',
  'TentacleGuardianPolicyTest',
  'TentacleScalingPolicyTest',
  'TransitionRuneControllerTest',
  'TransitionRunePolicyTest',
  'Wave3PortalPolicyTest',
  'WaveCommanderPolicyTest',
  'WaveDamagePolicyTest',
  'WaveMechanicsPolicyTest',
  'WaveRewardPolicyTest',
  'WaveScalingPolicyTest',
  'WaveVisualPolicyTest',
  'ZoneVisualPolicyTest'
)
$pureSources = @($domainSources + $runtimeSources + ($pureTests | ForEach-Object {
  $path = Join-Path $root ("tests\{0}.java" -f $_)
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing current Java test: $path" }
  $path
}))

Invoke-GateStep 'Current pure Java policies' {
  & javac -encoding UTF-8 -d $testBuild @pureSources
  if ($LASTEXITCODE -ne 0) { throw 'Current pure Java compilation failed.' }
  foreach ($name in $pureTests) {
    Invoke-JavaTest -Classpath $testBuild -MainClass $name
  }
}

$mavenJars = @(Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository') -Filter '*.jar' -Recurse |
  ForEach-Object FullName)
$pluginClasses = (Resolve-Path (Join-Path $root 'copimine-end-event\build\classes')).Path
$persistenceClasspath = @($testBuild, $pluginClasses) + $mavenJars
$persistenceClasspathText = $persistenceClasspath -join [IO.Path]::PathSeparator
$persistenceTests = @(
  'EventStateStoreTest',
  'DepositJournalTest',
  'EventLayoutStoreTest',
  'HazardMutationJournalTest',
  'LegacyEndRiftSnapshotDecoderTest'
)
$persistenceSources = @($persistenceTests | ForEach-Object {
  $path = Join-Path $root ("tests\{0}.java" -f $_)
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing persistence test: $path" }
  $path
})

Invoke-GateStep 'Current persistence and recovery' {
  & javac -encoding UTF-8 -cp $persistenceClasspathText -d $testBuild @persistenceSources
  if ($LASTEXITCODE -ne 0) { throw 'Persistence Java compilation failed.' }
  foreach ($name in $persistenceTests) {
    Invoke-JavaTest -Classpath $persistenceClasspathText -MainClass $name
  }
}

Invoke-GateStep 'Diff hygiene' {
  & git diff --check
  if ($LASTEXITCODE -ne 0) { throw 'Whitespace errors found by git diff --check.' }
}

Write-Host '== Current artifact hashes =='
foreach ($artifact in @(
  (Join-Path $root 'copimine-world-core\CopiMineWorldCore.jar'),
  (Join-Path $root 'copimine-artifacts\CopiMineArtifacts.jar'),
  (Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'),
  (Join-Path $root 'copimine-economy-core\CopiMineEconomyCore.jar'),
  (Join-Path $root 'copimine-election-core\CopiMineElectionCore.jar'),
  (Join-Path $root 'copimine-narcotics\CopiMineNarcotics.jar'),
  (Join-Path $root 'copimine-admin-plugin\CopiMineUltimateAdminPlus.jar'),
  (Join-Path $root 'minecraft\server\plugins\AuthEffects.jar'),
  (Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar'),
  (Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip')
)) {
  if (-not (Test-Path -LiteralPath $artifact)) { throw "Missing build artifact: $artifact" }
  Get-FileHash -LiteralPath $artifact -Algorithm SHA256
}

Write-Host 'End Rift current local checks passed.'
