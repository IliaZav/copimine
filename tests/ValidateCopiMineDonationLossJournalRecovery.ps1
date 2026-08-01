$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($source, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'private void releaseDonationLossJournalGuard' 'A completed loss journal entry must release its in-memory deduplication key.'
Require-Regex 'if \(!this\.applyDonationLossJournalEntry\(var4\)\) \{[\s\S]*?\} else \{[\s\S]*?releaseDonationLossJournalGuard\(var4\);' 'Only successfully applied loss entries may release the deduplication key.'
Require-Regex 'catch \(SQLException var6\) \{[\s\S]*?var2\.add\(var4\);' 'Database failures must leave the journal entry queued for retry.'
Require-Regex 'releaseDonationLossJournalGuard\(CopiMineArtifacts\.DonationLossJournalEntry var1\)[\s\S]*?lossJournalInFlight\.remove\(' 'The release helper must remove exactly the completed instance key.'
Require-Contains 'DamageCause.VOID' 'Void loss must remain covered.'
Require-Contains 'case KILL, WORLD_BORDER, CONTACT, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE,' 'All damage causes must remain covered.'
Require-Contains 'handleCreativeDonationLoss' 'Creative deletion must remain covered.'

if ($errors.Count -gt 0) {
  throw ("Donation loss journal recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Output 'Donation loss journal recovery contract: PASS'
