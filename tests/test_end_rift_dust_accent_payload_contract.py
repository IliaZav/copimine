"""Guards the DustOptions accent path that used to abort the whole Wave 7 tick.

Player playtest: Wave 7 "did not start" and its minibosses stood still. The
server log showed, every tick:

    END_RIFT_WAVE_TICK_FAILED wave=7 ... IllegalArgumentException:
    data (class org.bukkit.Particle$DustOptions) should be class java.lang.Void

The Wave 7 ability signatures name a vanilla particle (END_ROD, SOUL, CRIT,
SONIC_BOOM, ...) plus a colour accent. The shared emitter handed the accent to
``spawnParticle`` as a DustOptions payload for whatever particle the pass named,
and Bukkit validates that payload against the particle's declared data type.
Wave 7 passes overwhelmingly name particles whose type is Void, so the very
first emission threw, the trial tick aborted, and nothing advanced.

These tests are static because the failure is a wire-level payload contract:
they assert the adapter consults the declared data type before attaching the
accent, and that no other call site reintroduces the unconditional payload.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start:cursor + 1]
    raise AssertionError(f"Method not terminated: {signature}")


def test_dust_accent_is_only_attached_to_particles_that_declare_dust_data():
    source = EVENT.read_text(encoding="utf-8")
    adapter = _method(
        source,
        "private void spawnEventParticle(Location point, Particle particle, int count,\n"
        "                                    double offsetX, double offsetY, double offsetZ, double extra,\n"
        "                                    Particle.DustOptions data)",
    )
    assert "getDataType() == Particle.DustOptions.class" in adapter, (
        "the dust accent adapter must consult the particle's declared payload type; "
        "attaching DustOptions unconditionally aborted every Wave 7 tick"
    )
    # The guarded branch must still emit the authored particle and still paint the
    # accent, otherwise the repair would have traded a crash for a silent ability.
    assert adapter.count("spawnParticle") >= 2, "the accent must survive as its own DUST pass"
    assert "Particle.DUST" in adapter, "an accent on a Void particle must fall back to DUST"


def test_wave7_ability_signatures_only_name_dust_capable_particles_with_accents():
    """Document which passes legitimately carry an accent.

    A pass that names a Void particle *and* a colour is exactly the shape that
    broke. It is legal only because the adapter above reroutes the accent, so
    this test pins the reroute in place rather than banning the design.
    """
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/"
              "Wave7AbilityEffectPolicy.java").read_text(encoding="utf-8")
    assert "accentParticle" in policy
    # Every authored ability must have at least one pass; an empty table silently
    # degrades the trial room to "nothing happens", which reads as a dead wave.
    for ability in (
        "MARKSMAN_QUICK_SHOT",
        "MARKSMAN_HEAVY_SHOT",
        "MARKSMAN_SPREAD_SHOT",
        "ARCHMAGE_FANGS",
        "ARCHMAGE_SPEAR",
        "ARCHMAGE_STEP",
        "ARCHMAGE_BEAM",
        "WARDEN_NARROW_STRIKE",
        "WARDEN_WIDE_STRIKE",
        "WARDEN_SONIC_LUNGE",
        "WARDEN_SLAM",
    ):
        assert f'case "{ability}"' in policy, f"{ability} lost its authored signature"
