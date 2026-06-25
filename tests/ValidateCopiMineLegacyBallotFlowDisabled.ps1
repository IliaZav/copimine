$ErrorActionPreference = "Stop"

$file = "D:\Desktop\Copimine\opt\copimine\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$text = Get-Content $file -Raw

$onInteract = [regex]::Match($text, '(?s)public void onInteract\(PlayerInteractEvent e\)\{.*?\n    \}')
if (-not $onInteract.Success) {
    throw "onInteract not found"
}
if ($onInteract.Value -match 'openBallotCandidateHub|openCitizenElectionHub') {
    throw "Legacy ballot or citizen election flow is still active in onInteract"
}

$handle = [regex]::Match($text, '(?s)private void handle\(Player p, ClickType click, String a, String menuId\) throws Exception \{.*?\n    \}')
if (-not $handle.Success) {
    throw "handle not found"
}
$gate = $handle.Value.IndexOf('if(isLegacyElectionAction(a)){redirectLegacyElectionAction(p); return;}')
if ($gate -lt 0) {
    throw "Legacy redirect gate is missing"
}
$legacyBranches = @(
    'if(a.startsWith("vote-seal:"))',
    'if(a.startsWith("vote-deposit:"))',
    'if(a.startsWith("open:station-ballot:"))',
    'if(a.startsWith("open:station-hub:"))'
)
foreach ($branch in $legacyBranches) {
    $index = $handle.Value.IndexOf($branch)
    if ($index -ge 0 -and $index -lt $gate) {
        throw "Legacy ballot branch executes before redirect gate: $branch"
    }
}

Write-Host "ValidateCopiMineLegacyBallotFlowDisabled passed."
