param(
  [switch]$SkipServerProperties
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$builder = Join-Path $root 'build-resourcepack.py'
if ($SkipServerProperties) {
  python $builder '--skip-server-properties'
} else {
  python $builder
}
