$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
if ($main -match 'if\s*\(!\(sender instanceof Player\)\)\s*\{') {
  throw 'Global player-only command gate found.'
}
foreach ($marker in @('handleReload(CommandSender sender)', 'handleResetState(CommandSender sender, String[] args)', 'handleSelfCheck(CommandSender sender)', 'handleGive(CommandSender sender, String[] args)', 'requirePlayer(sender)')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Console support marker missing: $marker" }
}
Write-Host 'Console support validation passed.'
