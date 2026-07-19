$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$main = Get-Content (Join-Path $root 'admin-web/backend/main.py') -Raw -Encoding UTF8
$runtime = Get-Content (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js') -Raw -Encoding UTF8
$treasury = Get-Content (Join-Path $root 'admin-web/frontend/assets/js/player/treasury-pages.js') -Raw -Encoding UTF8

if ($main -notmatch 'idempotency_key: str = Field\(default="", max_length=120\)') { throw 'Transfer API must accept old clients that omit idempotency_key.' }
if ($main -notmatch 'legacy-\{secrets\.token_urlsafe\(18\)\}') { throw 'Transfer API must create a server-side idempotency key for old clients.' }
if ($main -notmatch 'counterparty\.owner_name') { throw 'Bank ledger must include a human-readable counterparty name.' }
if ($runtime -notmatch 'paymentHistoryTable\(') { throw 'Player cabinet must render the friendly payment history table.' }
if ($runtime -notmatch 'row\.sign === "negative"') { throw 'Payment history must show negative amounts clearly.' }
if ($treasury -notmatch 'idempotency_key: state\.playerBankTransferKey') { throw 'Current transfer UI must send idempotency_key.' }
Write-Output 'Player payment history and transfer compatibility validation passed.'
