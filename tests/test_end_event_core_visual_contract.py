from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method_body(name: str) -> str:
    source = SOURCE.read_text(encoding="utf-8")
    match = re.search(
        rf"private (?:Location|boolean) {name}\([^)]*\) \{{(?P<body>.*?)\n    \}}",
        source,
        re.DOTALL,
    )
    assert match, f"missing {name}"
    return match.group("body")


def test_core_overlay_is_centered_on_the_target_block():
    body = _method_body("coreOverlayLocation")
    assert "return core.getLocation().add(0.5D, 0.5D, 0.5D);" in body
    assert "coreY + 1.0D" not in body


def test_core_overlay_lookup_uses_the_same_block_anchor():
    body = _method_body("sameCoreOverlayBlock")
    assert "location.getBlockY() == core.getY()" in body
    assert "core.getY() + 1" not in body


def test_core_overlay_has_a_small_symmetric_shell_margin():
    source = SOURCE.read_text(encoding="utf-8")
    spawn = source[source.index("private void spawnCoreOverlay"):source.index("private void spawnRuneOverlay")]
    assert "private static final float CORE_OVERLAY_SCALE = 1.04F;" in source
    assert "entity.setDisplayWidth(CORE_OVERLAY_SCALE);" in spawn
    assert "entity.setDisplayHeight(CORE_OVERLAY_SCALE);" in spawn
    assert "new Vector3f(CORE_OVERLAY_SCALE, CORE_OVERLAY_SCALE, CORE_OVERLAY_SCALE)" in spawn


def test_core_removal_gui_has_no_world_floating_confirmation_caption():
    source = SOURCE.read_text(encoding="utf-8")
    assert "Подтверждение снятия Core" not in source
