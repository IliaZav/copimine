param(
    [string]$Root = "D:\Desktop\Copimine\opt\copimine"
)

$ErrorActionPreference = "Stop"

$source = Join-Path $Root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$pluginYml = Join-Path $Root "copimine-admin-plugin\plugin.yml"
$activePluginDir = Join-Path $Root "minecraft\server\plugins"

if (-not (Test-Path -LiteralPath $source)) {
    throw "AdminPlus source not found: $source"
}
if (-not (Test-Path -LiteralPath $pluginYml)) {
    throw "AdminPlus plugin.yml not found: $pluginYml"
}

$code = Get-Content -LiteralPath $source -Raw -Encoding UTF8
$plugin = Get-Content -LiteralPath $pluginYml -Raw -Encoding UTF8
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
    if (-not $code.Contains($needle)) {
        $script:errors.Add($message)
    }
}

function Require-Regex([string]$pattern, [string]$message) {
    if (-not [regex]::IsMatch($code, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $script:errors.Add($message)
    }
}

Require-Contains "openCitizenElectionHub" "Missing citizen election hub for normal players."
Require-Contains "openCitizenCandidates" "Missing citizen candidate/results GUI."
Require-Contains "isElectionUiItem" "Missing election item interaction detector."
Require-Contains "citizen:sidebar-hide" "Players cannot hide their own election sidebar through GUI."
Require-Contains "citizen:sidebar-show" "Players cannot restore their own election sidebar through GUI."
Require-Contains "open:citizen-election" "Citizen GUI is not wired into inventory actions."
Require-Regex "onInteract[\s\S]*isElectionUiItem[\s\S]*openCitizenElectionHub" "PlayerInteractEvent does not route election items to the citizen GUI."

if ($code.Contains('"menu","election","sidebar","ar","check","old"')) {
    $errors.Add("Top-level tab completion still exposes the removed old admin bridge.")
}

if ($code -match "/cmvote|/cmapply") {
    $errors.Add("AdminPlus still advertises legacy player election commands.")
}
if ($plugin -match '(?m)^  cmvote\s*:|(?m)^  cmapply\s*:|usage: /cmvote|usage: /cmapply') {
    $errors.Add("Single AdminPlus plugin.yml still registers legacy player election commands.")
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$playerElectionCommands = @('cmvote', 'cmapply')
Get-ChildItem -LiteralPath $activePluginDir -File -Filter 'CopiMine*.jar' | ForEach-Object {
    $jar = $_
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $entry = $zip.GetEntry('plugin.yml')
        if ($null -eq $entry) { return }
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
        $yaml = $reader.ReadToEnd()
        $reader.Dispose()
        foreach ($cmd in $playerElectionCommands) {
            if ([regex]::IsMatch($yaml, "(?m)^  $cmd\s*:")) {
                $errors.Add("Active plugin $($jar.Name) still registers player election command /$cmd.")
            }
        }
        if ($jar.Name -eq 'CopiMineUltimateAdmin.jar' -and [regex]::IsMatch($yaml, "(?m)^  election\s*:")) {
            $errors.Add("Core shim still registers the old /election command with vote/president aliases.")
        }
    } finally {
        $zip.Dispose()
    }
}

$coreJar = Join-Path $activePluginDir 'CopiMineUltimateAdmin.jar'
if (Test-Path -LiteralPath $coreJar) {
    $bytes = [IO.File]::ReadAllBytes($coreJar)
    $latin = [Text.Encoding]::GetEncoding('ISO-8859-1').GetString($bytes)
    if ($latin.Contains('cmvote')) {
        $errors.Add("Active CopiMineUltimateAdmin.jar still contains the legacy cmvote bridge string.")
    }
}

Require-Contains "graphBar(" "Sidebar still uses the old primitive bar instead of graphBar()."
Require-Contains "percent(" "Sidebar does not show vote percentages."
Require-Contains "org.bukkit.scoreboard.number.NumberFormat" "Sidebar number hiding does not support modern Bukkit/Paper NumberFormat."
Require-Contains "io.papermc.paper.scoreboard.numbers.NumberFormat" "Sidebar number hiding does not keep the legacy Paper fallback."
Require-Regex "tryBlankNumbers\(o\)" "Sidebar objective is not configured to hide red score numbers."

Require-Contains "CIK_CHAIR" "Election role model does not distinguish the CIK chair from regular curators."
Require-Contains "curatorRoleLabel" "Curator role label helper is missing."
Require-Contains "hasCitizenBallot" "Citizen hub does not check whether the player has a ballot."
Require-Contains "hasCitizenVote" "Citizen hub does not check whether the player has already voted."
Require-Contains "candidateRowsForElection" "Candidate/result query is not shared between sidebar and citizen GUI."
Require-Regex 'hasCitizenVote[\s\S]*cmv731_votes' "Citizen vote status does not read the active ElectionFlow vote ledger cmv731_votes."
Require-Regex 'resetVotes[\s\S]*cmv731_votes[\s\S]*cmv7_ballot_issues' "Vote reset does not clear the active ElectionFlow votes and ballot issues."
Require-Regex 'fullReset[\s\S]*cmv731_votes[\s\S]*cmv7_ballot_issues[\s\S]*cmv731_vote_sessions' "Full election reset does not clear the active ElectionFlow vote ledger, ballots, and vote sessions."

Require-Contains "cmv7_president_state" "President state table is missing."
Require-Contains "assignPresident(" "Election winners are not assigned through a single president state flow."
Require-Contains "retireActivePresident" "Old president is not retired before assigning a new winner."
Require-Regex 'stopElection[\s\S]*assignPresident' "Finishing an election does not assign the winner through president state."
Require-Regex 'setWinner[\s\S]*assignPresident' "Manual winner selection does not sync president state."
Require-Regex 'fullReset[\s\S]*retireActivePresident' "Full election reset does not retire the active president."
Require-Regex 'dispatchIfExists\("lp user "\+name\+" parent remove president"\)' "President retirement does not remove the LuckPerms president parent."
Require-Regex 'dispatchIfExists\("lp user "\+name\+" parent add president"\)' "President assignment does not add the LuckPerms president parent through the shared dispatcher."
Require-Contains "activePresidentName()" "Election UI does not expose the active president state."

Require-Contains 'new NamespacedKey("copimineelectionflow", key)' "AdminPlus does not write official ElectionFlow item PDC keys."
Require-Regex 'tagElectionItem\(meta,\s*"ballot"\s*,\s*eid\s*,\s*t\.getUniqueId\(\)\.toString\(\)\s*,\s*id\)' "AdminPlus-issued ballots are not tagged as official ElectionFlow ballots."
Require-Regex 'tagElectionItem\(m,\s*"application_book"\s*,\s*eid\s*,\s*t\.getUniqueId\(\)\.toString\(\)\s*,\s*issueId\)' "AdminPlus-issued application books are not tagged as official ElectionFlow application books with the issue registry id."
Require-Regex 'electionItemString\(old,\s*"type"\)[\s\S]*"application_book"' "AdminPlus does not skip official ElectionFlow application books, risking duplicate application submissions."

if (-not ($plugin.Contains("7.5.9-core-shim-merged") -or $plugin.Contains("7.7.0-release-readiness") -or $plugin.Contains("7.8.0-ar-economy-release") -or $plugin.Contains("7.9.0-election-emergency-gui") -or $plugin.Contains("8.0.0-hardening-auth-economy-players") -or $plugin.Contains("8.1.0-first-run-performance") -or $plugin.Contains("8.2.0-db-gui-ux") -or $plugin.Contains("8.3.0-station-gui") -or $plugin.Contains("8.4.0-big-admin-gui") -or $plugin.Contains("8.5.0-discord-ballot-book") -or $plugin.Contains("9.0.0-election-rebuild") -or $plugin.Contains("9.1.0-postgres-v4") -or $plugin.Contains("9.2.0-postgres-v4-balance-credit"))) {
    $errors.Add("plugin.yml version was not bumped to a known merged election experience release.")
}

if ($errors.Count -gt 0) {
    throw ("Election experience validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Election experience validation passed: citizen UI, CIK chair, sidebar graphs, no old player command advertising."
