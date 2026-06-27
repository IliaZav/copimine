. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineJwtAuth' @(
  'ValidateCopiMineWebCookieOnlyAuth.ps1',
  'ValidateCopiMineRefreshTokenRotation.ps1'
)
