from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, name: str) -> str:
    match = re.search(r"\b(?:public|private|protected)\s+[\w<>?,. ]+\s+" + re.escape(name)
                      + r"\s*\([^)]*\)\s*\{", source)
    assert match, f"missing method {name}"
    start = match.end()
    depth = 1
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index]
    raise AssertionError(f"unterminated method {name}")


def test_carrier_uses_actual_slowness_lease_and_retires_legacy_speed_modifier():
    source = SOURCE.read_text(encoding="utf-8")
    body = method_body(source, "updateCurrentCarrierSlowness")
    assert "PotionEffectType.SLOWNESS" in body
    # SUPERSEDED (audit wave 1, spec Slow III): the carrier used to receive
    # Slowness II (amplifier 1). The required debuff is Slow III, which is
    # amplifier 2 in Minecraft's zero-based scale, and the lease now names that
    # amplifier through one constant instead of a bare literal.
    assert "CARRIER_SLOWNESS_AMPLIFIER, false, false, true" in body
    assert "currentCarrierPotionLeases" in body
    assert "current.getAmplifier() >= CARRIER_SLOWNESS_AMPLIFIER" in body
    assert "CARRIER_SLOWNESS_AMPLIFIER = 2" in source, "Slow III is amplifier 2, not Slow II"
    assert "addTransientModifier" not in body
    assert "speed.removeModifier(legacy)" in body
    assert "updateCurrentCarrierSlowness(holder, true)" in method_body(source, "tickCurrentCarrierObjective")
    boundary = method_body(source, "onCarrierSlownessPotionChange")
    assert "currentCarrierPotionMutations" in boundary
    assert "!event.isOverride()" in boundary


def test_carrier_slow_is_released_for_charge_and_holder_lifecycle():
    source = SOURCE.read_text(encoding="utf-8")
    inventory_removal = method_body(source, "removeCurrentCarrierInventoryCharge")
    assert "updateCurrentCarrierSlowness(holder, false)" in inventory_removal

    delivery = method_body(source, "deliverCurrentCarrierCharge")
    # The removal is now token-scoped: delivery retires the SPECIFIC token it
    # accepted, so a concurrent rebind cannot have another holder's charge
    # purged by this player's transition. The slow must still be released, which
    # is what this test exists to protect.
    assert "removeCurrentCarrierInventoryCharge(holder, token)" in delivery, (
        "delivery must retire the accepted token, not whatever token is current"
    )
    assert "saveStateSync()" in delivery, (
        "delivery must checkpoint before consuming the physical item"
    )
    release = method_body(source, "releaseCurrentCarrierHolder")
    assert "removeCurrentCarrierInventoryCharge(Bukkit.getPlayer(holder))" in release

    holder_sync = method_body(source, "syncCurrentCarrierFields")
    assert "!Objects.equals(nextHolder, currentCarrierVisualHolder)" in holder_sync
    assert "clearCurrentCarrierPlayerVisual()" in holder_sync
    visual_cleanup = method_body(source, "clearCurrentCarrierPlayerVisual")
    assert "updateCurrentCarrierSlowness(Bukkit.getPlayer(currentCarrierVisualHolder), false)" in visual_cleanup

    join = method_body(source, "onPlayerJoin")
    assert "updateCurrentCarrierSlowness(player, false)" in join
    quit = method_body(source, "onPlayerQuit")
    assert "updateCurrentCarrierSlowness(event.getPlayer(), false)" in quit
    death = method_body(source, "onPlayerDeath")
    assert "updateCurrentCarrierSlowness(event.getEntity(), false)" in death
    world_change = method_body(source, "onPlayerChangedWorld")
    assert "updateCurrentCarrierSlowness(event.getPlayer(), false)" in world_change

    enable = method_body(source, "onEnable")
    assert "Bukkit.getOnlinePlayers()" in enable
    assert "updateCurrentCarrierSlowness(player, false)" in enable
    disable = method_body(source, "onDisable")
    assert "Bukkit.getOnlinePlayers()" in disable
    assert "updateCurrentCarrierSlowness(player, false)" in disable
