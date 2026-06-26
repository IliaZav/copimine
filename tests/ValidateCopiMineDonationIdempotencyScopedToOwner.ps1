. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$economy = Read-Utf8 $Paths.Economy

Require-Contains $mainPy 'SELECT id,player_uuid,player_name,provider,amount,amount_rub,donation_units,status,qr_payload,created_at,expires_at,updated_at FROM donation_payment_sessions WHERE idempotency_key=%s LIMIT 1' 'admin-web donation session idempotency replay must read the session owner.'
Require-Contains $mainPy 'if str(row.get("player_uuid") or "") != player_uuid:' 'admin-web must scope donation session idempotency replays to the same player.'
Require-Contains $mainPy 'SELECT id,player_uuid,item_id,status,price_donation,created_at FROM donation_purchases WHERE idempotency_key=%s LIMIT 1' 'admin-web donation purchase idempotency replay must read the purchase owner.'
Require-Contains $economy 'if (!uuid.equalsIgnoreCase(string(existing.get("player_uuid")))) {' 'EconomyCore donation session idempotency replays must stay owner-scoped.'
Require-Contains $economy 'if (!playerUuid.toString().equalsIgnoreCase(string(existing.get("player_uuid")))) {' 'EconomyCore donation purchase idempotency replays must stay owner-scoped.'
Require-Contains $economy 'belongs to another player' 'EconomyCore must fail loudly on cross-owner idempotency key reuse.'

Throw-IfErrors 'ValidateCopiMineDonationIdempotencyScopedToOwner'
