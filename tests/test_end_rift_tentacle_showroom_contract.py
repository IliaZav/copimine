from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT_SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def test_static_showroom_starts_permanent_tentacle_carriers_in_idle():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    spawn_start = source.index("private ItemDisplay spawnTentacle(")
    spawn_end = source.index("private void trimTentacles(", spawn_start)
    spawn = source[spawn_start:spawn_end]

    assert "TentacleAnimationPolicy.initialSpawnState" in spawn
    assert "localTextureShowcase" in spawn
    assert "TentacleAnimationPolicy.State initialState" in spawn
    assert spawn.count("initialState.name()") >= 2, "display and hitbox state must agree"
    registration = re.search(r"tentacleController\.register\(([^;]+)\);", spawn, re.DOTALL)
    assert registration is not None, "spawn must register the controller for the display"
    assert "generation, display.getUniqueId()" in registration.group(1)
    assert "initialState" in registration.group(1), "controller must start from the persisted state"
