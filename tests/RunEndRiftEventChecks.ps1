$ErrorActionPreference = 'Stop'

$endRiftRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$endRiftConfig = Join-Path $endRiftRoot 'copimine-end-event\config.yml'
$endRiftConfigText = Get-Content -LiteralPath $endRiftConfig -Raw
if ($endRiftConfigText -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'End Rift checks refuse to run: copimine-end-event/config.yml is not local.'
}

function Invoke-EndRiftStep {
  param([string]$Label, [scriptblock]$Action)
  Write-Host "== $Label =="
  & $Action
  if ($LASTEXITCODE -ne 0) {
    throw "$Label failed with exit code $LASTEXITCODE"
  }
}

Invoke-EndRiftStep 'WorldCore build' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $endRiftRoot 'copimine-world-core\build-plugin.ps1')
}
Invoke-EndRiftStep 'Artifacts build' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $endRiftRoot 'copimine-artifacts\build-plugin.ps1')
}
Invoke-EndRiftStep 'End Event plugin build' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $endRiftRoot 'copimine-end-event\build-plugin.ps1')
}
Invoke-EndRiftStep 'Fabric client tests and build' {
  Push-Location (Join-Path $endRiftRoot 'CopiMineClient')
  try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File '.\build-client.ps1'
  } finally {
    Pop-Location
  }
}
Invoke-EndRiftStep 'Resourcepack build' {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $endRiftRoot 'resourcepacks\build-resourcepack.ps1') -SkipServerProperties
}
Invoke-EndRiftStep 'Python event contracts' {
  Push-Location $endRiftRoot
  try {
    & python -m pytest -q tests\test_end_event_contract.py tests\test_end_event_resourcepack_contract.py `
      tests\test_end_event_client_contract.py tests\test_end_event_spec_contract.py `
      tests\test_end_event_layout_contract.py tests\test_end_event_commands_contract.py `
      tests\test_end_event_item_lore_contract.py tests\test_end_event_runtime_invariants_contract.py `
      tests\test_end_event_runtime_smoke_contract.py tests\test_end_event_ai_contract.py `
      tests\test_end_event_music_contract.py tests\test_end_event_release_contract.py `
      tests\test_end_event_visual_regressions.py tests\test_end_event_arena_protection_contract.py `
      tests\test_end_event_gate_contract.py tests\test_end_event_bossbar_contract.py `
      tests\test_end_event_client_texture_quality_contract.py tests\test_end_event_creative_run_contract.py `
      tests\test_end_event_spell_names_contract.py tests\test_end_event_gate_selection_contract.py `
      tests\test_end_event_command_reference_contract.py tests\test_end_event_completion_audit_contract.py `
      tests\test_end_event_boss_regressions_contract.py tests\test_end_event_wave_objective_contract.py `
      tests\test_end_event_wave_reward_contract.py tests\test_end_event_diagnostics_contract.py `
      tests\test_end_rift_performance_contract.py tests\test_end_event_boss_virtual_health_contract.py `
      tests\test_end_event_official_e2e_contract.py
  } finally {
    Pop-Location
  }
}

$endRiftTestBuild = Join-Path $endRiftRoot 'tests\build\end-event-check'
New-Item -ItemType Directory -Path $endRiftTestBuild -Force | Out-Null
$endRiftTestClasspathEntries = @((Resolve-Path (Join-Path $endRiftRoot 'copimine-end-event\build\classes')).Path)
$endRiftTestClasspathEntries += Get-ChildItem -Path (Join-Path $env:USERPROFILE '.m2\repository') -Filter '*.jar' -Recurse |
  ForEach-Object FullName
$endRiftTestClasspath = $endRiftTestClasspathEntries -join [IO.Path]::PathSeparator
$endRiftDomainSources = (Get-ChildItem (Join-Path $endRiftRoot 'copimine-end-event\src\me\copimine\endevent\domain\*.java')).FullName

Invoke-EndRiftStep 'Pure domain tests' {
  & javac -encoding UTF-8 -d $endRiftTestBuild $endRiftDomainSources `
      (Join-Path $endRiftRoot 'tests\EndEventDomainTest.java') `
      (Join-Path $endRiftRoot 'tests\BossThresholdPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\EndRiftAiPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\ResourceProgressFormatterTest.java') `
      (Join-Path $endRiftRoot 'tests\GateOpeningPlanTest.java') `
      (Join-Path $endRiftRoot 'tests\BossDamagePolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\BossMovementPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\BossStagePolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\CombatMovementPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\WaveObjectivePolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\WaveRewardPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\WaveMechanicsPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\StormPatternPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\BossCastPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\CombatTacticsPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\BossStatsPolicyTest.java') `
      (Join-Path $endRiftRoot 'tests\EndEventStateMachineTest.java')
  & java -cp $endRiftTestBuild EndEventDomainTest
  & java -cp $endRiftTestBuild BossThresholdPolicyTest
  & java -cp $endRiftTestBuild EndRiftAiPolicyTest
  & java -cp $endRiftTestBuild ResourceProgressFormatterTest
  & java -cp $endRiftTestBuild GateOpeningPlanTest
  & java -cp $endRiftTestBuild BossDamagePolicyTest
  & java -cp $endRiftTestBuild BossMovementPolicyTest
  & java -cp $endRiftTestBuild BossStagePolicyTest
  & java -cp $endRiftTestBuild CombatMovementPolicyTest
  & java -cp $endRiftTestBuild WaveObjectivePolicyTest
  & java -cp $endRiftTestBuild WaveRewardPolicyTest
  & java -cp $endRiftTestBuild WaveMechanicsPolicyTest
  & java -cp $endRiftTestBuild StormPatternPolicyTest
  & java -cp $endRiftTestBuild BossCastPolicyTest
  & java -cp $endRiftTestBuild CombatTacticsPolicyTest
  & java -cp $endRiftTestBuild BossStatsPolicyTest
  & java -cp $endRiftTestBuild EndEventStateMachineTest
}
Invoke-EndRiftStep 'Durable persistence and layout tests' {
  & javac -encoding UTF-8 -cp $endRiftTestClasspath -d $endRiftTestBuild `
    (Join-Path $endRiftRoot 'tests\EventStateStoreTest.java') `
    (Join-Path $endRiftRoot 'tests\DepositJournalTest.java') `
    (Join-Path $endRiftRoot 'tests\EventLayoutStoreTest.java') `
    (Join-Path $endRiftRoot 'tests\HazardMutationJournalTest.java')
  $endRiftRunClasspath = @($endRiftTestBuild, (Resolve-Path (Join-Path $endRiftRoot 'copimine-end-event\build\classes')).Path) + $endRiftTestClasspathEntries
  $endRiftRunClasspathText = $endRiftRunClasspath -join [IO.Path]::PathSeparator
  & java -cp $endRiftRunClasspathText EventStateStoreTest
  & java -cp $endRiftRunClasspathText DepositJournalTest
  & java -cp $endRiftRunClasspathText EventLayoutStoreTest
  & java -cp $endRiftRunClasspathText HazardMutationJournalTest
}

Write-Host '== Local artifact hashes =='
Get-FileHash (Join-Path $endRiftRoot 'copimine-world-core\CopiMineWorldCore.jar') -Algorithm SHA256
Get-FileHash (Join-Path $endRiftRoot 'copimine-artifacts\CopiMineArtifacts.jar') -Algorithm SHA256
Get-FileHash (Join-Path $endRiftRoot 'copimine-end-event\CopiMineEndEvent.jar') -Algorithm SHA256
Get-FileHash (Join-Path $endRiftRoot 'CopiMineClient\build\libs\CopiMineClient-0.1.0.jar') -Algorithm SHA256
Get-FileHash (Join-Path $endRiftRoot 'resourcepacks\build\CopiMineResourcePack.zip') -Algorithm SHA256
Write-Host 'End Rift local checks passed.'
