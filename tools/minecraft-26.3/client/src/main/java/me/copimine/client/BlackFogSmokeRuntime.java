package me.copimine.client;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/** Real, depth-tested world smoke. Never reloads resources or clears other particles/models. */
public final class BlackFogSmokeRuntime {
    private static final ArrayList<Particle> owned = new ArrayList<>();
    private static Object world;
    private static String event = "";
    private static long generation;
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
        if (BlackFogVolumeRuntime.chamber() != null) {
            for (Particle p : owned) p.remove();
            owned.clear();
            primed = false;
            return;
        }
        owned.removeIf(p -> {
            var b = p.getBoundingBox();
            double x = (b.minX + b.maxX) * 0.5, z = (b.minZ + b.maxZ) * 0.5;
            double radius = p instanceof SingleQuadParticle quad ? quad.getQuadSize(0) * 1.42 : 1.25;
            boolean discard = !p.isAlive() || x < arena.minX() + BlackFogSmokePolicy.RADIUS
                    || x > arena.maxX() + 1.0 - BlackFogSmokePolicy.RADIUS
                    || z < arena.minZ() + BlackFogSmokePolicy.RADIUS
                    || z > arena.maxZ() + 1.0 - BlackFogSmokePolicy.RADIUS
                    || b.minY - radius < base || b.minY + radius > base + BlackFogVolumePolicy.FOG_HEIGHT;
            if (discard) p.remove();
            return discard;
        });
        // Stop replenishing during CLEAR, but keep the owned smoke during the
        // short volume fade. Clearing it here made world smoke vanish instantly.
        if (!fog.active()) return;
        int budget = BlackFogSmokePolicy.budget(owned.size(), !primed);
        primed = true;
        for (int attempt = 0, spawned = 0; attempt < budget * 4 && spawned < budget; attempt++) {
            int style = random.nextInt(20);
            boolean ash = style < 7, soul = style >= 7 && style <= 9, spark = style == 10;
            boolean cluster = style >= 17;
            // Most sprites in the old 44x44 square were already hidden by the
            // optical volume. Spend the SAME budget where a player can see depth.
            double minRadius = ash ? .5D : soul || spark ? 1.8D : 3D;
            double maxRadius = ash ? 12D : soul || spark ? 8D : 7D;
            double radius = Math.sqrt(minRadius*minRadius
                    + random.nextDouble()*(maxRadius*maxRadius-minRadius*minRadius));
            double angle = random.nextDouble()*Math.PI*2D;
            double x = client.player.getX()+Math.cos(angle)*radius;
            double z = client.player.getZ()+Math.sin(angle)*radius;
            if (x < arena.minX()+1.3D || x > arena.maxX()-.3D
                    || z < arena.minZ()+1.3D || z > arena.maxZ()-.3D) continue;
            double y = base + (soul ? 0.3 + random.nextDouble() * 0.93 : 0.8 + random.nextDouble() * 2.4);
            // Large smoke sprites stay outside the readable near patch; small wisps may drift through it.
            if (!ash && !soul && !spark
                    && Math.hypot(x - client.player.getX(), z - client.player.getZ()) < 2.5) continue;
            BlockPos position = BlockPos.containing(x, y, z);
            if (!client.level.hasChunkAt(position) || !client.level.getBlockState(position).isAir()) continue;
            // Local vanilla sprite providers are bundled; no Iris or extra mod is required.
            var type = ash ? ParticleTypes.ASH : soul ? ParticleTypes.SOUL
                    : spark ? ParticleTypes.REVERSE_PORTAL : cluster ? ParticleTypes.CAMPFIRE_COSY_SMOKE
                    : ParticleTypes.SMOKE;
            double speed = ash ? 0.018 : spark ? 0.012 : 0.005;
            Particle p = client.particleEngine.createParticle(type, x, y, z,
                    (random.nextDouble() - 0.35) * speed,
                    soul ? 0.009 : ash ? -0.003 : 0.001 + random.nextDouble() * 0.003,
                    (random.nextDouble() - 0.5) * speed);
            if (p instanceof SingleQuadParticle quad) {
                float size = quad.getQuadSize(0);
                if (size > 0) quad.scale((ash ? 0.025F + random.nextFloat() * 0.04F : spark ? 0.06F : soul ? 0.13F
                        : cluster ? 0.45F + random.nextFloat() * 0.18F : 0.18F + random.nextFloat() * 0.18F) / size);
                float shade = 0.035F + random.nextFloat() * 0.035F;
                if (soul) quad.setColor(0.25F, 0.75F, 0.82F);
                else if (spark) quad.setColor(0.48F, 0.16F, 0.68F);
                else quad.setColor(shade, shade * 0.65F, shade * 0.85F);
                p.setLifetime((ash ? 35 : cluster ? 70 : 45) + random.nextInt(30));
                owned.add(p); spawned++;
            } else if (p != null) p.remove();
        }
    }

    public static void clear() {
        for (Particle p : owned) p.remove();
        owned.clear(); world = null; event = ""; generation = 0; primed = false;
    }
}
