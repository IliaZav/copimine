$ErrorActionPreference = 'Stop'

$dbPath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java'
$configPath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java'
$yamlPath = Join-Path $PSScriptRoot '..\copimine-narcotics\config.yml'
$db = Get-Content -LiteralPath $dbPath -Raw -Encoding UTF8
$config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
$yaml = Get-Content -LiteralPath $yamlPath -Raw -Encoding UTF8

if ($db -notmatch 'configService\.asyncThreads\(\)' -or $db -notmatch 'configService\.asyncQueueCapacity\(\)') {
    throw 'Narcotics database executor must use the configured thread count and queue capacity.'
}

if ($config -notmatch 'int asyncQueueCapacity\(\)' -or $config -notmatch 'runtime\.async_queue_capacity') {
    throw 'Narcotics configuration must expose a bounded database queue capacity.'
}

if ($yaml -notmatch 'async_queue_capacity:\s*\d+') {
    throw 'Narcotics default configuration must declare the database queue capacity.'
}

Write-Host 'Narcotics database executor contract OK'
