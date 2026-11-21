"""Wire-level contract for the per-viewer binding delta.

The pure cache is covered by ``test_end_rift_client_binding_snapshot_cache.py``.
This test pins the producer side: the boss tick must use the delta path, explicit
spawns must still force, failed sends must not be remembered, and every
lifecycle boundary must invalidate the baseline.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    return source[start:source.index(next_signature, start)]


def test_boss_tick_sends_a_delta_not_a_full_repeat():
    source = EVENT.read_text(encoding="utf-8")
    tick = _method(source, "private void tickBoss()", "private boolean isBossCombatRuntimeActive(")
    assert "CLIENT_BINDING_FULL_RESYNC_TICKS" in source
    assert "clientBindingSnapshots.clear()" in tick
    assert "bindBossClientForOnlinePlayers(false)" in tick
    assert "bindEventEntityClientForOnlinePlayers(entity, false)" in tick
    # The unforced path is the only one allowed in the per-second loop.
    assert "bindEventEntityClientForOnlinePlayers(entity);" not in tick


def test_explicit_spawn_binding_still_forces_and_failed_sends_retry():
    source = EVENT.read_text(encoding="utf-8")
    broadcast = _method(
        source,
        "private void bindEventEntityClientForOnlinePlayers(Entity entity)",
        "private TentacleAnimationPolicy.State tentacleAnimationState(",
    )
    assert "bindEventEntityClientForOnlinePlayers(entity, true)" in broadcast
    bind = _method(
        source,
        "private boolean bindEventEntityClient(Player player, Entity entity, boolean force)",
        "private void sendRitualCasterPhase(",
    )
    assert "shouldSend(" in bind
    assert "recordDelivered(" in bind
    assert "recordFailed()" in bind
    # The delivery record may only follow a successful send.
    assert bind.index("if (!bound)") < bind.index("recordDelivered(")


def test_late_join_reset_and_unbind_invalidate_the_baseline():
    source = EVENT.read_text(encoding="utf-8")
    assert "clientBindingSnapshots.invalidateViewer(" in source
    assert "clientBindingSnapshots.invalidateEntity(" in source
    assert "bossBindingSignatures.remove(" in source
    clear_all = _method(source, "private void clearClientEffects()", "private void clearClientEffects(Player player)")
    assert "clientBindingSnapshots.clear()" in clear_all
    assert "bossBindingSignatures.clear()" in clear_all
    unbind = _method(
        source,
        "private void unbindEventEntityClient(UUID entityUuid)",
        "/** A Vex that belongs to one of this event's Archmages. */",
    )
    assert "clientBindingSnapshots.invalidateEntity(entityUuid)" in unbind


def test_binding_metrics_are_exposed_in_runtime_diagnostics():
    source = EVENT.read_text(encoding="utf-8")
    assert "runtimeDiagnosticsClientBindingsSent" in source
    assert "runtimeDiagnosticsClientBindingsCoalesced" in source
    assert "runtimeDiagnosticsClientBindingsFailed" in source
    assert "client_bindings_sent=" in source
    assert "client_bindings_coalesced=" in source
    assert "client_bindings_dropped=" not in source  # dropped == failed here
    assert "client_bindings_failed=" in source