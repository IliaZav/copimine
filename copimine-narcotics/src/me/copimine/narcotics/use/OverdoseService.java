package me.copimine.narcotics.use;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.model.ConfiguredEffect;
import me.copimine.narcotics.model.NarcoticDefinition;
import me.copimine.visualruntime.VisualRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OverdoseService {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private final VisualRuntimeService visualRuntime;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PotionEffectType>> trackedEffects = new ConcurrentHashMap<>();
    private final Set<UUID> movementGuard = ConcurrentHashMap.newKeySet();

    public OverdoseService(CopiMineNarcotics plugin, NarcoticsConfigService configService, NarcoticsDatabase database, VisualRuntimeService visualRuntime) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.visualRuntime = visualRuntime;
    }

    public void reload(NarcoticsConfigService configService) {
        this.configService = configService;
    }

    public void preloadState(UUID playerUuid) {
        database.loadPlayerState(playerUuid).thenAccept(state -> states.put(playerUuid, state))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Failed to preload narcotics state: " + error.getMessage());
                    return null;
                });
    }

    public void consume(Player player, NarcoticDefinition definition) {
        long now = System.currentTimeMillis() / 1000L;
        PlayerState state = states.getOrDefault(player.getUniqueId(), PlayerState.empty(player.getUniqueId()));
        if (now - state.lastConsumedAt() > configService.usageWindowSeconds()) {
            state = state.withCurrentScale(0);
        }
        int newScale = state.currentScale() + Math.max(0, configService.overdoseWeightFor(definition));
        boolean overdose = "zhuzevo".equals(definition.id()) && configService.zhuzevoForcesOverdose();
        overdose = overdose || newScale >= configService.overdoseThreshold();

        if (configService.clearNormalEffectsBeforeNewUse()) {
            clearActiveEffects(player, false);
        }

        PlayerState updated = new PlayerState(
                player.getUniqueId(),
                newScale,
                now,
                state.overdoseUntil(),
                state.invertedMovementUntil(),
                definition.id()
        );

        if (overdose) {
            updated = applyOverdose(player, definition, updated, now);
        } else {
            applyConfiguredEffects(player, definition.normalEffects());
            visualRuntime.apply(player, definition.visualEffectId(), effectiveDuration(Math.max(15, definition.maxEffectDurationSeconds(false))), false);
        }
        states.put(player.getUniqueId(), updated);
        database.savePlayerState(updated);
    }

    public boolean shouldBlockMilk(Player player) {
        if (!configService.milkBlocksDuringOverdose()) {
            return false;
        }
        PlayerState state = states.get(player.getUniqueId());
        long now = System.currentTimeMillis() / 1000L;
        return state != null && state.overdoseUntil() > now;
    }

    public void handleMovementInversion(PlayerMoveEvent event) {
        PlayerState state = states.get(event.getPlayer().getUniqueId());
        if (state == null || state.invertedMovementUntil() <= (System.currentTimeMillis() / 1000L)) {
            return;
        }
        if (movementGuard.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        movementGuard.add(event.getPlayer().getUniqueId());
        try {
            double dx = event.getTo().getX() - event.getFrom().getX();
            double dz = event.getTo().getZ() - event.getFrom().getZ();
            event.setTo(event.getFrom().clone().add(-dx, 0.0D, -dz).setDirection(event.getFrom().getDirection()));
        } finally {
            Bukkit.getScheduler().runTask(plugin, () -> movementGuard.remove(event.getPlayer().getUniqueId()));
        }
    }

    public void clearActiveEffects(Player player, boolean preserveState) {
        Set<PotionEffectType> effects = trackedEffects.remove(player.getUniqueId());
        if (effects != null) {
            for (PotionEffectType effect : effects) {
                player.removePotionEffect(effect);
            }
        }
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        visualRuntime.clear(player);
        if (!preserveState) {
            PlayerState state = states.get(player.getUniqueId());
            if (state != null) {
                PlayerState cleared = new PlayerState(state.playerUuid(), state.currentScale(), state.lastConsumedAt(), 0L, 0L, state.lastItemId());
                states.put(player.getUniqueId(), cleared);
                database.savePlayerState(cleared);
            }
        }
    }

    public void clearAllCachedState() {
        states.clear();
        trackedEffects.clear();
        movementGuard.clear();
    }

    public void forceClearOverdose(Player player) {
        clearActiveEffects(player, false);
        PlayerState cleared = PlayerState.empty(player.getUniqueId());
        states.put(player.getUniqueId(), cleared);
        database.savePlayerState(cleared);
    }

    public PlayerState state(UUID playerUuid) {
        return states.getOrDefault(playerUuid, PlayerState.empty(playerUuid));
    }

    private PlayerState applyOverdose(Player player, NarcoticDefinition definition, PlayerState state, long now) {
        List<ConfiguredEffect> effectsToApply = new ArrayList<>(definition.overdoseEffects());
        String visualId = definition.visualEffectId();
        if ("zhuzevo".equals(definition.id())) {
            effectsToApply.clear();
            List<ConfiguredEffect> pool = new ArrayList<>();
            List<String> visuals = new ArrayList<>();
            for (NarcoticDefinition source : configService.items().values()) {
                if ("zhuzevo".equals(source.id())) {
                    continue;
                }
                pool.addAll(source.overdoseEffects());
                visuals.add(source.visualEffectId());
            }
            Collections.shuffle(pool);
            Collections.shuffle(visuals);
            for (int index = 0; index < Math.min(4, pool.size()); index++) {
                effectsToApply.add(pool.get(index));
            }
            if (!visuals.isEmpty()) {
                visualId = visuals.get(0);
            }
        }
        applyConfiguredEffects(player, effectsToApply);
        if ("sbp".equals(definition.id()) || "zhuzevo".equals(definition.id())) {
            state = state.withInvertedMovementUntil(now + 120);
        }
        int duration = effectiveDuration(Math.max(30, maxDuration(effectsToApply)));
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.45D, 0.55D, 0.45D, 0.01D);
        visualRuntime.apply(player, visualId, duration, true);
        return new PlayerState(state.playerUuid(), 0, state.lastConsumedAt(), now + duration, state.invertedMovementUntil(), state.lastItemId());
    }

    private void applyConfiguredEffects(Player player, List<ConfiguredEffect> configuredEffects) {
        Set<PotionEffectType> tracked = trackedEffects.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (ConfiguredEffect configured : configuredEffects) {
            PotionEffectType type = resolveEffect(configured.type());
            if (type == null) {
                continue;
            }
            int ticks = Math.max(1, effectiveDuration(configured.durationSeconds())) * 20;
            player.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, configured.amplifier()), false, false, true));
            tracked.add(type);
        }
    }

    private PotionEffectType resolveEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONFUSION" -> PotionEffectType.NAUSEA;
            case "HARM", "INSTANT_DAMAGE" -> PotionEffectType.INSTANT_DAMAGE;
            case "WEAVING", "UNLUCK", "BAD_OMEN", "LUCK", "DARKNESS" -> PotionEffectType.getByName(normalized);
            default -> PotionEffectType.getByName(normalized);
        };
    }

    private int maxDuration(List<ConfiguredEffect> effects) {
        int max = 0;
        for (ConfiguredEffect effect : effects) {
            max = Math.max(max, effect.durationSeconds());
        }
        return max;
    }

    private int effectiveDuration(int baseSeconds) {
        return configService.durationOverrideSeconds() > 0 ? configService.durationOverrideSeconds() : baseSeconds;
    }

    public record PlayerState(UUID playerUuid, int currentScale, long lastConsumedAt, long overdoseUntil, long invertedMovementUntil, String lastItemId) {
        public static PlayerState empty(UUID playerUuid) {
            return new PlayerState(playerUuid, 0, 0L, 0L, 0L, "");
        }

        public PlayerState withCurrentScale(int scale) {
            return new PlayerState(playerUuid, scale, lastConsumedAt, overdoseUntil, invertedMovementUntil, lastItemId);
        }

        public PlayerState withInvertedMovementUntil(long until) {
            return new PlayerState(playerUuid, currentScale, lastConsumedAt, overdoseUntil, until, lastItemId);
        }
    }
}
