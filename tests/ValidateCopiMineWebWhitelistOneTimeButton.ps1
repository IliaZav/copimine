. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$appJs = Read-Utf8 $Paths.FrontendApp

Require-Contains $mainPy '@app.post("/api/player/whitelist/request")' 'Web backend must expose the one-time whitelist request endpoint.'
Require-Contains $mainPy "status IN ('PENDING','APPROVED')" 'Whitelist request creation must deduplicate active or approved requests.'
Require-Contains $appJs 'playerRequestWhitelist' 'Frontend cabinet must expose the whitelist request action.'
Require-Contains $appJs 'onclick="playerRequestWhitelist()"' 'Frontend cabinet must render a one-time whitelist request button.'

Throw-IfErrors 'ValidateCopiMineWebWhitelistOneTimeButton'
