package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndRiftBossWorldScalePolicyTest {
    @Test
    void importedGuardianUsesItsAuthoredWorldScale() {
        assertEquals(1.0F, EndRiftBossWorldScalePolicy.renderScale(), 0.0001F);
    }

    @Test
    void suppliedGuardianMeshCancelsVanillaRootOffsetInsteadOfHovering() {
        assertEquals(1.4827F, EndRiftBossWorldScalePolicy.modelOriginCorrectionY(), 0.02F,
                "the source mesh foot at 0.0183 blocks must be lowered to the living-renderer floor");
    }
}
