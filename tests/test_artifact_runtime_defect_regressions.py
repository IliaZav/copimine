from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def _matching_brace(source: str, opening: int) -> int:
    depth = 0
    state = "code"
    index = opening
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "line-comment":
            if char == "\n":
                state = "code"
        elif state == "block-comment":
            if char == "*" and following == "/":
                state = "code"
                index += 1
        elif state == "text-block":
            if source.startswith('"""', index):
                state = "code"
                index += 2
        elif state == "string":
            if char == "\\":
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            if char == "\\":
                index += 1
            elif char == "'":
                state = "code"
        elif char == "/" and following == "/":
            state = "line-comment"
            index += 1
        elif char == "/" and following == "*":
            state = "block-comment"
            index += 1
        elif source.startswith('"""', index):
            state = "text-block"
            index += 2
        elif char == '"':
            state = "string"
        elif char == "'":
            state = "char"
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    raise AssertionError("unclosed Java block")


def _method_body(source: str, name: str) -> str:
    match = re.search(
        rf"(?m)^[ \t]*(?:public|private|protected)\s+[\w.$<>?, \[\]]+\s+{re.escape(name)}\s*\(",
        source,
    )
    assert match, f"missing method {name}"
    opening = source.find("{", match.end())
    assert opening >= 0, f"missing method body for {name}"
    closing = _matching_brace(source, opening)
    return source[opening + 1 : closing]


def _branch_body(source: str, marker: str) -> str:
    start = source.find(marker)
    assert start >= 0, f"missing branch {marker}"
    opening = source.find("{", start)
    assert opening >= 0, f"missing branch body for {marker}"
    closing = _matching_brace(source, opening)
    return source[opening + 1 : closing]


def _source() -> str:
    return SOURCE.read_text(encoding="utf-8")


def test_signal_bell_denies_invalid_vanilla_use_before_preserving_chest_binding() -> None:
    source = _source()
    interact = _method_body(source, "onArtifactInteract")
    branch = _branch_body(interact, 'if ("SIGNAL_BELL".equals(var4))')

    deny_use = branch.find("setUseItemInHand(Event.Result.DENY)")
    validate_chest = branch.find("SignalBellPolicy.isChestContainer")
    deny_chest_interaction = branch.find("setUseInteractedBlock(Event.Result.DENY)")
    bind_chest = branch.find("this.bindSignalBell(var1.getItem(), var1.getClickedBlock())")
    assert 0 <= deny_use < validate_chest < deny_chest_interaction < bind_chest
    assert "onSignalBellBlockPlace" in source
    placement = _method_body(source, "onSignalBellBlockPlace")
    assert "event.setCancelled(true)" in placement


def test_gravedigger_consumes_only_matching_authenticated_unique_instance() -> None:
    source = _source()
    finalizer = _method_body(source, "onGravediggerDeathFinalizer")
    spawn = _method_body(source, "onGravediggerContractItemSpawn")
    pickup = _method_body(source, "onGravediggerContractItemPickup")
    capture = _method_body(source, "captureGravediggerContract")
    schedule = _method_body(source, "scheduleGravediggerContractPurge")
    entity_purge = _method_body(source, "purgeGravediggerContractEntities")
    authenticated_item = _method_body(source, "gravediggerContractItem")
    consumed_match = _method_body(source, "isConsumedGravediggerContract")
    any_consumed_match = _method_body(source, "isConsumedGravediggerContractAnywhere")
    command = _method_body(source, "handleGraveCommand")

    for body in (finalizer, spawn, pickup, capture, schedule, entity_purge):
        assert "removeAllGravediggerContracts" not in body
    assert "isConsumedGravediggerContract" in finalizer
    assert "isConsumedGravediggerContractAnywhere" in spawn
    assert "isConsumedGravediggerContractAnywhere" in pickup
    assert "purgeConsumedGravediggerContract" in schedule
    assert "isConsumedGravediggerContract" in _method_body(source, "purgeConsumedGravediggerContract")
    assert "isConsumedGravediggerContractAnywhere" in entity_purge
    assert "instanceBindings.get(uniqueItemId)" in authenticated_item
    assert "instanceToItem.containsKey(uniqueItemId)" in authenticated_item
    assert "consumedUniqueItemId.equals(this.uniqueItemIdOf(stack))" in consumed_match
    assert "this.consumedGravediggerUniqueItemId(player)" in any_consumed_match
    assert "clearConsumedGravediggerMarker" not in command
    assert "isGravediggerContractStack" not in finalizer + spawn + pickup + schedule + entity_purge


def test_grave_command_is_dispatched_and_suggested() -> None:
    source = _source()
    dispatcher = _method_body(source, "onCommand")
    tab = _method_body(source, "onTabComplete")
    assert re.search(r'"grave"\.equalsIgnoreCase\(var4\[0\]\).*?handleGraveCommand\(var5\)', dispatcher, re.S)
    assert re.search(r'List\.of\([^)]*"grave"', tab)


def test_berserker_cooldown_is_stored_on_the_unique_item_instance() -> None:
    source = _source()
    read = _method_body(source, "actionCooldownUntil")
    write = _method_body(source, "storeActionCooldown")
    key = _method_body(source, "actionCooldownKey")
    assert "BERSERKER_HEART" in read and "keyBerserkerCooldownUntil" in read
    assert "BERSERKER_HEART" in write and "keyBerserkerCooldownUntil" in write
    assert "findAuthenticDirectPlayerArtifactStack" in read and "meta.getPersistentDataContainer().getOrDefault" in read
    assert "findAuthenticDirectPlayerArtifactStack" in write and "meta.getPersistentDataContainer().set" in write
    assert "uniqueItemIdOf(stack)" in key, "the runtime map key must follow the item across player changes"


def test_consumed_gravedigger_ids_survive_restart_for_offline_drop_and_pickup_guards() -> None:
    source = _source()
    enabled = _method_body(source, "onEnable")
    load = _method_body(source, "loadConsumedGravediggerContractIds")
    record = _method_body(source, "recordConsumedGravediggerContract")
    consumed_match = _method_body(source, "isConsumedGravediggerContractAnywhere")
    capture = _method_body(source, "captureGravediggerContract")
    pickup = _method_body(source, "onGravediggerContractItemPickup")

    assert "loadConsumedGravediggerContractIds" in enabled
    assert "gravedigger-consumed.yml" in load and "consumed-unique-item-ids" in load
    assert 'journal.getInt("version", -1) != 1' in load
    assert "gravediggerConsumptionJournalAvailable = false" in load
    assert "saveGravediggerConsumptionJournalAtomically" in record
    assert "consumedGravediggerUniqueItemIds.contains(uniqueItemId)" in consumed_match
    assert "recordConsumedGravediggerContract(capturedUniqueItemId)" in capture
    fail_closed_guard = pickup.find("!this.gravediggerConsumptionJournalAvailable")
    fail_closed_cancel = pickup.find("event.setCancelled(true)", fail_closed_guard)
    fail_closed_return = pickup.find("return;", fail_closed_cancel)
    consumed_check = pickup.find("isConsumedGravediggerContractAnywhere(stack)")
    entity_remove = pickup.find("event.getItem().remove()")
    assert 0 <= fail_closed_guard < fail_closed_cancel < fail_closed_return < consumed_check < entity_remove
    assert 'rawIds instanceof List<?>' in load
    assert 'rawId instanceof String uniqueItemId' in load
    assert 'this.consumedGravediggerUniqueItemIds.clear()' in load
    save = _method_body(source, "saveGravediggerConsumptionJournalAtomically")
    assert '".tmp"' in save and "StandardCopyOption.ATOMIC_MOVE" in save
    assert "AtomicMoveNotSupportedException" not in save
