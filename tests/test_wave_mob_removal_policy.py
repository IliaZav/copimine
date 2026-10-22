"""An external removal must not masquerade as a confirmed wave-mob death."""
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_numbered_wave_counter_requires_death_unload_or_owned_cleanup():
    source = SOURCE.read_text(encoding="utf-8")
    handler = method_body(source, "public void onOwnedEntityRemove(EntityRemoveEvent event)")

    assert "WaveMobRemovalPolicy.classify(" in handler
    assert "ownedBySession(entity, eventId, generation), !trackedAtRemoval" in handler
    assert "case CONFIRMED_DEATH, AUTHORIZED_CLEANUP -> unloadedWaveMobs.onDeath(entityId);" in handler
    unexpected = handler[handler.index("case UNEXPECTED -> {"):]
    unexpected = unexpected[:unexpected.index("case IGNORE ->")]
    assert "EventPhase.RECOVERY_REQUIRED" in unexpected
    assert "unloadedWaveMobs.onDeath(entityId)" not in unexpected


def test_giant_owner_external_removal_preserves_summons_and_requires_recovery():
    source = SOURCE.read_text(encoding="utf-8")
    handler = method_body(source, "public void onOwnedEntityRemove(EntityRemoveEvent event)")
    unexpected = handler[handler.index('requireRiftGiantOwnerRecovery(entity.getUniqueId(),'):
                         handler.index("if (giantDisposition != WaveMobRemovalPolicy.Disposition.IGNORE)")]

    assert '"unexpected-owner-removal-" + event.getCause()' in unexpected
    assert "giantDisposition == WaveMobRemovalPolicy.Disposition.CONFIRMED_DEATH" in handler


def test_wave6_guards_and_named_wave7_actors_cannot_disappear_as_a_successful_clear():
    source = SOURCE.read_text(encoding="utf-8")
    handler = method_body(source, "public void onOwnedEntityRemove(EntityRemoveEvent event)")
    wave6 = method_body(source, "private boolean isProtectedWave6Actor(")
    wave7 = method_body(source, "private boolean isNamedWave7TrialActor(")
    evidence = method_body(source, "private void requireProtectedActorRemovalEvidence(")

    assert "isProtectedWave6Actor(entity)" in handler
    assert "isNamedWave7TrialActor(entity)" in handler
    assert "EVENT_KIND_RITUAL_GUARD.equals(kind)" in wave6
    assert "EVENT_KIND_RITUAL_CASTER.equals(kind)" in wave6
    assert "EVENT_KIND_REALITY_SPLIT_TRIAL.equals(kind)" in wave7
    assert "EVENT_KIND_REALITY_SPLIT_OBJECTIVE.equals(kind)" in wave7
    assert "WaveMobRemovalPolicy.Disposition.UNEXPECTED" in evidence
    assert "EventPhase.RECOVERY_REQUIRED" in evidence
