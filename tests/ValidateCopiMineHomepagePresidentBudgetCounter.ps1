. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$frontend = Read-FrontendBundle
$server = Read-Utf8 $Paths.FrontendServer

Require-Contains $server 'id="presidentBudgetCounter"' 'Server page must expose the president budget counter mount.'
Require-Contains $server 'id="presidentBudgetDetail"' 'Server page must expose the president budget detail mount.'
Require-Contains $frontend 'renderBudget(payload = {})' 'Frontend must render the public president budget card.'
Require-Contains $frontend 'animateCounter(balance);' 'Homepage budget counter must animate from real payload data.'
Require-Contains $frontend '/api/public/president-budget' 'Public treasury page must load budget data from the public budget endpoint.'

Throw-IfErrors 'ValidateCopiMineHomepagePresidentBudgetCounter'
