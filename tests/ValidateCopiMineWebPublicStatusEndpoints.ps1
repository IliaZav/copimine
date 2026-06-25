. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$main = Read-Utf8 $Paths.MainPy

Require-Contains $main '@app.get("/api/public/config")' 'Backend must expose a public config endpoint for the guest homepage.'
Require-Contains $main '@app.get("/api/public/status")' 'Backend must expose a public status endpoint for the guest homepage.'
Require-Contains $main 'def public_site_config_sync()' 'Backend must implement public site config sync helper.'
Require-Contains $main 'def public_site_status_sync()' 'Backend must implement public site status sync helper.'
Require-Contains $main '"samplePlayers": online_players[:12]' 'Public status endpoint must return only a limited safe online-player sample.'

Throw-IfErrors 'ValidateCopiMineWebPublicStatusEndpoints'
