$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
python (Join-Path $root 'build-resourcepack.py')
