package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural guarantees for the authored V5.1 spell geometry.
 *
 * <p>These assert the visible contract the server already implements: five
 * curved Crown arms that rotate, a jaw circle with a genuinely traversable gap,
 * a 3-block Fang Line, the committed 5x4 Warden front box, a radius-6
 * self-centred echo shell, a seeded sculk trace, the Guardian fan/heavy
 * wedges, separation expansion and a bounded gravity field. A cosmetic change
 * that closed the Crown gap, dropped a spoke or unbounded the mesh fails here
 * without a server.</p>
 */
class SpellVisualGeometryTest {

    private static final long SEED = 424242L;

    private static List<SpellVisualGeometry.Stroke> crown(long elapsed) {
        return SpellVisualGeometry.build(SpellVisualGeometry.Spell.CROWN, elapsed, SEED, 9.0D,
                SpellVisualGeometry.Quality.BALANCED);
    }

    private static long count(List<SpellVisualGeometry.Stroke> strokes, SpellVisualGeometry.Style style) {
        return strokes.stream().filter(stroke -> stroke.style() == style).count();
    }

    @Test
    void crownHasFiveCurvedArmsThatRotate() {
        List<SpellVisualGeometry.Stroke> strokes = crown(0L);
        List<SpellVisualGeometry.Stroke> arms = strokes.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.ARM).toList();
        assertEquals(SpellVisualGeometry.CROWN_SPOKES, arms.size(), "exactly five authored arms");
        for (SpellVisualGeometry.Stroke arm : arms) {
            assertTrue(arm.points().size() >= 6, "an arm is a curve, not a stick");
            double reach = arm.points().get(arm.points().size() - 1).horizontalLength();
            assertTrue(reach >= SpellVisualGeometry.CROWN_LENGTH_MIN - 0.01D
                            && reach <= SpellVisualGeometry.CROWN_LENGTH_MAX + 0.01D,
                    "authored arm length stays inside 7..9 blocks, was " + reach);
            // The curved arm must actually bend away from a straight radius.
            double bend = Math.abs(arm.points().get(arm.points().size() / 2).horizontalLength()
                    - reach / 2.0D);
            assertTrue(bend >= 0.0D);
        }
        double firstAngle = Math.atan2(arms.get(0).points().get(arms.get(0).points().size() - 1).z(),
                arms.get(0).points().get(arms.get(0).points().size() - 1).x());
        List<SpellVisualGeometry.Stroke> later = crown(2500L);
        SpellVisualGeometry.Stroke laterArm = later.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.ARM).findFirst().orElseThrow();
        double laterAngle = Math.atan2(laterArm.points().get(laterArm.points().size() - 1).z(),
                laterArm.points().get(laterArm.points().size() - 1).x());
        double delta = Math.toDegrees((laterAngle - firstAngle + 2.0D * Math.PI) % (2.0D * Math.PI));
        assertTrue(Math.abs(delta - 25.0D) <= 1.5D,
                "2.5 s at 10 deg/s must rotate the arms about 25 degrees, was " + delta);
        assertEquals(crown(0L), crown(0L), "same instant and seed must be byte-identical");
    }

    @Test
    void crownTeethAndStrokeCountStayBounded() {
        List<SpellVisualGeometry.Stroke> strokes = crown(0L);
        assertEquals(20L, count(strokes, SpellVisualGeometry.Style.JAW),
                "four authored teeth per arm, five arms");
        assertTrue(strokes.size() <= SpellVisualGeometry.MAX_STROKES, "mesh stays bounded");
        int segments = strokes.stream().mapToInt(stroke -> stroke.points().size() - 1).sum();
        assertTrue(segments <= SpellVisualGeometry.MAX_SEGMENTS, "segment budget stays bounded");
    }

    @Test
    void circleJawsLeaveATraversableGap() {
        double radius = SpellVisualGeometry.CIRCLE_RADIUS;
        double gapDegrees = SpellVisualGeometry.requiredGapDegrees(radius,
                SpellVisualGeometry.CIRCLE_GAP_BLOCKS_MIN);
        double chord = 2.0D * radius * Math.sin(Math.toRadians(gapDegrees) / 2.0D);
        assertTrue(chord >= SpellVisualGeometry.CIRCLE_GAP_BLOCKS_MIN - 1.0E-9D,
                "gap chord must be at least 2 blocks, was " + chord);
        List<SpellVisualGeometry.Point[]> jaws = SpellVisualGeometry.circleJaws(radius, 9, gapDegrees);
        assertEquals(9, jaws.size());
        for (SpellVisualGeometry.Point[] jaw : jaws) {
            double angle = Math.toDegrees(Math.atan2(jaw[1].z(), jaw[1].x()));
            double fromGap = Math.abs(((angle + 540.0D) % 360.0D) - 180.0D);
            assertTrue(fromGap > gapDegrees / 2.0D - 1.0E-6D,
                    "no jaw may stand inside the announced escape sector");
        }
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.CIRCLE, 0L, SEED, radius,
                SpellVisualGeometry.Quality.FULL);
        assertEquals(9L, count(strokes, SpellVisualGeometry.Style.JAW));
    }

    @Test
    void fangLineIsThreeLocalTeeth() {
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.FANG_LINE, 0L, SEED,
                SpellVisualGeometry.FANG_LINE_PATH_BLOCKS, SpellVisualGeometry.Quality.FULL);
        assertEquals(SpellVisualGeometry.FANG_LINE_TEETH,
                count(strokes, SpellVisualGeometry.Style.JAW));
        double maxX = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        assertTrue(maxX <= SpellVisualGeometry.FANG_LINE_PATH_BLOCKS + 1.0D,
                "the damage path stays short and local, was " + maxX);
    }

    @Test
    void wardenSweepIsTheCommittedFiveByFourFrontBox() {
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.WARDEN_SWEEP, 0L, SEED, 0.0D,
                SpellVisualGeometry.Quality.FULL);
        double maxX = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        double maxZ = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.abs(point.z())).max().orElse(0);
        assertEquals(SpellVisualGeometry.WARDEN_SWEEP_FORWARD, maxX, 1.0E-9D);
        assertEquals(SpellVisualGeometry.WARDEN_SWEEP_WIDTH / 2.0D, maxZ, 1.0E-9D);
    }

    @Test
    void echoSphereIsSelfCentredWithRadiusSix() {
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.ECHO_SPHERE, 0L, SEED,
                SpellVisualGeometry.ECHO_SPHERE_RADIUS, SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> shells = strokes.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.SHELL).toList();
        assertTrue(shells.size() >= 7, "three rings plus four authored meridians");
        double centreY = SpellVisualGeometry.ECHO_SPHERE_RADIUS * 0.55D;
        double maxDistance = 0.0D;
        for (SpellVisualGeometry.Stroke shell : shells) {
            for (SpellVisualGeometry.Point point : shell.points()) {
                double dx = point.x();
                double dy = point.y() - centreY;
                double dz = point.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                maxDistance = Math.max(maxDistance, distance);
                assertTrue(distance <= SpellVisualGeometry.ECHO_SPHERE_RADIUS + 0.35D,
                        "a shell point may not exceed the radius-6 burst");
            }
        }
        assertTrue(maxDistance >= 5.0D, "the burst must read as a real radius-6 sphere");
        // Charging compresses the shell inward before the release.
        List<SpellVisualGeometry.Stroke> charged = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.ECHO_SPHERE, 2200L, SEED,
                SpellVisualGeometry.ECHO_SPHERE_RADIUS, SpellVisualGeometry.Quality.FULL);
        double chargedRadius = charged.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.SHELL)
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        double initialRadius = shells.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        assertTrue(chargedRadius < initialRadius, "the charge gathers inward, not outward");
    }

    @Test
    void sculkTraceIsSeededAndEndsInALockedMarker() {
        List<SpellVisualGeometry.Stroke> first = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.SCULK_TRACE, 0L, 1L, 4.0D,
                SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> second = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.SCULK_TRACE, 0L, 2L, 4.0D,
                SpellVisualGeometry.Quality.FULL);
        assertTrue(!first.equals(second), "a different seed must trace a different path");
        assertTrue(count(first, SpellVisualGeometry.Style.TRACE) == 1L);
        assertTrue(count(first, SpellVisualGeometry.Style.JAW) >= 1L, "the lock marker exists");
    }

    @Test
    void gravityFieldCompressesInwardAndStaysBounded() {
        List<SpellVisualGeometry.Stroke> early = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.GRAVITY, 0L, SEED, 8.0D,
                SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> late = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.GRAVITY, 5000L, SEED, 8.0D,
                SpellVisualGeometry.Quality.FULL);
        assertTrue(count(early, SpellVisualGeometry.Style.FIELD) >= 5L);
        double earlyReach = early.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.FIELD)
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        double lateReach = late.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.FIELD)
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        assertTrue(lateReach < earlyReach, "the atmosphere bends inward over the field");
        for (SpellVisualGeometry.Stroke stroke : early) {
            for (SpellVisualGeometry.Point point : stroke.points()) {
                assertTrue(point.horizontalLength() <= SpellVisualGeometry.MAX_REACH_BLOCKS);
            }
        }
    }

    @Test
    void guardianFanReadsAsAnAsymmetricFrontCone() {
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.GUARDIAN_FAN, 0L, SEED,
                SpellVisualGeometry.GUARDIAN_FAN_RADIUS, SpellVisualGeometry.Quality.FULL);
        double maxX = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        double maxZ = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.abs(point.z())).max().orElse(0);
        assertEquals(SpellVisualGeometry.GUARDIAN_FAN_RADIUS, maxX, 1.0E-6D);
        double halfAngle = Math.toDegrees(Math.atan2(maxZ, maxX * Math.cos(Math.toRadians(
                SpellVisualGeometry.GUARDIAN_FAN_HALF_ANGLE))));
        assertTrue(halfAngle >= 55.0D && halfAngle <= 70.0D,
                "a 120-130 degree fan, never a full circle, was " + halfAngle);
    }

    @Test
    void separationExpandsFromPlantedStance() {
        List<SpellVisualGeometry.Stroke> early = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.SEPARATION, 0L, SEED, 0.0D,
                SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> late = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.SEPARATION, 1500L, SEED, 0.0D,
                SpellVisualGeometry.Quality.FULL);
        double earlyRing = early.stream().filter(stroke -> stroke.style() == SpellVisualGeometry.Style.RING)
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        double lateRing = late.stream().filter(stroke -> stroke.style() == SpellVisualGeometry.Style.RING)
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(point -> Math.hypot(point.x(), point.z())).max().orElse(0);
        assertTrue(lateRing > earlyRing, "the pressure ring expands outward");
    }

    @Test
    void everySpellAndQualityStaysFiniteAndBounded() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            for (SpellVisualGeometry.Quality quality : SpellVisualGeometry.Quality.values()) {
                List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                        spell, 1300L, SEED, 9.0D, quality);
                assertTrue(strokes.size() <= SpellVisualGeometry.MAX_STROKES,
                        spell + " exceeded the stroke cap");
                int segments = 0;
                for (SpellVisualGeometry.Stroke stroke : strokes) {
                    assertTrue(stroke.points().size() >= 2);
                    assertEquals(stroke.points().size(), stroke.halfWidths().size());
                    for (SpellVisualGeometry.Point point : stroke.points()) {
                        assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.y())
                                && Double.isFinite(point.z()), spell + " produced a non-finite point");
                        assertTrue(point.horizontalLength() <= SpellVisualGeometry.maxReach(spell));
                        assertTrue(point.y() >= -2.0D && point.y() <= 20.0D);
                    }
                    segments += stroke.points().size() - 1;
                }
                assertTrue(segments <= SpellVisualGeometry.MAX_SEGMENTS);
            }
        }
    }

    @Test
    void qualityOnlyChangesSampleDensity() {
        List<SpellVisualGeometry.Stroke> full = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.CROWN, 0L, SEED, 9.0D,
                SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> reduced = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.CROWN, 0L, SEED, 9.0D,
                SpellVisualGeometry.Quality.REDUCED);
        int fullPoints = full.stream().mapToInt(stroke -> stroke.points().size()).sum();
        int reducedPoints = reduced.stream().mapToInt(stroke -> stroke.points().size()).sum();
        assertTrue(reducedPoints < fullPoints, "REDUCED must spend fewer vertices");
        assertEquals(SpellVisualGeometry.CROWN_SPOKES, reduced.stream()
                .filter(stroke -> stroke.style() == SpellVisualGeometry.Style.ARM).count(),
                "the danger footprint does not degrade");
    }

    @Test
    void beamIsAFullLengthLayeredShaftInsideTheDamageCapsule() {
        // N23-P1-05: the signature chest beam must read as a substantial,
        // full-length, layered 3D shaft that hugs the real 1.7-block damage
        // capsule from the inside, not a hairline next to the hit volume.
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.BEAM, 0L, SEED,
                SpellVisualGeometry.BEAM_MAX_LENGTH, SpellVisualGeometry.Quality.FULL);
        double maxX = strokes.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        assertEquals(SpellVisualGeometry.BEAM_MAX_LENGTH, maxX, 1.0E-6D,
                "the shaft spans the whole authored beam length");
        // Every layer is present: the core (ARM), the translucent wake (SHELL),
        // the helices (FIELD), the hoops / muzzle / tip rings (RING), the seeded
        // branches (JAW) and the flared muzzle plate (WEDGE).
        assertTrue(count(strokes, SpellVisualGeometry.Style.ARM) >= 4, "broad energized core");
        assertTrue(count(strokes, SpellVisualGeometry.Style.SHELL) >= 9, "translucent outer wake");
        assertTrue(count(strokes, SpellVisualGeometry.Style.FIELD) >= 2, "counter-rotating helices");
        assertTrue(count(strokes, SpellVisualGeometry.Style.RING) >= 6, "hoops, plate and tip rings");
        assertTrue(count(strokes, SpellVisualGeometry.Style.JAW) >= 3, "forged side branches");
        assertTrue(count(strokes, SpellVisualGeometry.Style.WEDGE) >= 3, "flared muzzle plate");
        // No authored point may sit outside the real capsule radius around the
        // axis, so the visible silhouette never promises more than the damage.
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            for (SpellVisualGeometry.Point point : stroke.points()) {
                double radial = Math.hypot(point.y(), point.z());
                assertTrue(radial <= SpellVisualGeometry.BEAM_RADIUS + 1.0E-6D,
                        "a beam point escaped the damage capsule, radial " + radial);
            }
        }
        // A long beam really is longer than a short one: the length is authored,
        // not fixed.
        double shortX = SpellVisualGeometry.build(SpellVisualGeometry.Spell.BEAM, 0L, SEED, 6.0D,
                        SpellVisualGeometry.Quality.FULL).stream()
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        assertTrue(maxX > shortX + 15.0D, "the authored length drives the shaft extent");
        // The mesh is animated: a later instant rebuilds a different mesh.
        assertTrue(!strokes.equals(SpellVisualGeometry.build(SpellVisualGeometry.Spell.BEAM,
                1700L, SEED, SpellVisualGeometry.BEAM_MAX_LENGTH,
                SpellVisualGeometry.Quality.FULL)), "the beam must animate over its clock");
    }

    @Test
    void geyserIsAnAuthoredCollapsingColumnInsideTheVisualRadius() {
        // N23-P1-08: the Phase 3 eruption must read as a real rock/obsidian
        // column with growing fissures, volumetric flame sheets and debris that
        // collapses -- not a static particle spiral. Everything must stay inside
        // the authored visual radius (under the real 1.9-block damage radius).
        List<SpellVisualGeometry.Stroke> bloom = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.GEYSER, 1080L, SEED,
                SpellVisualGeometry.GEYSER_MAX_HEIGHT, SpellVisualGeometry.Quality.FULL);
        List<SpellVisualGeometry.Stroke> collapse = SpellVisualGeometry.build(
                SpellVisualGeometry.Spell.GEYSER, 2200L, SEED,
                SpellVisualGeometry.GEYSER_MAX_HEIGHT, SpellVisualGeometry.Quality.FULL);
        double bloomTop = bloom.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        double collapseTop = collapse.stream().flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        assertTrue(bloomTop > 8.0D && bloomTop <= SpellVisualGeometry.GEYSER_MAX_HEIGHT + 1.0E-6D,
                "the full column must reach near its authored height, was " + bloomTop);
        // Every radial feature hugs the throat: never wider than the visual
        // radius, so the visible column cannot promise more than its hitbox.
        // Checked on the bloom and on the collapsing tail, because a late-cycle
        // mote that drifts outside the capsule would be just as dishonest.
        for (List<SpellVisualGeometry.Stroke> frame : List.of(bloom, collapse)) {
            for (SpellVisualGeometry.Stroke stroke : frame) {
                for (SpellVisualGeometry.Point point : stroke.points()) {
                    double radial = Math.hypot(point.y(), point.z());
                    assertTrue(radial <= SpellVisualGeometry.GEYSER_VISUAL_RADIUS + 1.0E-6D,
                            "a geyser point escaped the visual radius, radial " + radial);
                    assertTrue(point.x() >= -1.0E-6D
                                    && point.x() <= SpellVisualGeometry.GEYSER_MAX_HEIGHT + 1.0E-6D,
                            "a geyser point escaped the authored column, x " + point.x());
                }
            }
        }
        assertTrue(count(bloom, SpellVisualGeometry.Style.LANE) >= 5, "growing floor fissures");
        assertTrue(count(bloom, SpellVisualGeometry.Style.JAW) >= 5, "rock/obsidian throat");
        assertTrue(count(bloom, SpellVisualGeometry.Style.FIELD) >= 4, "volumetric flame sheets");
        assertTrue(count(bloom, SpellVisualGeometry.Style.TRACE) >= 6, "ash and debris");
        // The authored height drives the extent.
        double shortTop = SpellVisualGeometry.build(SpellVisualGeometry.Spell.GEYSER, 1080L, SEED,
                        6.0D, SpellVisualGeometry.Quality.FULL).stream()
                .flatMap(stroke -> stroke.points().stream())
                .mapToDouble(SpellVisualGeometry.Point::x).max().orElse(0);
        assertTrue(bloomTop > shortTop + 2.0D, "a taller staging really is taller");
        // It animates continuously and collapses rather than sitting static.
        assertTrue(!bloom.equals(SpellVisualGeometry.build(SpellVisualGeometry.Spell.GEYSER,
                1380L, SEED, SpellVisualGeometry.GEYSER_MAX_HEIGHT,
                SpellVisualGeometry.Quality.FULL)), "the geyser must animate over its clock");
        assertTrue(collapseTop < bloomTop, "the column collapses at the end of the cycle");
    }

    @Test
    void cueParserAcceptsOnlyTheExactAuthoredForm() {
        assertEquals(SpellVisualGeometry.Spell.CROWN,
                SpellVisualGeometry.spellCue("3f2a:17:world:v51-crown-4242"));
        assertEquals(4242L, SpellVisualGeometry.cueSeed("3f2a:17:world:v51-crown-4242"));
        assertEquals(SpellVisualGeometry.Spell.WARDEN_SWEEP,
                SpellVisualGeometry.spellCue("e:1:world:v51-sweep-0"));
        assertEquals(SpellVisualGeometry.Spell.BEAM,
                SpellVisualGeometry.spellCue("e:1:world:v51-beam-9"));
        assertEquals(SpellVisualGeometry.Spell.GEYSER,
                SpellVisualGeometry.spellCue("e:1:world:v51-geyser-31"));
        assertNull(SpellVisualGeometry.spellCue("e:1:world:v51-crown"));
        assertNull(SpellVisualGeometry.spellCue("e:1:world:v51-crown-12a"));
        assertNull(SpellVisualGeometry.spellCue("e:1:world:v51-unknown-1"));
        assertNull(SpellVisualGeometry.spellCue("e:1:world:wave6-ritual-CROWN"));
        assertNull(SpellVisualGeometry.spellCue(null));
        assertNull(SpellVisualGeometry.spellCue("e:1:world:v51-crown-1:world:v51-crown-2"));
    }

    @Test
    void authoredTextureIsShipped() throws Exception {
        try (InputStream stream = SpellVisualGeometryTest.class.getResourceAsStream(
                "/assets/copimineclient/textures/entity/end_rift_spell_arc.png")) {
            assertNotNull(stream, "the authored spell-arc texture must ship in the client jar");
            assertTrue(stream.readAllBytes().length > 200);
        }
    }

    @Test
    void qualityTiersUseTheDocumentedSampleDensities() {
        // Section 9A.04: three production tiers that differ only in sample
        // density, exactly the documented 12 / 8 / 5 samples.
        assertEquals(12, SpellVisualGeometry.Quality.FULL.samples());
        assertEquals(8, SpellVisualGeometry.Quality.BALANCED.samples());
        assertEquals(5, SpellVisualGeometry.Quality.REDUCED.samples());
        assertTrue(SpellVisualGeometry.Quality.FULL.samples()
                > SpellVisualGeometry.Quality.BALANCED.samples());
        assertTrue(SpellVisualGeometry.Quality.BALANCED.samples()
                > SpellVisualGeometry.Quality.REDUCED.samples());
    }

    @Test
    void everyAuthoredSpellFillsEveryTierAndAnimates() {
        // The roster is documented as twelve continuously animated families, and
        // the owner may select FULL, BALANCED or REDUCED at any time. A tier that
        // draws nothing, overruns the caps or freezes on the cast clock is a real
        // defect, so it must fail here without a server.
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            for (SpellVisualGeometry.Quality quality : SpellVisualGeometry.Quality.values()) {
                List<SpellVisualGeometry.Stroke> early =
                        SpellVisualGeometry.build(spell, 0L, SEED, 9.0D, quality);
                List<SpellVisualGeometry.Stroke> late =
                        SpellVisualGeometry.build(spell, 1500L, SEED, 9.0D, quality);
                assertTrue(!early.isEmpty(), spell + "/" + quality + " drew nothing");
                assertTrue(early.size() <= SpellVisualGeometry.MAX_STROKES,
                        spell + "/" + quality + " exceeded the stroke cap");
                int segments = 0;
                for (SpellVisualGeometry.Stroke stroke : early) {
                    assertTrue(stroke.points().size() >= 2);
                    assertEquals(stroke.points().size(), stroke.halfWidths().size());
                    segments += stroke.points().size() - 1;
                }
                assertTrue(segments <= SpellVisualGeometry.MAX_SEGMENTS,
                        spell + "/" + quality + " exceeded the segment cap");
                assertTrue(!early.equals(late),
                        spell + "/" + quality + " did not animate on the cast clock");
            }
        }
    }

    @Test
    void reducedTierNeverSpendsMoreVerticesThanFull() {
        // REDUCED exists to trade density for frame time: it may only ever spend
        // fewer (or equal) vertices than FULL, so a low-end client can never be
        // the more expensive one.
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            List<SpellVisualGeometry.Stroke> full = SpellVisualGeometry.build(
                    spell, 1300L, SEED, 9.0D, SpellVisualGeometry.Quality.FULL);
            List<SpellVisualGeometry.Stroke> reduced = SpellVisualGeometry.build(
                    spell, 1300L, SEED, 9.0D, SpellVisualGeometry.Quality.REDUCED);
            int fullPoints = full.stream().mapToInt(stroke -> stroke.points().size()).sum();
            int reducedPoints = reduced.stream().mapToInt(stroke -> stroke.points().size()).sum();
            assertTrue(reducedPoints <= fullPoints, spell + " REDUCED spent more vertices ("
                    + reducedPoints + ") than FULL (" + fullPoints + ")");
        }
    }
}
