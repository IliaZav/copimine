import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'
DOMAIN = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'domain'


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


def test_the_sealed_prisoner_is_not_a_combat_target():
    # The player inside the sphere is a participant for roster and reward
    # purposes, but cannot fight back. Mobs turning on it turned the prison into
    # a damage race instead of a rescue.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private boolean isCombatTarget(Player player)')

    assert 'isSealedRitualPrisoner' in body, (
        'isCombatTarget must exclude the sealed prisoner'
    )
    assert body.index('isSealedRitualPrisoner') < body.index('isActiveArenaParticipant'), (
        'the prisoner exclusion must gate the participant check'
    )

    sealed = method(root, 'private boolean isSealedRitualPrisoner(Player player)')
    assert 'wave6PrisonBroken' in sealed, (
        'once the prison breaks the freed player must be targetable again'
    )
    assert 'isCurrentRitualPrisoner' in sealed


def test_mage_health_has_exactly_one_curve_and_it_honours_the_named_envelope():
    # A second local formula used to live in WaveSixRosterPolicy and disagreed
    # with the live one: 320 HP at the smallest party (floor is 350) and 896 at
    # the largest (ceiling is 800). It was unreachable, which is exactly why it
    # was dangerous.
    roster = (DOMAIN / 'WaveSixRosterPolicy.java').read_text(encoding='utf-8')
    body = method(roster, 'public static double mageHealth(int admittedPlayers)')

    assert 'RitualCasterHealthPolicy.maxHealthFor' in body, (
        'the roster table must delegate to the one curve that owns the envelope'
    )
    assert '*' not in body.split('return')[1], (
        'no local arithmetic may reintroduce a second curve'
    )

    health = (DOMAIN / 'RitualCasterHealthPolicy.java').read_text(encoding='utf-8')
    assert 'MIN_HEALTH = 350.0D' in health, 'the player named 350 as the floor'
    assert 'MAX_HEALTH = 800.0D' in health, 'the player named 800 as the ceiling'


def test_sphere_shows_progress_per_dead_mage():
    # The prisoner must be able to read progress from inside the shell: colour
    # cools and a crack band is added for every mage destroyed.
    presentation = (DOMAIN / 'RitualSpherePresentationPolicy.java').read_text(encoding='utf-8')

    assert 'public static int sphereBodyColor(int deaths)' in presentation
    assert 'public static int crackStage(int deaths)' in presentation
    assert 'MAX_CRACK_STAGE = 5' in presentation

    body = method(presentation, 'public static int crackStage(int deaths)')
    assert 'MAX_CRACK_STAGE' in body, 'deaths beyond the wheel must clamp, not overflow'
