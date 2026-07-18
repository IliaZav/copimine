$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

foreach ($definition in @(
    @{ Name = 'taxMinAmount'; ReturnValue = 'return 0;' },
    @{ Name = 'taxMaxAmount'; ReturnValue = 'return 5;' }
)) {
    $method = [regex]::Match($source, "(?s)private int $($definition.Name)\(\) \{.*?(?=\r?\n\s*private )")
    if (-not $method.Success -or $method.Value -notmatch [regex]::Escape($definition.ReturnValue)) {
        throw "$($definition.Name) must enforce the immutable 0-5 AR tax range."
    }
}

$due = [regex]::Match($source, '(?s)private long dueTaxAmount\(String playerUuid, String taxId, Map<String, Object> tax\) throws Exception \{.*?(?=\r?\n\s*private int taxMinAmount)')
if (-not $due.Success -or $due.Value -notmatch 'Math\.min\(5L,') {
    throw 'Tax due calculation must clamp legacy or tampered tax records to 5 AR.'
}

$presets = [regex]::Match($source, '(?s)private List<Integer> taxAmountPresets\(int currentAmount\) \{.*?(?=\r?\n\s*private int taxPeriodHours)')
if (-not $presets.Success -or $presets.Value -match 'presets\.add\(10\)' -or $presets.Value -match 'presets\.add\(50\)') {
    throw 'President tax GUI must not offer amounts outside 0-5 AR.'
}

if ($source -match 'Math\.min\(50, amount\)') {
    throw 'No president-tax confirmation path may display or accept a legacy 0-50 AR amount.'
}

$webPath = Join-Path $PSScriptRoot '..\admin-web\backend\main.py'
$web = Get-Content -LiteralPath $webPath -Raw -Encoding UTF8
if ($web -notmatch 'def normalize_president_tax_amount\(amount: Any\) -> int:' -or
    $web -notmatch 'max\(0, min\(5, int\(amount or 0\)\)\)' -or
    $web -match 'int\(tax\.get\("amount"\) or 0\) - paid') {
    throw 'Website tax display and payment must clamp records to the same 0-5 AR range.'
}

Write-Host 'President tax hard limit contract OK'
