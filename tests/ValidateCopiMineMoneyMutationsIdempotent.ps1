. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineMoneyMutationsIdempotent' @(
  'ValidateCopiMineDonationIdempotencyScopedToOwner.ps1',
  'ValidateCopiMineDonationMarkPaidNoDoubleCredit.ps1',
  'ValidateCopiMineEconomyCreditIdempotent.ps1'
)
