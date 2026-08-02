param(
  [string]$Pattern = 'Validate*.ps1',
  [switch]$SkipReleaseArtifacts
)

$ErrorActionPreference = 'Stop'
$releaseArtifactValidators = @(
  'ValidateCopiMineAllPluginsAudit.ps1',
  'ValidateCopiMineArtifactsStructure.ps1',
  'ValidateCopiMineArtifactsV4ShopAndBlackMarket.ps1',
  'ValidateCopiMineAuditLeastPrivilegeAndWorldSafety.ps1',
  'ValidateCopiMineClientAssetsInsideModJar.ps1',
  'ValidateCopiMineClientJarContainsAssets.ps1',
  'ValidateCopiMineDirectionalSpecialItemModels.ps1',
  'ValidateCopiMineDonationTexturesInPack.ps1',
  'ValidateCopiMineElectionRolesAndCandidateBooks.ps1',
  'ValidateCopiMineFirstRunPerformancePlus.ps1',
  'ValidateCopiMineNarcoticsPlugin.ps1',
  'ValidateCopiMineNarcoticsResourcePackZipContainsAssets.ps1',
  'ValidateCopiMineNarcoticsResourcePackZipContainsOverlayAssets.ps1',
  'ValidateCopiMineNarcoticsResourcePackZipHashSynced.ps1',
  'ValidateCopiMineNarcoticsTextureAssetsInPack.ps1',
  'ValidateCopiMineNarcoticsTextureDefaultVanilla.ps1',
  'ValidateCopiMinePerformanceAnticheat.ps1',
  'ValidateCopiMinePermissions.ps1',
  'ValidateCopiMinePluginCommands.ps1',
  'ValidateCopiMineReleaseCleanlinessAndGuide.ps1',
  'ValidateCopiMineResourcePackBuildAndHash.ps1',
  'ValidateCopiMineResourcePackReproducible.ps1',
  'ValidateCopiMineShieldResourcePackModel.ps1',
  'ValidateCopiMineSinglePluginAndWorkflows.ps1',
  'ValidateCopiMineTabBannerConfigured.ps1'
)
$allTests = Get-ChildItem -LiteralPath $PSScriptRoot -Filter $Pattern -File |
  Where-Object { $_.Name -ne 'RunCopiMineValidators.ps1' } |
  Sort-Object Name
$tests = if ($SkipReleaseArtifacts) {
  $allTests | Where-Object { $_.Name -notin $releaseArtifactValidators }
} else {
  $allTests
}
$failures = [System.Collections.Generic.List[string]]::new()
$passed = 0
$index = 0
$skippedCount = 0

if ($SkipReleaseArtifacts) {
  $skipped = $allTests | Where-Object { $_.Name -in $releaseArtifactValidators }
  $skippedCount = $skipped.Count
  Write-Host "SKIPPED_RELEASE_ARTIFACT_VALIDATORS count=$skippedCount names=$($skipped.Name -join ',')"
}

foreach ($test in $tests) {
  $index++
  try {
    & $test.FullName *>&1 | Out-Null
    $passed++
  } catch {
    $failures.Add(('{0}: {1}' -f $test.Name, $_.Exception.Message))
  }
  if (($index % 25) -eq 0) {
    Write-Host "PROGRESS $index/$($tests.Count) passed=$passed failed=$($failures.Count)"
  }
}

Write-Host "VALIDATOR_SUMMARY total=$($tests.Count) passed=$passed failed=$($failures.Count) skipped=$skippedCount"
if ($failures.Count -gt 0) {
  $failures | ForEach-Object {
    Write-Host "FAIL $_"
    if ($env:GITHUB_ACTIONS -eq 'true') {
      Write-Output "::error title=CopiMine validator failed::$($_)"
    }
  }
  exit 1
}

# A validator can invoke native tools whose exit code is non-zero even when
# the validator itself passed.  Return an explicit process status so callers
# (including GitHub Actions on PowerShell 7) never inherit that stale code.
exit 0
