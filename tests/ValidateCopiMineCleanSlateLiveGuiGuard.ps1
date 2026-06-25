$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$frontend = Join-Path $root "admin-web\frontend\assets\app.js"
$backend = Join-Path $root "admin-web\backend\main.py"

$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) {
    $script:errors.Add("Missing file: $path")
    return ""
  }
  return (Get-Content -Raw -Encoding UTF8 -LiteralPath $path) -replace "`r", ""
}

function Require-Contains([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) {
    $script:errors.Add("$name is missing text: $needle")
  }
}

function Require-Regex([string]$name, [string]$text, [string]$pattern) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add("$name is missing pattern: $pattern")
  }
}

$java = Read-Text $plugin
$js = Read-Text $frontend
$py = Read-Text $backend

Require-Regex "plugin client brand listener" $java "implements Listener, CommandExecutor, TabCompleter, PluginMessageListener"
Require-Contains "plugin client brand channel" $java 'registerIncomingPluginChannel(this, "minecraft:brand", this)'
Require-Contains "plugin client brand unregister" $java 'unregisterIncomingPluginChannel(this, "minecraft:brand", this)'
Require-Contains "plugin client brand denylist" $java "BLOCKED_CLIENT_BRAND_TOKENS"
foreach ($token in @("meteor","wurst","liquidbounce","aristois","impact","vape","baritone","seedcracker","xray","freecam","rusherhack","future")) {
  Require-Contains "plugin client brand denylist" $java $token
}
Require-Regex "plugin brand decoder" $java "onPluginMessageReceived[\s\S]*decodeClientBrand[\s\S]*kickPlayer"
Require-Contains "plugin player GUI" $java "openClientGuard"
Require-Contains "plugin player GUI route" $java 'open:client-guard'

Require-Contains "plugin AR transfer drop claims" $java "ArDropClaim"
Require-Contains "plugin AR transfer drop claims" $java "arTransferClaims"
Require-Contains "plugin AR transfer drop claims" $java "AR_TRANSFER_DROP_CLAIM"
Require-Contains "plugin AR transfer pickup" $java "AR_TRANSFER_CLAIMED"
Require-Regex "plugin AR pickup uses claim" $java 'retagArOwner\(e\.getItem\(\),p,\s*"pickup",\s*claimArTransfer'

Require-Contains "economy simple section" $java "openEconomyBasic"
Require-Contains "economy advanced section" $java "openEconomyAdvanced"
Require-Regex "economy root split" $java "private void openEconomy\(Player p\)[\s\S]*open:economy-basic[\s\S]*open:economy-advanced"
Require-Regex "economy advanced contains DB and scans" $java "private void openEconomyAdvanced\(Player p\)[\s\S]*open:db-health[\s\S]*ar:scan-deep"
Require-Regex "economy basic keeps safe daily actions" $java "private void openEconomyBasic\(Player p\)[\s\S]*open:ar-top[\s\S]*ar:sync[\s\S]*open:ar-events:TRANSFER"

Require-Contains "election grouped operations" $java "openElectionOperations"
Require-Contains "election grouped ledgers" $java "openElectionLedgers"
Require-Contains "election advanced recovery" $java "openElectionRecoveryAdvanced"
Require-Regex "elections root split" $java "private void openElections\(Player p\)[\s\S]*open:election-operations[\s\S]*open:election-ledgers[\s\S]*open:election-recovery-advanced"

Require-Contains "players daily section" $java "openPlayersDaily"
Require-Contains "players advanced section" $java "openPlayersAdvanced"
Require-Regex "players root split" $java "private void openPlayers\(Player p\)[\s\S]*open:players-daily[\s\S]*open:players-advanced[\s\S]*open:client-guard"

Require-Contains "frontend live stream" $js "startLivePanelStream"
Require-Contains "frontend live stream" $js "stopLivePanelStream"
Require-Regex "frontend live stream endpoint" $js 'new EventSource\("/api/events/stream\?_fresh="\s*\+\s*Date\.now\(\),\s*\{\s*withCredentials:\s*true\s*\}\)'
Require-Regex "frontend live stream boot" $js "bootAuthed[\s\S]*startLivePanelStream\(\)"
Require-Regex "frontend live stream logout" $js "logout[\s\S]*stopLivePanelStream\(\)"
Require-Contains "backend events stream" $py '@app.get("/api/events/stream")'

if ($errors.Count -gt 0) {
  throw ("Clean-slate/live/GUI/client-guard validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Clean-slate/live/GUI/client-guard validation passed."
