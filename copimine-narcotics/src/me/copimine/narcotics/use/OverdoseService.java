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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OverdoseService {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private final VisualRuntimeService visualRuntime;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PotionEffectType>> trackedEffects = new ConcurrentHashMap<>();
    private final Set<UUID> movementGuard = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loadingStates = ConcurrentHashMap.newKeySet();
    private final Set<UUID> readyStates = ConcurrentHashMap.newKeySet();
    private final AtomicLong stateEpoch = new AtomicLong(0L);
    private final Map<UUID, Long> sessionEpochs = new ConcurrentHashMap<>();

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
        if (playerUuid == null || readyStates.contains(playerUuid) || !loadingStates.add(playerUuid)) {
            return;
        }
        long requestEpoch = stateEpoch.longValue();
        long requestSessionEpoch = sessionEpoch(playerUuid);
        database.loadPlayerState(playerUuid).thenAccept(state -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stateEpoch.longValue() != requestEpoch || sessionEpoch(playerUuid) != requestSessionEpoch) {
                            return;
                        }
                        loadingStates.remove(playerUuid);
                        PlayerState loaded = state == null ? PlayerState.empty(playerUuid) : state;
                        states.compute(playerUuid, (ignored, current) -> current == null || loaded.stateVersion() >= current.stateVersion() ? loaded : current);
                        readyStates.add(playerUuid);
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null && player.isOnline()) {
                            restoreActiveOverdose(player);
                        }
                    });
                })
                .exceptionally(error -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stateEpoch.longValue() != requestEpoch || sessionEpoch(playerUuid) != requestSessionEpoch) {
                            return;
                        }
                        loadingStates.remove(playerUuid);
                        readyStates.remove(playerUuid);
                        plugin.getLogger().warning("Failed to preload narcotics state: " + error.getMessage());
                        Bukkit.getScheduler().runTaskLater(plugin, () -> preloadState(playerUuid), 100L);
                    });
                    return null;
                });
    }

    public boolean isStateReady(Player player) {
        return player != null && readyStates.contains(player.getUniqueId());
    }

    public void restoreActiveOverdose(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerState state = states.get(player.getUniqueId());
        long now = System.currentTimeMillis() / 1000L;
        if (state == null || state.overdoseUntil() <= now) {
            return;
        }
        NarcoticDefinition definition = configService.items().get(state.lastItemId());
        if (definition == null) {
            return;
        }
        int remainingSeconds = (int) Math.min(Integer.MAX_VALUE, state.overdoseUntil() - now);
        applyConfiguredEffects(player, buildOverdoseEffects(definition), Math.max(1, remainingSeconds));
        visualRuntime.apply(player, resolveOverdoseVisual(definition), Math.max(1, remainingSeconds), true);
    }

    public void consume(Player player, NarcoticDefinition definition) {
        long now = System.currentTimeMillis() / 1000L;
        PlayerState state = states.getOrDefault(player.getUniqueId(), PlayerState.empty(player.getUniqueId()));
        if (now - state.lastConsumedAt() > configService.usageWindowSeconds()) {
            state = state.withCurrentScale(0);
        }
        boolean activeOverdose = state.overdoseUntil() > now;
        if ("zhuzevo".equals(definition.id())) {
            PlayerState base = new PlayerState(
                    player.getUniqueId(),
                    state.currentScale(),
                    now,
                    state.overdoseUntil(),
                    state.invertedMovementUntil(),
                    definition.id(),
                    state.stateVersion() + 1L
            );
            PlayerState updated = configService.zhuzevoForcesOverdose()
                    ? applyOverdose(player, definition, base, now)
                    : applyZhuzevo(player, definition, state, now);
            states.put(player.getUniqueId(), updated);
            database.savePlayerState(updated);
            return;
        }
        int newScale = state.currentScale() + Math.max(0, configService.overdoseWeightFor(definition));
        boolean overdose = activeOverdose;
        overdose = overdose || newScale >= configService.overdoseThreshold();

        if (configService.clearNormalEffectsBeforeNewUse() && !activeOverdose) {
            clearTransientEffects(player, false);
        }

        PlayerState updated = new PlayerState(
                player.getUniqueId(),
                newScale,
                now,
                state.overdoseUntil(),
                state.invertedMovementUntil(),
                definition.id(),
                state.stateVersion() + 1L
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
        clearTransientEffects(player, true);
        if (preserveState) {
            return;
        }
        PlayerState state = states.get(player.getUniqueId());
        if (state != null) {
            PlayerState cleared = new PlayerState(
                    state.playerUuid(),
                    state.currentScale(),
                    state.lastConsumedAt(),
                    0L,
                    0L,
                    state.lastItemId(),
                    state.stateVersion() + 1L
            );
            states.put(player.getUniqueId(), cleared);
            database.savePlayerState(cleared);
        }
    }

    public void clearPlayer(Player player) {
        clearTransientEffects(player, true);
        PlayerState previous = states.get(player.getUniqueId());
        long nextVersion = previous == null ? 1L : previous.stateVersion() + 1L;
        PlayerState cleared = new PlayerState(player.getUniqueId(), 0, 0L, 0L, 0L, "", nextVersion);
        states.put(player.getUniqueId(), cleared);
        database.savePlayerState(cleared);
    }

    public void runDrugTest(Player player, NarcoticDefinition definition, int durationSeconds, boolean includeVisuals) {
        clearTransientEffects(player, false);
        applyConfiguredEffects(player, definition.normalEffects(), durationSeconds);
        if (includeVisuals) {
            visualRuntime.apply(player, definition.visualEffectId(), Math.max(1, durationSeconds), false);
        }
        long now = System.currentTimeMillis() / 1000L;
        PlayerState previous = states.getOrDefault(player.getUniqueId(), PlayerState.empty(player.getUniqueId()));
        PlayerState updated = new PlayerState(
                player.getUniqueId(),
                previous.currentScale(),
                now,
                0L,
                0L,
                definition.id(),
                previous.stateVersion() + 1L
        );
        states.put(player.getUniqueId(), updated);
        database.savePlayerState(updated);
    }

    public void runOverdoseTest(Player player, NarcoticDefinition definition, int durationSeconds, boolean includeVisuals) {
        clearTransientEffects(player, false);
        List<ConfiguredEffect> effectsToApply = buildOverdoseEffects(definition);
        int effectiveSeconds = Math.max(1, durationSeconds);
        applyConfiguredEffects(player, effectsToApply, effectiveSeconds);
        if (includeVisuals) {
            visualRuntime.apply(player, resolveOverdoseVisual(definition), effectiveSeconds, true);
        }
        long now = System.currentTimeMillis() / 1000L;
        PlayerState previous = states.getOrDefault(player.getUniqueId(), PlayerState.empty(player.getUniqueId()));
        PlayerState updated = new PlayerState(
                player.getUniqueId(),
                0,
                now,
                now + effectiveSeconds,
                now + effectiveSeconds,
                definition.id(),
                previous.stateVersion() + 1L
        );
        states.put(player.getUniqueId(), updated);
        database.savePlayerState(updated);
    }

    private void clearTransientEffects(Player player, boolean clearVisuals) {
        Set<PotionEffectType> effects = trackedEffects.remove(player.getUniqueId());
        if (effects != null) {
            for (PotionEffectType effect : effects) {
                player.removePotionEffect(effect);
            }
        }
        if (clearVisuals) {
            visualRuntime.clear(player);
        }
    }

    public void clearAllCachedState() {
        stateEpoch.incrementAndGet();
        states.clear();
        trackedEffects.clear();
        movementGuard.clear();
        loadingStates.clear();
        readyStates.clear();
        sessionEpochs.clear();
    }

    public void releasePlayerSession(Player player) {
        if (player == null) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        clearActiveEffects(player, true);
        invalidateSession(playerUuid);
        states.remove(playerUuid);
        movementGuard.remove(playerUuid);
        loadingStates.remove(playerUuid);
        readyStates.remove(playerUuid);
    }

    public void forceClearOverdose(Player player) {
        clearTransientEffects(player, true);
        PlayerState previous = states.get(player.getUniqueId());
        long nextVersion = previous == null ? 1L : previous.stateVersion() + 1L;
        PlayerState cleared = new PlayerState(player.getUniqueId(), 0, 0L, 0L, 0L, "", nextVersion);
        states.put(player.getUniqueId(), cleared);
        database.savePlayerState(cleared);
    }

    public PlayerState state(UUID playerUuid) {
        return states.getOrDefault(playerUuid, PlayerState.empty(playerUuid));
    }

    private long sessionEpoch(UUID playerUuid) {
        return sessionEpochs.getOrDefault(playerUuid, 0L);
    }

    private void invalidateSession(UUID playerUuid) {
        sessionEpochs.merge(playerUuid, 1L, Long::sum);
    }

    private PlayerState applyOverdose(Player player, NarcoticDefinition definition, PlayerState state, long now) {
        List<ConfiguredEffect> effectsToApply = buildOverdoseEffects(definition);
        applyConfiguredEffects(player, effectsToApply);
        int duration = effectiveDuration(Math.max(30, maxDuration(effectsToApply)));
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.45D, 0.55D, 0.45D, 0.01D);
        visualRuntime.apply(player, resolveOverdoseVisual(definition), duration, true);
        return new PlayerState(state.playerUuid(), 0, state.lastConsumedAt(), now + duration, now + duration, state.lastItemId(), state.stateVersion());
    }

    private PlayerState applyZhuzevo(Player player, NarcoticDefinition definition, PlayerState state, long now) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int duration = random.nextInt(240, 301);
        List<ConfiguredEffect> pool = new ArrayList<>();
        pool.add(new ConfiguredEffect("DARKNESS", 0, duration));
        pool.add(new ConfiguredEffect("HUNGER", random.nextInt(0, 3), duration));
        pool.add(new ConfiguredEffect("SLOWNESS", random.nextInt(0, 3), duration));
        pool.add(new ConfiguredEffect("MINING_FATIGUE", random.nextInt(1, 5), duration));
        pool.add(new ConfiguredEffect("NAUSEA", random.nextInt(0, 3), duration));
        Collections.shuffle(pool);
        int take = random.nextInt(4, 6);
        List<ConfiguredEffect> effects = new ArrayList<>(pool.subList(0, Math.min(take, pool.size())));
        boolean luckyShader = random.nextInt(10) == 0;
        if (luckyShader) {
            effects.add(new ConfiguredEffect("WITHER", 0, Math.min(duration, 60)));
            visualRuntime.apply(player, resolveOverdoseVisual(definition), duration, false);
        } else if (state.overdoseUntil() <= now) {
            visualRuntime.clear(player);
        }
        applyConfiguredEffects(player, effects, duration);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.45D, 0.35D, 0.01D);
        return new PlayerState(
                state.playerUuid(),
                state.currentScale(),
                now,
                state.overdoseUntil(),
                state.invertedMovementUntil(),
                state.overdoseUntil() > now ? state.lastItemId() : definition.id(),
                state.stateVersion() + 1L
        );
    }

    private void applyConfiguredEffects(Player player, List<ConfiguredEffect> configuredEffects) {
        applyConfiguredEffects(player, configuredEffects, -1);
    }

    private void applyConfiguredEffects(Player player, List<ConfiguredEffect> configuredEffects, int overrideDurationSeconds) {
        Set<PotionEffectType> tracked = trackedEffects.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (ConfiguredEffect configured : configuredEffects) {
            PotionEffectType type = resolveEffect(configured.type());
            if (type == null) {
                continue;
            }
            int effectSeconds = overrideDurationSeconds > 0 ? overrideDurationSeconds : effectiveDuration(configured.durationSeconds());
            int ticks = Math.max(1, effectSeconds) * 20;
            player.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, configured.amplifier()), false, false, true));
            tracked.add(type);
        }
    }

    private List<ConfiguredEffect> buildOverdoseEffects(NarcoticDefinition definition) {
        List<ConfiguredEffect> effectsToApply = new ArrayList<>(definition.overdoseEffects());
        if ("zhuzevo".equals(definition.id())) {
            effectsToApply.clear();
            List<ConfiguredEffect> pool = new ArrayList<>();
            for (NarcoticDefinition source : configService.items().values()) {
                if ("zhuzevo".equals(source.id())) {
                    continue;
                }
                pool.addAll(source.overdoseEffects());
            }
            Collections.shuffle(pool);
            for (int index = 0; index < Math.min(4, pool.size()); index++) {
                effectsToApply.add(pool.get(index));
            }
        }
        appendUniversalOverdoseEffects(effectsToApply);
        return effectsToApply;
    }

    private String resolveOverdoseVisual(NarcoticDefinition definition) {
        if (!"zhuzevo".equals(definition.id())) {
            return definition.visualEffectId();
        }
        List<String> visuals = new ArrayList<>();
        for (NarcoticDefinition source : configService.items().values()) {
            if (!"zhuzevo".equals(source.id())) {
                visuals.add(source.visualEffectId());
            }
        }
        Collections.shuffle(visuals);
        return visuals.isEmpty() ? definition.visualEffectId() : visuals.get(0);
    }

    private PotionEffectType resolveEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONFUSION" -> PotionEffectType.NAUSEA;
            case "HARM", "INSTANT_DAMAGE" -> PotionEffectType.INSTANT_DAMAGE;
            case "WEAVING" -> null;
            case "UNLUCK", "BAD_OMEN", "LUCK", "DARKNESS" -> PotionEffectType.getByName(normalized);
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

    private void appendUniversalOverdoseEffects(List<ConfiguredEffect> effects) {
        if (ThreadLocalRandom.current().nextInt(10) == 0) {
            addOrUpgrade(effects, new ConfiguredEffect("DARKNESS", 0, 45));
        }
        addOrUpgrade(effects, new ConfiguredEffect("WEAKNESS", 0, 300));
        addOrUpgrade(effects, new ConfiguredEffect("INSTANT_DAMAGE", 0, 1));
        addOrUpgrade(effects, new ConfiguredEffect("NAUSEA", 2, 180));
        addOrUpgrade(effects, new ConfiguredEffect("MINING_FATIGUE", 4, 300));
    }

    private void addOrUpgrade(List<ConfiguredEffect> effects, ConfiguredEffect required) {
        for (int index = 0; index < effects.size(); index++) {
            ConfiguredEffect current = effects.get(index);
            if (current.type().equalsIgnoreCase(required.type())) {
                effects.set(index, new ConfiguredEffect(
                        current.type(),
                        Math.max(current.amplifier(), required.amplifier()),
                        Math.max(current.durationSeconds(), required.durationSeconds())
                ));
                return;
            }
        }
        effects.add(required);
    }

    public record PlayerState(UUID playerUuid, int currentScale, long lastConsumedAt, long overdoseUntil, long invertedMovementUntil, String lastItemId, long stateVersion) {
        public static PlayerState empty(UUID playerUuid) {
            return new PlayerState(playerUuid, 0, 0L, 0L, 0L, "", 0L);
        }

        public PlayerState withCurrentScale(int scale) {
            return new PlayerState(playerUuid, scale, lastConsumedAt, overdoseUntil, invertedMovementUntil, lastItemId, stateVersion);
        }

        public PlayerState withInvertedMovementUntil(long until) {
            return new PlayerState(playerUuid, currentScale, lastConsumedAt, overdoseUntil, until, lastItemId, stateVersion);
        }
    }
}
