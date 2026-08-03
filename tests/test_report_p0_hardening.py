"""Focused source contracts for the report's critical security boundaries.

These checks intentionally complement the existing PowerShell marker suite:
they assert that the trust boundary is not silently reopened by a later
refactor.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ECONOMY = (ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(encoding="utf-8")
NARCOTICS_FACTORY = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java").read_text(encoding="utf-8")
AUTH = (ROOT / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java").read_text(encoding="utf-8")


def test_ar_has_no_generic_legacy_uuid_bypass() -> None:
    assert "isLegacyArSerial" not in ECONOMY
    assert "|certified|" in ECONOMY
    assert "officialArDenominationKey" in ECONOMY
    assert "officialArSignatureVersionKey" in ECONOMY
    assert "cmv8_ar_assets" in ECONOMY


def test_broken_donation_items_are_not_reclaimable() -> None:
    assert 'recordDonationLossOnce(var2, "break")' not in ARTIFACTS
    assert 'recordDonationLossOnce(ref, "durability-break")' not in ARTIFACTS
    assert "markDonationInstanceBroken" in ARTIFACTS
    assert "'BROKEN'" in ARTIFACTS
    assert "Migrated " not in ARTIFACTS or "BROKEN" not in ARTIFACTS[ARTIFACTS.index("Migrated "):]


def test_artifacts_does_not_inherit_other_domain_roles() -> None:
    admin_section = ARTIFACTS[ARTIFACTS.index("private boolean isArtifactsAdmin"):ARTIFACTS.index("private boolean isRestrictedJuniorArtifactsAdmin")]
    for permission in ("copimine.election.admin", "copimine.election.cik", "copimine.economy.admin", "copimine.players.admin"):
        assert permission not in admin_section


def test_narcotics_identity_is_fungible_and_signed() -> None:
    factory = NARCOTICS_FACTORY
    assert "NARCOTIC_STACK:" in factory
    assert "int stackAmount = Math.max(1, Math.min(Math.max(1, base.getMaxStackSize()), amount))" in factory
    assert "new ItemStack(base, 1)" not in factory
    resolver = factory[factory.index("public NarcoticDefinition resolveOfficial"):factory.index("public boolean isOfficialFinishedItem")]
    assert "setItemMeta" not in resolver
    assert "UUID.randomUUID" not in resolver
    assert "stack.getAmount() != 1" not in resolver
    assert "STACK_SIGNATURE_VERSION" in resolver
    assert "narcoticSignatureKey" in factory


def test_auth_effects_has_logout_hook_and_api_fail_closed() -> None:
    assert '"fr.xephi.authme.events.LogoutEvent"' in AUTH
    assert "authenticated.remove" in AUTH[AUTH.index("private void handleAuthEvent"):AUTH.index("@EventHandler\n    public void onPlayerQuit")]
    assert "getPlugin(\"AuthMe\")" in AUTH
    assert "authApiAvailable = false" in AUTH
