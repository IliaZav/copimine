. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineArShopFlowStillWorks' @(
  'ValidateCopiMineArtifactsWebsite.ps1',
  'ValidateCopiMineArtifactsArSeparatedFromDonation.ps1',
  'ValidateCopiMineArtifactsArRepairOnly.ps1'
)
