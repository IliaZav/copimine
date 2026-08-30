package me.copimine.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Main-thread-owned client state for the optional End Rift boss/reverse-control visuals. */
public final class EndEventClientState {
    private String eventId = "";
    private long generation;
    private String bossUuid = "";
    private String bossBindingInstance = "";
    private String bossVisualId = "";
    private BossBarState bossBar;
    private final Map<String, BossPhaseBinding> bossPhase = new HashMap<>();
    private final Map<String, EntityVisualBinding> entityVisuals = new HashMap<>();
    private String controlInstance = "";
    private long controlExpiresAt;

    public synchronized boolean apply(EndEventPacket packet, long nowMillis) {
        if (packet == null || nowMillis < 0L) {
            return false;
        }
        if (!acceptEnvelope(packet)) {
            return false;
        }
        return switch (packet.type()) {
            case "END_BOSS_BIND" -> bindBoss(packet);
            case "END_BOSS_UNBIND" -> unbindBoss(packet);
            case "END_BOSS_PHASE" -> applyBossPhase(packet);
            // The bar carries its numeric state in the bridge envelope's
            // bounded metadata fields, so ClientBridgeProtocol dispatches it
            // through the overload below.
            case "END_BOSS_BAR" -> false;
            case "END_ENTITY_BIND" -> bindEntity(packet);
            case "END_ENTITY_UNBIND" -> unbindEntity(packet);
            case "END_CONTROL_START" -> startControl(packet, nowMillis);
            case "END_CONTROL_STOP" -> stopControl(packet);
            default -> false;
        };
    }

    /** Apply the server-authoritative health/progress snapshot for this boss. */
    public synchronized boolean applyBossBar(EndEventPacket packet, float progress,
                                             int health, int maxHealth, long nowMillis) {
        if (packet == null || nowMillis < 0L || !"END_BOSS_BAR".equals(packet.type())
                || !acceptEnvelope(packet)) {
            return false;
        }
        if (packet.subjectId().isBlank()
                || packet.instanceId().isBlank()
                || bossUuid.isBlank()
                || !Objects.equals(bossUuid, packet.subjectId())
                || !Objects.equals(bossBindingInstance, packet.instanceId())) {
            return false;
        }
        float normalizedProgress = Float.isFinite(progress)
                ? Math.max(0.0F, Math.min(1.0F, progress)) : -1.0F;
        int normalizedMax = Math.max(1, maxHealth);
        int normalizedHealth = Math.max(0, Math.min(normalizedMax, health));
        if (normalizedProgress < 0.0F) {
            return false;
        }
        String[] phaseParts = packet.phaseId().split("\\|", -1);
        String phaseId = phaseParts.length == 0 ? "" : phaseParts[0].trim().toUpperCase();
        String castState = phaseParts.length > 1 ? phaseParts[1].trim().toUpperCase() : "NONE";
        if (!BOSS_PHASES.contains(phaseId) || !BOSS_CAST_STATES.contains(castState)) {
            return false;
        }
        bossBar = new BossBarState(
                packet.instanceId(), packet.subjectId(), phaseId, castState,
                normalizedHealth, normalizedMax, normalizedProgress, true, nowMillis);
        return true;
    }

    public synchronized boolean isReverseActive(long nowMillis) {
        if (controlInstance.isBlank() || nowMillis >= controlExpiresAt) {
            controlInstance = "";
            controlExpiresAt = 0L;
            return false;
        }
        return true;
    }

    public synchronized boolean isBossBound(String uuid) {
        return uuid != null && !uuid.isBlank() && Objects.equals(bossUuid, uuid);
    }

    public synchronized boolean hasBossBinding() {
        return !bossUuid.isBlank();
    }

    public synchronized boolean hasActiveBossBar() {
        return bossBar != null && bossBar.visible()
                && !bossBar.bossUuid().isBlank()
                && Objects.equals(bossUuid, bossBar.bossUuid())
                && Objects.equals(bossBindingInstance, bossBar.instanceId());
    }

    public synchronized BossBarState bossBar() {
        return bossBar;
    }

    public synchronized String visualForEntity(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "";
        }
        if (Objects.equals(bossUuid, uuid)) {
            return bossVisualId;
        }
        EntityVisualBinding binding = entityVisuals.get(uuid);
        return binding == null ? "" : binding.visualId();
    }

    public synchronized String bossUuid() {
        return bossUuid;
    }

    public synchronized String bossPhaseForEntity(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "";
        }
        BossPhaseBinding binding = bossPhase.get(uuid);
        return binding == null ? "" : binding.phaseId();
    }

    public synchronized long bossPhaseTransitionMillisForEntity(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return 0L;
        }
        BossPhaseBinding binding = bossPhase.get(uuid);
        return binding == null ? 0L : binding.transitionDurationMillis();
    }

    public synchronized String controlInstanceId() {
        return controlInstance;
    }

    public synchronized String eventId() {
        return eventId;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized void clear() {
        clearEffects();
        eventId = "";
        generation = 0L;
    }

    private boolean bindBoss(EndEventPacket packet) {
        if (packet.subjectId().isBlank() || packet.instanceId().isBlank()) {
            return false;
        }
        if (!Objects.equals(bossUuid, packet.subjectId())) {
            bossPhase.clear();
            bossBar = null;
        }
        bossUuid = packet.subjectId();
        bossBindingInstance = packet.instanceId();
        bossVisualId = packet.visualId().isBlank() ? "END_RIFT_GUARDIAN_V1" : packet.visualId();
        return true;
    }

    private boolean unbindBoss(EndEventPacket packet) {
        if (bossUuid.isBlank() || !Objects.equals(bossBindingInstance, packet.instanceId())) {
            return false;
        }
        bossPhase.remove(bossUuid);
        bossBar = null;
        bossUuid = "";
        bossBindingInstance = "";
        bossVisualId = "";
        return true;
    }

    public synchronized boolean applyBossPhase(EndEventPacket packet) {
        if (packet.subjectId().isBlank()
                || packet.instanceId().isBlank()
                || packet.phaseId().isBlank()
                || bossUuid.isBlank()
                || !Objects.equals(bossUuid, packet.subjectId())
                || !Objects.equals(bossBindingInstance, packet.instanceId())) {
            return false;
        }
        bossPhase.put(packet.subjectId(), new BossPhaseBinding(
                packet.instanceId(),
                packet.phaseId(),
                packet.durationMillis()));
        return true;
    }

    private boolean bindEntity(EndEventPacket packet) {
        if (packet.subjectId().isBlank() || packet.instanceId().isBlank() || packet.visualId().isBlank()) {
            return false;
        }
        entityVisuals.put(packet.subjectId(), new EntityVisualBinding(packet.instanceId(), packet.visualId()));
        return true;
    }

    private boolean unbindEntity(EndEventPacket packet) {
        EntityVisualBinding binding = entityVisuals.get(packet.subjectId());
        if (binding == null || !Objects.equals(binding.instanceId(), packet.instanceId())) {
            return false;
        }
        entityVisuals.remove(packet.subjectId());
        return true;
    }

    private boolean startControl(EndEventPacket packet, long nowMillis) {
        if (packet.instanceId().isBlank() || packet.durationMillis() <= 0L) {
            return false;
        }
        if (Objects.equals(controlInstance, packet.instanceId())) {
            // A retransmitted START is an idempotent delivery confirmation.
            // It must not turn a ten-second effect into a longer one.
            return true;
        }
        if (!controlInstance.isBlank() && nowMillis < controlExpiresAt) {
            // The server owns the one-active-effect invariant.  Refuse a
            // second instance until the current deadline has elapsed rather
            // than silently replacing the first effect.
            return false;
        }
        controlInstance = packet.instanceId();
        controlExpiresAt = nowMillis + packet.durationMillis();
        return true;
    }

    private boolean stopControl(EndEventPacket packet) {
        if (controlInstance.isBlank() || !Objects.equals(controlInstance, packet.instanceId())) {
            return false;
        }
        controlInstance = "";
        controlExpiresAt = 0L;
        return true;
    }

    private void clearEffects() {
        bossUuid = "";
        bossBindingInstance = "";
        bossVisualId = "";
        bossBar = null;
        bossPhase.clear();
        entityVisuals.clear();
        controlInstance = "";
        controlExpiresAt = 0L;
    }

    private boolean acceptEnvelope(EndEventPacket packet) {
        if (!eventId.isBlank() && !Objects.equals(eventId, packet.eventId())) {
            clearEffects();
            eventId = packet.eventId();
            generation = packet.generation();
        } else if (!eventId.isBlank() && packet.generation() < generation) {
            return false;
        } else if (!Objects.equals(eventId, packet.eventId())) {
            eventId = packet.eventId();
            generation = packet.generation();
        } else if (packet.generation() > generation) {
            generation = packet.generation();
        }
        return true;
    }

    private static final Set<String> BOSS_PHASES = Set.of(
            "AWAKENING", "HUNTER", "DISTORTION", "ABSORPTION", "CATASTROPHE");
    private static final Set<String> BOSS_CAST_STATES = Set.of(
            "NONE", "ABSORPTION_CHANNEL", "JUDGMENT_CAST", "EXHAUSTED");

    public record BossBarState(
            String instanceId,
            String bossUuid,
            String phaseId,
            String castState,
            int health,
            int maxHealth,
            float progress,
            boolean visible,
            long updatedAtMillis) {
    }

    private record EntityVisualBinding(String instanceId, String visualId) {
    }

    private record BossPhaseBinding(String instanceId, String phaseId, long transitionDurationMillis) {
    }
}
