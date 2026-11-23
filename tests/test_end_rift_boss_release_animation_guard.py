"""Guard the Guardian spell release pose for the whole boss kit.

Binding source:
- The 2026-11-22 objective prompt, sections 6 and 9H: every phase needs a real
  finished presentation, and the centre-core chest beam is the visual benchmark
  of the whole encounter -- "if this looks cheap, the VFX overhaul is not done".
- Section 9A.01: every beat has a readable, authored pose; a cast may never be
  silently cancelled or rendered as nothing.

The release path used ``BossVisualCuePolicy.animationFor(spellId, RELEASE)`` for
spells that are outside the ten-spell cue table (``chest_beam``, ``boss_fireball``
and the rest of the contact/ultimate kit). ``animationFor`` returns ``UNKNOWN``
there, and the client's ``UserEndBossAnimationPlayer.clipFor`` resolves
``UNKNOWN`` to ``IDLE_BREATH`` -- so the Guardian visibly stopped performing the
instant its own ultimate left the chest. The fix routes both release sites
through the tested ``BossVisualCuePolicy.releaseAnimation`` source of truth.

This is a static source scan on purpose: the release path needs a live Bukkit
scheduler, so the behavioural coverage lives in the pure Java
``BossVisualCuePolicyTest`` which exercises ``releaseAnimation`` directly. This
scan fails loudly if the runtime ever goes back to resolving its own release
pose and re-introduces the idle-breath regression.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CUE_POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossVisualCuePolicy.java"


def test_runtime_release_uses_the_tested_policy_not_the_cue_only_lookup():
    source = EVENT.read_text(encoding="utf-8")
    # The old cue-only lookup returns UNKNOWN for every non-cue spell, and the
    # client draws UNKNOWN as the idle breath.
    assert "BossVisualCuePolicy.animationFor(spell.id()," not in source, (
        "the release must not resolve through the cue-only animationFor table"
    )
    assert "BossVisualCuePolicy.animationFor(\n                EndRiftAiPolicy.BossSpell.CHEST_BEAM.id()" not in source, (
        "the chest beam release must not resolve through the cue-only table"
    )
    # Both release sites must delegate to the single tested source of truth.
    assert source.count("BossVisualCuePolicy.releaseAnimation(") >= 2, (
        "both the chest beam and the generic release must use releaseAnimation"
    )


def test_release_policy_never_returns_the_residual_unknown_pose():
    policy = CUE_POLICY.read_text(encoding="utf-8")
    assert "public static String releaseAnimation(String spellId)" in policy, (
        "releaseAnimation must remain the single source of truth for a release pose"
    )
    body = policy.split("public static String releaseAnimation(String spellId)", 1)[1]
    body = body.split("private static Map<String, Map<CueStage, Cue>> buildCues", 1)[0]
    # An empty/unknown id must fall back to the shared cast pose, never UNKNOWN.
    assert "BossAnimationId.CAST_RELEASE.wireId()" in body, (
        "an uncatalogued spell must fall back to the shared CAST_RELEASE pose"
    )
    assert "BossAnimationId.UNKNOWN" not in body, (
        "a release pose must never resolve to UNKNOWN"
    )
    # The signature ultimate must keep the chest pose it charged in.
    assert '"chest_strike", "chest_beam"' in policy, (
        "the chest beam must release on the chest strike pose"
    )

