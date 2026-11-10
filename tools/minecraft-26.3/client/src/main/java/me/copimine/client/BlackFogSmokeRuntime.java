package me.copimine.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

/** Real, depth-tested world smoke. Never reloads resources or clears other particles/models. */
public final class BlackFogSmokeRuntime {
    private static final ArrayList<Particle> owned = new ArrayList<>();
    private static final Map<Long, Boolean> floorCache = new HashMap<>();
    private static Object world;
    private static String event = "";
    private static long generation;
    private static int tick, floorY;
    private static boolean primed;
    private static final java.util.Random random = new java.util.Random();
    private BlackFogSmokeRuntime() {}

    public static void tick(Minecraft client) {
        var fog = ClientBridgeProtocol.endEventBlackFog();
        long now = System.currentTimeMillis();
        var arena = ClientBridgeProtocol.endEventArenaBounds(fog.eventId(), fog.generation());
        if (client.level == null || client.player == null || client.player.isDeadOrDying() || arena == null
                || fog.fadeStrength(client.level.dimension().identifier().getPath(), now) <= 0
                || client.player.getX() < arena.minX() || client.player.getX() >= arena.maxX() + 1.0
                || client.player.getZ() < arena.minZ() || client.player.getZ() >= arena.maxZ() + 1.0
                || client.player.getY() < arena.minY() || client.player.getY() >= arena.maxY() + 1.0) {
            clear();
            return;
        }
        boolean first = world != client.level || !event.equals(fog.eventId()) || generation != fog.generation();
        if (first) {
            clear();
            world = client.level; event = fog.eventId(); generation = fog.generation();
        }
        double base = BlackFogChamberRuntime.floorTop(client, fog, now);
        if (!Double.isFinite(base)) return;
        int nextY = (int) base;
        if (tick++ % 10 == 0 || floorY != nextY) floorCache.clear();
        floorY = nextY;
        BlackFogVolumePolicy.Floor safeFloor = (x, z) -> floorCache.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> {
            for (int dy = -3; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(x, floorY + dy, z);
                if (client.level.hasChunkAt(pos) && client.level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) return true;
            }
            return false;
        });
        owned.removeIf(p -> {
            var b = p.getBoundingBox();
            double x = (b.minX + b.maxX) * 0.5, z = (b.minZ + b.maxZ) * 0.5;
            double radius = p instanceof SingleQuadParticle quad ? quad.getQuadSize(0) * 1.42 : 1.25;
            boolean discard = !p.isAlive() || x < arena.minX() + BlackFogSmokePolicy.RADIUS
                    || x > arena.maxX() + 1.0 - BlackFogSmokePolicy.RADIUS
                    || z < arena.minZ() + BlackFogSmokePolicy.RADIUS
                    || z > arena.maxZ() + 1.0 - BlackFogSmokePolicy.RADIUS
                    || b.minY - radius < base || b.minY + radius > base + BlackFogVolumePolicy.FOG_HEIGHT
                    || (b.minY - radius < base + BlackFogVolumePolicy.CHAMBER_HEIGHT
                    && !BlackFogSmokePolicy.clearOfSafeFloor(x, z, safeFloor));
            if (discard) p.remove();
            return discard;
        });
        // Stop replenishing during CLEAR; the post pass alone has a short exit fade.
        if (!fog.active()) { clear(); return; }
        int budget = BlackFogSmokePolicy.budget(owned.size(), !primed);
        primed = true;
        for (int attempt = 0, spawned = 0; attempt < budget * 4 && spawned < budget; attempt++) {
            double x = Math.max(arena.minX() + 1.3, client.player.getX() - 22)
                    + random.nextDouble() * (Math.min(arena.maxX() - 0.3, client.player.getX() + 22)
                    - Math.max(arena.minX() + 1.3, client.player.getX() - 22));
            double z = Math.max(arena.minZ() + 1.3, client.player.getZ() - 22)
                    + random.nextDouble() * (Math.min(arena.maxZ() - 0.3, client.player.getZ() + 22)
                    - Math.max(arena.minZ() + 1.3, client.player.getZ() - 22));
            boolean roof = random.nextInt(3) == 0;
            boolean soul = !roof && random.nextInt(6) == 0;
            double y = base + (roof ? 3.5 : 1.25 + random.nextDouble() * 1.4);
            if ((!roof && !soul && Math.hypot(x - client.player.getX(), z - client.player.getZ()) < 2.0)
                    || (!roof && !BlackFogSmokePolicy.clearOfSafeFloor(x, z, safeFloor))) continue;
            BlockPos position = BlockPos.containing(x, y, z);
            if (!client.level.hasChunkAt(position) || !client.level.getBlockState(position).isAir()) continue;
            // Built-in translucent sprite provider works without Iris or optional mods.
            Particle p = client.particleEngine.createParticle(soul ? ParticleTypes.SOUL : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z, (random.nextDouble() - 0.5) * 0.009, 0.001,
                    (random.nextDouble() - 0.5) * 0.009);
            if (p instanceof SingleQuadParticle quad) {
                float size = quad.getQuadSize(0);
                if (size > 0) quad.scale((soul ? 0.15F : roof ? 0.25F : 0.55F + random.nextFloat() * 0.3F) / size);
                float shade = 0.035F + random.nextFloat() * 0.035F;
                if (soul) quad.setColor(0.25F, 0.75F, 0.82F);
                else quad.setColor(shade * 0.68F, shade * 0.78F, shade);
                p.setLifetime(55 + random.nextInt(25));
                owned.add(p); spawned++;
            } else if (p != null) p.remove();
        }
    }

    public static void clear() {
        for (Particle p : owned) p.remove();
        owned.clear(); floorCache.clear(); world = null; event = ""; generation = 0; tick = 0; primed = false;
    }
}
