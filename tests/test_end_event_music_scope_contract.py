from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")


def _method(name: str, next_name: str) -> str:
    start = MAIN.index(name)
    end = MAIN.index(next_name, start)
    return MAIN[start:end]


def test_custom_music_has_an_explicit_event_phase_gate_and_stops_on_exit() -> None:
    for marker in (
        "private boolean isEventMusicPhase()",
        "VICTORY_PROCESSING, VICTORY -> true",
        "if (!isEventMusicPhase())",
        "stopEventMusic();",
        "private boolean isVictoryMusicTail(EventPhase previous, EventPhase next)",
        "phase == EventPhase.VICTORY_PROCESSING || phase == EventPhase.VICTORY",
    ):
        assert marker in MAIN
    transition = _method("private boolean transition(", "private void forcePhase")
    forced = _method("private void forcePhase", "private void recoverTransientSession")
    assert "if (!isEventMusicPhase() && !isVictoryMusicTail(current, next))" in transition
    assert "if (!isEventMusicPhase() && !isVictoryMusicTail(previous, next))" in forced
    assert "!isVictoryMusicTail(current, next)" in transition
    assert "!isVictoryMusicTail(previous, next)" in forced
    play = _method("private void playEventMusic", "private void stopEventMusic")
    assert "if (!isEventMusicPhase() || track == null" in play
    assert "if (!isEventMusicPhase())" in play
    assert "stopEventMusic();" in play
    assert "if (!isEventMusicPhase())" in play[play.index("musicLoopTask") :]


def test_normal_phases_have_no_music_track_and_manual_music_check_is_local_only() -> None:
    music_for_phase = _method("private EventConfig.MusicTrack musicForPhase()", "private void playEventMusic")
    assert "default -> null" in music_for_phase
    for phase in ("UNCONFIGURED", "COLLECTING", "READY_FOR_PLAYERS", "COUNTDOWN", "UNLOCKED"):
        assert f"case EventPhase.{phase}" not in music_for_phase
    test_music = _method("private void playTestMusic", "@Override\n    public void onDisable")
    assert '"local".equalsIgnoreCase(config.environment())' in test_music
    assert "END_EVENT_MUSIC_TEST" in test_music


def test_join_sync_cannot_start_or_replay_custom_music_outside_event_combat() -> None:
    sync = _method("private void syncEventMusic", "private void playTestMusic")
    assert "!isEventMusicPhase()" in sync
    assert "stopEventMusic(player)" in sync
    victory = _method("private void announceVictory", "@EventHandler(priority = EventPriority.MONITOR)")
    assert "boolean victoryMusicAllowed = phase == EventPhase.VICTORY_PROCESSING || phase == EventPhase.VICTORY" in victory
    assert "activeMusicTrackId = config.victoryMusic().soundId()" in victory
    assert victory.count("if (victoryMusicAllowed)") >= 2
