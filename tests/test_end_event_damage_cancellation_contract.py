from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_cancelled_boss_damage_returns_before_virtual_health_mutation() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert "if (event.isCancelled())" in damage, (
        "onBossDamage needs an explicit early guard for a pre-cancelled Bukkit event"
    )
    guard = damage.index("if (event.isCancelled())")
    return_after_guard = damage.index("return;", guard)
    first_final_damage_after_guard = damage.find("event.getFinalDamage()", guard)

    assert return_after_guard < first_final_damage_after_guard, (
        "a Bukkit event cancelled by another handler must not reach the virtual-health path"
    )
    assert "BOSS_DAMAGE_CANCELLED_BEFORE_VIRTUAL_HEALTH" in damage


def test_cancelled_boss_damage_does_not_un_cancel_auth_or_admin_protection() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert "if (event.isCancelled())" in damage, (
        "onBossDamage needs an explicit early guard for a pre-cancelled Bukkit event"
    )
    guard = damage.index("if (event.isCancelled())")
    return_after_guard = damage.index("return;", guard)
    assert "event.setCancelled(false)" not in damage[:return_after_guard]
