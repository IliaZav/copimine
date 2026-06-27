. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineMoneyTransfersAtomic' @(
  'ValidateCopiMineWebBankLockOrder.ps1',
  'ValidateCopiMineEconomyTransferLockOrder.ps1'
)
