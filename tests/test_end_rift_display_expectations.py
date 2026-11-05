"""A display whose model cannot resolve must never be published.",

The portal / core / sphere reports were all "the picture is a placeholder".
The server kept publishing ItemDisplays whose custom_model_data had no
authored model, so the client legitimately drew the missing-model cube.
"""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndEventDisplayItems.java"


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_display_model_range_covers_every_published_overlay():
    policy = POLICY.read_text(encoding="utf-8")
    assert "FIRST_MODEL_DATA = " in policy
    assert "LAST_MODEL_DATA = " in policy
    first = int(policy.split("FIRST_MODEL_DATA = ")[1].split(";")[0])
    last = int(policy.split("LAST_MODEL_DATA = ")[1].split(";")[0])
    source = SOURCE.read_text(encoding="utf-8")

    import re
    for name, value in re.findall(r"(MODEL_[A-Z0-9_]+) = (\d+);", source):
        assert first <= int(value) <= last, name + " outside the authored model range"


def test_portal_layers_refuse_unresolvable_geometry():
    source = SOURCE.read_text(encoding="utf-8")
    body = declaration(source, "private ItemDisplay spawnPortalModelLayer(")
    guard = body.index("EndEventDisplayItems.hasResolvableModel(modelData)")
    spawn = body.index("world.spawn(origin, ItemDisplay.class")
    assert guard < spawn, "the guard has to run before the display is spawned"
