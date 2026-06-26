. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$economy = Read-Utf8 $Paths.Economy

Require-Contains $mainPy 'if status == "PAID":' 'admin-web mark-paid flow must branch explicitly on already-PAID donation sessions.'
Require-Contains $mainPy 'if not ledger:' 'admin-web mark-paid flow must refuse PAID sessions with a missing ledger entry.'
Require-Contains $mainPy 'donation.session.manual_review' 'admin-web must audit PAID-without-ledger sessions for manual review instead of crediting again.'
Require-Contains $mainPy '"DONATION_TOPUP"' 'admin-web mark-paid flow must use the canonical DONATION_TOPUP reason.'
Require-Contains $mainPy 'donation_now_ms()' 'admin-web donation flows must use millisecond timestamps.'
Require-Contains $mainPy 'donation_epoch_ms(data.get("paid_at"))' 'admin-web mark-paid flow must normalize legacy paid_at timestamps before returning them.'
Require-Contains $mainPy 'DONATION_SESSION_TTL_MS' 'admin-web donation sessions must use millisecond TTLs.'
Require-NotContains $mainPy 'if status == "PAID" and ledger:' 'admin-web mark-paid flow must not allow a missing-ledger PAID session to fall through into another credit.'

Require-Contains $economy 'if ("PAID".equalsIgnoreCase(status)) {' 'EconomyCore mark-paid flow must branch explicitly on already-PAID donation sessions.'
Require-Contains $economy 'SELECT id,balance_after FROM donation_balance_ledger WHERE idempotency_key=? LIMIT 1' 'EconomyCore must re-check the donation ledger before accepting an already-paid session.'
Require-Contains $economy 'session_manual_review' 'EconomyCore must emit a manual-review event when a PAID session has no ledger row.'
Require-Contains $economy 'paid_without_ledger' 'EconomyCore must label the PAID-without-ledger case explicitly.'
Require-Contains $economy 'String ledgerKey = "donation-session-paid-" + sessionId;' 'EconomyCore mark-paid flow must derive a stable ledger key from sessionId.'
Require-NotContains $economy 'first(idempotencyKey, "donation-session-paid-" + sessionId)' 'EconomyCore mark-paid flow must not depend on caller-supplied idempotency keys for ledger lookup.'

Throw-IfErrors 'ValidateCopiMineDonationMarkPaidNoDoubleCredit'
