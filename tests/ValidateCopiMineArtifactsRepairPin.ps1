. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
Require-Contains $artifacts 'String pinAction = "purchase"' 'Artifact sessions need an explicit PIN action.'
Require-Contains $artifacts 'pinAction = "repair"' 'Repair flow must mark its PIN action.'
Require-Contains $artifacts 'this.openPin(var1, repairItem)' 'Paid repair confirmation must open the PIN pad.'
Require-Regex $artifacts 'executeRepair\([^,]+, [^,]+, [^\)]+\)' 'Repair execution must receive the entered PIN.'
Require-Regex $artifacts 'transferToAccount\(\s*var1,\s*var3' 'Repair transfer must pass the entered PIN to the economy bridge.'
Require-Contains $artifacts 'repairInFlightId' 'Repair flow must reject duplicate submissions while a debit is pending.'
Require-Contains $artifacts 'artifact-repair-" + repairId' 'Repair debit must use a stable idempotency key.'
Throw-IfErrors 'ValidateCopiMineArtifactsRepairPin'
