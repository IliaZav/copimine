"""Regression contracts for the current release-readiness report.

These tests intentionally inspect the source boundaries that are easy to
reopen by a later refactor.  Runtime tests are added alongside them where the
plugin API makes that practical.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ECONOMY = (ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(encoding="utf-8")
ELECTION = (ROOT / "copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java").read_text(encoding="utf-8")
NARCOTICS = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java").read_text(encoding="utf-8")
WORLD = (ROOT / "copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java").read_text(encoding="utf-8")
AUTH = (ROOT / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")


def test_ci_runs_the_entire_python_suite_with_real_pytest_and_junit_output():
    assert "python -m pytest" in CI
    assert "--junitxml" in CI
    assert "Get-ChildItem tests -Filter 'test_*.py'" not in CI
    assert "-SkipReleaseArtifacts" not in CI


def test_ar_deposit_credit_and_asset_lifecycle_share_one_transaction():
    assert "commitArDepositAtomically" in ECONOMY
    atomic = ECONOMY[ECONOMY.index("commitArDepositAtomically"):ECONOMY.index("private List<ArDepositIntent>")]
    assert "cmv8_ar_asset_movements" in atomic
    assert "FOR UPDATE" in atomic
    assert "creditAccountAsync" not in ECONOMY[ECONOMY.index("advanceArDepositIntent"):ECONOMY.index("advanceArDepositIntent") + 7000]
    assert "INSERT INTO cmv8_ar_assets" not in atomic


def test_physical_ar_theft_does_not_remove_one_item_and_refund_separately():
    theft = ARTIFACTS[ARTIFACTS.index("private void tryRareArTheft"):ARTIFACTS.index("private boolean isArtifactsAdmin")]
    assert "removeAmount" not in theft
    assert "createStack" not in theft
    assert "dropItemNaturally" not in theft
    assert "legacyTryRareArTheft" not in ARTIFACTS


def test_personal_pin_is_verified_inside_the_same_mutation_transaction():
    assert "verifyPersonalPinForMutation" in ECONOMY
    style_start = ECONOMY.index("private TxnResult artifactStyleTxn")
    style = ECONOMY[style_start:style_start + 12000]
    assert "verifyPersonalPinForMutation(" in style
    assert style.index("verifyPersonalPinForMutation(") < style.index("INSERT INTO cmv4_bank_ledger")


def test_election_repair_never_overrides_an_external_protection_cancel():
    section = ELECTION[ELECTION.index("public void onBlockPlace"):ELECTION.index("public void onProtectedBreak")]
    assert "event.setCancelled(false)" not in section
    assert "event.isCancelled()" in section
    assert "event.getPlayer()" not in section[section.index("runTask"):]


def test_election_and_background_workers_are_bounded_and_shutdown_gracefully():
    assert "ArrayBlockingQueue" in ELECTION
    assert "shutdownNow()" not in ELECTION


def test_brewing_owner_is_preserved_and_final_ingredient_is_durable_before_consume():
    assert "prepareBrewingCompletionIntent" in NARCOTICS
    assert "base.ownerUuid()" in NARCOTICS
    assert "nearby.damage" not in NARCOTICS
    final_section = NARCOTICS[NARCOTICS.index("NarcoticDefinition exact"):NARCOTICS.index("private boolean queueIngredients")]
    assert final_section.index("prepareBrewingCompletionIntent") < final_section.index("consumeOne")


def test_world_teleport_tokens_are_bound_and_border_replacement_is_atomic():
    assert "TeleportToken" in WORLD
    assert "Files.move" in WORLD
    assert "ATOMIC_MOVE" in WORLD
    assert "FileChannel" in WORLD


def test_auth_effects_covers_attacker_side_events_and_fails_closed_without_login_logout_api():
    assert "EntityDamageByEntityEvent" in AUTH
    assert "Projectile" in AUTH
    assert "PlayerInteractEntityEvent" in AUTH
    assert "login hook" in AUTH.lower()
    assert "logout hook" in AUTH.lower()
