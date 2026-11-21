package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * The Wave 6 Rift Ram bar: a forged restraint with a real health track and the
 * seven accepted Ram/pillar collisions depicted as chained armour plates.
 *
 * <p>The Ravager's HP is still the server-authoritative health meter and is
 * drawn as a separate track.  The seven plates are a second, independent
 * readout: plate {@code n} is intact until the server publishes the durable
 * {@code END_RIFT_RAM_ARMOR<impacts>_V1} visual id for {@code impacts >= n}.
 * The client never infers a break from a start animation, prediction or a
 * duplicated packet; it only reacts to a higher accepted count.</p>
 *
 * <p>Break animation is driven locally by wall time, so it is FPS independent.
 * A late join or a reconnect reconstructs the current pose from the snapshot
 * and does not replay historic one-shot sounds.  Reduced motion draws the
 * final pose immediately.</p>
 */
public final class RiftRamBossBarHud {
    private static final Identifier RAIL = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/ram_seal_rail.png");
    private static final Identifier PLATES = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/ram_seal_plates.png");

    private static final int WIDTH = 300;
    private static final int HEIGHT = 56;
    private static final int PLATE_COUNT = 7;
    private static final int PLATE_SIZE = 32;
    private static final int PLATE_X = 16;
    private static final int PLATE_STEP = 40;
    private static final int PLATE_Y = 4;
    private static final int TRACK_LEFT = 18;
    private static final int TRACK_RIGHT = 282;
    private static final int TRACK_TOP = 40;
    private static final int TRACK_BOTTOM = 52;

    /** Impact flash, fracture growth, and the detached chain, in wall millis. */
    private static final long CRACK_MILLIS = 120L;
    private static final long SHATTER_MILLIS = 350L;
    private static final long CHAIN_DROP_MILLIS = 500L;
    private static final long PLATE_STAGGER_MILLIS = 90L;
    /** The last three plates are the monumental tower: slower, heavier, final. */
    private static final int FINAL_TOWER_FIRST_PLATE = 4;
    private static final long FINAL_CRACK_MILLIS = 170L;
    private static final long FINAL_SHATTER_MILLIS = 470L;
    private static final long FINAL_CHAIN_DROP_MILLIS = 700L;
    private static final long FINAL_STAGGER_MILLIS = 150L;
    private static final long FINAL_RELEASE_MILLIS = 900L;

    private static final int ATLAS_COLUMNS = 8;
    private static final int ATLAS_ROWS = 4;
    private static final String IMPACT_PREFIX = "END_RIFT_RAM_ARMOR";

    private static String trackedEntity = "";
    private static int shownImpacts = -1;
    private static final long[] plateBrokenAt = new long[PLATE_COUNT];
    private static final int[] plateSoundStage = new int[PLATE_COUNT];
    private static long finalReleaseAtMillis;
    /** Cached accepted-impact label, rebuilt only when the count changes. */
    private static int labelImpacts = Integer.MIN_VALUE;
    private static String labelText = "";
    private static net.minecraft.network.chat.Component labelComponent;

    private RiftRamBossBarHud() {
    }

    static int remainingPlates(String visual) {
        int impacts = impactCount(visual);
        return impacts < 0 ? -1 : PLATE_COUNT - impacts;
    }

    /** Accepted impact count encoded by the server visual id, or -1. */
    static int impactCount(String visual) {
        if (visual == null || !visual.startsWith(IMPACT_PREFIX)
                || visual.length() != IMPACT_PREFIX.length() + 4
                || !visual.endsWith("_V1")) return -1;
        char digit = visual.charAt(IMPACT_PREFIX.length());
        return digit >= '0' && digit <= '7' ? digit - '0' : -1;
    }

    /** Resets the per-viewer animation state; exposed for the regression tests. */
    static void resetForTest() {
        trackedEntity = "";
        shownImpacts = -1;
        java.util.Arrays.fill(plateBrokenAt, 0L);
        java.util.Arrays.fill(plateSoundStage, 0);
        finalReleaseAtMillis = 0L;
    }

    /** Clears per-viewer animation state on logout, death or event rotation. */
    static void clearState() {
        resetForTest();
    }

    public static boolean render(GuiGraphicsExtractor gui, int ignoredX, int y, BossEvent bar) {
        if (gui == null || bar == null || bar.getName() == null
                || !bar.getName().getString().startsWith("Таран Разлома")) return false;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.font == null) return false;
        var state = ClientBridgeProtocol.endEventState();

        String entity = "";
        int impacts = -1;
        for (String id : state.eventVisualEntityIds()) {
            int count = impactCount(state.visualForEntity(id));
            if (count >= 0) {
                entity = id;
                impacts = count;
                break;
            }
        }
        if (impacts < 0) {
            // Wave ended or the Ram is gone: drop all per-viewer state so a new
            // generation cannot inherit a stale broken plate or sound stage.
            resetForTest();
            return false;
        }

        long now = System.currentTimeMillis();
        boolean reducedMotion = reducedMotion();
        if (!entity.equals(trackedEntity)) {
            resetForTest();
            trackedEntity = entity;
            // A late join / reconnect sees the committed pose without replaying
            // historic sounds; only a live increase below schedules animation.
            shownImpacts = impacts;
            for (int plate = 0; plate < Math.min(impacts, PLATE_COUNT); plate++) {
                // Backdate past this plate's own shatter/drop window: the final
                // tower animates longer than the minor pillars, so a single
                // SHATTER_MILLIS would leave it half-fallen on a late join.
                plateBrokenAt[plate] = now - Math.max(shatterMillis(plate), dropMillis(plate));
                plateSoundStage[plate] = 3;
            }
        } else if (impacts > shownImpacts) {
            long cursor = now;
            for (int plate = Math.max(0, shownImpacts); plate < Math.min(impacts, PLATE_COUNT); plate++) {
                plateBrokenAt[plate] = cursor;
                plateSoundStage[plate] = 0;
                cursor += staggerMillis(plate);
            }
            if (impacts >= PLATE_COUNT) {
                // The seventh collision breaks the monumental final tower. Time
                // its release to land after the last plate has fully shattered.
                finalReleaseAtMillis = plateBrokenAt[PLATE_COUNT - 1]
                        + shatterMillis(PLATE_COUNT - 1);
            }
            shownImpacts = impacts;
        }
        // A lower count on the same entity is a reordered or stale snapshot.
        // Ignore it: rewinding would replay a break and its sounds.

        float progress = Float.isFinite(bar.getProgress())
                ? Math.max(0.0F, Math.min(1.0F, bar.getProgress())) : 0.0F;
        float scale = Math.min(1.0F, Math.max(1, gui.guiWidth() - 8) / (float) WIDTH);
        int x = Math.round((gui.guiWidth() - WIDTH * scale) / 2.0F);

        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        try {
            // Rail first: its health recess is transparent, so the real HP fill
            // underneath can never be hidden behind the artwork.
            gui.blit(RenderPipelines.GUI_TEXTURED, RAIL, 0, 0, 0F, 0F,
                    WIDTH, HEIGHT, WIDTH, HEIGHT, WIDTH, HEIGHT);

            int innerWidth = TRACK_RIGHT - TRACK_LEFT;
            gui.fill(TRACK_LEFT, TRACK_TOP, TRACK_RIGHT, TRACK_BOTTOM, 0xFF120F18);
            int filled = Math.round(innerWidth * progress);
            // Palette follows the accepted count that drives the plates, not a
            // reordered raw snapshot that may still carry a lower impact count.
            boolean allSealsBroken = Math.min(shownImpacts, PLATE_COUNT) >= PLATE_COUNT;
            if (filled > 0) {
                int body = allSealsBroken ? 0xFFBA3748 : 0xFF8753AA;
                gui.fill(TRACK_LEFT, TRACK_TOP, TRACK_LEFT + filled, TRACK_BOTTOM, body);
                gui.fill(TRACK_LEFT, TRACK_TOP, TRACK_LEFT + filled, TRACK_TOP + 2,
                        allSealsBroken ? 0xFFFF8D8F : 0xFFD098EC);
                for (int segment = 1; segment < PLATE_COUNT; segment++) {
                    int notch = TRACK_LEFT + Math.round(innerWidth * segment / (float) PLATE_COUNT);
                    gui.fill(notch, TRACK_TOP, notch + 1, TRACK_BOTTOM, 0x99080710);
                }
            }

            for (int plate = 0; plate < PLATE_COUNT; plate++) {
                drawPlate(gui, client, plate, now, reducedMotion);
            }
            if (!reducedMotion) {
                drawFinalRelease(gui, now);
            }

            gui.centeredText(client.font, "ТАРАН РАЗЛОМА", WIDTH / 2, -12, 0xFFF1E5F6);
            int visibleImpacts = Math.min(shownImpacts, PLATE_COUNT);
            if (visibleImpacts != labelImpacts) {
                labelImpacts = visibleImpacts;
                labelText = "ПЕЧАТИ " + visibleImpacts + "/" + PLATE_COUNT;
                labelComponent = net.minecraft.network.chat.Component.literal(labelText);
            }
            gui.text(client.font, labelComponent,
                    WIDTH - 4 - client.font.width(labelText), HEIGHT - 4, 0xFFC9B8DB);
        } finally {
            gui.pose().popMatrix();
        }
        return true;
    }

    private static void drawPlate(GuiGraphicsExtractor gui, Minecraft client, int plate,
                                 long now, boolean reducedMotion) {
        boolean broken = plate < shownImpacts;
        int variant;
        int offsetY = 0;
        long crack = crackMillis(plate);
        long shatter = shatterMillis(plate);
        long drop = dropMillis(plate);
        if (!broken) {
            variant = 0;
        } else if (reducedMotion) {
            variant = 3;
        } else {
            long elapsed = now - plateBrokenAt[plate];
            if (elapsed < 0L) {
                // Staggered but not started yet: the previous plate is still
                // visibly breaking, so hold the intact pose.
                variant = 0;
            } else if (elapsed < crack) {
                variant = 1;
            } else if (elapsed < shatter) {
                variant = 2;
            } else {
                variant = 3;
            }
            if (elapsed >= shatter && elapsed < drop) {
                float progress = (elapsed - shatter) / (float) (drop - shatter);
                offsetY = Math.round((plate >= FINAL_TOWER_FIRST_PLATE ? 9.0F : 7.0F) * progress);
            }
            playStage(client, plate, elapsed, broken);
        }
        int cellX = plate * PLATE_SIZE;
        int cellY = variant * PLATE_SIZE;
        gui.blit(RenderPipelines.GUI_TEXTURED, PLATES,
                PLATE_X + plate * PLATE_STEP, PLATE_Y + offsetY,
                (float) cellX, (float) cellY,
                PLATE_SIZE, PLATE_SIZE,
                PLATE_SIZE, PLATE_SIZE,
                PLATE_SIZE * ATLAS_COLUMNS, PLATE_SIZE * ATLAS_ROWS);
    }

    private static long crackMillis(int plate) {
        return plate >= FINAL_TOWER_FIRST_PLATE ? FINAL_CRACK_MILLIS : CRACK_MILLIS;
    }

    private static long shatterMillis(int plate) {
        return plate >= FINAL_TOWER_FIRST_PLATE ? FINAL_SHATTER_MILLIS : SHATTER_MILLIS;
    }

    private static long dropMillis(int plate) {
        return plate >= FINAL_TOWER_FIRST_PLATE ? FINAL_CHAIN_DROP_MILLIS : CHAIN_DROP_MILLIS;
    }

    private static long staggerMillis(int plate) {
        return plate >= FINAL_TOWER_FIRST_PLATE ? FINAL_STAGGER_MILLIS : PLATE_STAGGER_MILLIS;
    }

    /**
     * One restrained payoff when the seventh collision breaks the final tower:
     * a hot band across the plates and one expanding ring, confined to the bar.
     * Reduced motion skips it; the empty sockets already carry the state.
     */
    private static void drawFinalRelease(GuiGraphicsExtractor gui, long now) {
        if (finalReleaseAtMillis <= 0L) return;
        long age = now - finalReleaseAtMillis;
        if (age < 0L || age >= FINAL_RELEASE_MILLIS) return;
        float t = age / (float) FINAL_RELEASE_MILLIS;
        int alpha = Math.max(0, (int) ((1.0F - t) * 120.0F));
        if (alpha == 0) return;
        gui.fill(PLATE_X, PLATE_Y, PLATE_X + PLATE_COUNT * PLATE_STEP - 8, PLATE_Y + PLATE_SIZE,
                (alpha / 4 << 24) | 0x00FF6B7A);
        int lastX = PLATE_X + (PLATE_COUNT - 1) * PLATE_STEP;
        int grow = Math.round(26.0F * t);
        int left = lastX - grow;
        int top = PLATE_Y - grow;
        int right = lastX + PLATE_SIZE + grow;
        int bottom = PLATE_Y + PLATE_SIZE + grow;
        gui.fill(left, top, right, top + 2, (alpha << 24) | 0x00FFD9C2);
        gui.fill(left, bottom - 2, right, bottom, (alpha << 24) | 0x00FFD9C2);
        gui.fill(left, top, left + 2, bottom, (alpha << 24) | 0x00FFD9C2);
        gui.fill(right - 2, top, right, bottom, (alpha << 24) | 0x00FFD9C2);
    }

    /**
     * One restrained sound per accepted collision, local to this viewer.
     * Metal slam -> plate crack -> chain snap/fall.  The stage guard makes the
     * sequence play exactly once even though render() runs every frame.
     */
    private static void playStage(Minecraft client, int plate, long elapsed, boolean broken) {
        if (!broken || client.level == null || client.player == null) return;
        boolean finalTower = plate >= FINAL_TOWER_FIRST_PLATE;
        long crack = crackMillis(plate);
        long shatter = shatterMillis(plate);
        int stage = plateSoundStage[plate];
        if (stage == 0 && elapsed >= 0L) {
            play(client, SoundEvents.ANVIL_LAND, finalTower ? 0.72F : 0.55F,
                    finalTower ? 0.82F : 1.0F);
            plateSoundStage[plate] = 1;
        }
        if (plateSoundStage[plate] == 1 && elapsed >= crack) {
            play(client, SoundEvents.ANVIL_BREAK, finalTower ? 0.58F : 0.45F,
                    finalTower ? 1.15F : 1.45F);
            plateSoundStage[plate] = 2;
        }
        if (plateSoundStage[plate] == 2 && elapsed >= shatter) {
            // The final tower release is deeper and slower than an ordinary lock.
            play(client, SoundEvents.ANVIL_BREAK, finalTower ? 0.62F : 0.35F,
                    finalTower ? 0.5F : 0.72F);
            plateSoundStage[plate] = 3;
        }
    }

    private static void play(Minecraft client, net.minecraft.sounds.SoundEvent event,
                             float volume, float pitch) {
        if (client.player == null) return;
        client.level.playLocalSound(client.player.getX(), client.player.getY(),
                client.player.getZ(), event, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static boolean reducedMotion() {
        ClientConfig config = CopiMineClient.config();
        return config != null && config.reducedMotion();
    }
}