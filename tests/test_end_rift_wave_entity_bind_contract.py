"""Regression contract for Wave entity visuals sent through the V4 bridge."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATH = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def _java_method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated Java method: {signature}")


def _java_block_body(source: str, opening: int) -> str:
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError("unterminated Java block")


def test_shared_wave_entity_bind_uses_visual_id_with_empty_clear_policy_and_logs_only_when_accepted():
    source = SOURCE_PATH.read_text(encoding="utf-8")
    binder = _java_method_body(
        source,
        "private void bindEventEntityClient(Player player, Entity entity)",
    )
    default_payload = _java_method_body(
        source,
        "float progress) {",
    )

    defects = []
    bind_call = re.search(
        r"sendClientPacket\(\s*player,\s*\"END_ENTITY_BIND\",\s*instance,\s*0L,\s*"
        r"entity\.getUniqueId\(\)\.toString\(\),\s*visualId\s*\)\s*;",
        binder,
        re.DOTALL,
    )
    if bind_call is None:
        defects.append("shared binder must send its resolved visualId as the END_ENTITY_BIND visual field")

    bind_send = re.search(
        r"boolean\s+(?P<accepted>\w+)\s*=\s*sendClientPacket\(\s*player,\s*\"END_ENTITY_BIND\","
        r"(?P<arguments>.*?)\)\s*;",
        binder,
        re.DOTALL,
    )
    if bind_send is None:
        defects.append("END_ENTITY_BIND acceptance result is not captured by the shared binder")

    if not re.search(
        r"return\s+sendClientPacket\(\s*player,\s*type,\s*instanceId,\s*durationMillis,\s*subjectId,\s*visualId,\s*"
        r"health,\s*maxHealth,\s*progress,\s*\"\"\s*\)\s*;",
        default_payload,
    ):
        defects.append("default entity-bind payload must preserve visualId and pass blank clearPolicy")

    log_guarded = False
    if bind_send is not None:
        accepted = re.escape(bind_send.group("accepted"))
        guard = re.search(rf"if\s*\(\s*{accepted}\s*\)\s*\{{", binder)
        if guard is not None:
            block = _java_block_body(binder, binder.index("{", guard.start()))
            log_guarded = 'getLogger().info("SKELETON_TEXTURE_BIND' in block
    if not log_guarded:
        defects.append("SKELETON_TEXTURE_BIND success log is not guarded by the accepted bind result")

    assert not defects, "Wave entity-bind contract violations:\n- " + "\n- ".join(defects)
