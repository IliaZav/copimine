$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

function Get-MethodBody {
    param(
        [string] $Start,
        [string] $End
    )

    $match = [regex]::Match($source, "(?s)$Start.*?(?=\r?\n    private void $End)")
    if (-not $match.Success) {
        throw "Could not locate method beginning with: $Start"
    }
    return $match.Value
}

$adminMenu = Get-MethodBody 'private void openPresidentAdminMenu\(Player player, int selectedPeriodHours\) \{' 'openPresidentMandateMenu'
$mandateMenu = Get-MethodBody 'private void openPresidentMandateMenu\(Player player\) \{' 'openPresidentTaxRoster'

foreach ($menu in @($adminMenu, $mandateMenu)) {
    $inventoryCreation = $menu.IndexOf('Inventory inv = holder.create(')
    $firstButton = $menu.IndexOf('setButton(holder,')
    if ($inventoryCreation -lt 0 -or $firstButton -lt 0 -or $inventoryCreation -gt $firstButton) {
        throw 'President tax menu must create its inventory before registering buttons.'
    }
}

if ($mandateMenu -notmatch 'setButton\(holder, 45, Material\.LIME_DYE,.*tax-roster:paid' -or
    $mandateMenu -notmatch 'setButton\(holder, 46, Material\.GRAY_DYE,.*tax-roster:unpaid') {
    throw 'Mandate menu must keep paid and unpaid roster actions outside tax amount slots.'
}

if ($mandateMenu -match 'setButton\(holder, 42, Material\.LIME_DYE,.*tax-roster:paid' -or
    $mandateMenu -match 'setButton\(holder, 43, Material\.RED_DYE,.*tax-roster:unpaid') {
    throw 'Mandate roster controls collide with the 0-5 AR tax amount slots.'
}

Write-Host 'President tax roster GUI contract OK'
