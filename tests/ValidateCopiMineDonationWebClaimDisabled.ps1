. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy '@app.post("/api/player/donation/claim")' 'Backend may keep the donation claim route only as an explicit disabled endpoint.'
Require-Contains $mainPy 'status_code=410' 'Web donation claim route must be hard-disabled instead of acting as a second claim channel.'
Require-Contains $mainPy 'donation shop.' 'Disabled web donation claim route must redirect users back to the in-game claim flow.'

Throw-IfErrors 'ValidateCopiMineDonationWebClaimDisabled'
