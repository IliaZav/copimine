$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
if ($source -match 'пїЅ|����') {
  throw 'CopiMineElectionCore.java still contains broken Russian encoding markers.'
}
Write-Host 'ValidateCopiMineElectionCoreNoBrokenRussianEncoding passed.'
