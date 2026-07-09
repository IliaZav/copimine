. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $election 'ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class' 'Protected block visuals must use ItemDisplay.'
Require-Contains $election 'entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);' 'Protected block visual ItemDisplay must be fixed.'
Require-Contains $admin 'blockLocation.getWorld().spawn(displayLocation,ItemDisplay.class' 'ATM visuals must use ItemDisplay.'
Require-Regex $artifacts 'spawn\([^;\n]+ItemDisplay\.class' 'Artifact shop visuals must use ItemDisplay.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsUseItemDisplay'
