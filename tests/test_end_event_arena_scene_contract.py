from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
MAIN = MAIN_PATH.read_text(encoding="utf-8")


def _slice(start: str, end: str | None = None) -> str:
    begin = MAIN.index(start)
    finish = len(MAIN) if end is None else MAIN.index(end, begin)
    return MAIN[begin:finish]


def test_final_scene_lifecycle_is_explicit_and_policy_backed() -> None:
    assert "private void startFinalArenaScene(EventPhase scene)" in MAIN
    assert "private void renderFinalArenaScene(EventPhase scene, LivingEntity boss, int elapsedTicks)" in MAIN
    assert "private void clearFinalArenaScene(String reason)" in MAIN
    start = _slice("private void startFinalArenaScene", "private void renderFinalArenaScene")
    render = _slice("private void renderFinalArenaScene", "private void clearFinalArenaScene")
    assert "BossArenaSetPiecePolicy.Scene" in start
    assert "BossArenaSetPiecePolicy.Bounds" in start
    assert "BossArenaSetPiecePolicy.frame" in render
    assert "frame.allPointsInside(bounds)" in render
    assert "BossArenaSetPiecePolicy.Point" in render


def test_final_scene_is_display_only_and_tracks_every_temporary_entity() -> None:
    assert "finalArenaSceneVisuals" in MAIN
    start = _slice("private void startFinalArenaScene", "private void renderFinalArenaScene")
    render = _slice("private void renderFinalArenaScene", "private void clearFinalArenaScene")
    cleanup = _slice("private void clearFinalArenaScene", "private void scheduleFinalRitualVisual")
    assert "world.spawn" in start
    assert "BlockDisplay" in start
    assert "tag(display, EVENT_KIND_DISPLAY" in start
    assert "finalArenaSceneVisuals.add" in start
    assert "setType(" not in start
    assert "viewer.spawnParticle" in render
    assert "spawnParticleLine" in render
    assert "ownedEntities.remove" in cleanup
    assert "visual.remove()" in cleanup
    assert "finalArenaSceneVisuals.clear()" in cleanup


def test_final_scene_reuses_the_single_final_ritual_task_and_generation_guard() -> None:
    visual = _slice("private void scheduleFinalRitualVisual", "private void spawnParticleLine(Player")
    assert "finalRitualVisualTask" in visual
    assert "runTaskTimer(this" in visual
    assert ", 5L" in visual
    assert "renderFinalArenaScene" in visual
    assert "callbackGeneration" in visual
    assert "taskRegistry.owns(callbackGeneration)" in visual
    assert "phase != EventPhase.FINAL_DRAIN && phase != EventPhase.FINAL_RITUAL" in visual


def test_all_exit_paths_request_scene_cleanup() -> None:
    for method, end in (
        ("private void cancelSessionTasks", "private void cancelCreativeTestTask"),
        ("private void removeCore", "private void resetEventSafely"),
        ("private void resetEventSafely", "private void updateCoreChargeState"),
        ("private void cancelRitual", "private void updateCombatHelpers"),
        ("private void commitOfficialBossDefeat", "private void triggerFinalPhase"),
        ("private void beginVictory", "private void unlockEnd"),
    ):
        assert "clearFinalArenaScene" in _slice(method, end), method
    force = _slice("private void forcePhase", "private void recoverTransientSession")
    assert "clearFinalArenaScene" in force


def test_scene_never_mutates_real_arena_blocks() -> None:
    render = _slice("private void renderFinalArenaScene", "private void clearFinalArenaScene")
    assert ".setType(" not in render
    assert "setBlock(" not in render
    assert "HazardMutationJournal" not in render
