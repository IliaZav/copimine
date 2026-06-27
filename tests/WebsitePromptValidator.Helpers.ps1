. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"

function Invoke-ValidatorSet([string]$name, [string[]]$validators) {
  foreach ($validator in $validators) {
    & (Join-Path $PSScriptRoot $validator)
  }
  Write-Host ($name + ' passed.')
}
