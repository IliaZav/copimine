$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$groups = Join-Path $root "minecraft\server\plugins\TAB\groups.yml"

$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) {
    $script:errors.Add("Missing file: $path")
    return ""
  }
  return (Get-Content -Raw -Encoding UTF8 -LiteralPath $path) -replace "`r", ""
}

function Require-Contains([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) { $script:errors.Add("$name is missing text: $needle") }
}

function Require-Regex([string]$name, [string]$text, [string]$pattern) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add("$name is missing pattern: $pattern")
  }
}

$java = Read-Text $plugin
$tab = Read-Text $groups

Require-Contains "sidebar objective" $java 'SIDEBAR_OBJECTIVE = "cmulive"'
Require-Contains "sidebar empty state" $java "sidebarNoElectionLines"
Require-Contains "sidebar snapshot" $java "updateGlobalSidebar"
Require-Contains "sidebar renderer" $java "renderSidebarObjective"
Require-Contains "sidebar quiet toggle" $java "citizen:sidebar-hide"
Require-Contains "sidebar quiet toggle" $java "citizen:sidebar-show"
Require-Contains "citizen hub" $java "openCitizenElectionHub"
Require-Regex "sidebar hide returns to citizen hub" $java 'a\.equals\("citizen:sidebar-hide"\)\)\{sidebarHidden\.add[\s\S]*hideSidebar\(p,true\)[\s\S]*openCitizenElectionHub\(p\);return;\}'
Require-Regex "sidebar show returns to citizen hub" $java 'a\.equals\("citizen:sidebar-show"\)\)\{sidebarHidden\.remove[\s\S]*sidebarPersonal\.add[\s\S]*updateSidebar\(p,true\)[\s\S]*openCitizenElectionHub\(p\);return;\}'
Require-Regex "sidebar uses latest election fallback" $java 'String eid=activeOrLatestElectionId\(\);'

Require-Contains "admin delegation" $java 'plugin.getClass().getMethod("openAdminElectionHub",Player.class).invoke(plugin,p);'
Require-Contains "president mandate cooldown" $java "PRESIDENT_MANDATE_HOURLY_V3"
Require-Contains "president mandate cooldown" $java "presidentHourlyAnnouncement"
Require-Contains "president mandate cooldown" $java "3600000L"
Require-Contains "president mandate item" $java "createPresidentMandateItem"

Require-Contains "AR strict counting" $java "private int countArItem(ItemStack it)"
Require-Contains "AR strict counting" $java "return isOfficialAr(it)?it.getAmount():0;"
Require-Contains "AR official gate" $java 'return "certified".equals(meta.getPersistentDataContainer().get(arKey("type"),org.bukkit.persistence.PersistentDataType.STRING));'
Require-Contains "AR batch metadata" $java "AR_STACK_BATCH_LABEL"
Require-Contains "AR batch metadata" $java "AR_STACK_VISIBLE_ID"
Require-Contains "AR batch metadata" $java "AR_STACK_UNIQUE_COUNT"
Require-Contains "AR batch metadata" $java "ensureArStackBatch"

Require-Contains "TAB admin group" $tab "admin:"
Require-Contains "TAB president group" $tab "president:"
Require-Contains "TAB chair group" $tab "cik_chair:"

if ($errors.Count -gt 0) {
  throw ("Election/AR clean UX validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Election/AR clean UX validation passed."
