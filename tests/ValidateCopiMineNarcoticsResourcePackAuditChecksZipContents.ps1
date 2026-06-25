. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$audit = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\resourcepack\NarcoticsResourcePackAudit.java')

Require-Contains $audit 'new ZipFile(zipPath.toFile())' 'Narcotics resource-pack audit must inspect the built zip directly.'
Require-Contains $audit 'validateZipContents(zipPath, expectedZipEntries, errors);' 'Narcotics resource-pack audit must verify that required source assets exist in the built zip.'
Require-Contains $audit 'zip.getEntry(entry) == null' 'Narcotics resource-pack audit must report missing zip entries.'

Throw-IfErrors 'ValidateCopiMineNarcoticsResourcePackAuditChecksZipContents'
