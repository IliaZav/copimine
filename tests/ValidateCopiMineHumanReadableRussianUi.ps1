. "$PSScriptRoot\WebsitePromptValidator.Helpers.ps1"
Invoke-ValidatorSet 'ValidateCopiMineHumanReadableRussianUi' @(
  'ValidateCopiMineWebNoBrokenRussianEncoding.ps1',
  'ValidateCopiMineNoQuestionMarkBrokenUiText.ps1',
  'ValidateCopiMineNoAiPlaceholderText.ps1'
)
