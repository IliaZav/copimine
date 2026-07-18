$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private boolean handleReload\(CommandSender sender\) \{.*?(?=\r?\n\s*private boolean handleResetState)')

if (-not $method.Success -or $method.Value -notmatch 'NarcoticsConfigService candidate = new NarcoticsConfigService\(this\)' -or $method.Value -notmatch 'candidate\.reload\(\)' -or $method.Value -notmatch 'configService = candidate;' -or $method.Value -notmatch 'catch \(Exception error\)') {
    throw 'Narcotics reload must validate a new configuration instance before replacing the live configuration.'
}

Write-Host 'Narcotics atomic reload contract OK'
