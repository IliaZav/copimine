. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$html = Read-Utf8 (Join-Path $root 'admin-web\frontend\index.html')
$js = Read-Utf8 $Paths.FrontendApp

$forbidden = @(
  'MinePro123',
  'BlockLegend',
  'CraftKing',
  'SkyMaster',
  'GreenStone',
  'VoteRunner',
  'BankerAR',
  'ArtifactFan',
  'MarketWatch',
  'Онлайн · 1.21.x · Paper',
  'play.copimine.ru'
)

foreach ($token in $forbidden) {
  if ($html.Contains($token)) {
    $errors.Add("Guest frontend still contains fake hardcoded data token: $token")
  }
}

Require-Contains $js 'safeApi("/api/public/status"' 'Guest frontend must load public status from backend instead of hardcoded fake data.'
Require-Contains $js 'safeApi("/api/public/config"' 'Guest frontend must load public config from backend instead of hardcoded fake data.'

Throw-IfErrors 'ValidateCopiMineWebNoFakeHardcodedData'
