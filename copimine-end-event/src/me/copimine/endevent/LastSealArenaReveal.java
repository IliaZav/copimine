package me.copimine.endevent;

import me.copimine.endevent.domain.LastSealStructuralGeometryPolicy;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Changes only the cells pinned by a site plan. The journal also owns recovery without that plan. */
final class LastSealArenaReveal {
    static final int DURATION_TICKS = 320;
    // The approved site mask is authored, not generated, so the ceiling has to
    // cover the full square->circle transformation: the concealed corridor plus
    // the four authored inner-square wall partitions (a 67x60 face pair on each
    // side is ~16.3k cells). The V6/V7 audit requires "the exact mask" be honoured
    // in bounded batches, never that a real mask be rejected for exceeding a
    // guess. The per-tick BATCH below still bounds world writes (64 cells/tick,
    // the ceiling the reveal contract test pins), the 64-chunk span guard still
    // bounds chunk spread (the square alone spans 36 chunks), and MAX_BYTES
    // below bounds the journal/plan one atomic recovery can carry.
    static final int MAX_CELLS = 20_000;
    static final int MAX_BYTES = 3_000_000;
    private static final String VERSION = "last-seal-v1";
    private static final String VERSION_V2 = "last-seal-v2";
    private static final int BATCH = 64;
    private static final int FALL_TICKS = 22;

    record Square(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean horizontal(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        boolean contains(Location at) {
            return horizontal(at.getBlockX(), at.getBlockZ()) && at.getBlockY() >= minY && at.getBlockY() <= maxY;
        }
    }
    record Cell(int x, int y, int z) {
        Block block(World world) { return world.getBlockAt(x, y, z); }
        Location at(World world) { return new Location(world, x + .5, y, z + .5); }
        boolean loaded(World world) { return world.isChunkLoaded(x >> 4, z >> 4); }
    }
    private record Change(String kind, Cell cell, String original) {
        boolean wall() { return !kind.equals("fissure"); }
    }
    record Plan(UUID worldId, int x, int z, int radius, int floor, int rescueY, Cell landing,
                String digest, List<Change> changes, Square protectedSafeZone) {
        boolean horizontal(double px, double pz) {
            double dx = px - x - .5, dz = pz - z - .5;
            return dx * dx + dz * dz <= (double) radius * radius;
        }
    }
    private record CollapseCell(Change change, int tick) { }
    private record Fragment(BlockDisplay display, Transformation fall, long start, long end) { }

    private final Path planPath;
    private final Path journal;
    private final List<Fragment> fragments = new ArrayList<>();
    private List<CollapseCell> collapse = List.of();
    private Plan plan;
    private Square square;
    private long owner;
    private long startTick;
    private int next;
    private int lastBeat = -1;
    private boolean open;
    private boolean ascended;

    LastSealArenaReveal(Path directory) {
        planPath = directory.resolve("last-seal-arena.tsv");
        journal = directory.resolve("last-seal-arena-journal.tsv");
    }

    boolean configured() { return Files.exists(planPath); }
    boolean needsRecovery() { return Files.exists(journal); }

    Plan preview(World world, Location core, Square original, Square corridor, Square safeZone,
                 Predicate<Location> protectedCell)
            throws IOException {
        if (world == null || core == null || original == null) throw new IOException("arena layout is unavailable");
        byte[] bytes = readBounded(planPath);
        List<String> lines = java.util.Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\\R"));
        if (lines.isEmpty()) throw new IOException("missing Last Seal header");
        String[] header = lines.getFirst().split("\t", -1);
        try {
            if (header.length != 11 || !VERSION.equals(header[0])) throw new IOException("invalid Last Seal header");
            UUID id = UUID.fromString(header[1]);
            int x = number(header[2]), z = number(header[3]), radius = number(header[4]);
            int floor = number(header[5]), rescueY = number(header[6]);
            Cell landing = new Cell(number(header[7]), number(header[8]), number(header[9]));
            int expectedCount = number(header[10]);
            if (!world.getUID().equals(id) || core.getBlockX() != x || core.getBlockZ() != z
                    || radius < 8 || radius > 96 || floor < world.getMinHeight() + 5 || floor >= world.getMaxHeight() - 3
                    || rescueY < world.getMinHeight() + 4 || rescueY >= floor || expectedCount < 1 || expectedCount > MAX_CELLS
                    || !original.contains(landing.at(world)) || !safeLanding(landing, world)) {
                throw new IOException("world, Core, radius or rescue landing is invalid");
            }
            if ((corridor == null) != (safeZone == null))
                throw new IOException("concealed corridor and protected safe zone must both be configured");
            byte[] approval = corridor == null ? bytes
                    : (new String(bytes, StandardCharsets.UTF_8) + "\n" + corridor + "\n" + safeZone)
                            .getBytes(StandardCharsets.UTF_8);
            Plan candidate = new Plan(id, x, z, radius, floor, rescueY, landing, digest(approval), List.of(), safeZone);
            for (int sx : new int[]{original.minX(), original.maxX()}) {
                for (int sz : new int[]{original.minZ(), original.maxZ()}) {
                    if (!candidate.horizontal(sx + .5, sz + .5))
                        throw new IOException("the approved outer hall must enclose the four square corners");
                }
            }
            List<Change> changes = parseChanges(lines.subList(1, lines.size()));
            if (safeZone != null && safeZone.contains(landing.at(world)))
                throw new IOException("rescue landing cannot be in the protected safe zone");
            if (changes.size() != expectedCount || changes.stream().noneMatch(Change::wall))
                throw new IOException("wrong cell count or no wall segment");
            LastSealStructuralGeometryPolicy.Box squareBox = new LastSealStructuralGeometryPolicy.Box(
                    original.minX(), original.minY(), original.minZ(),
                    original.maxX(), original.maxY(), original.maxZ());
            LastSealStructuralGeometryPolicy.Box corridorBox = corridor == null ? null
                    : new LastSealStructuralGeometryPolicy.Box(corridor.minX(), corridor.minY(),
                            corridor.minZ(), corridor.maxX(), corridor.maxY(), corridor.maxZ());
            for (Change change : changes) {
                Cell cell = change.cell();
                Location at = cell.at(world);
                boolean geometry = LastSealStructuralGeometryPolicy.cellKindApproved(
                        change.kind(), cell.x(), cell.y(), cell.z(), squareBox, floor, corridorBox);
                if (!geometry || !world.getWorldBorder().isInside(at)
                        || !candidate.horizontal(at.getX(), at.getZ()) || protectedCell.test(at)
                        || safeZone != null && safeZone.contains(at)
                        || !ensureLoaded(world, cell)) throw new IOException("unapproved, protected, or unloaded cell " + cell);
                Block block = cell.block(world);
                if (!block.getType().isSolid() || block.getState(false) instanceof TileState
                        || !block.getBlockData().getAsString().equals(change.original())) {
                    throw new IOException("site no longer matches original block at " + cell);
                }
            }
            return new Plan(id, x, z, radius, floor, rescueY, landing, candidate.digest(), changes, safeZone);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid Last Seal plan", invalid);
        }
    }

    void begin(Plan checked, Square original, String approved, long generation, long tick) throws IOException {
        if (plan != null || needsRecovery() || !checked.digest().equalsIgnoreCase(approved))
            throw new IOException("missing approval for the exact manifest or unfinished recovery");
        writeJournal(checked, original);
        plan = checked;
        collapse = checked.changes().stream()
                .map(change -> new CollapseCell(change, collapseTick(checked, change)))
                .sorted(Comparator.comparingInt(CollapseCell::tick))
                .toList();
        open = false;
        square = original;
        owner = generation;
        startTick = tick;
        next = 0;
        lastBeat = -1;
        ascended = false;
    }

    boolean active(long generation) { return plan != null && owner == generation; }
    boolean playing(long generation, long tick) { return active(generation) && tick - startTick < DURATION_TICKS; }
    Plan opened(long generation) { return active(generation) && open ? plan : null; }
    boolean ascensionStarted(long generation) { return active(generation) && ascended; }

    double ascentProgress(long generation, long tick) {
        return ascensionStarted(generation) ? Math.clamp((tick - startTick - 140) / 76.0D, 0, 1) : 0;
    }

    boolean takeAscension(long generation, long tick) {
        if (!active(generation) || !open || ascended || tick - startTick < 140) return false;
        ascended = true;
        return true;
    }

    boolean protects(Location at, long generation) {
        return active(generation) && plan.protectedSafeZone() != null && at.getWorld() != null
                && at.getWorld().getUID().equals(plan.worldId()) && plan.protectedSafeZone().contains(at);
    }

    boolean contains(Location at, long generation) {
        return active(generation) && open && at.getWorld() != null && at.getWorld().getUID().equals(plan.worldId())
                && (plan.protectedSafeZone() == null || !plan.protectedSafeZone().contains(at))
                && plan.horizontal(at.getX(), at.getZ()) && at.getY() >= plan.floor()
                && at.getY() < square.maxY() + 1.0;
    }

    void tick(World world, Location core, long generation, long now) throws IOException {
        if (!active(generation)) return;
        if (!world.getUID().equals(plan.worldId())) throw new IOException("arena world changed");
        fragments.removeIf(fragment -> {
            if (fragment.display().isValid() && now >= fragment.start() && fragment.display().getInterpolationDelay() != 0) {
                fragment.display().setInterpolationDelay(0);
                fragment.display().setTransformation(fragment.fall());
            }
            if (fragment.end() > now) return false;
            fragment.display().remove();
            return true;
        });
        long age = now - startTick;
        beat(world, core, age);
        if (next < collapse.size()) {
            CollapseCell lead = collapse.get(next);
            if (lead.tick() - age >= 1 && lead.tick() - age <= 12 && age % 10 < 5)
                crumble(world, lead.change().cell().at(world).add(0, .55, 0), lead.change().wall(), false);
            for (int i = 0; i < BATCH && next < collapse.size(); i++) {
                CollapseCell scheduled = collapse.get(next);
                if (age < scheduled.tick()) break;
                Change change = scheduled.change();
                Cell cell = change.cell();
                if (!ensureLoaded(world, cell)) throw new IOException("unloaded approved cell " + cell);
                Block block = cell.block(world);
                if (!block.getBlockData().getAsString().equals(change.original()) || block.getState(false) instanceof TileState)
                    throw new IOException("site was modified during reveal at " + cell);
                if (i == 0 || i == BATCH / 2)
                    crumble(world, cell.at(world).add(0, .55, 0), change.wall(), true);
                if (fragments.size() < 40 && ((next & 3) == 0 || !change.wall() && (next & 1) == 0))
                    animate(world, block, change.wall(), now);
                block.setType(Material.AIR, false);
                next++;
            }
        }
        open = next == collapse.size() && age >= 100;
        if (open && age % 12 == 0) renderOpenFissures(world, age);
        if (age >= 100) {
            for (Player player : world.getPlayers()) {
                Location at = player.getLocation();
                if (protects(at, generation)) continue;
                if (at.getY() < plan.rescueY() && at.getY() >= plan.rescueY() - 4
                        && player.getVelocity().getY() < -.04D && player.getFallDistance() >= 1.0F
                        && plan.horizontal(at.getX(), at.getZ())
                        && plan.changes().stream().anyMatch(change -> !change.wall()
                        && Math.abs(change.cell().x() - at.getBlockX()) <= 1
                        && Math.abs(change.cell().z() - at.getBlockZ()) <= 1)) {
                    evacuate(player, plan.landing(), world);
                }
            }
        }
    }

    private void renderOpenFissures(World world, long age) {
        int count = plan.changes().size();
        int cursor = (int) ((age / 12L * 31L) % count);
        int emitted = 0;
        for (int i = 0; i < count && emitted < 5; i++) {
            Change change = plan.changes().get((cursor + i) % count);
            if (change.wall() || !change.cell().loaded(world)
                    || !change.cell().block(world).getType().isAir()) continue;
            Location rim = change.cell().at(world).add(0, .84D, 0);
            world.spawnParticle(Particle.DUST, rim, 4, .21D, .08D, .21D, 0,
                    new Particle.DustOptions(Color.fromRGB(0x9749c2), 1.25F));
            world.spawnParticle(Particle.REVERSE_PORTAL, rim.clone().subtract(0, .8D, 0),
                    2, .16D, .52D, .16D, .025D);
            if (emitted++ == 0 && age % 60 == 0)
                world.playSound(rim, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, .42F, .51F);
        }
    }

    private static int collapseTick(Plan plan, Change change) {
        Cell cell = change.cell();
        if (!change.wall()) {
            double distance = Math.hypot(cell.x() - plan.x(), cell.z() - plan.z());
            return 100 + (int) (25 * Math.min(1, distance / plan.radius()));
        }
        double angle = Math.atan2(cell.z() - plan.z(), cell.x() - plan.x());
        double sweep = (angle + Math.PI) / (2 * Math.PI);
        int verticalDelay = Math.clamp(plan.floor() + 12 - cell.y(), 0, 12);
        return 38 + (int) (42 * sweep) + verticalDelay;
    }

    private static void crumble(World world, Location center, boolean wall, boolean impact) {
        world.playSound(center, collapseSound(wall, impact), SoundCategory.BLOCKS,
                impact ? 1.55f : .68f, wall ? .62f : .86f);
        world.spawnParticle(Particle.LARGE_SMOKE, center, impact ? 14 : 4,
                impact ? .9 : .4, .3, impact ? .9 : .4, .035);
        world.spawnParticle(Particle.DUST, center, impact ? 16 : 7, .7, wall ? .6 : .24, .7, 0,
                new Particle.DustOptions(Color.fromRGB(wall ? 0x946680 : 0xca68ff), impact ? 1.35f : .85f));
        if (impact) world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                wall ? .8f : .55f, wall ? .65f : .82f);
    }

    private static Sound collapseSound(boolean wall, boolean impact) {
        if (wall) return impact ? Sound.BLOCK_DEEPSLATE_BREAK : Sound.BLOCK_DEEPSLATE_HIT;
        return impact ? Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE : Sound.BLOCK_AMETHYST_BLOCK_HIT;
    }

    private void beat(World world, Location core, long age) {
        int beat = age >= 240 ? 4 : age >= 160 ? 3 : age >= 100 ? 2 : age >= 40 ? 1 : 0;
        if (beat <= lastBeat) return;
        lastBeat = beat;
        Sound cue = switch (beat) {
            case 0 -> Sound.ENTITY_WARDEN_HEARTBEAT;
            case 1 -> Sound.BLOCK_DEEPSLATE_BREAK;
            case 2 -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case 3 -> Sound.ENTITY_WARDEN_ROAR;
            default -> Sound.BLOCK_END_PORTAL_SPAWN;
        };
        world.playSound(core, cue, SoundCategory.HOSTILE, beat == 1 ? 2.5f : 1.4f, .66f + .07f * beat);
    }

    private void animate(World world, Block block, boolean wall, long tick) {
        Location at = block.getLocation();
        BlockDisplay shard = world.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(block.getBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(12, 12));
            display.setViewRange(1.15f);
            display.setInterpolationDuration(FALL_TICKS);
            display.setInterpolationDelay(-1);
        });
        double dx = at.getX() - plan.x(), dz = at.getZ() - plan.z();
        double length = Math.max(1, Math.hypot(dx, dz));
        float nx = (float) (dx / length), nz = (float) (dz / length);
        var fall = new Transformation(new Vector3f(nx * 2.2f, wall ? -2.6f : -4f, nz * 2.2f),
                new Quaternionf().rotateAxis(1.18f, nz, .15f, -nx), new Vector3f(.72f), new Quaternionf());
        fragments.add(new Fragment(shard, fall, tick + 5, tick + FALL_TICKS + 6));
        world.spawnParticle(Particle.BLOCK, at.clone().add(.5, .5, .5), 10,
                .34, .30, .34, .035, block.getBlockData());
        if (!wall) world.spawnParticle(Particle.DUST, at.clone().add(.5, .2, .5), 12,
                .42, .40, .42, 0, new Particle.DustOptions(Color.fromRGB(157, 70, 231), 1.25f));
    }

    void restore() throws IOException {
        for (Fragment fragment : fragments) fragment.display().remove();
        fragments.clear();
        collapse = List.of();
        if (!needsRecovery()) { plan = null; open = false; return; }
        String contents = new String(readBounded(journal), StandardCharsets.UTF_8);
        List<String> lines = java.util.Arrays.asList(contents.split("\\R"));
        String[] header = lines.getFirst().split("\t", -1);
        // V2 persists the arena floor and exact vertical extent; a V1 journal
        // stored only horizontal bounds. Both are still recoverable, but a
        // legacy journal must not run the broad horizontal evacuation - see the
        // withinArenaVolume guard below (V7 A-02).
        boolean v2 = VERSION_V2.equals(header[0]);
        int baseFields = v2 ? 17 : 14;
        int safeFields = v2 ? 23 : 20;
        if ((header.length != baseFields && header.length != safeFields)
                || !(v2 || VERSION.equals(header[0])))
            throw new IOException("invalid arena recovery journal");
        try {
            World world = Bukkit.getWorld(UUID.fromString(header[1]));
            if (world == null) throw new IOException("arena journal world unavailable");
            Cell landing = new Cell(number(header[2]), number(header[3]), number(header[4]));
            int originalMinY = v2 ? number(header[12]) : world.getMinHeight();
            int originalMaxY = v2 ? number(header[13]) : world.getMaxHeight() - 1;
            Square original = new Square(number(header[7]), originalMinY, number(header[8]),
                    number(header[9]), originalMaxY, number(header[10]));
            int centerX = v2 ? number(header[14]) : number(header[11]);
            int centerZ = v2 ? number(header[15]) : number(header[12]);
            int radius = v2 ? number(header[16]) : number(header[13]);
            int safeBase = v2 ? 17 : 14;
            Square safeZone = header.length > safeBase
                    ? new Square(number(header[safeBase]), number(header[safeBase + 1]), number(header[safeBase + 2]),
                            number(header[safeBase + 3]), number(header[safeBase + 4]), number(header[safeBase + 5]))
                    : null;
            if (radius < 8 || radius > 96 || original.minX() > original.maxX() || original.minZ() > original.maxZ())
                throw new IOException("invalid recovery footprint");
            List<Change> changes = parseChanges(lines.subList(1, lines.size()));
            if (changes.size() != number(header[5])
                    || !digest((String.join("\n", lines.subList(1, lines.size())) + "\n").getBytes(StandardCharsets.UTF_8))
                        .equals(header[6])) throw new IOException("arena recovery journal checksum mismatch");
            for (Change change : changes) {
                Cell cell = change.cell();
                world.getChunkAt(cell.x() >> 4, cell.z() >> 4);
                Block block = cell.block(world);
                if (!block.getBlockData().getAsString().equals(change.original()) && !block.getType().isAir())
                    throw new IOException("non-owned block obstructs restoration at " + cell);
                Bukkit.createBlockData(change.original());
            }
            boolean wasRevealed = changes.stream().anyMatch(change -> change.cell().block(world).getType().isAir());
            // Build the changed-cell boxes once; recovery runs at a boundary, not
            // per tick, but the displacement decision itself is a pure policy and
            // is unit tested (V7 A-02).
            List<LastSealStructuralGeometryPolicy.Box> changedBoxes = changes.stream()
                    .map(change -> {
                        Cell cell = change.cell();
                        return new LastSealStructuralGeometryPolicy.Box(cell.x(), cell.y(), cell.z(),
                                cell.x() + 1, cell.y() + 1, cell.z() + 1);
                    })
                    .toList();
            LastSealStructuralGeometryPolicy.Box arenaBox = new LastSealStructuralGeometryPolicy.Box(
                    original.minX(), originalMinY, original.minZ(),
                    original.maxX(), originalMaxY, original.maxZ());
            for (Player player : world.getPlayers()) {
                Location at = player.getLocation();
                if (safeZone != null && safeZone.contains(at)) continue;
                BoundingBox body = player.getBoundingBox();
                // A body may only be displaced by the broad sweep when the
                // journal knows the true vertical volume (V2) and the body is
                // actually inside the arena's floor/ceiling band. A player on
                // the surface above an underground complex, or on a stacked
                // neighbouring floor, shares X/Z but is not in the arena, so
                // the horizontal radius test alone must never teleport them.
                boolean evacuate = LastSealStructuralGeometryPolicy.recoveryEvacuation(
                        body.getMinX(), body.getMinY(), body.getMinZ(),
                        body.getMaxX(), body.getMaxY(), body.getMaxZ(),
                        at.getX(), at.getY(), at.getZ(),
                        changedBoxes, arenaBox, centerX, centerZ, radius, v2, wasRevealed);
                if (evacuate) evacuate(player, landing, world);
            }
            for (Change change : changes.reversed()) {
                var block = change.cell().block(world);
                if (block.getType().isAir()) block.setBlockData(Bukkit.createBlockData(change.original()), false);
            }
            // Flush restored terrain before releasing the only recovery receipt.
            world.save();
            Files.delete(journal);
            plan = null;
            open = false;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid arena recovery data", invalid);
        }
    }

    private void writeJournal(Plan checked, Square original) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Change change : checked.changes()) {
            Cell c = change.cell();
            body.append(change.kind()).append('\t').append(c.x()).append('\t')
                    .append(c.y()).append('\t').append(c.z()).append('\t').append(change.original()).append('\n');
        }
        Cell safe = checked.landing();
        // V2: floor, origMinY, origMaxY inserted after origXZ (indices 11-13)
        String header = String.join("\t", VERSION_V2, checked.worldId().toString(),
                "" + safe.x(), "" + safe.y(), "" + safe.z(),
                "" + checked.changes().size(), digest(body.toString().getBytes(StandardCharsets.UTF_8)),
                "" + original.minX(), "" + original.minZ(),
                "" + original.maxX(), "" + original.maxZ(),
                "" + checked.floor(), "" + original.minY(), "" + original.maxY(),
                "" + checked.x(), "" + checked.z(), "" + checked.radius());
        Square safeZone = checked.protectedSafeZone();
        if (safeZone != null) header += "\t" + String.join("\t", "" + safeZone.minX(), "" + safeZone.minY(),
                "" + safeZone.minZ(), "" + safeZone.maxX(), "" + safeZone.maxY(), "" + safeZone.maxZ());
        Files.createDirectories(journal.getParent());
        Path temporary = Files.createTempFile(journal.getParent(), "last-seal-arena-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(header + "\n" + body);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<Change> parseChanges(List<String> lines) throws IOException {
        var parsed = new ArrayList<Change>();
        var seen = new HashSet<Cell>();
        boolean fissure = false;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 5 || !(fields[0].equals("wall") || fields[0].equals("fissure") || fields[0].equals("corridor"))
                    || fields[4].isBlank() || parsed.size() >= MAX_CELLS)
                throw new IOException("invalid arena cell");
            boolean wall = !fields[0].equals("fissure");
            if (wall && fissure) throw new IOException("walls must come before fissures");
            fissure |= !wall;
            Cell cell = new Cell(number(fields[1]), number(fields[2]), number(fields[3]));
            if (!seen.add(cell)) throw new IOException("duplicate arena cell " + cell);
            parsed.add(new Change(fields[0], cell, fields[4]));
        }
        long chunks = parsed.stream().map(change -> {
            Cell cell = change.cell();
            return ((long) (cell.x() >> 4) << 32) | ((cell.z() >> 4) & 0xffffffffL);
        }).distinct().count();
        if (chunks > 64) throw new IOException("arena cells span too many chunks");
        return List.copyOf(parsed);
    }

    /**
     * Load the cell's chunk before any block read. A reveal can legitimately
     * start while a player stands at one edge of the 69x61 square, so the far
     * chunks of the authored mask are not guaranteed loaded yet. The mask is
     * already bounded to {@link #MAX_CELLS} cells and 64 chunk columns by
     * {@link #parseChanges}, so this forces at most that bounded set and never a
     * whole-dimension load. Returning the real post-load state keeps a genuinely
     * unavailable chunk a hard failure instead of silently skipping a cell.
     */
    private static boolean ensureLoaded(World world, Cell cell) {
        world.getChunkAt(cell.x() >> 4, cell.z() >> 4);
        return world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4);
    }

    private static boolean safeLanding(Cell p, World world) {
        if (!ensureLoaded(world, p) || p.y() <= world.getMinHeight() || p.y() >= world.getMaxHeight() - 1
                || !world.getWorldBorder().isInside(p.at(world))) return false;
        Block feet = p.block(world);
        Block below = feet.getRelative(0, -1, 0);
        return feet.getType().isAir() && feet.getRelative(0, 1, 0).getType().isAir()
                && below.getType().isSolid() && !below.isLiquid()
                && below.getType() != Material.MAGMA_BLOCK && below.getType() != Material.CAMPFIRE;
    }

    private static void evacuate(Player player, Cell p, World world) throws IOException {
        if (!safeLanding(p, world) || !player.teleport(p.at(world)))
            throw new IOException("approved arena rescue landing is obstructed");
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
    }

    private static int number(String text) { return Integer.parseInt(text); }

    private static byte[] readBounded(Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("invalid arena file size");
            return bytes;
        }
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
