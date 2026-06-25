. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$main = Read-Utf8 $Paths.MainPy

Require-Contains $main '/api/public/status' 'main.py must expose the public status endpoint.'
Require-Contains $main '/api/public/modpack' 'main.py must expose the public modpack endpoint.'
Require-Regex $main 'read_donation_admin_overview_sync[\s\S]{0,2000}LIMIT %s' 'Donation admin overview queries must stay bounded by LIMIT.'
Require-Regex $main 'read_artifact_rows[\s\S]{0,2000}LIMIT %s' 'Artifact dashboard queries must stay bounded by LIMIT.'
Require-Regex $main 'read_donation_sessions_sync[\s\S]{0,1200}LIMIT %s' 'Donation session queries must stay bounded by LIMIT.'

Throw-IfErrors 'ValidateCopiMineNoUnboundedSqlForDashboards'
