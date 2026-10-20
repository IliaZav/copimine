from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unclosed Java method: {signature}")


def test_wave7_legacy_elite_provenance_uses_only_plugin_owned_markers():
    source = SOURCE.read_text(encoding="utf-8")
    classifier = _method_body(source, "private boolean isKnownLegacyWave7Elite(")

    assert "getCustomName()" not in classifier
    assert "keyMiniBossSpell" in classifier
    assert "isWaveCommander(entity)" in classifier
    assert 'lootProfile.startsWith("elite-")' in classifier
