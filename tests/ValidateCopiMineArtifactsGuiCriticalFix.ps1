param(
    [string]$Root = "D:\Desktop\Copimine\opt\copimine"
)

$ErrorActionPreference = "Stop"

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Needle,
        [string]$Message
    )
    $text = Get-Content -Raw -Encoding UTF8 $Path
    if (-not $text.Contains($Needle)) {
        throw $Message
    }
}

$src = Join-Path $Root "copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java"
$pluginYml = Join-Path $Root "copimine-artifacts\plugin.yml"
$config = Join-Path $Root "copimine-artifacts\config.yml"

Assert-Contains $src "final UUID sessionId;" "GUI holder must keep a stable sessionId."
Assert-Contains $src "event.getView().getTopInventory()" "InventoryClickEvent must target the top GUI inventory."
Assert-Contains $src "rawSlot < 0 || rawSlot >= top.getSize()" "InventoryClickEvent must ignore player inventory slots by rawSlot."
Assert-Contains $src "Bukkit.getScheduler().runTaskLater(this" "InventoryCloseEvent must use delayed cleanup so transitions keep actions."
Assert-Contains $src "sendPlayerShopHelp(player);" "Plain /cmartifacts must show player help instead of opening the shop."
Assert-Contains $src "openArtifactsAdminMenu(player);" "/cmartifacts admin must open the artifacts admin menu."
Assert-Contains $src '"pending".equals(action)' "Main GUI must route pending delivery button."
Assert-Contains $src '"refresh".equals(action)' "GUI must support refresh actions after transitions."
Assert-Contains $src "debugGui(" "GUI diagnostic logging must be available without logging PIN."

Assert-Contains $pluginYml "copimine.artifacts.shop.create" "plugin.yml must declare shop create permission."
Assert-Contains $pluginYml "copimine.artifacts.shop.remove" "plugin.yml must declare shop remove permission."
Assert-Contains $pluginYml "copimine.artifacts.shop.list" "plugin.yml must declare shop list permission."
Assert-Contains $pluginYml "copimine.artifacts.reload" "plugin.yml must declare reload permission."
Assert-Contains $config "debug_gui: false" "config.yml must expose debug_gui: false."

Write-Host "OK: CopiMineArtifacts GUI critical fix validated"
