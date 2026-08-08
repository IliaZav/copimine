$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($source, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Require-NotRegex([string]$pattern, [string]$message) {
  if ([regex]::IsMatch($source, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'rp:block:break:' 'Breaking a protected RP station must use a distinct action from menu deactivation.'
Require-Contains 'disableRpVotingBlockAsync(player, action.substring("rp:block:break:".length()), true)' 'Confirmed station break must request physical block removal.'
Require-Contains 'disableRpVotingBlockAsync(player, action.substring("rp:block:disable:".length()), false)' 'Menu deactivation must keep the physical block intact.'
Require-Regex 'private void disableRpVotingBlockAsync\(Player player, String blockId, boolean removePhysicalBlock\)[\s\S]*?if \(removePhysicalBlock\)[\s\S]*?setType\(Material\.AIR' 'Physical removal must happen only after the station deactivation transaction.'
Require-Regex 'INSERT INTO election_voting_blocks[\s\S]*?ON CONFLICT\(election_id,world,x,y,z\) DO UPDATE SET active=1' 'Recreating a station at the same coordinates must reactivate its persisted voting block.'
Require-Regex 'UPDATE protected_blocks SET active=0[\s\S]*?reloadProtectedBlocks\(\);[\s\S]*?if \(removePhysicalBlock\)' 'The protection cache must be refreshed before removing the old station block.'
Require-Contains 'reactivateRpVotingBlockAfterPlacementAsync' 'Replacing a physically removed station must reactivate it from the normal block-place path.'
Require-Regex 'public void onBlockPlace\(BlockPlaceEvent event\)[\s\S]*?UUID playerUuid = event\.getPlayer\(\)\.getUniqueId\(\)[\s\S]*?reactivateRpVotingBlockAfterPlacementAsync\(player, ref\)' 'Block placement must trigger station reactivation after the event is accepted.'
Require-Regex 'private void routePollingStationInteractAsync\(Player player, String stationId\)[\s\S]*?boolean votingOpen = rpStation && isDirectVotingOpen\(station\)[\s\S]*?hasElectionAdmin\(player\) && \(player\.isSneaking\(\) \|\| !votingOpen\)[\s\S]*?openRpBlocksMenu\(player, 0\);' 'Administrators need station management when voting is closed, while an open station keeps the normal ballot flow.'

if ($errors.Count -gt 0) {
  throw ("Election RP station replacement validation failed:`n - " + ($errors -join "`n - "))
}

Write-Output 'Election RP station replacement contract: PASS'
