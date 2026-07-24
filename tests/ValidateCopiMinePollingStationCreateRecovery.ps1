$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$coreSource = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$coreText = Get-Content -Raw -Encoding UTF8 -LiteralPath $coreSource
$errors = New-Object System.Collections.Generic.List[string]

$method = [regex]::Match($text, 'private String createPollingStationFromTarget\(Player p\)throws Exception\{(?<body>[\s\S]*?)\n    private String archivePollingStation', [System.Text.RegularExpressions.RegexOptions]::Singleline).Groups['body'].Value

if ([regex]::IsMatch($method, 'startElection\(', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('The retired AdminPlus station creator must not start a legacy election automatically.')
}

if ($method -match 'cmv7_polling_stations|INSERT INTO|UPDATE cmv7_') {
    $errors.Add('The retired AdminPlus station creator must not write legacy election tables.')
}

$coreMethod = [regex]::Match($coreText, 'private void createPollingStationFromTarget\(Player player\) throws Exception \{(?<body>[\s\S]*?)\n    private void createTaxOfficeFromTarget', [System.Text.RegularExpressions.RegexOptions]::Singleline).Groups['body'].Value
if ([regex]::IsMatch($coreMethod, 'ensureElectionExists\(', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('ElectionCore station creation must not auto-create a legacy election.')
}
if (-not [regex]::IsMatch($coreMethod, 'activeRpElectionId\(\)|currentElectionId\(\)', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add('ElectionCore station creation must bind the block to the active RP campaign.')
}

if ($errors.Count -gt 0) {
    throw ("Polling station create recovery validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Polling station create recovery validation passed.'
