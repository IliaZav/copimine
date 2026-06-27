. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineRoleChecksForAdminEndpoints' @(
  'ValidateCopiMineWebOwnerOnlyAdminMutations.ps1',
  'ValidateCopiMineWebTreasuryAccessChecksEnabledRole.ps1'
)
