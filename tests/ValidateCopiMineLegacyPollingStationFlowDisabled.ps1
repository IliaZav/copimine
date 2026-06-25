$ErrorActionPreference = "Stop"

$file = "D:\Desktop\Copimine\opt\copimine\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$text = Get-Content $file -Raw

$onInteract = [regex]::Match($text, '(?s)public void onInteract\(PlayerInteractEvent e\)\{.*?\n    \}')
if (-not $onInteract.Success) {
    throw "onInteract not found"
}

$forbidden = @(
    'depositSealedBallotAtStation',
    'sendPollingStationCitizenInfo',
    'openPollingStationHub'
)

foreach ($name in $forbidden) {
    if ($onInteract.Value -match [regex]::Escape($name)) {
        throw "Legacy polling-station flow is still active in onInteract: $name"
    }
}

$placeHandler = [regex]::Match($text, '(?s)public void onPollingStationPlace\(BlockPlaceEvent e\)\{.*?\n    \}')
if (-not $placeHandler.Success) {
    throw "onPollingStationPlace not found"
}
if ($placeHandler.Value -match 'cmv7_polling_stations|INSERT INTO cmv7_polling_stations') {
    throw "Legacy polling station creation is still active"
}

Write-Host "ValidateCopiMineLegacyPollingStationFlowDisabled passed."
