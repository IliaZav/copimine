from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
CLIENT_RENDERER = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "RiftGuardianModelRenderer.java"
CLIENT_MODEL = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "RiftGuardianModel.java"
CLIENT_TEXTURE = ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "entity" / "rift_guardian_final_strike.png"
RESOURCE_MODEL = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "models" / "item" / "end_event_rift_obelisk_full.json"
CONFIG = ROOT / "copimine-end-event" / "config.yml"


def test_final_policies_and_runtime_hooks_exist():
    assert (DOMAIN / "BossFinalStrikePolicy.java").exists()
    assert (DOMAIN / "BossDefeatCinematicPolicy.java").exists()
    source = MAIN.read_text(encoding="utf-8")
    for marker in (
        "BossFinalStrikePolicy",
        "BossDefeatCinematicPolicy",
        "bossFinalStrikeTask",
        "bossDefeatCinematicTask",
        "FINAL_STRIKE",
        "FINISHING_BLOW",
        "completeBossDefeatCinematic",
    ):
        assert marker in source, marker


def test_final_strike_is_not_a_global_vanilla_visual():
    renderer = CLIENT_RENDERER.read_text(encoding="utf-8")
    model = CLIENT_MODEL.read_text(encoding="utf-8")
    assert "FINAL_STRIKE" in renderer
    assert "FINISHING_BLOW" in renderer
    assert "FINAL_STRIKE" in model
    assert "FINISHING_BLOW" in model
    assert CLIENT_TEXTURE.exists()
    assert CLIENT_TEXTURE.stat().st_size > 1024


def test_obelisk_asset_and_final_strike_config_are_present():
    assert RESOURCE_MODEL.exists()
    config = CONFIG.read_text(encoding="utf-8")
    assert "final-strike" in config
    assert "rift-obelisks" in config


def test_cleanup_covers_both_final_sequences_and_obelisk_projectiles():
    source = MAIN.read_text(encoding="utf-8")
    for marker in (
        "cancelBossFinalStrike",
        "cancelBossDefeatCinematic",
        "clearRiftObeliskPulseDisplays",
        "clearRiftObelisks",
        "clearActiveRiftProjectiles",
        "onDisable",
    ):
        assert marker in source, marker


def test_restart_cannot_leave_an_interrupted_finisher_or_rearm_final_strike():
    source = MAIN.read_text(encoding="utf-8")
    policy = (DOMAIN / "BossDefeatCinematicPolicy.java").read_text(encoding="utf-8")
    assert "shouldFinalizeAfterRestart" in policy
    assert "keyBossFinalStrikeUsed" in source
    assert "PersistentDataType.BYTE, (byte) 0" in source
    assert "getOrDefault(" in source
    assert "BOSS_DEFEAT_CINEMATIC_RECOVERED" in source
    assert "restart-after-zero-virtual-health" in source
