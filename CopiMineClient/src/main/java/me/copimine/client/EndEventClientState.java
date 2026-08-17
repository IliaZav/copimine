package me.copimine.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Main-thread-owned client state for the optional End Rift boss/reverse-control visuals. */
public final class EndEventClientState {
    private String eventId = "";
    private long generation;
    private String bossUuid = "";
    private String bossBindingInstance = "";
    private String bossVisualId = "";
    private final Map<String, EntityVisualBinding> entityVisuals = new HashMap<>();
    private String controlInstance = "";
    private long controlExpiresAt;

    public synchronized boolean apply(EndEventPacket packet, long nowMillis) {
        if (packet == null || nowMillis < 0L) {
            return false;
        }
        if (!eventId.isBlank() && !Objects.equals(eventId, packet.eventId())) {
            clearEffects();
        } else if (!eventId.isBlank() && packet.generation() < generation) {
            return false;
        }
        if (!Objects.equals(eventId, packet.eventId()) || packet.generation() > generation) {
            clearEffects();
            eventId = packet.eventId();
            generation = packet.generation();
        }
        return switch (packet.type()) {
            case "END_BOSS_BIND" -> bindBoss(packet);
            case "END_BOSS_UNBIND" -> unbindBoss(packet);
            case "END_ENTITY_BIND" -> bindEntity(packet);
            case "END_ENTITY_UNBIND" -> unbindEntity(packet);
            case "END_CONTROL_START" -> startControl(packet, nowMillis);
            case "END_CONTROL_STOP" -> stopControl(packet);
            default -> false;
        };
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
        bossUuid = packet.subjectId();
        bossBindingInstance = packet.instanceId();
        bossVisualId = packet.visualId().isBlank() ? "END_RIFT_GUARDIAN_V1" : packet.visualId();
        return true;
    }

    private boolean unbindBoss(EndEventPacket packet) {
        if (bossUuid.isBlank() || !Objects.equals(bossBindingInstance, packet.instanceId())) {
            return false;
        }
        bossUuid = "";
        bossBindingInstance = "";
        bossVisualId = "";
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
            controlExpiresAt = Math.max(controlExpiresAt, nowMillis + packet.durationMillis());
            return true;
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
        entityVisuals.clear();
        controlInstance = "";
        controlExpiresAt = 0L;
    }

    private record EntityVisualBinding(String instanceId, String visualId) {
    }
}
