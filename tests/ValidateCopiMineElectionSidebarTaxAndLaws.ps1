$ErrorActionPreference = 'Stop'
$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$sidebar = [regex]::Match($source, '(?s)private void renderSidebar\(Player player, LiveSnapshot snap\) \{.*?(?=\r?\n\s*private void clearSidebar)')
if (-not $sidebar.Success) { throw 'Sidebar renderer was not found.' }
if ($sidebar.Value -notmatch 'snap\.taxAmount\(\)') { throw 'Sidebar must display the current tax amount.' }
if ($sidebar.Value -notmatch 'snap\.laws\(\)\.stream\(\)\.limit\(3\)') { throw 'Sidebar laws must be bounded and displayed.' }
if ($sidebar.Value -notmatch 'snap\.candidates\(\)\.stream\(\)\.limit\(2\)') { throw 'Sidebar candidate list must be bounded.' }
Write-Host 'Election sidebar tax and laws contract OK'
