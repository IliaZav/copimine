. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private static final class MenuHolder implements InventoryHolder' 'MenuHolder must be a mutable class, not a record.'
Require-Contains $text 'private Inventory inventory;' 'MenuHolder must keep inventory state on the same holder instance.'
Require-Contains $text 'this.inventory = Bukkit.createInventory(this, size, title);' 'MenuHolder.create() must assign inventory to the current holder.'
Require-Contains $text 'public Inventory getInventory()' 'MenuHolder must implement InventoryHolder through getInventory().'
Require-NotContains $text 'private record MenuHolder' 'Legacy record-based MenuHolder must be removed.'

Throw-IfErrors 'ValidateCopiMineElectionMenuHolderNoNull'
