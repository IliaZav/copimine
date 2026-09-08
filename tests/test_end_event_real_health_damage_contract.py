from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
POLICY = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EventRealHealthDamagePolicy.java").read_text(encoding="utf-8")


def test_player_damage_uses_authoritative_real_entity_health_after_protection_checks():
    assert "onWaveMobPlayerDamageAuthoritative" in MAIN
    assert "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)" in MAIN
    assert "EventRealHealthDamagePolicy.apply(" in MAIN
    assert "victim.setHealth(result.remainingHealth())" in MAIN
    assert "authoritativeEventMobDamage.put(event, result)" in MAIN
    assert "event.setCancelled(true)" in MAIN


def test_repair_does_not_clear_the_native_hurt_window_as_a_shortcut():
    assert "setNoDamageTicks(0)" not in MAIN
    assert "setMaximumNoDamageTicks(0)" not in MAIN
    assert "applySeries" in POLICY
    assert "before - requested" in POLICY
