package me.copimine.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

/** World-depth fog pass. All world reads occur during extraction, never in a GPU callback. */
public final class BlackFogVolumeRuntime {
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath("copimineclient", "wave5_volume");
    public static final int UNIFORM_BYTES = 224;
    private static volatile float[] snapshot = new float[56];
    private static final ThreadLocal<ByteBuffer> uniformBytes = ThreadLocal.withInitial(() ->
            ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder()));
    private static Object cachedWorld;
    private static String cachedEvent = "";
    private static long cachedGeneration, sampledAt = -1;
    private static int cachedX, cachedZ, cachedY;
    private static final float[] safe = new float[8];
    public record Chamber(float minX, float minZ, float maxX, float maxZ, float floor, float strength) {}
    private static volatile Chamber chamber;
    public static Chamber chamber() { return chamber; }

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
            snapshot = new float[56];
            cachedWorld = null;
            chamber = null;
            return;
        }
        var camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.projectionMatrix == null
                || camera.viewRotationMatrix == null) return;
        if (!effects.contains(EFFECT)) effects.add(EFFECT);
        refreshSafeVolume(client, manager, now);
        double floorTop = BlackFogChamberRuntime.floorTop(client, manager, now);
        if (safe[3] > 0) {
            // Safe occupants receive NO event post effect. Their surrounding
            // smoke is actual world geometry; user-selected shaders stay alone.
            chamber = new Chamber(safe[0], safe[2], safe[4], safe[6], safe[1] + 0.15F, strength);
            if (camera.pos.x >= safe[0] && camera.pos.x <= safe[4]
                    && camera.pos.z >= safe[2] && camera.pos.z <= safe[6]
                    && camera.pos.y >= safe[1] && camera.pos.y <= safe[5]) {
                effects.remove(EFFECT);
                snapshot = new float[56];
                return;
            }
        } else {
            chamber = null;
        }
        float[] data = new float[56];
        new Matrix4f(camera.projectionMatrix).invert().get(data);
        float[] rotation = new float[16];
        new Matrix4f(camera.viewRotationMatrix).invert().get(rotation);
        System.arraycopy(rotation, 0, data, 16, 16);
        data[32] = (float) camera.pos.x; data[33] = (float) camera.pos.y; data[34] = (float) camera.pos.z;
        data[35] = (now % 1_000_000L) / 1000.0F;
        System.arraycopy(safe, 0, data, 36, 8);
        data[44] = strength;
        data[45] = BlackFogVolumePolicy.EXTINCTION_PER_BLOCK;
        data[46] = safe[3];
        data[47] = 12.0F;
        data[48] = arena.minX(); data[49] = Double.isFinite(floorTop) ? (float) floorTop : arena.minY(); data[50] = arena.minZ();
        data[52] = arena.maxX() + 1;
        data[53] = Double.isFinite(floorTop) ? (float) floorTop + BlackFogVolumePolicy.FOG_HEIGHT : arena.maxY() + 1;
        data[54] = arena.maxZ() + 1;
        snapshot = data;
    }

    public static ByteBuffer uniforms() {
        float[] data = snapshot;
        ByteBuffer result = uniformBytes.get();
        result.clear();
        result.asFloatBuffer().put(data);
        return result;
    }

    private static void refreshSafeVolume(Minecraft client, EndEventBlackFogManager manager, long now) {
        int x = client.player.getBlockX(), y = client.player.getBlockY(), z = client.player.getBlockZ();
        if (cachedWorld == client.level && cachedEvent.equals(manager.eventId())
                && cachedGeneration == manager.generation() && cachedX == x && cachedZ == z
                && Math.abs(y - cachedY) <= 1 && sampledAt >= 0 && now >= sampledAt && now - sampledAt < 250) return;
        cachedWorld = client.level; cachedEvent = manager.eventId(); cachedGeneration = manager.generation();
        cachedX = x; cachedY = y; cachedZ = z; sampledAt = now;
        java.util.Arrays.fill(safe, 0);
        int floor = Integer.MIN_VALUE;
        for (int dy = -3; dy <= 1; dy++) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (client.level.hasChunkAt(p) && client.level.getBlockState(p).is(Blocks.EMERALD_BLOCK)
                    && Math.abs(client.player.getY() - (y + dy + 1.0)) <= 2.0) { floor = y + dy; break; }
        }
        if (floor == Integer.MIN_VALUE) return;
        // Only the occupied connected platform is clear. Another safe zone is never revealed.
        final int floorY = floor;
        var box = BlackFogVolumePolicy.find(x, z, (columnX, columnZ) -> {
            BlockPos p = new BlockPos(columnX, floorY, columnZ);
            return client.level.hasChunkAt(p) && client.level.getBlockState(p).is(Blocks.EMERALD_BLOCK);
        });
        safe[0] = box.minX(); safe[1] = floor + 0.85F; safe[2] = box.minZ(); safe[3] = box.cells() > 0 ? 1 : 0;
        safe[4] = box.maxX(); safe[5] = floor + 1.0F + BlackFogVolumePolicy.CHAMBER_HEIGHT; safe[6] = box.maxZ();
    }
}
