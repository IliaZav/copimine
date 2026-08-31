import me.copimine.endevent.domain.ZoneVisualPolicy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ZoneVisualPolicyTest {
    private static final Set<String> VALID_PARTICLE_IDS = Set.of(
            "minecraft:poof",
            "minecraft:explosion",
            "minecraft:explosion_emitter",
            "minecraft:firework",
            "minecraft:bubble",
            "minecraft:splash",
            "minecraft:fishing",
            "minecraft:underwater",
            "minecraft:crit",
            "minecraft:enchanted_hit",
            "minecraft:smoke",
            "minecraft:large_smoke",
            "minecraft:effect",
            "minecraft:instant_effect",
            "minecraft:entity_effect",
            "minecraft:witch",
            "minecraft:dripping_water",
            "minecraft:dripping_lava",
            "minecraft:angry_villager",
            "minecraft:happy_villager",
            "minecraft:mycelium",
            "minecraft:note",
            "minecraft:portal",
            "minecraft:enchant",
            "minecraft:flame",
            "minecraft:lava",
            "minecraft:cloud",
            "minecraft:dust",
            "minecraft:item_snowball",
            "minecraft:item_slime",
            "minecraft:heart",
            "minecraft:item",
            "minecraft:block",
            "minecraft:rain",
            "minecraft:elder_guardian",
            "minecraft:dragon_breath",
            "minecraft:end_rod",
            "minecraft:damage_indicator",
            "minecraft:sweep_attack",
            "minecraft:falling_dust",
            "minecraft:totem_of_undying",
            "minecraft:spit",
            "minecraft:squid_ink",
            "minecraft:bubble_pop",
            "minecraft:current_down",
            "minecraft:bubble_column_up",
            "minecraft:nautilus",
            "minecraft:dolphin",
            "minecraft:sneeze",
            "minecraft:campfire_cosy_smoke",
            "minecraft:campfire_signal_smoke",
            "minecraft:composter",
            "minecraft:flash",
            "minecraft:falling_lava",
            "minecraft:landing_lava",
            "minecraft:falling_water",
            "minecraft:dripping_honey",
            "minecraft:falling_honey",
            "minecraft:landing_honey",
            "minecraft:falling_nectar",
            "minecraft:soul_fire_flame",
            "minecraft:ash",
            "minecraft:crimson_spore",
            "minecraft:warped_spore",
            "minecraft:soul",
            "minecraft:dripping_obsidian_tear",
            "minecraft:falling_obsidian_tear",
            "minecraft:landing_obsidian_tear",
            "minecraft:reverse_portal",
            "minecraft:white_ash",
            "minecraft:dust_color_transition",
            "minecraft:vibration",
            "minecraft:falling_spore_blossom",
            "minecraft:spore_blossom_air",
            "minecraft:small_flame",
            "minecraft:snowflake",
            "minecraft:dripping_dripstone_lava",
            "minecraft:falling_dripstone_lava",
            "minecraft:dripping_dripstone_water",
            "minecraft:falling_dripstone_water",
            "minecraft:glow_squid_ink",
            "minecraft:glow",
            "minecraft:wax_on",
            "minecraft:wax_off",
            "minecraft:electric_spark",
            "minecraft:scrape",
            "minecraft:sonic_boom",
            "minecraft:sculk_soul",
            "minecraft:sculk_charge",
            "minecraft:sculk_charge_pop",
            "minecraft:shriek",
            "minecraft:cherry_leaves",
            "minecraft:egg_crack",
            "minecraft:dust_plume",
            "minecraft:white_smoke",
            "minecraft:gust",
            "minecraft:small_gust",
            "minecraft:gust_emitter_large",
            "minecraft:gust_emitter_small",
            "minecraft:trial_spawner_detection",
            "minecraft:trial_spawner_detection_ominous",
            "minecraft:vault_connection",
            "minecraft:infested",
            "minecraft:item_cobweb",
            "minecraft:dust_pillar",
            "minecraft:ominous_spawning",
            "minecraft:raid_omen",
            "minecraft:trial_omen",
            "minecraft:block_marker"
    );

    public static void main(String[] args) {
        Map<ZoneVisualPolicy.ZoneState, ZoneVisualPolicy.Profile> profiles = ZoneVisualPolicy.profiles();
        require(profiles.size() == ZoneVisualPolicy.ZoneState.values().length,
                "every zone state must have a visual profile");

        Set<Integer> colors = new LinkedHashSet<>();

        for (ZoneVisualPolicy.ZoneState state : ZoneVisualPolicy.ZoneState.values()) {
            ZoneVisualPolicy.Profile profile = ZoneVisualPolicy.profile(state);
            require(profile != null, state + " profile must exist");
            require(!profile.id().isBlank(), state + " profile id must be stable");
            require(!profile.primaryParticle().isBlank(), state + " primary particle must exist");
            require(!profile.accentParticle().isBlank(), state + " accent particle must exist");
            require(VALID_PARTICLE_IDS.contains(profile.primaryParticle()),
                    state + " primary particle must be a valid Minecraft 1.21.1 particle id");
            require(VALID_PARTICLE_IDS.contains(profile.accentParticle()),
                    state + " accent particle must be a valid Minecraft 1.21.1 particle id");
            require(!profile.primaryParticle().equals(profile.accentParticle()),
                    state + " needs distinct particles");
            require(profile.colorRgb() >= 0 && profile.colorRgb() <= 0xFFFFFF,
                    state + " color must be valid RGB");
            require(profile.floorY() >= 0.04D && profile.floorY() <= 0.12D,
                    state + " floor height must stay anchored to the floor");
            require(profile.ringPoints() > 0 && profile.ringPoints() <= 96,
                    state + " ring points must stay bounded");
            require(profile.particleBudget() > 0 && profile.particleBudget() <= 96,
                    state + " particle budget must stay bounded");
            require(colors.add(profile.colorRgb()), state + " colors must all be distinct");
        }

        requireThrows(UnsupportedOperationException.class, () -> profiles.put(ZoneVisualPolicy.ZoneState.FREE, profiles.get(ZoneVisualPolicy.ZoneState.FREE)),
                "zone profile catalog must be immutable");
        System.out.println("ZoneVisualPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrows(Class<? extends Throwable> expected, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + " (unexpected " + thrown.getClass().getSimpleName() + ")", thrown);
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
