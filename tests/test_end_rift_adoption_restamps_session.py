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


def test_adoption_restamps_every_ownership_tag_cleanup_matches_on():
    # Production showed a Core display that /cmend cleanup confirm could not
    # remove: its event_generation tag still said 369 while the live session was
    # on 371, so every ownership probe answered "not mine" and the fixture was
    # immortal. Adoption moved keyGeneration and keyEventId forward but left
    # keyEventSessionId behind - and ownedByEvent() prefers the session tag over
    # the event tag, so the entity kept reporting its old session.
    root = SRC.read_text(encoding='utf-8')
    adopt = method(root, 'private void adoptEntityIntoCurrentSession(')

    assert 'keyEventSessionId' in adopt, (
        'adoption must restamp event_session_id: ownedByEvent() reads that tag '
        'first, so leaving it stale makes the adopted entity unremovable'
    )
    assert 'keyEventSessionId, PersistentDataType.STRING, eventId' in adopt, (
        'the session tag has to be written with the live event id, exactly as tag() does'
    )
    # A guard that can never be true would satisfy a plain substring check, so
    # require the write to sit behind a real condition on the session key.
    assert 'if (false' not in adopt, (
        'the session restamp must actually run, not be parked behind a dead branch'
    )
    write_at = adopt.index('keyEventSessionId, PersistentDataType.STRING, eventId')
    guard_at = adopt.rindex('if (', 0, write_at)
    guard = adopt[guard_at:write_at]
    assert 'keyEventSessionId != null' in guard, (
        'the restamp must be guarded by the live session key being available'
    )
    assert 'eventId != null' in guard, (
        'a null event id must never be stamped onto an entity'
    )
    assert 'eventId.isBlank()' in guard, (
        'a blank event id must never be stamped onto an entity'
    )
    assert 'keyGeneration, PersistentDataType.LONG, generation' in adopt
    assert 'keyEventGeneration, PersistentDataType.LONG, generation' in adopt
    assert 'registerOwnedEntity(entity)' in adopt


def test_cleanup_removes_entities_whose_session_tag_is_stale():
    # The complement of the test above: cleanup must be able to recognise a
    # fixture that adoption missed, otherwise a single skipped adoption strands
    # a display for the lifetime of the world.
    root = SRC.read_text(encoding='utf-8')
    cleanup = method(root, 'private void cleanupOwnedEntitiesForEvent(')

    assert 'ownedByEvent(entity, expectedEventId)' in cleanup
    assert 'isEndEventOwnedRole(entity)' in cleanup, (
        'an entity carrying this plugin event role must be removable even when '
        'its session stamp predates the current session'
    )
