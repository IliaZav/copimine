from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'


def method(root: str, signature: str) -> str:
    start = root.index(signature)
    depth = 0
    for index in range(start, len(root)):
        if root[index] == '{':
            depth += 1
        elif root[index] == '}':
            depth -= 1
            if depth == 0:
                return root[start:index + 1]
    raise AssertionError('unbalanced body for ' + signature)


def test_core_repair_never_deletes_the_core_it_is_repairing():
    # removeOwnedVisuals() followed by a fresh spawn left a window in which the
    # Core genuinely did not exist. The repair tick runs on the same tick as
    # every /cmend wave spawn, so the objective blinked out on every wave.
    root = SRC.read_text(encoding='utf-8')
    rebuild = method(root, 'private void rebuildPersistedVisuals(')

    assert 'hasCoreOverlay(world, core)' in rebuild, (
        'the rebuild must first ask whether a valid Core overlay already stands'
    )
    assert 'removeOwnedVisualsPreserving(' in rebuild, (
        'the rebuild must preserve the standing Core overlay'
    )
    assert rebuild.index('removeOwnedVisualsPreserving(') < rebuild.index('spawnCoreOverlay('), (
        'the replacement must be spawned after the preserved cleanup, never before'
    )
    # The repair branch (after the core-presence probe) must not blanket-delete.
    repair_branch = rebuild[rebuild.index('boolean corePresent'):]
    assert 'removeOwnedVisuals();' not in repair_branch, (
        'the Core repair branch must repair, not rebuild from scratch'
    )


def test_preserving_cleanup_keeps_the_current_core_display():
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private void removeOwnedVisualsPreserving(')

    assert 'sameCoreOverlayBlock(display, keepCoreBlock)' in body
    assert 'ownedBySession(entity, eventId, generation)' in body
    assert 'waveObjectiveVisuals.contains' in body, (
        'a live wave owns its own objective visuals; the repair must not delete them'
    )


def test_rune_overlays_are_only_spawned_when_missing():
    root = SRC.read_text(encoding='utf-8')
    rebuild = method(root, 'private void rebuildPersistedVisuals(')
    assert 'findRuneOverlay(world, floor) == null' in rebuild, (
        'a rune that is already standing must not be respawned'
    )
