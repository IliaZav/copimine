package me.copimine.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.HashSet;
import java.util.Locale;

/**
 * Single source of truth for the client-side End Rift entity textures.
 * Server resource packs cannot replace one entity texture by UUID, so these
 * assets deliberately live in CopiMineClient and are selected only after the
 * server has bound the matching entity UUID.
 */
public final class EndEventTextureCatalog {
    private static final Map<String, Identifier> VISUAL_TEXTURES = createVisualTextures();
    private static final Set<Identifier> LOGGED_LOOKUPS = new HashSet<>();

    private EndEventTextureCatalog() {
    }

    public static Identifier textureForVisual(String visualId) {
        if (visualId == null || visualId.isBlank()) {
            return null;
        }
        String normalized = visualId.trim().toUpperCase(Locale.ROOT);
        if ("END_RIFT_GUARDIAN".equals(normalized)) {
            normalized = "END_RIFT_GUARDIAN_V1";
        }
        // Contract 15.2/15.3: the Phase 5 echoes and decoys ARE the Guardian
        // model family - that is the whole point of the deception. They carry
        // their own visual ids so the server can tell them apart, but they must
        // resolve to the authored god-body rather than fall through to a
        // vanilla Enderman.
        if ("END_RIFT_ECHO_REPLICA_V1".equals(normalized)
                || "END_RIFT_ECHO_DECOY_V1".equals(normalized)) {
            normalized = "END_RIFT_GUARDIAN_V1";
        }
        return VISUAL_TEXTURES.get(normalized);
    }

    public static boolean isAvailable(Identifier texture) {
        if (texture == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getResourceManager() != null
                && client.getResourceManager().getResource(texture).isPresent();
    }

    public static void logLookup(String context, Identifier texture) {
        if (texture == null) {
            return;
        }
        // Texture IDs come from this finite catalog. Do not key the diagnostic
        // cache by a raw server-provided visual ID (case variants are unbounded).
        if (VISUAL_TEXTURES.containsValue(texture) && LOGGED_LOOKUPS.add(texture)) {
            CopiMineClientLogger.info("End Rift texture lookup context=" + context
                    + " texture=" + texture + " resourcePresent=" + isAvailable(texture));
        }
    }

    public static List<String> diagnosticLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Identifier> entry : VISUAL_TEXTURES.entrySet()) {
            lines.add(entry.getKey() + " -> " + entry.getValue()
                    + " / present=" + isAvailable(entry.getValue()));
        }
        return List.copyOf(lines);
    }

    public static Map<String, Identifier> visualTextures() {
        return Map.copyOf(VISUAL_TEXTURES);
    }

    private static Map<String, Identifier> createVisualTextures() {
        Map<String, Identifier> textures = new LinkedHashMap<>();
        textures.put("END_RIFT_ENDERMAN_V1", entityTexture("end_rift_user_enderman.png"));
        textures.put("END_RIFT_ELITE_V1", entityTexture("end_rift_elite.png"));
        textures.put("END_RIFT_ZOMBIE_V1", entityTexture("end_rift_zombie.png"));
        textures.put("END_RIFT_ELITE_ZOMBIE_V1", entityTexture("end_rift_elite_zombie.png"));
        // Waves 5 and 6 have their own authored skins. Mapping them onto the
        // Wave 1/2 user textures is what made those mobs look like another
        // wave's actor -- the reported "textures are lost" defect.
        textures.put("END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", entityTexture("end_rift_wave_guardian_enderman.png"));
        textures.put("END_RIFT_RITUAL_GUARD_ENDERMAN_V1", entityTexture("end_rift_ritual_guard_enderman.png"));
        textures.put("END_RIFT_GUARDIAN_V1", entityTexture("end_rift_user_boss.png"));
        textures.put("END_RIFT_SPIDER_V1", entityTexture("end_rift_user_spider.png"));
        textures.put("END_RIFT_ELITE_SPIDER_V1", entityTexture("end_rift_elite_spider.png"));
        textures.put("END_RIFT_WAVE_GUARDIAN_SPIDER_V1", entityTexture("end_rift_wave_guardian_spider.png"));
        textures.put("END_RIFT_RITUAL_GUARD_SPIDER_V1", entityTexture("end_rift_ritual_guard_spider.png"));
        textures.put("END_RIFT_SKELETON_V1", entityTexture("end_rift_user_skeleton.png"));
        textures.put("END_RIFT_ELITE_SKELETON_V1", entityTexture("end_rift_elite_skeleton.png"));
        textures.put("END_RIFT_WAVE_GUARDIAN_SKELETON_V1", entityTexture("end_rift_wave_guardian_skeleton.png"));
        textures.put("END_RIFT_RITUAL_GUARD_SKELETON_V1", entityTexture("end_rift_ritual_guard_skeleton.png"));
        textures.put("END_RIFT_RITUAL_CASTER_V1", entityTexture("end_rift_ritual_caster.png"));
        // Wave 6 Rift Ram adds. This sheet existed in the pre-26.3 client and
        // was dropped with the old wave implementation, which silently reverted
        // every Ram add to an unbranded vanilla bug. Restored verbatim.
        textures.put("END_RIFT_ENDERMITE_V1", entityTexture("end_rift_endermite.png"));
        textures.put(Wave7NamedTrialVisualPolicy.WARDEN_VISUAL,
                entityTexture("end_rift_wave7_warden.png"));
        textures.put(Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL,
                entityTexture("end_rift_wave7_archmage.png"));
        textures.put(Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL,
                entityTexture("end_rift_wave7_marksman.png"));
        // The Archmage's summoned servants had NO authored skin at all: they
        // rendered with the washed-out vanilla Vex sheet and read as background
        // noise next to the trial actors they belong to.
        textures.put(Wave7NamedTrialVisualPolicy.VEX_SERVANT_VISUAL,
                entityTexture("end_rift_wave7_vex_servant.png"));
        textures.put("END_RIFT_OBELISK_FULL_V1", entityTexture("end_event_rift_obelisk_full_hd.png"));
        textures.put("END_RIFT_OBELISK_DAMAGED_V1", entityTexture("end_event_rift_obelisk_damaged_hd.png"));
        textures.put("END_RIFT_OBELISK_CRITICAL_V1", entityTexture("end_event_rift_obelisk_critical_hd.png"));
        textures.put("END_RIFT_FIREBALL_V1", entityTexture("end_event_rift_fireball_hd.png"));
        textures.put("END_RIFT_TENTACLE_V1", entityTexture("end_rift_tentacle_hd.png"));
        textures.put("END_RIFT_GUARDIAN_SHIELD_V1", entityTexture("end_rift_guardian_shield_hd.png"));
        // Wave 6 Rift Ram: SIX armour stages (0 intact .. 5 exposed) of ONE beast.
        // The stage is chosen by the server's destroyed-pillar count, never by the
        // client, so every player sees the same stage at the same moment.
        //
        // Every stage deliberately resolves to the SAME vanilla ravager sheet.
        // The armour is not painted on: it is carried by server-owned 3D displays
        // that break away as pillars fall, and the exposed animal underneath must
        // stay the vanilla ravager all the way through. Emitting six distinct PNGs
        // here would imply the hides differ, and they must not.
        //
        // A natural (unbound) Ravager therefore keeps its vanilla sheet too.
        for (int stage = 0; stage <= 5; stage++) {
            textures.put(RiftRamVisualPolicy.visualForArmorStage(stage),
                    Identifier.fromNamespaceAndPath("minecraft", "textures/entity/illager/ravager.png"));
        }
        return Map.copyOf(textures);
    }

    public static Identifier entityTexture(String fileName) {
        return Identifier.fromNamespaceAndPath("copimineclient", "textures/entity/" + fileName);
    }
}
