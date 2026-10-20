param(
  [string]$Pattern = 'Validate*.ps1',
  [switch]$SkipReleaseArtifacts,
  [string]$MigrationJavaHome = $env:COPIMINE_JAVA_25_HOME,
  [string]$MigrationGradleExecutable = $env:COPIMINE_MIGRATION_GRADLE_EXECUTABLE,
  [string]$MavenRepository = $env:COPIMINE_MAVEN_REPOSITORY,
  [string]$PaperApiJar = $env:COPIMINE_PAPER_263_API_JAR
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
$validatorShell = Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue
if (-not $validatorShell) {
  $validatorShell = Get-Command powershell -CommandType Application -ErrorAction Stop
}

if ($SkipReleaseArtifacts) {
  $skipped = $allTests | Where-Object { $_.Name -in $releaseArtifactValidators }
  $skippedCount = $skipped.Count
  Write-Host "SKIPPED_RELEASE_ARTIFACT_VALIDATORS count=$skippedCount names=$($skipped.Name -join ',')"
}

function Get-ValidatorArguments([string]$ValidatorName) {
  switch ($ValidatorName) {
    'ValidateCopiMinePaper263CompileDependencies.ps1' {
      foreach ($argument in @($MigrationJavaHome, $MavenRepository, $PaperApiJar)) {
        if ([string]::IsNullOrWhiteSpace($argument)) {
          throw 'Paper 26.3 compile dependency validation requires MigrationJavaHome, MavenRepository, and PaperApiJar.'
        }
      }
      return @('-JavaHome', $MigrationJavaHome, '-MavenRepository', $MavenRepository, '-PaperApiJar', $PaperApiJar)
    }
    'ValidateClientBridgeV4FailClosed.ps1' {
      foreach ($argument in @($MigrationJavaHome, $MigrationGradleExecutable)) {
        if ([string]::IsNullOrWhiteSpace($argument)) {
          throw 'Client bridge fail-closed validation requires MigrationJavaHome and MigrationGradleExecutable.'
        }
      }
      return @('-JavaHome', $MigrationJavaHome, '-GradleExecutable', $MigrationGradleExecutable)
    }
    default {
      return @()
    }
  }
}

foreach ($test in $tests) {
  $index++
  try {
    $validatorArguments = Get-ValidatorArguments $test.Name
    $testOutput = @(& $validatorShell.Source -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass `
      -File $test.FullName @validatorArguments *>&1)
    $testExitCode = $LASTEXITCODE
    if ($testExitCode -ne 0) {
      $details = ($testOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
      throw "Validator process exited with code $testExitCode.$([Environment]::NewLine)$details"
    }
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
