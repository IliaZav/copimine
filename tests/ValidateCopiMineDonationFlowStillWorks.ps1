. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineDonationFlowStillWorks' @(
  'ValidateCopiMineDonationMockSbpFixedPacks.ps1',
  'ValidateCopiMineDonationPurchaseIntentBackendPriced.ps1',
  'ValidateCopiMineDonationWebClaimDisabled.ps1',
  'ValidateCopiMineWebUsesExistingEndpoints.ps1',
  'ValidateCopiMineExistingFunctionsNotRemoved.ps1'
)
