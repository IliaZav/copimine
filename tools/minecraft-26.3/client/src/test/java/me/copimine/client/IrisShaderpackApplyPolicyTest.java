package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisShaderpackApplyPolicyTest {
    @Test
    void blocksAnExistingUserShaderpackWhenOverrideIsDisabled() {
        assertTrue(IrisShaderpackApplyPolicy.isBlockedByExistingPack(false, true, "Complementary", false));
    }

    @Test
    void permitsExistingPackWhenOverrideIsEnabled() {
        assertFalse(IrisShaderpackApplyPolicy.isBlockedByExistingPack(false, true, "Complementary", true));
    }

    @Test
    void permitsCopiMinePackAndSubsequentOwnedSwitches() {
        assertFalse(IrisShaderpackApplyPolicy.isBlockedByExistingPack(false, true, "copimine_visual.zip", false));
        assertFalse(IrisShaderpackApplyPolicy.isBlockedByExistingPack(true, true, "Complementary", false));
    }

    @Test
    void permitsSwitchWhenNoExistingPackIsEnabled() {
        assertFalse(IrisShaderpackApplyPolicy.isBlockedByExistingPack(false, false, "Complementary", false));
    }
}
