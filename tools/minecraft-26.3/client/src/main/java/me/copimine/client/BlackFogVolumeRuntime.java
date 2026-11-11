package me.copimine.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** World-depth fog pass. All world reads occur during extraction, never in a GPU callback. */
public final class BlackFogVolumeRuntime {
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath("copimineclient", "wave5_volume");
    public static final int UNIFORM_BYTES = (14 + BlackFogGuidePolicy.MAX_GUIDES) * 16;
    private static volatile float[] snapshot = new float[UNIFORM_BYTES / Float.BYTES];
    private static final ThreadLocal<ByteBuffer> uniformBytes = ThreadLocal.withInitial(() ->
            ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder()));
    /** Kept as a compatibility type for the retired chamber renderer. */
    public record Chamber(float minX, float minZ, float maxX, float maxZ, float floor, float strength) {}
    public static Chamber chamber() { return null; }

    private BlackFogVolumeRuntime() {}

    public static void extract(GameRenderer renderer) {
        Minecraft client = Minecraft.getInstance();
        var effects = renderer.gameRenderState().requestedPostEffects;
        var manager = ClientBridgeProtocol.endEventBlackFog();
        long now = System.currentTimeMillis();
        float strength = client.level == null || client.player == null || client.player.isDeadOrDying()
                ? 0 : manager.fadeStrength(client.level.dimension().identifier().getPath(), now);
        var arena = ClientBridgeProtocol.endEventArenaBounds(manager.eventId(), manager.generation());
        if (arena == null || client.player == null || client.player.getX() < arena.minX()
                || client.player.getX() >= arena.maxX() + 1.0 || client.player.getZ() < arena.minZ()
                || client.player.getZ() >= arena.maxZ() + 1.0 || client.player.getY() < arena.minY()
                || client.player.getY() >= arena.maxY() + 1.0) strength = 0;
        if (strength <= 0) {
            effects.remove(EFFECT);
            snapshot = new float[UNIFORM_BYTES / Float.BYTES];
            return;
        }
        var camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.projectionMatrix == null
                || camera.viewRotationMatrix == null) return;
        if (!effects.contains(EFFECT)) effects.add(EFFECT);
        // Safety is server gameplay state; every arena cell uses the same optical volume.
        double floorTop = BlackFogChamberRuntime.floorTop(client, manager, now);
        float[] data = new float[UNIFORM_BYTES / Float.BYTES];
        new Matrix4f(camera.projectionMatrix).invert().get(data);
        float[] rotation = new float[16];
        new Matrix4f(camera.viewRotationMatrix).invert().get(rotation);
        System.arraycopy(rotation, 0, data, 16, 16);
        data[32] = (float) camera.pos.x; data[33] = (float) camera.pos.y; data[34] = (float) camera.pos.z;
        data[35] = (now % 1_000_000L) / 1000.0F;
        // Reserved former safe-box slots remain zero for uniform-buffer compatibility.
        data[44] = strength;
        data[45] = BlackFogVolumePolicy.EXTINCTION_PER_BLOCK;
        data[46] = 0;
        data[47] = BlackFogGuidePolicy.MAX_DISTANCE;
        data[48] = arena.minX(); data[49] = Double.isFinite(floorTop) ? (float) floorTop : arena.minY(); data[50] = arena.minZ();
        data[52] = arena.maxX() + 1;
        data[53] = Double.isFinite(floorTop) ? (float) floorTop + BlackFogVolumePolicy.FOG_HEIGHT : arena.maxY() + 1;
        data[54] = arena.maxZ() + 1;
        float guideStrength = BlackFogGuidePolicy.strength(manager.cycleAgeMillis(now));
        if (guideStrength > 0.0F) {
            var guides = ClientBridgeProtocol.endEventWorldVfx().snapshots(manager.eventId(), manager.generation(), now)
                    .stream().filter(beam -> BlackFogGuidePolicy.isGuide(beam.instanceId())
                            && beam.dimension().equals(manager.dimension()) && beam.expiresAtMillis() > now
                            && beam.start().x >= arena.minX() && beam.start().x <= arena.maxX() + 1.0
                            && beam.start().z >= arena.minZ() && beam.start().z <= arena.maxZ() + 1.0
                            && beam.start().y >= arena.minY() && beam.start().y <= arena.maxY() + 1.0)
                    .sorted(java.util.Comparator.comparingDouble(beam -> beam.start().distanceToSqr(camera.pos)))
                    .limit(BlackFogGuidePolicy.MAX_GUIDES).toList();
            for (int i = 0; i < guides.size(); i++) {
                var point = guides.get(i).start();
                int offset = 56 + i * 4;
                data[offset] = (float) point.x; data[offset + 1] = (float) point.y;
                data[offset + 2] = (float) point.z; data[offset + 3] = guideStrength;
            }
        }
        snapshot = data;
    }

    public static ByteBuffer uniforms() {
        float[] data = snapshot;
        ByteBuffer result = uniformBytes.get();
        result.clear();
        result.asFloatBuffer().put(data);
        return result;
    }

}
