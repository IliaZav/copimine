from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_rune_runtime_uses_server_clock_and_resets_between_objective_polls():
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    for start, end in [('private void tickStartRitual(', 'private void completeStartRitual('),
                       ('private void tickCurrentIntermission(', 'private EventPhase wavePhase(')]:
        body = source[source.index(start):source.index(end, source.index(start))]
        assert 'transitionRuneController.observeServerTicks(' in body
        assert 'eventTickCounter' in body
        assert 'ServerTickClock.millis(eventTickCounter)' in body
    assert 'public void onTransitionRuneMove(PlayerMoveEvent event)' in source
    assert 'public void onTransitionRuneTeleport(PlayerTeleportEvent event)' in source
    body = source[source.index('private void resetTransitionRuneHoldOnExit('):source.index('private void tickStartRitual(')]
    assert 'transitionRuneController.reset()' in body
    assert 'padOccupants.remove(' in body


def test_preboss_runtime_uses_one_eight_hundred_tick_gateway_timer():
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    body = source[source.index('private void tickPreBossCooldown('):source.index('private EncounterContext currentEncounterContext(')]
    assert 'preBossTransitionController.tickServerTicks(' in body
    assert 'restoreServerTicks(' in body
    assert 'phaseDeadlineMillis - PreBossTransitionController.DURATION_MILLIS' not in body


def test_lifecycle_exit_resets_an_in_progress_rune_hold_immediately():
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    for method in ('onPlayerQuit', 'onPlayerDeath', 'onPlayerChangedWorld'):
        start = source.index('public void ' + method + '(')
        end = source.index('\n    @EventHandler', start)
        assert 'resetTransitionRuneHoldForParticipant(uuid)' in source[start:end]
