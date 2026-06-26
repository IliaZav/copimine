. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy

$creditBody = Method-Body $economy 'public TxnResult credit(UUID toUuid, String toName, long amount, String idempotencyKey, String action, String details) {'
if ($null -eq $creditBody) {
  $errors.Add('BankService.credit() method not found.')
} else {
  Require-Contains $creditBody 'replayCreditIfCommitted(connection, txKey, accountId)' 'credit() must replay committed idempotent credits before mutating balance.'
  Require-Contains $creditBody 'String txKey = first(idempotencyKey, "credit-" + UUID.randomUUID())' 'credit() must derive one stable tx key per call.'
  Require-Contains $creditBody 'String txId = txKey;' 'credit() must persist the stable idempotency key as tx id.'
  Require-Contains $creditBody 'if (replay != null) {' 'credit() must return replayed committed results instead of double-crediting.'
}

Require-Contains $economy 'private TxnResult replayCreditIfCommitted(Connection connection, String txKey, String accountId) throws Exception {' 'EconomyCore must provide a replay helper for committed credit operations.'
Require-Contains $economy 'return new TxnResult(false, "IDEMPOTENCY_CONFLICT"' 'Credit replay helper must reject cross-account idempotency key reuse with an explicit idempotency conflict.'

Throw-IfErrors 'ValidateCopiMineEconomyCreditIdempotent'
