package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleFacingPolicyTest {
    @Test
    void hurtAndDeathKeepTheirLastFacingAndActiveTurnsFollowTheShortestSmoothArc() throws Exception {
        Class<?> policy = Class.forName("me.copimine.client.EndRiftTentacleFacingPolicy");
        Method tracksTarget = policy.getMethod("tracksTarget", String.class);
        assertTrue((boolean) tracksTarget.invoke(null, "TELEGRAPH_GRAB"));
        assertTrue((boolean) tracksTarget.invoke(null, "HOLD"));
        assertFalse((boolean) tracksTarget.invoke(null, "HIT_RECOVERY"));
        assertFalse((boolean) tracksTarget.invoke(null, "DYING"));

        Method step = policy.getMethod("advanceYaw", float.class, float.class, long.class);
        float current = (float) Math.toRadians(170.0D);
        float target = (float) Math.toRadians(-170.0D);
        float advanced = (float) step.invoke(null, current, target, 100L);

        assertEquals(target, advanced, 0.001F,
                "the facing should take the 20-degree shortest arc instead of rotating 340 degrees");
    }
}
