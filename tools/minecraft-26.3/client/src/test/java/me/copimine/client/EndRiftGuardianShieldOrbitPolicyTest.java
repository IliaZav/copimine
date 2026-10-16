package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftGuardianShieldOrbitPolicyTest {
    @Test
    void keepsCachedOrbitWhenItMatchesTheServerCarrier() {
        EndRiftGuardianShieldModel.Orbit orbit = EndRiftGuardianShieldModel.orbitFromCarrier(
                0.0D, 0.0D, 0.0D,
                1.5D, 1.0D, 0.0D, 100.0D);

        assertFalse(EndRiftGuardianShieldModel.shouldReseedOrbit(
                orbit, 100.0D,
                0.0D, 0.0D, 0.0D,
                1.5D, 1.0D, 0.0D));
    }

    @Test
    void reseedsWhenThePredictedOrbitDriftsFromTheServerCarrier() {
        EndRiftGuardianShieldModel.Orbit orbit = EndRiftGuardianShieldModel.orbitFromCarrier(
                0.0D, 0.0D, 0.0D,
                1.5D, 1.0D, 0.0D, 100.0D);

        assertTrue(EndRiftGuardianShieldModel.shouldReseedOrbit(
                orbit, 100.0D,
                0.0D, 0.0D, 0.0D,
                2.5D, 1.0D, 0.0D));
    }

    @Test
    void reseedsMissingOrNonFiniteOrbitState() {
        assertTrue(EndRiftGuardianShieldModel.shouldReseedOrbit(
                null, 100.0D,
                0.0D, 0.0D, 0.0D,
                1.5D, 1.0D, 0.0D));
        assertTrue(EndRiftGuardianShieldModel.shouldReseedOrbit(
                new EndRiftGuardianShieldModel.Orbit(Double.NaN, 1.0D, 100.0D, 0.0D),
                100.0D,
                0.0D, 0.0D, 0.0D,
                1.5D, 1.0D, 0.0D));
    }
}
