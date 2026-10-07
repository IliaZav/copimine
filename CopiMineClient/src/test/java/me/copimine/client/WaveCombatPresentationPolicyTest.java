package me.copimine.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaveCombatPresentationPolicyTest {
    @Test void ObeliskShotWarningIsLocalToTheCrownWithoutAFlightPath() {
        var warning=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-charge-reflect");
        assertEquals(WaveCombatPresentationPolicy.Shape.BILLBOARD,warning.shape());
        var release=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-release-reflect");
        assertEquals(WaveCombatPresentationPolicy.Shape.BILLBOARD,release.shape());
        assertTrue(WaveCombatPresentationPolicy.visible(warning,9));
    }
    @Test void onlyAuthoredWaveKeysProduceTexturedCues() {
        var cue=WaveCombatPresentationPolicy.parse("event:12:world:wave-ai-ab12cd34-charge-salvo");
        assertNotNull(cue);
        assertEquals(WaveCombatPresentationPolicy.Shape.LANE,cue.shape());
        assertEquals(3,cue.tile());
        for(String malformed:new String[]{"wave-ai-ab12cd34-charge-salvo","event:12:world:wave-ai-ab12cd34-charge-boss",
                "event:12:world:wave-ai-ab12cd34-unknown-salvo","event:12:world:wave-ai-ab12cd34-charge-salvo-extra",
                "event:12:world:wave-ai-ab12cd34-charge-salvo:world:ignored"}) assertNull(WaveCombatPresentationPolicy.parse(malformed));
    }
    @Test void FrozenAndRecoverySignalsStayAboveMobAndFadeOut() {
        var frozen=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-frozen-freeze");
        assertEquals(WaveCombatPresentationPolicy.Shape.BILLBOARD,frozen.shape());
        assertEquals(5,frozen.tile());
        var recovery=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-recover-dash");
        assertEquals(6,recovery.tile());
        assertEquals(0,WaveCombatPresentationPolicy.alpha(recovery,1000,2000,2000));
        assertEquals(0,WaveCombatPresentationPolicy.alpha(frozen,1000,2000,900));
        assertTrue(WaveCombatPresentationPolicy.alpha(frozen,1000,2000,1500)>100);
        assertFalse(WaveCombatPresentationPolicy.visible(0.8));
        assertTrue(WaveCombatPresentationPolicy.visible(9));
    }
    @Test void WidthAndLengthCannotFillTheArenaFromMalformedPackets() {
        assertEquals(12,WaveCombatPresentationPolicy.laneLength(1000));
        assertEquals(0,WaveCombatPresentationPolicy.laneLength(Double.NaN));
        assertEquals(1.35,WaveCombatPresentationPolicy.laneHalfWidth(0.45),.001);
        assertEquals(.45,WaveCombatPresentationPolicy.laneHalfWidth(Double.NaN),.001);
    }
    @Test void FloorWarningsRemainVisibleWhenThePlayerStandsInsideTheAttack() {
        var floor=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-charge-snare");
        var lane=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-charge-dash");
        var frozen=WaveCombatPresentationPolicy.parse("event:2:world:wave-ai-01234567-frozen-freeze");
        assertTrue(WaveCombatPresentationPolicy.visible(floor,0.8));
        assertTrue(WaveCombatPresentationPolicy.visible(lane,0.8));
        assertFalse(WaveCombatPresentationPolicy.visible(frozen,0.8));
        assertFalse(WaveCombatPresentationPolicy.visible(floor,Double.NaN));
        assertFalse(WaveCombatPresentationPolicy.visible(floor,4097));
        assertEquals(1.8F,WaveCombatPresentationPolicy.impactHalfWidth(lane));
        assertEquals(0,WaveCombatPresentationPolicy.impactHalfWidth(frozen));
    }
}
