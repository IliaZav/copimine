$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$coreSource = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$coreText = Get-Content -Raw -Encoding UTF8 -LiteralPath $coreSource
$errors = New-Object System.Collections.Generic.List[string]

$method = [regex]::Match($text, 'private String createPollingStationFromTarget\(Player p\)throws Exception\{(?<body>[\s\S]*?)\n    private String archivePollingStation', [System.Text.RegularExpressions.RegexOptions]::Singleline).Groups['body'].Value

if (-not [regex]::IsMatch($method, 'String eid=issuableElectionId\(\);[\s\S]*?if\(eid==null\)[\s\S]*?startElection\(p\.getName\(\),\s*168\)', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('Creating a polling station must recover by starting a new draft election when no issuable election exists.')
}

if ([regex]::IsMatch($method, 'if\(eid==null\)throw new SQLException\("')) {
    $errors.Add('Polling-station creation must not report the expected missing-election state as a generic SQL bug.')
}

$coreMethod = [regex]::Match($coreText, 'private void createPollingStationFromTarget\(Player player\) throws Exception \{(?<body>[\s\S]*?)\n    private void createTaxOfficeFromTarget', [System.Text.RegularExpressions.RegexOptions]::Singleline).Groups['body'].Value
if (-not [regex]::IsMatch($coreMethod, 'String electionId = ensureElectionExists\(player\.getName\(\)\)', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('ElectionCore station creation must recover by creating a draft election when none is active.')
}
if ([regex]::IsMatch($coreMethod, 'String electionId = requireActiveElectionId\(\)', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('ElectionCore station creation must not throw when no election is active.')
}

if ($errors.Count -gt 0) {
    throw ("Polling station create recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Polling station create recovery validation passed.'
