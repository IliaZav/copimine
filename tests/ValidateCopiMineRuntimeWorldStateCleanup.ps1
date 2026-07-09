$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$sql = Get-Content -Raw -Encoding UTF8 (Join-Path $root "db\runtime\clean_world_state.sql")
$common = Get-Content -Raw -Encoding UTF8 (Join-Path $root "deploy\shared\common.sh")
$unpack = Get-Content -Raw -Encoding UTF8 (Join-Path $root "deploy\ubuntu\copimine_unpack_and_verify.sh")
$replace = Get-Content -Raw -Encoding UTF8 (Join-Path $root "deploy\ubuntu\copimine_full_replace.sh")

foreach ($marker in @(
    "artifact_shops",
    "ar_atms",
    "polling_stations",
    "cik_chairs",
    "protected_block_visuals",
    "text_display_links",
    "narcotics_brewing_states"
)) {
    if ($sql -notmatch [regex]::Escape($marker)) {
        throw "Runtime cleanup SQL is missing table marker: $marker"
    }
}

foreach ($marker in @(
    "copimine_apply_clean_world_state",
    "COPIMINE_CLEAN_WORLD_STATE_SQL",
    "psql"
)) {
    if ($common -notmatch [regex]::Escape($marker)) {
        throw "Shared deploy helper is missing cleanup marker: $marker"
    }
}

if ($unpack -notmatch "CLEAN_WORLD_STATE" -or $unpack -notmatch "clean_world_state_if_requested") {
    throw "Ubuntu unpack deploy script does not wire CLEAN_WORLD_STATE cleanup."
}

if ($replace -notmatch "CLEAN_WORLD_STATE" -or $replace -notmatch "copimine_apply_clean_world_state") {
    throw "Full replace deploy script does not wire runtime cleanup."
}

Write-Host "ValidateCopiMineRuntimeWorldStateCleanup passed."
