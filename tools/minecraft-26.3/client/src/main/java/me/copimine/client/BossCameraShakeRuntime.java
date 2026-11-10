package me.copimine.client;

import java.util.UUID;
import me.copimine.client.mixin.ClientWorldAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** No extra packets, player rotation changes or persistent camera offsets. */
public final class BossCameraShakeRuntime {
    private static ClientConfig config;
    private BossCameraShakeRuntime() { }

    public static void configure(ClientConfig value) { config = value; }

    public static void apply(Camera camera) {
        if (config == null || config.bossCameraShakeStrength() <= 0 || camera == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.isDeadOrDying()
                || client.gui.screen() != null || client.isPaused() || camera.entity() != client.player) return;
        EndEventClientState state = ClientBridgeProtocol.endEventState();
        if (!state.hasBossBinding() || !state.hasActiveBossBar()) return;
        String bossId = state.bossBar().bossUuid();
        Entity boss;
        try {
            boss = ((ClientWorldAccessor) client.level).copimine$getEntities().get(UUID.fromString(bossId));
        } catch (IllegalArgumentException error) {
            return;
        }
        if (boss == null || boss.isRemoved()) return;
        var sample = BossCameraShakePolicy.sample(state.bossAnimationForEntity(bossId),
                state.bossAnimationElapsedMillisForEntity(bossId, System.currentTimeMillis()),
                camera.position().distanceTo(boss.position()), config.bossCameraShakeStrength());
        if (sample.isZero()) return;
        // Camera.update rebuilt the base quaternion this frame. Modify only
        // that render quaternion, never the player's aim, input or position.
        camera.rotation().rotateXYZ((float) Math.toRadians(sample.pitchDegrees()),
                (float) Math.toRadians(sample.yawDegrees()), (float) Math.toRadians(sample.rollDegrees()));
    }
}
