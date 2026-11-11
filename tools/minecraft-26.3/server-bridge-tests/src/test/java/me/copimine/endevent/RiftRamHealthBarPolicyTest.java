package me.copimine.endevent;
import me.copimine.endevent.domain.RiftRamPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiftRamHealthBarPolicyTest {
    @Test void objectiveAndLiveArmourHaveOneCompletionCount() {
        assertEquals(RiftRamPolicy.REQUIRED_IMPACTS,
                me.copimine.endevent.domain.EndRiftObjective.REQUIRED_PILLAR_IMPACTS);
    }
    @Test void breakingArmourDoesNotFabricateMissingHealth() {
        for (int impacts = 0; impacts <= RiftRamPolicy.REQUIRED_IMPACTS; impacts++) {
            assertEquals(1, RiftRamPolicy.bossBarProgress(400, 400, impacts));
            assertEquals(.75, RiftRamPolicy.bossBarProgress(300, 400, impacts));
            assertEquals(0, RiftRamPolicy.bossBarProgress(0, 400, impacts));
        }
        assertEquals(0, RiftRamPolicy.bossBarProgress(Double.NaN, 400, 0));
    }
}
