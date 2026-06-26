. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy

$transferBody = Method-Body $economy 'public TxnResult transferWithPin(UUID fromUuid, String fromName, UUID toUuid, String toName, long amount, String pin, String idempotencyKey, String action, String details) {'
if ($null -eq $transferBody) {
  $errors.Add('BankService.transferWithPin() method not found.')
} else {
  Require-Contains $transferBody 'if (fromUuid.equals(toUuid)) {' 'transferWithPin() must reject self-transfers explicitly.'
  Require-Contains $transferBody 'TxnResult replay = replayTransferIfCommitted(connection, txKey, fromId, toId, amount);' 'transferWithPin() must replay committed idempotent transfers before mutating balances.'
  Require-Contains $transferBody 'if (fromId.compareTo(toId) <= 0) {' 'transferWithPin() must lock accounts in deterministic order to reduce deadlocks.'
  Require-Contains $transferBody 'targetBefore = scalarLong(connection, "SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE", toId);' 'transferWithPin() must row-lock both accounts explicitly.'
  Require-Contains $transferBody 'String txId = txKey;' 'transferWithPin() must reuse the stable idempotency key as transfer tx id.'
}

Require-Contains $economy 'private TxnResult replayTransferIfCommitted(Connection connection, String txKey, String fromAccountId, String toAccountId, long amount) throws Exception {' 'EconomyCore must provide a replay helper for committed transfers.'
Require-Contains $economy 'return new TxnResult(false, "IDEMPOTENCY_CONFLICT"' 'Transfer replay helper must reject cross-operation idempotency key reuse with an explicit idempotency conflict.'

Throw-IfErrors 'ValidateCopiMineEconomyTransferLockOrder'
