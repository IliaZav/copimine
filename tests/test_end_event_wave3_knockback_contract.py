from pathlib import Path
import re


SOURCE = Path(__file__).resolve().parents[1] / (
    "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
)


def _method(name: str) -> str:
    source = SOURCE.read_text(encoding="utf-8")
    match = re.search(
        rf"public void {name}\([^)]*\) \{{(?P<body>.*?)\n    \}}",
        source,
        re.DOTALL,
    )
    assert match, f"missing {name}"
    return match.group("body")


def test_accepted_wave_three_hit_arms_the_scoped_knockback_guard():
    body = _method("onWaveMobPlayerDamageAuthoritative")
    assert "armPortalWaveNoKnockback(victim);" in body


def test_knockback_observer_can_see_the_cancelled_authoritative_damage_event():
    source = SOURCE.read_text(encoding="utf-8")
    method_start = source.index("public void onPortalWaveMobAttack")
    annotation = source[source.rfind("@EventHandler", 0, method_start):method_start]
    assert "priority = EventPriority.MONITOR" in annotation
    assert "ignoreCancelled = false" in annotation


def test_knockback_guard_remains_wave_three_scoped():
    body = _method("onPortalWaveMobKnockback")
    assert "readInt(mob, keyWave, 0) != 3" in body
    assert "event.setCancelled(true);" in body
