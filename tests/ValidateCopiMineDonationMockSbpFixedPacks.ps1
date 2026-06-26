. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts
$config = Read-Utf8 $Paths.ArtifactsConfig
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $artifacts 'List<?> raw = getConfig().getList("donation.fixed-packs", List.of(50, 100, 250, 500, 1000));' 'Artifacts must default fixed donation packs to 50/100/250/500/1000.'
Require-Contains $artifacts 'return packs.stream().distinct().sorted().toList();' 'Artifacts must normalize and sort fixed donation packs before using them.'
Require-Contains $economy 'allowedPacks()' 'EconomyCore donation payment service must expose fixed-pack allowlist.'
Require-Contains $economy 'requireFixedDonationPack(amountRub);' 'EconomyCore session creation must validate requested donation pack amount.'
Require-Contains $config 'fixed-packs:' 'Artifacts config must declare fixed donation packs.'
Require-Contains $mainPy 'DONATION_FIXED_PACKS = (50, 100, 250, 500, 1000)' 'admin-web must declare the canonical fixed donation packs.'
Require-Contains $mainPy 'if safe_amount not in DONATION_FIXED_PACKS:' 'admin-web must enforce the same fixed donation packs.'

Throw-IfErrors 'ValidateCopiMineDonationMockSbpFixedPacks'
