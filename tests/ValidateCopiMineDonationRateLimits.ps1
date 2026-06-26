. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'check_rate_limit(request, "player-donation-session"' 'Player donation session creation must be rate limited.'
Require-Contains $mainPy 'check_rate_limit(request, "player-donation-purchase"' 'Player donation purchase-intent must be rate limited.'
Require-Contains $mainPy 'check_rate_limit(request, "admin-donation-add-balance"' 'Admin donation top-up must be rate limited.'
Require-Contains $mainPy 'check_rate_limit(request, "admin-donation-test-purchase"' 'Admin donation test purchase must be rate limited.'
Require-Contains $mainPy 'check_rate_limit(request, "admin-donation-mark-paid"' 'Admin donation mark-paid must be rate limited.'
Require-Contains $mainPy 'check_rate_limit(request, "admin-donation-cancel"' 'Admin donation cancel must be rate limited.'

Throw-IfErrors 'ValidateCopiMineDonationRateLimits'
