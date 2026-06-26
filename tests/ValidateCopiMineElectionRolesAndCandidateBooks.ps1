$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$adminPlus = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$plugins = Join-Path $root 'minecraft\server\plugins'

$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$adminText = Get-Content -Raw -Encoding UTF8 -LiteralPath $adminPlus
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains $text 'openSealTargetMenu' 'Missing seal target GUI for personal issuance.'
Require-Contains $text 'revalidateSealContext' 'Seal GUI must revalidate live seal context before issuing.'
Require-Contains $text 'validateSealUsage' 'Seal runtime must validate the held seal item.'
Require-Contains $text 'issueApplicationBook(Player target, Player issuer, SealContext sealContext)' 'Application issuance must stay bound to SealContext.'
Require-Contains $text 'issueBallot(Player target, Player issuer, SealContext sealContext)' 'Ballot issuance must stay bound to SealContext.'
Require-Contains $text 'chair:application:recommend:' 'Chair recommendation action is missing.'
Require-Contains $text 'chair:application:no-recommend:' 'Chair negative recommendation action is missing.'
Require-Contains $text 'vote:view-program:' 'Ballot/application preview action is missing.'
Require-Contains $text 'Material.WRITTEN_BOOK' 'Candidate/application review must use written books.'
Require-Contains $text 'player.openBook(book);' 'Application preview must open as a book, not as raw text.'
Require-Contains $text 'holder.rightActions().put(slot, "vote:view-program:" + appId);' 'Ballot GUI must keep right-click preview actions.'
Require-Contains $text 'holder.actions().put(slot, "vote:confirm:" + ballotId + ":" + candidateUuid);' 'Ballot GUI must keep left-click confirmation actions.'
Require-Contains $text 'holder.data().put("seal_id", sealContext.sealId());' 'Seal menu must pin seal_id in holder data.'
Require-Contains $text 'holder.data().put("station_id", sealContext.stationId());' 'Seal menu must pin station_id in holder data.'
Require-Contains $text 'holder.data().put("election_id", sealContext.electionId());' 'Seal menu must pin election_id in holder data.'
Require-Contains $text 'holder.data().put("chair_uuid", sealContext.playerUuid());' 'Seal menu must pin chair_uuid in holder data.'

Require-Regex $text 'if \(action\.startsWith\("seal:issue-application:"\)\)[\s\S]*SealContext sealContext = revalidateSealContext\(player, holder\);[\s\S]*issueApplicationBook\(target, player, sealContext\);' 'seal:issue-application must revalidate the seal before issuing.'
Require-Regex $text 'if \(action\.startsWith\("seal:issue-ballot:"\)\)[\s\S]*SealContext sealContext = revalidateSealContext\(player, holder\);[\s\S]*issueBallot\(target, player, sealContext\);' 'seal:issue-ballot must revalidate the seal before issuing.'
Require-Regex $text 'private void openChairApplicationDetail\(Player player, String stationId, String applicationId\) throws Exception \{[\s\S]*"chair:application:recommend:"[\s\S]*"chair:application:no-recommend:"' 'Chair application details screen must expose recommendation-only actions.'
Require-Regex $text 'private void openBallotVoteMenu\(Player player, ItemStack ballot\) \{[\s\S]*holder\.actions\(\)\.put\(slot, "vote:confirm:" \+ ballotId \+ ":" \+ candidateUuid\);[\s\S]*holder\.rightActions\(\)\.put\(slot, "vote:view-program:" \+ appId\);' 'Ballot menu must separate vote confirmation from application preview.'
Require-Regex $text 'private SealContext revalidateSealContext\(Player player, MenuHolder holder\) throws Exception \{[\s\S]*validateSealUsage\(player\.getInventory\(\)\.getItemInMainHand\(\), player\)[\s\S]*expectedSealId[\s\S]*expectedStationId[\s\S]*expectedElectionId[\s\S]*expectedChairUuid' 'Seal revalidation must compare holder context with the live seal item.'
Require-Regex $text 'private void issueApplicationBook\(Player target, Player issuer, SealContext sealContext\) throws Exception \{[\s\S]*validateSealContext\(' 'Application issuance must recheck the DB context.'
Require-Regex $text 'private void issueBallot\(Player target, Player issuer, SealContext sealContext\) throws Exception \{[\s\S]*validateSealContext\(' 'Ballot issuance must recheck the DB context.'
Require-Regex $adminText 'openAdminElectionHub' 'AdminPlus must expose election admin entry via ElectionCore bridge.'

$expectedCoreJars = @(
  'CopiMineArtifacts.jar',
  'CopiMineEconomyCore.jar',
  'CopiMineElectionCore.jar',
  'CopiMineNarcotics.jar',
  'CopiMineUltimateAdminPlus.jar',
  'CopiMineWorldCore.jar'
)
$activeCopiMineJars = @(Get-ChildItem -LiteralPath $plugins -File -Filter 'CopiMine*.jar' | Select-Object -ExpandProperty Name | Sort-Object)
foreach ($jar in $expectedCoreJars) {
  if ($activeCopiMineJars -notcontains $jar) {
    $errors.Add("Missing active CopiMine jar: $jar")
  }
}

if ($errors.Count -gt 0) {
  throw ("Election role/candidate-book validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election role/candidate-book validation passed: seal-bound issuance, chair recommendation flow, and ballot right-click application preview are wired through ElectionCore.'
