$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$adminYml = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\plugin.yml')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$artifactsYml = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\plugin.yml')
$narcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$pluginNarcotics = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\plugin.yml')
$electionYml = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\plugin.yml')

foreach ($marker in @('copimine.election.admin','copimine.election.cik','copimine.bank.admin')) {
  if ($admin -notmatch [regex]::Escape($marker)) { $errors.Add("AdminPlus missing active permission marker: $marker") }
}
if ($adminYml -notmatch [regex]::Escape('copimine.diagnostics')) { $errors.Add('AdminPlus plugin.yml must keep copimine.diagnostics.') }

foreach ($marker in @('copimine.artifacts.admin','copimine.artifacts.use')) {
  if (($artifacts + $artifactsYml) -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts missing permission marker: $marker") }
}

foreach ($marker in @('name: CopiMineElectionCore','hidelive:','copimine.election.admin')) {
  if ($electionYml -notmatch [regex]::Escape($marker)) { $errors.Add("ElectionCore plugin.yml missing marker: $marker") }
}

foreach ($marker in @('copimine.narcotics.admin','copimine.narcotics.give','copimine.narcotics.reload','copimine.narcotics.reset','copimine.narcotics.visuals','copimine.narcotics.clearoverdose','/cmnarcotics give','/cmnarcotics reset confirm','/cmnarcotics info','/cmnarcotics setweight','/cmnarcotics setthreshold','/cmnarcotics setwindow','/cmnarcotics setduration','/cmnarcotics texture migrate','/cmnarcotics visuals test')) {
  if (($narcotics + $pluginNarcotics) -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics Phase 1 permission marker missing: $marker") }
}

foreach ($legacy in @('hasPermission("copimine.elections.admin")','hasPermission("copimine.elections.curator")','/election','/voteadmin','/oldvote start')) {
  if ($admin -match [regex]::Escape($legacy)) { $errors.Add("Legacy election command/permission text still present: $legacy") }
}

if ($errors.Count -gt 0) { throw ("Commands/permissions validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Commands/permissions validation passed for ElectionCore rebuild, artifacts, and Phase 1 narcotics.'
