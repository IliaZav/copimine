param(
  # Useful after the current process has already built every artifact: this
  # preserves the exact verification suite without redundantly compiling all
  # unrelated first-party plugins a second time.
  [switch]$SkipBuilds
)

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
  # A managed-only scriptblock does not set LASTEXITCODE.  Reset the inherited
  # native value so this gate is judged by its own exceptions rather than a
  # command which ran before the gate.
  $global:LASTEXITCODE = 0
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
  @{ Label = 'End Event'; Directory = 'copimine-end-event'; SyncServerConfig = $true },
  @{ Label = 'EconomyCore'; Directory = 'copimine-economy-core' },
  @{ Label = 'ElectionCore'; Directory = 'copimine-election-core' },
  @{ Label = 'Narcotics'; Directory = 'copimine-narcotics' },
  @{ Label = 'UltimateAdminPlus'; Directory = 'copimine-admin-plugin' },
  @{ Label = 'AuthEffects'; Directory = 'minecraft\server\plugins\AuthEffects' }
)
if (-not $SkipBuilds) {
  foreach ($build in $firstPartyBuilds) {
    $buildPath = Join-Path $root $build.Directory
    Invoke-GateStep ("$($build.Label) build") {
      $buildArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $buildPath 'build-plugin.ps1'))
      if ($build.SyncServerConfig) { $buildArgs += '-SyncServerConfig' }
      & powershell @buildArgs
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
  Invoke-GateStep 'Authored boss pose generator parity' {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'copimine-end-event\tools\GenerateBossAnimationPoses.ps1') -Check
  }
  Invoke-GateStep 'Modpack stages source-built client' {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\thirdparty\build_modpack.ps1') -SyncBuiltClient
  }
} else {
  Invoke-GateStep 'Prebuilt artifact presence' {
    foreach ($artifact in @(
      (Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'),
      (Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar'),
      (Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip')
    )) {
      if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "Missing required prebuilt artifact: $artifact"
      }
    }
  }
}

Invoke-GateStep 'Local probe AuthMe mode behavior' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'EndRiftLocalAuthModeTest.ps1')
}

Invoke-GateStep 'Wave navigation and physical passage regression' {
  Push-Location $root
  try {
    & python -m pytest -q '.\tests\test_wave_navigation_adapter.py' '.\tests\test_wave7_passage_commit.py' '.\tests\test_wave_carrier_marker_adapter.py' '.\tests\test_wave_dash_runtime.py'
    if ($LASTEXITCODE -ne 0) { throw "Wave navigation and passage regressions failed with exit code $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
}

Invoke-GateStep 'Current Python contract' {
  Push-Location $root
  try {
  & python -m pytest -q '.\tests\test_end_event_current_contract.py' '.\tests\test_end_event_boss_hitbox_contract.py' '.\tests\test_end_event_boss_oriented_hitbox_contract.py' '.\tests\test_end_event_boss_animation_pose_contract.py' '.\tests\test_end_event_boss_hitbox_reconciliation_contract.py' '.\tests\test_end_event_boss_projectile_segment_contract.py' '.\tests\test_end_event_model_uv_contract.py' '.\tests\test_end_event_skeleton_look_contract.py' '.\tests\test_end_event_wave6_ritual_live_contract.py' '.\tests\test_end_event_core_visual_contract.py' '.\tests\test_end_event_resource_visual_contract.py' '.\tests\test_end_event_wave3_knockback_contract.py' '.\tests\test_end_event_wave6_wave7_boundaries_contract.py' '.\tests\test_end_event_wave_mob_visual_contract.py' '.\tests\test_end_rift_model_evidence_portability.py' '.\tests\test_end_rift_evidence_portability.py' '.\tests\test_end_rift_test_quality_contract.py' '.\tests\test_wave6_ritual_caster_behavior_contract.py' '.\tests\test_end_rift_ai_phase_probe_contract.py' '.\tests\test_end_rift_multiplayer_probe_contract.py' '.\tests\test_end_rift_recovery_contract.py' '.\tests\test_end_event_ritual_projectile_provenance_contract.py' '.\tests\test_end_event_ritual_sphere_projectile_origin_contract.py' '.\tests\test_end_event_ritual_prisoner_health_contract.py' '.\tests\test_end_event_ritual_control_pair_contract.py' '.\tests\test_end_event_ritual_zone_effect_contract.py' '.\tests\test_end_event_ritual_sphere_authoritative_state_contract.py' '.\tests\test_end_event_ritual_chains_target_contract.py' '.\tests\test_end_rift_diagnostic_report.py' '.\tests\test_end_rift_stage1_lifecycle_contract.py' '.\tests\test_end_rift_reality_split_trials_contract.py' '.\tests\test_end_rift_event_gate_contract.py'
  if ($LASTEXITCODE -ne 0) { throw "Current Python contract failed with exit code $LASTEXITCODE" }
  & python -m pytest -q '.\tests\test_end_rift_wave_adapter_contract.py' '.\tests\test_end_rift_wave_probe_adapters.py'
  if ($LASTEXITCODE -ne 0) { throw "Wave adapter regression tests failed with exit code $LASTEXITCODE" }
  & python -m pytest -q '.\tests\test_end_rift_prisoner_client_boundaries.py' '.\tests\test_end_rift_portal_presentation_contract.py' '.\tests\test_end_rift_wave6_visual_mechanics.py' '.\tests\test_end_rift_barrier_coverage.py' '.\tests\test_end_rift_fog_effect_cleanup.py' '.\tests\test_end_rift_server_tick_transitions.py'
  if ($LASTEXITCODE -ne 0) { throw "Wave presentation regression tests failed with exit code $LASTEXITCODE" }
  & python -m pytest -q '.\tests\test_wave6_visual_cleanup_contract.py' '.\tests\test_end_event_wave6_progression_contract.py' '.\tests\test_end_event_wave6_no_legacy_contract.py' '.\tests\test_wave6_ritual_amplifier_contract.py' '.\tests\test_official_live_runner_wave6_contract.py' '.\tests\test_end_rift_wave1_interaction_harness_contract.py' '.\tests\test_end_rift_prisoner_hud_assets.py' '.\tests\test_end_rift_guard_tactics_runtime.py' '.\tests\test_end_rift_supplied_asset_checkout.py' '.\tests\test_end_rift_wave_daylight_runtime.py'
  if ($LASTEXITCODE -ne 0) { throw "Additional wave gameplay regression tests failed with exit code $LASTEXITCODE" }
  & python -m pytest -q '.\tests\test_end_rift_wave_ai_roles.py' '.\tests\test_end_rift_miniboss_counterplay.py' '.\tests\test_end_rift_waves2_4_mechanics.py' '.\tests\test_end_rift_obelisk_pulse_roster.py' '.\tests\test_end_rift_wave_structure_assets.py' '.\tests\test_end_rift_wave5_safe_tile_assets.py' '.\tests\test_end_rift_wave_glyph_assets.py' '.\tests\test_end_rift_disabled_packet_cleanup.py' '.\tests\test_end_rift_local_pack_sync.py' '.\tests\test_end_rift_main_tick_dispatch.py'
  if ($LASTEXITCODE -ne 0) { throw "Waves 1-5 gameplay and authored assets failed with exit code $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
}

Invoke-GateStep 'Live bot reflection target' {
  Push-Location $root
  try {
    & node --test '.\tests\LocalEndRiftReflectionTarget.test.js'
    if ($LASTEXITCODE -ne 0) {
      throw "Live bot reflection target tests failed with exit code $LASTEXITCODE"
    }
    & node --check '.\tests\LocalEndRiftMobCombatBot.js'
    if ($LASTEXITCODE -ne 0) {
      throw "Live bot syntax check failed with exit code $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }
}

Invoke-GateStep 'Ritual restart contract' {
  Push-Location $root
  try {
    & python -m pytest -q '.\tests\test_end_event_ritual_restart_contract.py'
  } finally {
    Pop-Location
  }
}

$testBuild = Join-Path $root 'tests\build\end-event-current'
New-Item -ItemType Directory -Path $testBuild -Force | Out-Null
$domainSources = @(Get-ChildItem (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\domain') -Filter '*.java' |
  ForEach-Object FullName)
$runtimeSources = @(
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\WaveCombatCoordinator.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\AttemptLifecycleController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\CombatTraceService.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EncounterContext.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EndRiftEncounterController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EndRiftSession.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EndRiftEncounterCoordinator.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\RealitySplitChamberController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\RealitySplitTrialController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\RealitySplitTrialSnapshot.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\TentacleController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\TransitionRuneController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\PreBossTransitionController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\BossStartGateway.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\ritual\RitualSpellController.java'),
  (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\ritual\PrisonerAbilityController.java')
)
$runtimeSources += @(Get-ChildItem (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\encounter') -Filter '*.java' |
  ForEach-Object FullName)
$diagnosticSources = @(Get-ChildItem (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\diagnostics') -Filter '*.java' |
  ForEach-Object FullName)
$pureTests = @(
  'Wave7AdmissionPolicyTest',
  'WaveCombatCoordinatorTest',
  'WaveStructurePlacementTest',
  'AbyssAnchorPolicyTest',
  'AttemptLifecycleControllerTest',
  'BossDamagePolicyTest',
  'BossAnimationIdTest',
  'BossAnimationPosePolicyTest',
  'CreativeTestAdmissionPolicyTest',
  'BossAiSimulationTest',
  'BossCastTimelineTest',
  'BlackFogDamagePolicyTest',
  'BlackFogSafeZonePolicyTest',
  'BlackFogTimingPolicyTest',
  'BlackFogEffectLeasePolicyTest',
  'CollapseRingGeometryPolicyTest',
  'BossDefeatCinematicPolicyTest',
  'BossHitboxDedupePolicyTest',
  'BossHitboxProfileTest',
  'BossHitboxTransformPolicyTest',
  'BossOrientedHitboxPolicyTest',
  'BossProjectileSweepPolicyTest',
  'BossHitboxProxyReconciliationPolicyTest',
  'BossHitboxProxyMetadataPolicyTest',
  'BossFinalStrikePolicyTest',
  'BossMovementPolicyTest',
  'BossRealHealthDamagePolicyTest',
  'BossStatsPolicyTest',
  'BossTargetPolicyTest',
  'BossVisualCuePolicyTest',
  'ChamberIsolationPolicyTest',
  'ClientBindingReconnectPolicyTest',
  'ChamberScalingPolicyTest',
  'CollapseRingEncounterPolicyTest',
  'CollapseRingEncounterSnapshotTest',
  'RealitySplitBarrierPolicyTest',
  'RealitySplitBarrierCoverageTest',
  'RealitySplitBarrierRecoveryTest',
  'RealitySplitChamberControllerTest',
  'RealitySplitTrialControllerTest',
  'RealitySplitRuntimePolicyTest',
  'RealitySplitTrialSnapshotTest',
  'RealitySplitChamberSnapshotTest',
  'SandboxWaveSessionSnapshotTest',
  'RealitySplitPlayerTeleportPolicyTest',
  'RealitySplitPlayerKnockbackPolicyTest',
  'RealitySplitCombatSeparationPolicyTest',
  'RitualCasterShieldPolicyTest',
  'RitualCasterTacticsPolicyTest',
  'RitualSpellControllerTest',
  'RitualCasterAiOwnershipPolicyTest',
  'RitualGuardAbilityPolicyTest',
  'RitualGuardAggroPolicyTest',
  'RitualGuardStatsPolicyTest',
  'RitualSealCapturePolicyTest',
  'RitualPrisonerCapturePolicyTest',
  'RitualTargetPolicyTest',
  'RitualZoneEffectPolicyTest',
  'RitualConversionTargetPolicyTest',
  'PrisonerAbilityControllerTest',
  'RitualCasterProgressionPolicyTest',
  'RitualSphereEncounterPolicyTest',
  'RitualSphereCaptureTransitionTest',
  'RitualSphereEncounterSnapshotTest',
  'RitualSphereAuthoritativeStateTest',
  'RitualSphereScalingPolicyTest',
  'RitualSphereProjectilePolicyTest',
  'RitualSphereProjectileProvenancePolicyTest',
  'CombatMovementPolicyTest',
  'CombatTacticsPolicyTest',
  'CombatTraceDiagnosisTest',
  'CombatTraceRecordTest',
  'EndEventDomainTest',
  'EndEventStateMachineTest',
  'EndRiftEncounterControllerTest',
  'TransitionIdempotencyTest',
  'EndRiftEncounterCoordinatorTest',
  'PreBossTransitionControllerTest',
  'PostWaveRecoveryPolicyTest',
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
  'VisualRefreshDeadlineTest',
  'RitualShieldOrbitPolicyTest',
  'RitualCasterHandPolicyTest',
  'RitualSpellVisualPolicyTest',
  'RitualSpherePresentationPolicyTest',
  'PortalPresentationPolicyTest',
  'RiftFireballCollisionPolicyTest',
  'RiftFireballReflectionPolicyTest',
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
  'PreBossTickSnapshotPolicyTest',
  'TransitionRunePolicyTest',
  'Wave3PortalPolicyTest',
  'WaveCommanderPolicyTest',
  'WaveDamagePolicyTest',
  'WaveMechanicsPolicyTest',
  'WaveRewardPolicyTest',
  'WaveScalingPolicyTest',
  'WaveVisualPolicyTest',
  'ZoneVisualPolicyTest',
  'EndRiftDiagnosticJsonTest',
  'EndRiftDiagnosticSinkTest',
  'EndRiftDiagnosticInvariantMonitorTest',
  'EndRiftDiagnosticSnapshotTest',
  'EndRiftDiagnosticServiceTest'
)
$pureSources = @($domainSources + $runtimeSources + $diagnosticSources + ($pureTests | ForEach-Object {
  $path = Join-Path $root ("tests\{0}.java" -f $_)
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing current Java test: $path" }
  $path
}))

Invoke-GateStep 'Current pure Java policies' {
  $pureSourceList = Join-Path $testBuild 'pure-sources.args'
  $pureSourceArguments = @($pureSources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' })
  Set-Content -LiteralPath $pureSourceList -Value $pureSourceArguments -Encoding ascii
  & javac -encoding UTF-8 -d $testBuild ('@' + $pureSourceList)
  if ($LASTEXITCODE -ne 0) { throw 'Current pure Java compilation failed.' }
  foreach ($name in $pureTests) {
    Invoke-JavaTest -Classpath $testBuild -MainClass $name
  }
}

$mavenRepository = Join-Path $env:USERPROFILE '.m2\repository'
$guavaJar = Get-ChildItem -Path $mavenRepository -Filter 'guava-32.1.2-jre.jar' -Recurse -ErrorAction SilentlyContinue |
  Select-Object -First 1 -ExpandProperty FullName
if (-not $guavaJar -or -not (Test-Path -LiteralPath $guavaJar -PathType Leaf)) {
  throw 'Pinned Guava 32.1.2 jar is required to run persistence tests.'
}
$mavenJars = @(Get-ChildItem -Path $mavenRepository -Filter '*.jar' -Recurse |
  Where-Object { $_.Name -notlike 'guava-*.jar' } |
  ForEach-Object FullName)
$pluginClasses = (Resolve-Path (Join-Path $root 'copimine-end-event\build\classes')).Path
$paperApiJar = $env:PAPER_API_JAR
if (-not $paperApiJar -or -not (Test-Path -LiteralPath $paperApiJar -PathType Leaf)) {
  $paperApiJar = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository') -Filter 'paper-api-*-R0.1-SNAPSHOT.jar' -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if (-not $paperApiJar -or -not (Test-Path -LiteralPath $paperApiJar -PathType Leaf)) {
  throw 'Pinned Paper API jar is required to compile persistence tests.'
}
$persistenceClasspath = @($testBuild, $pluginClasses, $paperApiJar, $guavaJar) + $mavenJars
$persistenceClasspathText = $persistenceClasspath -join [IO.Path]::PathSeparator
$persistenceTests = @(
  'EventStateStoreTest',
  'DepositJournalTest',
  'EventLayoutStoreTest',
  'HazardMutationJournalTest',
  'LegacyEndRiftSnapshotDecoderTest',
  'EncounterResourceScopeTest',
  'EventTaskRegistryTest'
  'PostWaveRecoveryServiceTest'
)
$persistenceSources = @($persistenceTests | ForEach-Object {
  $path = Join-Path $root ("tests\{0}.java" -f $_)
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing persistence test: $path" }
  $path
})
$persistenceSources += Join-Path $root 'copimine-end-event\src\me\copimine\endevent\EventTaskRegistry.java'
$persistenceSources += Join-Path $root 'copimine-end-event\src\me\copimine\endevent\runtime\EncounterResourceScope.java'

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
