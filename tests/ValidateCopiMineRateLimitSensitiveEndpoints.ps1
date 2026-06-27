. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineRateLimitSensitiveEndpoints' @(
  'ValidateCopiMineDonationRateLimits.ps1',
  'ValidateCopiMineWebIpLimit5.ps1'
)
