. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineNoCrossUserDataLeak' @(
  'ValidateCopiMineWebMultiUserNoGlobalState.ps1',
  'ValidateCopiMineFrontendLogoutClearsState.ps1'
)
