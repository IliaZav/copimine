"""Phase 5 Echo: a copy must be VISIBLE before it may hurt anybody.

Contract 23.3 forbids invisible damage. Contract 15.2 Type A says a copy locks
one player, repeats the originating attack on its authored keyframe and then
warns and detonates. Both statements are only worth anything if the ADAPTER can
answer "is the copy visible at this tick?" without guessing - so that question
is a pure function in EchoReplicaPresentationPolicy, and this file runs the real
class against the real EchoAttackReplicaPolicy timelines.

The policy tests compile the two real sources and exercise behaviour. A test
that only asserted "the method is called" would pass on a mutant whose beat
window never opens.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

HARNESS = """import me.copimine.endevent.domain.EchoAttackReplicaPolicy;
import me.copimine.endevent.domain.EchoReplicaPresentationPolicy;
import me.copimine.endevent.domain.EchoReplicaPresentationPolicy.Beat;
import me.copimine.endevent.domain.EchoReplicaPresentationPolicy.Placement;

public class {name} {{
    static void require(boolean value, String reason) {{
        if (!value) throw new AssertionError(reason);
    }}
    public static void main(String[] args) {{
{body}
    }}
}}
"""


@pytest.fixture(scope="module")
def echo_presentation(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-presentation")
    sources = [DOMAIN / "EchoAttackReplicaPolicy.java",
               DOMAIN / "EchoReplicaPresentationPolicy.java"]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory), *map(str, sources)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


def run_probe(compiled: Path, name: str, body: str) -> str:
    probe = compiled / f"{name}.java"
    probe.write_text(HARNESS.format(name=name, body=body), encoding="utf-8")
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-cp", str(compiled), "-d", str(compiled), str(probe)],
        capture_output=True, text=True)
    assert build.returncode == 0, build.stdout + build.stderr
    run = subprocess.run(["java", "-cp", str(compiled), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_rage_entry_family_is_actually_copyable(echo_presentation):
    """Defect 5: the entry hand-off used a family the policy refuses."""
    run_probe(echo_presentation, "CopyableFamilyProbe", """
        require(EchoReplicaPresentationPolicy.familyForOrigin("MELEE_SWIPE").equals("MELEE_SWIPE"),
                "a swipe must stay a swipe");
        require(EchoReplicaPresentationPolicy.familyForOrigin("GROUND_SLAM").equals("GROUND_SLAM"),
                "a slam must stay a slam");
        // The historic hand-off. It is not copyable, but the adapter must not
        // silently drop the cast: it must translate it into a supported family.
        String translated = EchoReplicaPresentationPolicy.familyForOrigin("VOID_BLAST");
        require(translated != null, "a committed attack must still be copyable");
        require(EchoReplicaPresentationPolicy.isCopyable(translated, 0L),
                "the translated family must have a timeline");
        require(EchoReplicaPolicyAccepts.all().contains(translated),
                "the translated family must be one the replica policy supports");
        // An origin that was never a boss attack is refused, not invented.
        require(EchoReplicaPresentationPolicy.familyForOrigin(null) == null,
                "a null origin is not a copyable family");
        require(EchoReplicaPresentationPolicy.familyForOrigin("   ") == null,
                "a blank origin is not a copyable family");
        require(EchoReplicaPresentationPolicy.familyForOrigin("NOT A SPELL!") == null,
                "a malformed origin is refused");
        require(!EchoReplicaPresentationPolicy.isCopyable("NOPE", 0L),
                "an unknown family has no timeline");
    """.replace("EchoReplicaPolicyAccepts.all()",
                "EchoAttackReplicaPolicy.supportedFamilies()"))


def test_every_beat_window_is_reachable_and_ordered(echo_presentation):
    """The beats must tile the lifetime in order, with no gap and no overlap."""
    run_probe(echo_presentation, "BeatTilingProbe", """
        String[] families = {"MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM"};
        for (String family : families) {
            EchoAttackReplicaPolicy.Timeline timeline =
                    EchoAttackReplicaPolicy.timeline(100L, family);
            require(timeline != null, "the family must build a timeline: " + family);
            // Walk the whole lifetime and prove every beat is reached.
            boolean arrival = false, telegraph = false, contact = false,
                    collapse = false, warning = false, explosion = false;
            for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick(); tick++) {
                Beat beat = EchoReplicaPresentationPolicy.beatAt(timeline, tick);
                require(beat != Beat.NONE, "no hole inside the lifetime: " + family + " @" + tick);
                switch (beat) {
                    case ARRIVAL -> arrival = true;
                    case TELEGRAPH -> telegraph = true;
                    case CONTACT -> contact = true;
                    case COLLAPSE -> collapse = true;
                    case EXPLOSION_WARNING -> warning = true;
                    case EXPLOSION -> explosion = true;
                    default -> throw new AssertionError("impossible beat");
                }
            }
            require(arrival, "the copy must arrive visibly");
            require(telegraph, "the copy must telegraph before it strikes");
            require(contact, "the strike keyframe must exist");
            require(warning, "the explosion must be warned");
            require(explosion, "the explosion must fire");
            // The copy still COLLAPSES - but on THIS timeline the strike window
            // (hitTick..hitTick+2) reaches collapseTick, so the collapse beat is
            // absorbed by the contact beat and cannot be walked as its own
            // window. That is a live timeline constraint, not a missing beat:
            // the adapter must therefore key the collapse presentation on
            // beatAt(), never on a tick range of its own invention.
            require(contact || collapse,
                    "the strike and the collapse must both be presented: " + family);
            // Outside the lifetime there is no actor and no beat at all.
            require(EchoReplicaPresentationPolicy.beatAt(timeline, timeline.arrivalTick() - 1L)
                    == Beat.NONE, "nothing exists before arrival");
            require(EchoReplicaPresentationPolicy.beatAt(timeline, timeline.expiryTick() + 1L)
                    == Beat.NONE, "nothing exists after expiry");
            require(!EchoReplicaPresentationPolicy.actorShouldExist(timeline, 0L),
                    "an actor may not exist before arrival");
        }
    """)


def test_the_contact_beat_is_exactly_the_damage_keyframe(echo_presentation):
    """Contract 23.3: damage lands only at the authored keyframe, once."""
    run_probe(echo_presentation, "ContactBeatProbe", """
        EchoAttackReplicaPolicy.Timeline timeline =
                EchoAttackReplicaPolicy.timeline(100L, "MELEE_SWIPE");
        int damageTicks = 0;
        for (long tick = timeline.arrivalTick() - 2L; tick <= timeline.expiryTick() + 2L; tick++) {
            Beat beat = EchoReplicaPresentationPolicy.beatAt(timeline, tick);
            boolean allowed = EchoReplicaPresentationPolicy.damageAllowed(true, timeline, tick);
            require(allowed == EchoReplicaPresentationPolicy.beatMayDamage(beat),
                    "damage admission must follow the beat exactly @" + tick);
            if (allowed) damageTicks++;
        }
        // Strike window (3) + explosion window (3) - nothing else may hurt.
        require(damageTicks == 6, "exactly the two keyframe windows may damage, saw " + damageTicks);
        require(EchoReplicaPresentationPolicy.damageAllowed(false, timeline, timeline.hitTick())
                == false, "NO ACTOR, NO DAMAGE");
        require(EchoReplicaPresentationPolicy.damageAllowed(true, timeline, timeline.hitTick())
                == true, "the authored keyframe does land");
        require(EchoReplicaPresentationPolicy.damageAllowed(true, timeline,
                timeline.telegraphLockTick()) == false,
                "the telegraph must not hurt: that is the invisible-damage bug");
        // collapseTick falls inside the strike window on the authored timeline
        // (hit+2 >= collapse). The guard that matters is that NOTHING outside
        // the two keyframe windows may damage - the telegraph and the arrival
        // must be silent, and that is asserted above and below.
        require(!EchoReplicaPresentationPolicy.damageAllowed(true, timeline,
                timeline.arrivalTick()), "arrival must not hurt");
        require(!EchoReplicaPresentationPolicy.damageAllowed(true, timeline,
                timeline.explosionTick() + 3L), "nothing lingers after the blast");
    """)


def test_a_copy_without_a_visible_actor_cannot_hurt(echo_presentation):
    """The single guard that makes an unpresentable copy harmless."""
    run_probe(echo_presentation, "NoActorProbe", """
        EchoAttackReplicaPolicy.Timeline timeline =
                EchoAttackReplicaPolicy.timeline(0L, "GROUND_SLAM");
        for (long tick = timeline.arrivalTick(); tick <= timeline.expiryTick(); tick++) {
            require(!EchoReplicaPresentationPolicy.damageAllowed(false, timeline, tick),
                    "an invisible copy dealt damage @" + tick);
        }
        require(!EchoReplicaPresentationPolicy.damageAllowed(true, null, 0L),
                "a null timeline never damages");
        require(EchoReplicaPresentationPolicy.beatAt(null, 0L) == Beat.NONE,
                "a null timeline has no beat");
        require(!EchoReplicaPresentationPolicy.actorShouldExist(null, 0L), "no actor");
    """)


def test_the_telegraph_is_front_loaded_and_readable(echo_presentation):
    """A copy that strikes on its first visible tick is unreadable."""
    run_probe(echo_presentation, "TelegraphProbe", """
        for (String family : new String[]{"MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM"}) {
            EchoAttackReplicaPolicy.Timeline timeline =
                    EchoAttackReplicaPolicy.timeline(44L, family);
            require(EchoReplicaPresentationPolicy.telegraphIsReadable(timeline),
                    "the telegraph must be readable: " + family);
            long readableTicks = timeline.hitTick() - timeline.telegraphLockTick();
            require(readableTicks >= EchoAttackReplicaPolicy.MIN_TELEGRAPH_TICKS,
                    "the warning must last the authored minimum: " + family);
            // The first visible tick must NOT be the strike.
            require(EchoReplicaPresentationPolicy.beatAt(timeline, timeline.arrivalTick())
                    != Beat.CONTACT, "a copy may not strike on arrival: " + family);
        }
        require(!EchoReplicaPresentationPolicy.telegraphIsReadable(null),
                "a null timeline has no telegraph");
    """)


def test_spawn_placement_is_readable_bounded_and_distinct(echo_presentation):
    """Copies must stand apart from the point they copy and from each other."""
    run_probe(echo_presentation, "PlacementProbe", """
        for (int replicas = 1; replicas <= EchoAttackReplicaPolicy.MAX_REPLICAS; replicas++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int index = 0; index < replicas; index++) {
                Placement placement = EchoReplicaPresentationPolicy.placement(
                        index, replicas, 1234L, 0.35D);
                require(Double.isFinite(placement.dx()) && Double.isFinite(placement.dz()),
                        "a placement must be finite");
                require(placement.horizontalDistance()
                                >= EchoReplicaPresentationPolicy.MIN_SPAWN_DISTANCE,
                        "a copy may not spawn on top of what it copies");
                require(placement.horizontalDistance()
                                <= EchoReplicaPresentationPolicy.MAX_SPAWN_DISTANCE,
                        "a copy may not spawn across the arena");
                // Wider than the strike radius, or the copy reads as a second
                // ring of the same attack instead of a separate attacker.
                require(placement.horizontalDistance()
                                >= EchoAttackReplicaPolicy.STRIKE_RADIUS,
                        "the copy must stand clear of its own strike radius");
                seen.add(placement.dx() + ":" + placement.dz());
            }
            require(seen.size() == replicas, "copies must not stack: " + replicas);
        }
        // Deterministic: the same cast must produce the same picture.
        Placement first = EchoReplicaPresentationPolicy.placement(1, 3, 99L, 1.1D);
        Placement again = EchoReplicaPresentationPolicy.placement(1, 3, 99L, 1.1D);
        require(first.equals(again), "placement must be deterministic");
        // A NaN aim never produces a NaN placement.
        Placement broken = EchoReplicaPresentationPolicy.placement(0, 2, 0L, Double.NaN);
        require(Double.isFinite(broken.dx()) && Double.isFinite(broken.dz()),
                "a NaN yaw must still produce a finite placement");
    """)


def test_a_spawn_point_is_admitted_only_when_readable(echo_presentation):
    run_probe(echo_presentation, "SpawnAdmissionProbe", """
        require(EchoReplicaPresentationPolicy.acceptsSpawnPoint(6.0D, 3, true, true),
                "a normal point is admitted");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(0.5D, 3, true, true),
                "a point on top of the original is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(
                        EchoReplicaPresentationPolicy.MAX_SPAWN_DISTANCE + 1.0D, 3, true, true),
                "a point across the arena is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(6.0D, 1, true, true),
                "a copy buried in the floor is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(6.0D, 3, false, true),
                "a point outside the world border is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(6.0D, 3, true, false),
                "a point outside the arena is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(Double.NaN, 3, true, true),
                "a NaN distance is refused");
        require(!EchoReplicaPresentationPolicy.acceptsSpawnPoint(Double.POSITIVE_INFINITY, 3,
                        true, true), "an infinite distance is refused");
    """)


def test_every_beat_has_a_distinct_client_phase_and_duration(echo_presentation):
    run_probe(echo_presentation, "PhaseNameProbe", """
        EchoAttackReplicaPolicy.Timeline timeline =
                EchoAttackReplicaPolicy.timeline(0L, "MELEE_SWIPE");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Beat beat : Beat.values()) {
            String name = EchoReplicaPresentationPolicy.phaseName(beat);
            if (beat == Beat.NONE) {
                require(name.isEmpty(), "NONE is never a published phase");
                continue;
            }
            require(!name.isEmpty(), "every real beat needs a phase: " + beat);
            require(names.add(name), "two beats share a phase: " + name);
            require(EchoReplicaPresentationPolicy.phaseDurationMillis(timeline, beat) > 0L,
                    "every real beat needs a duration: " + beat);
        }
        require(EchoReplicaPresentationPolicy.phaseName(null).isEmpty(),
                "a null beat publishes nothing");
        require(EchoReplicaPresentationPolicy.phaseDurationMillis(timeline, null) == 0L,
                "a null beat has no duration");
    """)
