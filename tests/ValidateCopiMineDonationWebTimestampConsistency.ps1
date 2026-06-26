. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'DONATION_SESSION_TTL_MS = DONATION_SESSION_TTL_SECONDS * 1000' 'Donation session TTL must be tracked in milliseconds.'
Require-Contains $mainPy 'def donation_now_ms() -> int:' 'admin-web must expose a dedicated donation millisecond clock helper.'
Require-Contains $mainPy 'def donation_epoch_ms(value: Any) -> int:' 'admin-web must expose a timestamp normalizer for legacy donation rows.'
Require-Contains $mainPy 'paid_at = donation_epoch_ms(data.get("paid_at"))' 'mark_donation_session_paid_sync must normalize stored paid_at values.'
Require-Contains $mainPy 'now = donation_now_ms()' 'Donation session/test purchase flows must use millisecond timestamps.'

$testPurchaseBody = Method-Body $mainPy 'def admin_create_donation_test_purchase_sync(player_uuid: str, player_name: str, item_id: str, actor: str) -> dict[str, Any]:'
if (-not $testPurchaseBody) {
  $errors.Add('Missing admin_create_donation_test_purchase_sync in admin-web backend.')
} else {
  Require-Contains $testPurchaseBody 'now = donation_now_ms()' 'admin donation test purchases must use donation_now_ms() instead of second-based now_ts().'
  Require-NotContains $testPurchaseBody 'now = now_ts()' 'admin donation test purchases must not use second-based timestamps.'
}

Throw-IfErrors 'ValidateCopiMineDonationWebTimestampConsistency'
