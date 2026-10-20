package me.copimine.client;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RitualSpellPresentationPolicyTest {
    @Test
    void onlyExactServerWorldKeysEnableGlyphOrTargetFeedback() {
        var glyph = RitualSpellPresentationPolicy.parse("event:9:world:wave6-spell-glyph-GRAVITY_WELL-warning");
        assertNotNull(glyph);
        assertEquals(RitualSpellPresentationPolicy.Spell.GRAVITY_WELL, glyph.spell());
        assertEquals(RitualSpellPresentationPolicy.Channel.GLYPH, glyph.channel());
        assertEquals(RitualSpellPresentationPolicy.Stage.WARNING, glyph.stage());
        assertEquals(RitualSpellPresentationPolicy.Channel.SCREEN,
                RitualSpellPresentationPolicy.parse("event:9:world:wave6-spell-screen-SOUL_BRAND-active").channel());
        for (String invalid : new String[]{
                "wave6-spell-screen-SOUL_BRAND-active",
                "event:9:world:wave6-spell-screen-SOUL_BRAND-active-extra",
                "event:9:world:wave6-spell-screen-soul_brand-active",
                "event:9:world:wave6-spell-screen-UNKNOWN-active",
                "event:9:world:wave6-spell-glyph-RIFT_CHAINS-hit",
                "event:9:world:prefix-wave6-spell-screen-SOUL_BRAND-active",
                "event:9:world:nested:world:wave6-spell-screen-SOUL_BRAND-active"}) {
            assertNull(RitualSpellPresentationPolicy.parse(invalid), invalid);
        }
    }

    @Test
    void gravityRuneMatchesFourBlockGameplayRadiusAndBillboardsStaySmall() {
        var gravity = cue("glyph-GRAVITY_WELL-warning");
        var gravityQuad = RitualSpellPresentationPolicy.glyphQuad(gravity);
        assertEquals(-4.0F, gravityQuad.get(0).x());
        assertEquals(4.0F, gravityQuad.get(1).x());
        assertTrue(gravityQuad.stream().allMatch(point -> point.y() == 0));
        assertEquals(4.7F, cue("glyph-RIFT_BARRAGE-active").spell().diameter());
        var crown = RitualSpellPresentationPolicy.glyphQuad(cue("glyph-SOUL_BRAND-active"));
        assertEquals(1.5F, crown.get(1).x() - crown.get(0).x());
        assertTrue(crown.stream().allMatch(point -> point.z() == 0));
        var chains = RitualSpellPresentationPolicy.glyphQuad(cue("glyph-RIFT_CHAINS-active"));
        assertEquals(1.1F, chains.get(1).x() - chains.get(0).x());
        assertEquals(RitualSpellPresentationPolicy.Orientation.BILLBOARD,
                cue("glyph-RIFT_CHAINS-active").spell().orientation());
    }

    @Test
    void billboardsHideNearTheVictimsCameraWhileFloorRunesRemainVisible() {
        for (String spell : List.of("SOUL_BRAND", "RIFT_CHAINS")) {
            var billboard = cue("glyph-" + spell + "-active");
            assertFalse(RitualSpellPresentationPolicy.glyphVisibleFrom(billboard, .25), "half metre from victim camera");
            assertFalse(RitualSpellPresentationPolicy.glyphVisibleFrom(billboard, .64), "0.8 metre from victim camera");
            assertFalse(RitualSpellPresentationPolicy.glyphVisibleFrom(billboard, 2.249), "below1.5 metre boundary");
            assertTrue(RitualSpellPresentationPolicy.glyphVisibleFrom(billboard, 2.25));
            assertTrue(RitualSpellPresentationPolicy.glyphVisibleFrom(billboard, 4));
        }
        assertTrue(RitualSpellPresentationPolicy.glyphVisibleFrom(cue("glyph-GRAVITY_WELL-active"), 0));
        assertTrue(RitualSpellPresentationPolicy.glyphVisibleFrom(cue("glyph-RIFT_BARRAGE-warning"), .25));
        assertFalse(RitualSpellPresentationPolicy.glyphVisibleFrom(cue("glyph-SOUL_BRAND-active"), Double.NaN));
    }

    @Test
    void layersAreAcquiredAndConsumedInCompletePassesBeforeAnySwitch() {
        List<EndEventWorldVfxManager.BeamSnapshot> beams = List.of(
                beam("world:wave6-spell-glyph-SOUL_BRAND-active", "overworld", 100, 1000),
                beam("ordinary-1", "overworld", 100, 1000),
                beam("world:wave6-ritual-caster", "overworld", 100, 1000),
                beam("world:wave6-spell-screen-SOUL_BRAND-active", "overworld", 100, 1000),
                beam("ordinary-2", "overworld", 100, 1000),
                beam("world:wave6-spell-glyph-GRAVITY_WELL-active", "overworld", 100, 1000),
                beam("other-dimension", "the_end", 100, 1000),
                beam("expired", "overworld", 100, 300));
        List<String> drawn = new ArrayList<>();
        List<RitualSpellPresentationPolicy.WorldPass> acquired = new ArrayList<>();
        int[] currentLayer = {0};
        RitualSpellPresentationPolicy.drawWorldPasses(beams, "overworld", 400, pass -> {
            acquired.add(pass);
            int thisLayer = ++currentLayer[0];
            return beam -> {
                // Immediate providers end a non-shared buffer when another layer is acquired.
                assertEquals(thisLayer, currentLayer[0], "attempted write to an ended layer");
                drawn.add(beam.instanceId());
            };
        });
        assertEquals(List.of("event:9:ordinary-1", "event:9:ordinary-2",
                "event:9:world:wave6-ritual-caster",
                "event:9:world:wave6-spell-glyph-GRAVITY_WELL-active",
                "event:9:world:wave6-spell-glyph-SOUL_BRAND-active"), drawn);
        assertEquals(RitualSpellPresentationPolicy.PassKind.RIBBON, acquired.get(0).kind());
        assertEquals("CHANNEL", acquired.get(1).kind().name(), "caster channel requires a textured beam pass");
        assertEquals(4, acquired.size());
    }

    @Test
    void targetCueUsesActiveStageAndNeverAppearsForAnExpiredOrOtherDimensionPacket() {
        var warning = beam("world:wave6-spell-screen-RIFT_CHAINS-warning", "overworld", 100, 1000);
        var active = beam("world:wave6-spell-screen-RIFT_CHAINS-active", "overworld", 300, 800);
        assertEquals(active, RitualSpellPresentationPolicy.screenCue(List.of(warning, active), "overworld", 400));
        var delayedWarning = beam("world:wave6-spell-screen-RIFT_CHAINS-warning", "overworld", 500, 1500);
        assertEquals(active, RitualSpellPresentationPolicy.screenCue(List.of(delayedWarning, active), "overworld", 600));
        assertNull(RitualSpellPresentationPolicy.screenCue(List.of(active), "the_end", 400));
        assertNull(RitualSpellPresentationPolicy.screenCue(List.of(active), "overworld", 800));
        assertNull(RitualSpellPresentationPolicy.screenCue(
                List.of(beam("world:wave6-spell-glyph-RIFT_CHAINS-active", "overworld", 100, 1000)), "overworld", 400));
    }

    @Test
    void panelAndFaintEdgeCuesAvoidTheArenaConePrisonerPanelAndVanillaHud() {
        for (int[] viewport : new int[][]{{854, 480}, {426, 240}, {320, 180}, {960, 180}, {256, 144}}) {
            var prisoner = PrisonerHudLayout.compute(viewport[0], viewport[1], 100, 20);
            var protectedAreas = List.of(prisoner.bounds(), prisoner.vanillaHud());
            var layout = RitualSpellPresentationPolicy.screenLayout(viewport[0], viewport[1], protectedAreas);
            if (!layout.visible()) continue;
            var areas = new ArrayList<>(layout.edgeCues());
            areas.add(layout.panel());
            for (var area : areas) {
                assertFalse(area.intersects(layout.clearView()), "arena cone must stay clear");
                for (var reserved : protectedAreas) assertFalse(area.intersects(reserved));
                assertTrue(area.left() >= 0 && area.right() <= viewport[0]);
                assertTrue(area.top() >= 0 && area.bottom() <= viewport[1]);
            }
        }
        assertFalse(RitualSpellPresentationPolicy.screenLayout(100, 60, List.of()).visible());
        for (var stage : RitualSpellPresentationPolicy.Stage.values()) {
            for (long now = 100; now <= 1100; now += 25) {
                int alpha = RitualSpellPresentationPolicy.screenAlpha(stage, 100, 1100, now);
                assertTrue(alpha >= 0 && alpha <= 40);
            }
        }
        assertEquals(0, RitualSpellPresentationPolicy.screenAlpha(RitualSpellPresentationPolicy.Stage.ACTIVE, 100, 1100, 1100));
        assertTrue(RitualSpellPresentationPolicy.screenAlpha(RitualSpellPresentationPolicy.Stage.ACTIVE, 100, 1100, 400) > 0);
        assertTrue(RitualSpellPresentationPolicy.glyphAlpha(cue("glyph-GRAVITY_WELL-active"), 100, 1100, 400) > 0);
        assertEquals(0, RitualSpellPresentationPolicy.glyphAlpha(cue("glyph-GRAVITY_WELL-active"), 100, 1100, 1100));
    }

    static RitualSpellPresentationPolicy.Cue cue(String suffix) {
        return RitualSpellPresentationPolicy.parse("event:9:world:wave6-spell-" + suffix);
    }

    static EndEventWorldVfxManager.BeamSnapshot beam(String suffix, String dimension, long started, long expires) {
        return new EndEventWorldVfxManager.BeamSnapshot("event:9:" + suffix, dimension,
                Vec3d.ZERO, new Vec3d(0, .1, 0), 0xAA44FF, .1F, started, expires);
    }
}
