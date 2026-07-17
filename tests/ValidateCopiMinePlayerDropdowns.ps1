$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js')
$legacyRuntime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/legacy/app-legacy.js')
$treasury = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/player/treasury-pages.js')
$preview = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/preview-player.html')

if ($runtime -notmatch 'function playerSelectOptions\(') {
  throw 'Admin runtime must render reusable player dropdown options.'
}
foreach ($group in @(
  @{ Name = 'online'; Pattern = '\u0421\u0435\u0439\u0447\u0430\u0441 \u043e\u043d\u043b\u0430\u0439\u043d' },
  @{ Name = 'offline'; Pattern = '\u041e\u0444\u0444\u043b\u0430\u0439\u043d' }
)) {
  if ($runtime -notmatch $group.Pattern) {
    throw "Admin player dropdowns must expose the '$($group.Name)' group."
  }
}

$adminSelectIds = @(
  'playerActionTarget',
  'inventoryPlayerInput',
  'appPlayer',
  'repReporter',
  'repTarget',
  'invPlayer',
  'accessPlayer',
  'newAdminUsername'
)
foreach ($id in $adminSelectIds) {
  if ($runtime -notmatch "<select[^>]+id=`"$id`"") {
    throw "Admin action '$id' must use a player select."
  }
  if ($runtime -match "<input[^>]+id=`"$id`"") {
    throw "Admin action '$id' must not accept a free-text player nickname."
  }
  if ($legacyRuntime -notmatch "<select[^>]+id=`"$id`"") {
    throw "Legacy fallback action '$id' must use a player select."
  }
  if ($legacyRuntime -match "<input[^>]+id=`"$id`"") {
    throw "Legacy fallback action '$id' must not accept a free-text player nickname."
  }
}

if ($runtime -match '<datalist[^>]+(?:players|Players)') {
  throw 'Admin runtime must not use player nickname datalists.'
}
if ($legacyRuntime -match '<datalist[^>]+(?:players|Players)') {
  throw 'Legacy fallback must not use player nickname datalists.'
}
if ($treasury -notmatch '<select[^>]+id="bankRecipient"') {
  throw 'Player bank transfer must use a recipient select.'
}
if ($treasury -match '<input[^>]+id="bankRecipient"|bankRecipientList|<datalist') {
  throw 'Player bank transfer must not accept arbitrary or datalist recipient nicknames.'
}
if ($preview -notmatch '<select[^>]+class="preview-input"[^>]*data-preview-player-select') {
  throw 'Player preview must demonstrate the recipient dropdown.'
}

Write-Host 'ValidateCopiMinePlayerDropdowns passed.'
