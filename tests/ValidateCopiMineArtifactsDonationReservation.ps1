. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$economy = Read-Utf8 $Paths.Economy

Require-Contains $economy 'CompletableFuture<Boolean> reserveClaimAsync' 'Economy donation service must expose reservation step.'
Require-Contains $economy 'CompletableFuture<Boolean> completeClaimAsync' 'Economy donation service must expose completion step.'
Require-Contains $economy 'CompletableFuture<Boolean> releaseClaimAsync' 'Economy donation service must expose release step.'
Require-Contains $economy "status='RESERVED'" 'Economy donation claims must use RESERVED status before final claim.'
Require-Contains $artifacts 'reserveDonationClaimAsync' 'Artifacts donation delivery must reserve claim before giving item.'
Require-Contains $artifacts 'completeDonationClaimAsync' 'Artifacts donation delivery must complete claim after giving item.'
Require-Contains $artifacts 'releaseDonationClaimAsync' 'Artifacts donation delivery must release reservation on failure.'
Require-NotContains $artifacts 'claimItemAsync(playerUuid, claimId).get(5, TimeUnit.SECONDS)' 'Artifacts must not synchronously block on donation claim completion.'

Throw-IfErrors 'ValidateCopiMineArtifactsDonationReservation'
