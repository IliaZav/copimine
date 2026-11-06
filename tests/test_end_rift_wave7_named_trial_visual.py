"""Wave 7 dispatches three named trial actors, and must never swap their models.

Complaint (11) is that wave 7 does not launch and the Warden does not emerge with its own
animation. The named trial actors - the Warden, the Archmage and the Marksman - each carry a
visual id, and this policy decides which renderer may draw which. A crossed mapping would show
an Archmage in the Warden's armour, which looks like a broken wave.

The class imports real Minecraft render state types, so it cannot be compiled without the
client jar; the invariants here are ordinary strings and a class-assignability table, which are
read from the source instead.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/Wave7NamedTrialVisualPolicy.java"
)

# Which render state each actor must be bound to, by the vanilla type the policy names.
EXPECTED_BINDING = {
    "WARDEN": "WardenRenderState",
    "ARCHMAGE": "EvokerRenderState",
    "MARKSMAN": "SkeletonRenderState",
}


def _source() -> str:
    assert POLICY.is_file(), f"wave 7 visual policy is missing: {POLICY}"
    return POLICY.read_text(encoding="utf-8")


def test_visual_ids_are_declared_once_and_distinct() -> None:
    source = _source()
    ids = re.findall(r'public static final String (\w+_VISUAL)\s*=\s*"([^"]+)";', source)
    assert ids, "no visual id constants found"

    names = [name for name, _ in ids]
    values = [value for _, value in ids]

    assert len(names) == len(set(names)), f"duplicate constant names: {names}"
    assert len(values) == len(set(values)), (
        f"two actors share a visual id, so one would be dispatched to the wrong renderer: {values}"
    )

    # The three named trial actors plus the Archmage's Vex servant.
    assert set(names) == {"WARDEN_VISUAL", "ARCHMAGE_VISUAL", "MARKSMAN_VISUAL", "VEX_SERVANT_VISUAL"}, names

    # Every id is namespaced to the wave 7 family, so a stray wave 6 id cannot be accepted here.
    for value in values:
        assert value.startswith("END_RIFT_WAVE7_"), f"visual id escapes the wave 7 family: {value}"
        assert value == value.upper(), f"visual id must be upper case to match the upper-casing lookup: {value}"


def test_the_vex_servant_is_not_a_named_trial_actor() -> None:
    source = _source()
    # The servant keeps the vanilla Vex mesh, so it must not appear in the dispatch switch.
    match = re.search(r"public static ActorModel modelFor\(String visualId\).*?\n    }", source, re.S)
    assert match, "modelFor is missing"
    body = match.group(0)
    assert "VEX_SERVANT_VISUAL" not in body, (
        "the Vex servant was added to the actor dispatch, which would give it a trial actor's model"
    )
    for name in ("WARDEN_VISUAL", "ARCHMAGE_VISUAL", "MARKSMAN_VISUAL"):
        assert name in body, f"{name} is not dispatched by modelFor"


def test_each_actor_is_bound_to_its_own_render_state() -> None:
    source = _source()
    match = re.search(r"public static boolean matchesRenderState\(.*?\n    }", source, re.S)
    assert match, "matchesRenderState is missing"
    body = match.group(0)

    arms = dict(re.findall(r"case (\w+) ->\s*(\w+)\.class\.isAssignableFrom\(renderStateType\)", body))
    assert arms, "no render state bindings found"
    assert arms == EXPECTED_BINDING, (
        f"an actor is bound to the wrong render state, so it would be drawn by another actor's "
        f"renderer: {arms}"
    )

    # A null or unknown visual must never match, or every mob would take a trial actor's model.
    assert re.search(r"if \(model == null \|\| renderStateType == null\) return false;", body), (
        "matchesRenderState must refuse a null model or render state"
    )


def test_unknown_and_blank_visual_ids_are_refused() -> None:
    source = _source()
    match = re.search(r"public static ActorModel modelFor\(String visualId\).*?\n    }", source, re.S)
    assert match, "modelFor is missing"
    body = match.group(0)

    assert re.search(r"if \(visualId == null \|\| visualId\.isBlank\(\)\) return null;", body), (
        "modelFor must refuse a null or blank visual id before the switch"
    )
    assert re.search(r"default -> null", body), (
        "modelFor must have a default that refuses unknown ids rather than falling through"
    )
    assert "trim()" in body and "toUpperCase(Locale.ROOT)" in body, (
        "the lookup must normalise case and padding, since the server and client may differ"
    )
