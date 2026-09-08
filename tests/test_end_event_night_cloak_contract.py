from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts/items.yml").read_text(encoding="utf-8")
ARTIFACTS_JAVA = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(
    encoding="utf-8"
)


def _item_block(item_id: str) -> str:
    match = re.search(
        rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id:|^donation-catalog:|\Z)",
        ARTIFACTS,
    )
    assert match, item_id
    return match.group(0)


def test_night_cloak_is_event_only_and_not_a_shop_item() -> None:
    block = _item_block("night_cloak")
    assert "material: LEATHER_CHESTPLATE" in block
    assert "source: ADMIN_ONLY" in block
    assert "name: \"&eПлащ Ночи\"" in block
    assert "effect: NIGHT_CLOAK" in block
    assert "price_ar: 0" in block
    assert "custom_model_data: 830005" in block


def test_night_cloak_roll_is_configured_and_persisted_before_issue() -> None:
    assert "night-cloak-chance: 0.30" in CONFIG
    assert "nightCloakRolls" in MAIN
    assert "NightCloakRollPolicy.resolve" in MAIN
    assert "NIGHT_CLOAK_WON" in MAIN
    assert ":night-cloak" in MAIN
    assert "saveStateSync()" in MAIN


def test_only_a_winning_persisted_roll_calls_the_artifact_boundary() -> None:
    start = MAIN.index("private void issueVictoryRewards")
    end = MAIN.index("private void applyBossLootOnce", start)
    body = MAIN[start:end]
    assert "NightCloakRollPolicy.isWon" in body
    assert 'config.nightCloakChance()' in body
    assert '"night_cloak"' in body
    assert "rewardService.issueToPlayer" in body
    assert 'NIGHT_CLOAK_NOT_WON' in body


def test_artifacts_reward_boundary_accepts_only_the_two_event_artifacts() -> None:
    start = ARTIFACTS_JAVA.index("private RewardIssueResult reserveEventReward")
    end = ARTIFACTS_JAVA.index("UUID ownerUuid", start)
    body = ARTIFACTS_JAVA[start:end]
    assert '"rift_core_shard"' in body
    assert '"night_cloak"' in body
    assert "isAdminOnlyCatalogItem" in body
    assert "Only official End Rift artifacts" in body
