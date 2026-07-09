. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$java = Read-Utf8 $Paths.Admin
$frontend = Read-FrontendBundle
$backend = Read-Utf8 $Paths.MainPy

Require-Regex $java 'implements Listener, CommandExecutor, TabCompleter, PluginMessageListener' 'AdminPlus must keep the client brand listener.'
Require-Contains $java 'registerIncomingPluginChannel(this, "minecraft:brand", this)' 'Client brand channel must be registered.'
Require-Contains $java 'unregisterIncomingPluginChannel(this, "minecraft:brand", this)' 'Client brand channel must be unregistered.'
Require-Contains $java 'BLOCKED_CLIENT_BRAND_TOKENS' 'Client brand denylist must exist.'
foreach ($token in @("meteor","wurst","liquidbounce","aristois","impact","vape","baritone","seedcracker","xray","freecam","rusherhack","future")) {
  Require-Contains $java $token "Client brand denylist token missing: $token"
}
Require-Regex $java 'onPluginMessageReceived[\s\S]*decodeClientBrand[\s\S]*kickPlayer' 'Client brand decoder must be able to kick blocked clients.'
Require-Contains $java 'openClientGuard' 'Player GUI must expose client guard diagnostics.'
Require-Contains $java 'open:client-guard' 'Player GUI route must expose client guard diagnostics.'

Require-Contains $java 'ArDropClaim' 'AR drop claim tracking must exist.'
Require-Contains $java 'arTransferClaims' 'AR transfer claim map must exist.'
Require-Contains $java 'AR_TRANSFER_DROP_CLAIM' 'AR transfer drop claim audit must exist.'
Require-Contains $java 'AR_TRANSFER_CLAIMED' 'AR transfer claimed audit must exist.'
Require-Regex $java 'retagArOwner\(e\.getItem\(\),p,\s*"pickup",\s*claimArTransfer' 'AR pickup must use transfer claims.'

Require-Contains $java 'private CopiMineEconomyCore economyCore()' 'AdminPlus must resolve EconomyCore through a bridge method.'
Require-Contains $java 'economy.openAdminEconomyHub(p)' 'AdminPlus economy routes must delegate to EconomyCore.'
Require-Contains $java 'legacyOpenEconomyBasic' 'Legacy economy GUI must be isolated behind a legacy name.'
Require-Contains $java 'legacyOpenEconomyAdvanced' 'Legacy advanced economy GUI must be isolated behind a legacy name.'

Require-Contains $java 'openElectionOperations' 'Election operations section must exist.'
Require-Contains $java 'openElectionLedgers' 'Election ledgers section must exist.'
Require-Contains $java 'openElectionRecoveryAdvanced' 'Election recovery section must exist.'
Require-Regex $java 'private void openElections\(Player p\)[\s\S]*open:election-operations[\s\S]*open:election-ledgers[\s\S]*open:election-recovery-advanced' 'Election root must stay grouped.'

Require-Contains $java 'openPlayersDaily' 'Players daily section must exist.'
Require-Contains $java 'openPlayersAdvanced' 'Players advanced section must exist.'
Require-Regex $java 'private void openPlayers\(Player p\)[\s\S]*open:players-daily[\s\S]*open:players-advanced[\s\S]*open:client-guard' 'Players root must stay grouped.'

Require-Contains $frontend 'startLivePanelStream' 'Frontend live stream starter must exist.'
Require-Contains $frontend 'stopLivePanelStream' 'Frontend live stream stopper must exist.'
Require-Regex $frontend 'new EventSource\("/api/events/stream\?_fresh="\s*\+\s*Date\.now\(\),\s*\{\s*withCredentials:\s*true\s*\}\)' 'Frontend live stream endpoint must use authenticated SSE.'
Require-Regex $frontend 'bootAuthed[\s\S]*startLivePanelStream\(\)' 'Authed boot must start live stream.'
Require-Regex $frontend 'logout[\s\S]*stopLivePanelStream\(\)' 'Logout must stop live stream.'
Require-Contains $backend '@app.get("/api/events/stream")' 'Backend events stream endpoint must exist.'

Throw-IfErrors 'ValidateCopiMineCleanSlateLiveGuiGuard'
