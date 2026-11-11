from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java'


def method(source, name, next_name):
    return source[source.index(name):source.index(next_name, source.index(name))]


def test_safe_zone_and_roster_exit_release_owned_effects_before_damage_cadence():
    source = SOURCE.read_text(encoding='utf-8')
    from tests.test_wave_navigation_adapter import extract
    body = extract(source, 'void applyBlackFogEffects(long now, Location core)')
    assert 'releaseBlackFogEffects(player)' in body
    assert body.index('releaseBlackFogEffects(player)') < body.index('now - last < 1_000L')
    assert 'releaseInactiveBlackFogEffects(' in body
    assert 'applyOwnedBlackFogEffect(player, PotionEffectType.SLOWNESS, 1)' in body
    assert 'PotionEffectType.BLINDNESS' not in body
    assert 'PotionEffectType.DARKNESS' not in body
    assert 'applyOwnedBlackFogEffect(player, PotionEffectType.WITHER, witherAmplifier)' in body
    assert 'player.damage(' not in body
    assert '15 * 20' not in body


def test_cleanup_and_lifecycle_restore_effect_leases():
    source = SOURCE.read_text(encoding='utf-8')
    assert 'public void onBlackFogPotionEffectChange(EntityPotionEffectEvent event)' in source
    assert 'BlackFogEffectLeasePolicy.remainingDuration(' in source
    for start, end in [
        ('public void onPlayerQuit(', 'public void onPlayerDeath('),
        ('public void onPlayerDeath(', 'public void onPlayerRespawn('),
        ('public void onPlayerChangedWorld(', 'public void onShardInteract('),
    ]:
        assert 'releaseBlackFogEffects(' in method(source, start, end)
    assert 'releaseAllBlackFogEffects();' in method(source, 'public void onDisable(', 'public boolean onCommand(')
    assert 'releaseAllBlackFogEffects();' in method(source, 'private void clearCurrentBlackFogPackets(', 'private void sendCurrentBlackFogPacket(')


def test_leaving_arena_suspends_viewer_and_reentry_sends_explicit_resume():
    source = SOURCE.read_text(encoding='utf-8')
    body = method(source, 'private void refreshCurrentBlackFogPackets(', 'private void clearCurrentBlackFogPackets(')
    assert '"SUSPEND"' in body
    assert 'sendCurrentPresentationResume(player)' in body
    client = (ROOT / 'CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java').read_text(encoding='utf-8')
    assert '"END_PRESENTATION_RESUME".equals(eventType)' in client
    assert 'client.player.isDead()' in client
    assert 'getPath().equals(payload.mode())' in client
