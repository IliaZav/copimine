$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$treasury = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\player\treasury-pages.js')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
  'idempotency_key: str = Field(min_length=8, max_length=120)',
  'player-bank-transfer',
  'idempotency_key=%s',
  'from_scope not in {"PERSONAL", "TREASURY"}',
  'idempotent'
)) {
  if ($main -notmatch [regex]::Escape($marker)) { $errors.Add("Bank transfer marker missing: $marker") }
}
if ($treasury -notmatch 'idempotency_key:\s*state\.playerBankTransferKey' -or $treasury -notmatch 'state\.playerBankTransferKey\s*\|\|=\s*randomActionKey\("bank-transfer"\)') {
  $errors.Add('The live player transfer form must send a stable idempotency key.')
}
if ($errors.Count) { throw ("Bank transfer idempotency validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'CopiMine bank transfer idempotency contract OK'
