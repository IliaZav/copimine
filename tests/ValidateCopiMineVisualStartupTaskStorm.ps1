$ErrorActionPreference = "Stop"

$targets = @(
    "D:\Desktop\Copimine\opt\copimine\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java",
    "D:\Desktop\Copimine\opt\copimine\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java",
    "D:\Desktop\Copimine\opt\copimine\copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
)

foreach ($file in $targets) {
    $text = Get-Content $file -Raw
    $match = [regex]::Match($text, '(?s)private void repairProtectedBlockVisuals\(\).*?\{(.*?)\n    \}')
    if (-not $match.Success) {
        throw "repairProtectedBlockVisuals() not found in $file"
    }
    $body = $match.Groups[1].Value
    if ($body -notmatch 'loadedChunks') {
        throw "repairProtectedBlockVisuals() in $file does not batch loaded chunks"
    }
    if ($body -match 'for\s*\(.*getLoadedChunks.*repairProtectedBlockVisuals\(world\.getName\(\), chunk\.getX\(\), chunk\.getZ\(\)\);') {
        throw "repairProtectedBlockVisuals() in $file still schedules per-chunk startup work directly"
    }
}

Write-Host "ValidateCopiMineVisualStartupTaskStorm passed."
