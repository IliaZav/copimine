. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'def lock_bank_accounts_ordered(conn: Any, first_account_id: str, second_account_id: str) -> tuple[dict[str, Any], dict[str, Any]]:' 'admin-web must centralize ordered bank row locking to avoid transfer deadlocks.'

$transferBody = Method-Body $mainPy 'def transfer_player_bank_sync(account: dict[str, Any], data: PlayerBankTransferIn) -> dict[str, Any]:'
if (-not $transferBody) {
  $errors.Add('Missing transfer_player_bank_sync in admin-web backend.')
} else {
  Require-Contains $transferBody 'lock_bank_accounts_ordered(conn, str(from_bank["account_id"]), str(to_bank["account_id"]))' 'Player bank transfers must use ordered account locks.'
  Require-NotContains $transferBody 'SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE", (str(from_bank["account_id"])' 'Player bank transfers must not lock sender/recipient rows in handwritten order.'
}

$taxBody = Method-Body $mainPy 'def pay_player_election_tax_sync(account: dict[str, Any], data: PlayerElectionTaxPayIn) -> dict[str, Any]:'
if (-not $taxBody) {
  $errors.Add('Missing pay_player_election_tax_sync in admin-web backend.')
} else {
  if ($taxBody -match 'HTTPException\(status_code=410') {
    Require-Contains $taxBody 'status_code=410' 'Disabled election tax flow must stay explicitly disabled instead of silently mutating balances.'
  } else {
    Require-Contains $taxBody 'lock_bank_accounts_ordered(conn, str(bank["account_id"]), str(president_bank["account_id"]))' 'Election tax payments must use ordered account locks.'
  }
}

Throw-IfErrors 'ValidateCopiMineWebBankLockOrder'
