"""Source contracts for recently repaired Wave 5 visuals and mob labels."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, name: str) -> str:
    declaration = re.search(
        rf"\bprivate\s+(?:static\s+)?[\w.<>, ?\[\]]+\s+{re.escape(name)}\s*\([^;]*?\)\s*\{{",
        source,
        re.DOTALL,
    )
    assert declaration, f"missing required production method: {name}"
    opening = source.index("{", declaration.start())
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[declaration.start() : index + 1]
    raise AssertionError(f"unterminated production method: {name}")


def test_wave5_safe_zones_use_emerald_blocks_and_a_central_green_locator_beam():
    source = EVENT.read_text(encoding="utf-8")
    safe_zones = _method(source, "spawnCurrentSafeZoneVisuals")
    beams = _method(source, "renderCurrentSafeZoneBeams")

    assert "setType(Material.EMERALD_BLOCK, false)" in safe_zones
    assert re.search(r"new Location\(core\.getWorld\(\),\s*point\.x\(\)\s*\+\s*\.5D", beams)
    assert "point.z() + .5D" in beams
    assert "0x42FF66" in beams
    assert re.search(r"base\.clone\(\)\.add\(0,\s*5\.5D,\s*0\)", beams)


def test_wave_mob_names_are_hidden_without_changing_objective_or_boss_labels():
    source = EVENT.read_text(encoding="utf-8")
    visible_calls = re.findall(r"\b(\w+)\.setCustomNameVisible\(true\);", source)

    # The remaining visible labels belong to the existing objective anchors and
    # the explicitly out-of-scope Rift Guardian boss. Event mobs stay hidden.
    assert visible_calls == ["entity", "boss"]
