"""A tentacle hold must never outlive the tentacle that enforced it.

The grab is enforced by a per-tick teleport onto the display's socket, so the
display is the only thing holding the body in the air. V6 master §716 requires
every cast and entity to clean up on death, logout, removal, phase switch and
force-reset. A hold that is forgotten only by the normal THROW timer leaves a
player hanging at the socket after a guardian dies or the encounter resets.

This probe covers the teardown wiring the pure policy tests cannot see: the hold
ledger is populated on a real lock, cleared on the normal throw, and explicitly
released from every removal/reset/death/logout path.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
            / "CopiMineEndEvent.java")


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    depth = 0
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_a_lock_records_the_held_body_and_a_throw_forgets_it():
    source = MONOLITH.read_text(encoding="utf-8")
    lock = _method(source, "private boolean lockTentaclePlayer(")
    assert "tentacleHeldPlayers.put(display.getUniqueId(), target.getUniqueId());" in lock, (
        "a successful hold must be recorded, or teardown cannot find the body")
    # A refused lock must never record a body that was not moved.
    assert lock.index("target.teleport(socket)") < lock.index("tentacleHeldPlayers.put("), (
        "the ledger must only be written after the teleport succeeded")

    throw = _method(source, "private void releaseTentaclePlayer(")
    assert "tentacleHeldPlayers.remove(display.getUniqueId());" in throw, (
        "the normal throw must clear the hold before it can early-return")
    assert throw.index("tentacleHeldPlayers.remove(") < throw.index("return;"), (
        "the ledger must be cleared ahead of every guard")


def test_removal_and_reset_release_held_bodies():
    source = MONOLITH.read_text(encoding="utf-8")
    remove = _method(source, "private void removeTentacle(")
    assert "releaseTentacleHolder(entityId, reason);" in remove, (
        "removing a display (death, unload, cleanup) must release its held body")

    clear = _method(source, "private void clearTentacles(")
    assert "releaseAllTentacleHolders(reason);" in clear, (
        "clearing the whole set must release any hold the loop missed")

    release_all = _method(source, "private void releaseAllTentacleHolders(")
    assert "tentacleHeldPlayers.clear();" in release_all, (
        "the release sweep must not leave stale entries behind")


def test_death_and_logout_drop_the_hold_without_teleporting_a_corpse():
    source = MONOLITH.read_text(encoding="utf-8")
    quit_event = _method(source, "public void onPlayerQuit(")
    assert 'dropTentacleHoldsForPlayer(uuid, "disconnect");' in quit_event, (
        "a disconnect must drop the hold so a reconnect cannot resume it")
    death_event = _method(source, "public void onPlayerDeath(")
    assert 'dropTentacleHoldsForPlayer(uuid, "death");' in death_event, (
        "a death must drop the hold instead of leaving the body pinned")

    drop = _method(source, "private void dropTentacleHoldsForPlayer(")
    assert "tentacleHeldPlayers.values().removeIf(playerId::equals);" in drop, (
        "the dead/offline player's entry must be removed from every display")
    assert "teleport(" not in drop, (
        "a dead or leaving body must not be teleported by the release helper")


def test_a_live_release_lands_the_body_on_the_real_arena_floor():
    source = MONOLITH.read_text(encoding="utf-8")
    drop = _method(source, "private void dropHeldTentaclePlayer(")
    assert "!player.isOnline()" in drop and "player.isDead()" in drop, (
        "an offline or dead body has no live entity to place")
    assert "findSafeCombatLocation(" in drop, (
        "the release must use the validated arena-floor resolver, not a guessed Y")
    assert "player.teleport(safe)" in drop, (
        "the released body must actually be moved off the grab socket")
    assert "player.setVelocity(new Vector());" in drop, (
        "the released body must not keep the socket's zero/inherited motion")
    assert "player.setFallDistance(0.0F);" in drop, (
        "the release must not add fall damage on top of the hold")
