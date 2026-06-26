. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$economy = Read-Utf8 $Paths.Economy

Require-Contains $mainPy 'def donation_session_is_expired' 'admin-web must centralize donation session expiry checks.'
Require-Contains $mainPy "SET status='EXPIRED'" 'admin-web must persist EXPIRED status for stale donation sessions.'
Require-Contains $mainPy "status IN ('CREATED','PENDING') AND (expires_at=0 OR expires_at>%s)" 'admin-web overview must exclude expired open sessions.'
Require-Contains $mainPy 'if donation_session_is_expired(data, now):' 'admin-web mark-paid and cancel flows must block expired sessions.'
Require-Contains $economy 'private boolean donationSessionExpired(Map<String, Object> row, long current)' 'EconomyCore must have the same donation session expiry helper.'
Require-Contains $economy 'Donation session is expired.' 'EconomyCore must reject expired donation sessions.'
Require-Contains $economy "status='EXPIRED'" 'EconomyCore must persist EXPIRED when a stale session is touched.'

Throw-IfErrors 'ValidateCopiMineDonationExpiredSessionsNotPayable'
