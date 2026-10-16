[CmdletBinding()]
param(
  [string]$EvidenceDirectory='',
  # Explicitly permits a temporary fixture rule change when this isolated
  # world was previously configured to retain every inventory. Production
  # gameplay never changes the rule; finally restores the original value.
  [switch]$AllowFixtureKeepInventoryChange
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event/config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {throw 'Local fixture environment required'}
if([string]::IsNullOrWhiteSpace($EvidenceDirectory)){$EvidenceDirectory=Join-Path $root ('artifacts/end-rift-death-inventory/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))}
$EvidenceDirectory=[IO.Path]::GetFullPath($EvidenceDirectory)
$allowed=[IO.Path]::GetFullPath((Join-Path $root 'artifacts')) + [IO.Path]::DirectorySeparatorChar
if(-not $EvidenceDirectory.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Fixture evidence must stay in this worktree artifacts directory'}
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$rcon=Join-Path $root 'tests/InvokeEndRiftLocalRcon.ps1'
$log=Join-Path $EvidenceDirectory 'item-probe-rcon.log'
function Rc([string]$command) {
  $text=(& $rcon -CommandText $command | Out-String).Trim()
  Add-Content -LiteralPath $log -Value ($command + "`n" + $text) -Encoding UTF8
  return $text
}
function Snapshot([string]$name,[string]$label) {
  $null=Rc 'save-all flush'
  $json=(& node (Join-Path $PSScriptRoot 'ReadEndRiftFixtureInventory.js') $name | Out-String).Trim()
  if($LASTEXITCODE -ne 0){throw 'Fixture NBT read failed'}
  $json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$label-$name.json") -Encoding UTF8
  return ($json | ConvertFrom-Json)
}
function Seed([string]$name) {
  $null=Rc "minecraft:clear $name"
  for($i=0;$i -lt 36;$i++) {
    $slot=if($i -lt 9){"hotbar.$i"}else{"inventory.$($i-9)"}
    $item="minecraft:stone[minecraft:custom_data={endrift_inventory_probe:634251,slot:$i},minecraft:custom_name='`"Probe slot $i`"']"
    $result=Rc "minecraft:item replace entity $name $slot with $item 1"
    if($result -notmatch 'Replaced a slot'){throw "Synthetic slot setup failed: $result"}
  }
  foreach($spec in @(
    @('armor.head','minecraft:diamond_helmet[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:damage=73,minecraft:enchantments={levels:{"minecraft:binding_curse":1}}]'),
    @('armor.chest','minecraft:diamond_chestplate[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:damage=99,minecraft:enchantments={levels:{"minecraft:vanishing_curse":1}}]'),
    @('armor.legs','minecraft:diamond_leggings[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:damage=43]'),
    @('armor.feet','minecraft:diamond_boots[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:damage=7]'),
    @('weapon.offhand','minecraft:shield[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:damage=42,minecraft:custom_name=''"Probe offhand"'']'),
    @('hotbar.0','minecraft:golden_apple[minecraft:custom_data={endrift_inventory_probe:634251},minecraft:custom_name=''"Probe spent apples"'']')
  )) {
    $count=if($spec[0] -eq 'hotbar.0'){4}else{1}
    $result=Rc "minecraft:item replace entity $name $($spec[0]) with $($spec[1]) $count"
    if($result -notmatch 'Replaced a slot'){throw "Synthetic equipment setup failed: $result"}
  }
  $removed=Rc "minecraft:clear $name minecraft:golden_apple 3"
  if($removed -notmatch 'Removed 3 item'){throw "Consumable reduction failed: $removed"}
}
$players=Rc 'minecraft:list'
if($players -notmatch '^There are 4 of a max of \d+ players online: (.+)$'){throw 'Exactly four dedicated local fixture accounts must be online'}
$names=@($Matches[1] -split ',\s*' | Sort-Object)
if(($names -join ',') -ne 'EndRiftTarget1,EndRiftTarget2,EndRiftTarget3,EndRiftTarget4'){throw 'Refusing to change fixture rules while other players are online'}
$status=(Rc 'cmend status') -replace '§.',''
if($status -notmatch 'state=WAVE_1' -or $status -notmatch '\broster=2\b'){throw 'Official two-participant rune-started attempt required'}
$prior=Rc 'execute as EndRiftTarget1 at @s run gamerule keepInventory'
$old=if($prior -match 'true'){'true'}elseif($prior -match 'false'){'false'}else{throw 'Cannot read prior world gamerule'}
if($old -eq 'true' -and -not $AllowFixtureKeepInventoryChange){throw 'Fixture keepInventory is true; opt in to its temporary change before testing real inventory drops'}
$before=@{}
try {
  $null=Rc 'execute as EndRiftTarget1 at @s run gamerule keepInventory false'
  foreach($n in 1..2) {
    $name="EndRiftTarget$n"
    $null=Rc "minecraft:tp $name $(40.5+$n*2) 68 -39.5"
    $null=Rc "heal $name"
    Seed $name
    $before[$name]=Snapshot $name 'before'
    if($before[$name].slotCount -ne 41 -or $before[$name].itemCount -ne 41){throw 'Before snapshot did not contain exactly 41 current items'}
    $null=Rc "tag $name add endriftInventoryDeathFixture"
  }
  $rule=Rc 'execute as EndRiftTarget1 at @s run gamerule keepInventory'
  if($rule -notmatch 'false'){throw 'Fixture keepInventory changed before the lethal callback'}
  $null=Rc 'minecraft:kill @a[tag=endriftInventoryDeathFixture]'
  Start-Sleep -Seconds 3
  foreach($n in 1..2) {
    $name="EndRiftTarget$n"
    $after=Snapshot $name 'after'
    if($after.inventorySha256 -ne $before[$name].inventorySha256){throw "$name retained inventory changed across actual same-tick deaths"}
  }
  $ground=Rc 'execute at EndRiftTarget1 if entity @e[type=minecraft:item,distance=..100,nbt={Item:{components:{"minecraft:custom_data":{endrift_inventory_probe:634251}}}}]'
  if($ground -match 'Test passed'){throw 'Retained fixture item was also dropped'}
  # Negative control proves the same selector detects real ordinary drops.
  # Use the arena's solid floor: outside ground can fall into lava before
  # the three-second delayed-emitter scan, invalidating the positive control.
  # Respawn away from the death location so automatic pickup cannot hide
  # the ordinary-drop positive control before the selector checks it.
  $null=Rc 'minecraft:spawnpoint EndRiftTarget3 64 68 -39'
  $null=Rc 'minecraft:tp EndRiftTarget3 15.5 68 -45.5'
  $null=Rc 'minecraft:clear EndRiftTarget3'
  $null=Rc 'minecraft:item replace entity EndRiftTarget3 hotbar.0 with minecraft:stone[minecraft:custom_data={endrift_inventory_probe:634252}] 3'
  $null=Rc 'minecraft:kill EndRiftTarget3'
  Start-Sleep -Seconds 3
  $ordinary=Snapshot 'EndRiftTarget3' 'ordinary-after'
  if($ordinary.slotCount -ne 0){throw 'Nonparticipant retained a vanilla inventory'}
  $control=Rc 'execute at EndRiftTarget3 if entity @e[type=minecraft:item,distance=..100,nbt={Item:{components:{"minecraft:custom_data":{endrift_inventory_probe:634252}}}}]'
  if($control -notmatch 'Test passed'){throw 'Ordinary drop control failed; no valid ground-scan proof'}
  $null=Rc 'execute at EndRiftTarget3 run minecraft:kill @e[type=minecraft:item,distance=..100,nbt={Item:{components:{"minecraft:custom_data":{endrift_inventory_probe:634252}}}}]'
  @{passed=$true;officialRosterSize=2;sameTickDeaths=2;retainedSlotsPerPlayer=41;retainedCopiesOnGround=0;ordinaryDropsDetected=$true;oldAppleSuppliesNotRestored=$true;nativeRenderingVerified=$false;priorGamerule=$old} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'item-proof.json') -Encoding UTF8
  Write-Output 'ACTUAL_SERVER_ITEM_PROBE_PASS two same-tick participant deaths; 41 slots each byte-equivalent; no retained ground copies; ordinary drops detected'
} finally {
  $null=Rc "execute as EndRiftTarget1 at @s run gamerule keepInventory $old"
  foreach($n in 1..2){$null=Rc "tag EndRiftTarget$n remove endriftInventoryDeathFixture"}
}
