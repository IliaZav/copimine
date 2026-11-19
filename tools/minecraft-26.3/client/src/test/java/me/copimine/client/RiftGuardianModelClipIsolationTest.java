package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One {@code RiftGuardianModel} is owned by one renderer instance, and every
 * Echo decoy is a Pillager on the single {@code PillagerRenderer}. The clip and
 * blend state must therefore be keyed per body, or the last submitted decoy
 * decides the clip and the "previous pose" of the next one.
 */
class RiftGuardianModelClipIsolationTest {

    @Test
    void aBodyDoesNotInheritAnotherBodysBlend() {
        var interleaved = new RiftGuardianModelRenderer();
        var control = new RiftGuardianModelRenderer();
        var state = new EndermanRenderState();

        // Warm body A into a mid-blend flinch: HURT lasts two blend ticks, so
        // one tick in the pose is still a blend and remembers a source pose.
        var bodyA = interleaved.modelForPhase("body-a", "RAGE", 0L, "HURT", 1);
        bodyA.setupAnim(state);
        assertEquals("body-a", bodyA.activeClipKey(), "the renderer must keep the per-body key");

        // Body B's first frame on the shared renderer...
        var bodyB = interleaved.modelForPhase("body-b", "RAGE", 0L, "RUN", 1);
        bodyB.setupAnim(state);
        var interleavedB = bodyB.currentPose();

        // ...must equal B's first frame on a renderer that never drew A.
        var controlB = control.modelForPhase("body-b", "RAGE", 0L, "RUN", 1);
        controlB.setupAnim(state);
        assertEquals(controlB.currentPose(), interleavedB,
                "body B inherited body A's blended pose");
        assertEquals("body-b", bodyB.activeClipKey());

        // Body A's next frame must likewise equal A's second frame on a
        // renderer that never drew B.
        var controlA = new RiftGuardianModelRenderer();
        controlA.modelForPhase("body-a", "RAGE", 0L, "HURT", 1).setupAnim(state);
        var controlA2 = controlA.modelForPhase("body-a", "RAGE", 0L, "HURT", 2);
        controlA2.setupAnim(state);

        var interleavedA2 = interleaved.modelForPhase("body-a", "RAGE", 0L, "HURT", 2);
        interleavedA2.setupAnim(state);
        assertEquals(controlA2.currentPose(), interleavedA2.currentPose(),
                "body A's flinch was rewritten by body B's clip");
    }

    @Test
    void theDefaultKeyKeepsSingleBodyCallersWorking() {
        var renderer = new RiftGuardianModelRenderer();
        var model = renderer.modelForPhase("RAGE", 0L, "FINAL_STRIKE", 10);
        model.setupAnim(new EndermanRenderState());
        assertEquals(RiftGuardianModel.DEFAULT_CLIP_KEY, model.activeClipKey());
        assertEquals(UserEndBossAnimationPlayer.sample("FINAL_STRIKE", 10), model.currentPose());
    }
}