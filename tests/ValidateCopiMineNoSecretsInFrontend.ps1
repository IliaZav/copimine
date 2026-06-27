. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$frontend = Read-FrontendBundle

$banned = @(
  'DISCORD_TOKEN',
  'RCON_PASSWORD',
  'OPENAI_API_KEY',
  'SBP_SECRET',
  'PAYMENT_PROVIDER_SECRET',
  'WEBHOOK_SECRET',
  'JWT_SECRET',
  'AUTH_COOKIE_SECRET'
)

foreach ($needle in $banned) {
  if ($frontend.Contains($needle)) {
    $errors.Add("Frontend bundle must not embed secret-like key name: $needle")
  }
}

Throw-IfErrors 'ValidateCopiMineNoSecretsInFrontend'
