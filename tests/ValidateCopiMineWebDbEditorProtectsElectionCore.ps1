. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

foreach($marker in @('("elections"', '("candidate_applications"', '("candidates"', '("ballots"', '("votes"', '("polling_stations"', '("cik_chairs"', '("cik_seals"', '("president_terms"', '("president_laws"', '("president_taxes"', '("president_tax_payments"', '("protected_blocks"', '("protected_block_visuals"', '("text_display_links"', '("round_candidates"')) {
  Require-Contains $mainPy $marker "Protected DB patterns must include $marker."
}

Throw-IfErrors 'ValidateCopiMineWebDbEditorProtectsElectionCore'
