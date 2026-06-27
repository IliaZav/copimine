. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineUploadSecurity' @(
  'ValidateCopiMinePluginRegistryNoArbitraryWrite.ps1',
  'ValidateCopiMineWebManagedResourcePackApply.ps1',
  'ValidateCopiMineWebOwnerOnlyServerProperties.ps1'
)
