package me.copimine.clientbridge;

import me.copimine.protocol.NarcoticsBridgeBodyCodec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded, stream-scoped correlation for untrusted protocol-v3 visual delivery reports. */
public final class ClientVisualCommandCorrelation {
    public static final int MAX_RECORDS_PER_RECIPIENT_SESSION = 512;
    public static final long DELIVERY_TIMEOUT_MILLIS = 30_000L;
    public static final long MAX_NEWER_COMMANDS = 256L;
    private static final Pattern REASON = Pattern.compile("[A-Za-z0-9_.:-]{1,256}");
    private static final Pattern STARTED_ROUTE = Pattern.compile("STARTED_[A-Z0-9_-]{1,24}");

    private final Map<Key, Record> records = new LinkedHashMap<>();
    private final Map<SessionKey, Long> issueOrders = new HashMap<>();

    /** Registers a command before it is sent. Returns false when the bounded store is full. */
    public synchronized boolean register(UUID playerUuid, UUID sessionUuid, String streamId,
            NarcoticsBridgeBodyCodec.Body body, long firstSentAtMillis) {
        if (playerUuid == null || sessionUuid == null || streamId == null || streamId.isBlank()
                || body == null || firstSentAtMillis <= 0 || !sessionUuid.equals(body.sessionUuid())) {
            return false;
        }
        try {
            NarcoticsBridgeBodyCodec.encode(body);
        } catch (RuntimeException invalid) {
            return false;
        }
        prune(firstSentAtMillis);
        SessionKey session = new SessionKey(playerUuid, sessionUuid);
        long count = 0;
        for (Key key : records.keySet()) {
            if (key.playerUuid.equals(playerUuid) && key.sessionUuid.equals(sessionUuid)) {
                count++;
            }
        }
        if (count >= MAX_RECORDS_PER_RECIPIENT_SESSION) {
            return false;
        }
        Key key = new Key(playerUuid, sessionUuid, body.commandSeq());
        if (records.containsKey(key)) {
            return false;
        }
        long order = issueOrders.getOrDefault(session, 0L);
        if (order == Long.MAX_VALUE) {
            return false;
        }
        order++;
        issueOrders.put(session, order);
        long duration = body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START
                ? body.durationMillis() : 0L;
        long expiry = safeAdd(firstSentAtMillis, DELIVERY_TIMEOUT_MILLIS);
        records.put(key, new Record(key, streamId, body, firstSentAtMillis, expiry, order));
        return true;
    }

    public synchronized boolean canRegister(UUID playerUuid, UUID sessionUuid, long commandSeq, long nowMillis) {
        if (playerUuid == null || sessionUuid == null || commandSeq <= 0 || nowMillis <= 0) {
            return false;
        }
        prune(nowMillis);
        int count = 0;
        for (Key key : records.keySet()) {
            if (key.playerUuid.equals(playerUuid) && key.sessionUuid.equals(sessionUuid)) {
                count++;
            }
        }
        return count < MAX_RECORDS_PER_RECIPIENT_SESSION
                && !records.containsKey(new Key(playerUuid, sessionUuid, commandSeq));
    }

    /** Accepts one report only when every identity, command, type, effect, and status field matches. */
    public synchronized Optional<CorrelatedReport> accept(UUID playerUuid, UUID sessionUuid,
            String activeStreamId, ClientBridgePayloads.Message report, long nowMillis) {
        if (playerUuid == null || sessionUuid == null || activeStreamId == null || report == null
                || !isStrictReportShape(report) || !sessionUuid.toString().equals(report.sessionId())) {
            return Optional.empty();
        }
        prune(nowMillis);
        Key key = new Key(playerUuid, sessionUuid, report.seq());
        Record record = records.get(key);
        if (record == null || !record.streamId.equals(activeStreamId)
                || !reportMatches(record.body, report, record.startedAckAccepted)) {
            return Optional.empty();
        }
        ReportKind kind = switch (report.type()) {
            case ClientBridgePayloads.VISUAL_ACK -> ReportKind.ACK;
            case ClientBridgePayloads.VISUAL_FINISHED -> ReportKind.FINISHED;
            case ClientBridgePayloads.VISUAL_ERROR -> ReportKind.ERROR;
            default -> throw new IllegalStateException("Validated visual report has an unknown type");
        };
        if (kind == ReportKind.ACK) {
            if (record.ackAccepted) {
                return Optional.empty();
            }
            record.ackAccepted = true;
            if (record.body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START) {
                record.startedAckAccepted = true;
                record.expiresAtMillis = safeAdd(
                        safeAdd(record.body.bodyTimestampMillis(), record.body.durationMillis()),
                        DELIVERY_TIMEOUT_MILLIS);
            } else {
                records.remove(key);
            }
        } else {
            records.remove(key);
        }
        return Optional.of(new CorrelatedReport(record.body, kind, report.status(), report.reason()));
    }

    /** Retries are limited to the original stream, timeout, and 256 newer unique commands. */
    public synchronized boolean mayRetry(UUID playerUuid, UUID sessionUuid, long commandSeq,
            String activeStreamId, long nowMillis) {
        if (playerUuid == null || sessionUuid == null || activeStreamId == null) {
            return false;
        }
        prune(nowMillis);
        Record record = records.get(new Key(playerUuid, sessionUuid, commandSeq));
        if (record == null || record.ackAccepted || !record.streamId.equals(activeStreamId)
                || nowMillis < record.firstSentAtMillis
                || nowMillis - record.firstSentAtMillis >= DELIVERY_TIMEOUT_MILLIS) {
            return false;
        }
        long currentOrder = issueOrders.getOrDefault(new SessionKey(playerUuid, sessionUuid), 0L);
        long newer = currentOrder - record.issueOrder;
        return newer >= 0 && newer < MAX_NEWER_COMMANDS;
    }

    public synchronized void retireStream(UUID playerUuid, UUID sessionUuid, String streamId) {
        if (playerUuid == null || sessionUuid == null || streamId == null) {
            return;
        }
        records.keySet().removeIf(key -> {
            Record record = records.get(key);
            return record != null && key.playerUuid.equals(playerUuid)
                    && key.sessionUuid.equals(sessionUuid) && record.streamId.equals(streamId);
        });
    }

    public synchronized void removeCommand(UUID playerUuid, UUID sessionUuid, long commandSeq) {
        if (playerUuid != null && sessionUuid != null && commandSeq > 0) {
            records.remove(new Key(playerUuid, sessionUuid, commandSeq));
        }
    }

    public synchronized void clearSession(UUID playerUuid, UUID sessionUuid) {
        if (playerUuid == null || sessionUuid == null) {
            return;
        }
        records.keySet().removeIf(key -> key.playerUuid.equals(playerUuid) && key.sessionUuid.equals(sessionUuid));
        issueOrders.remove(new SessionKey(playerUuid, sessionUuid));
    }

    public synchronized void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        records.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        issueOrders.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
    }

    public synchronized int size(UUID playerUuid, UUID sessionUuid) {
        int count = 0;
        for (Key key : records.keySet()) {
            if (key.playerUuid.equals(playerUuid) && key.sessionUuid.equals(sessionUuid)) {
                count++;
            }
        }
        return count;
    }

    public synchronized int sizeTotal() {
        return records.size();
    }

    public synchronized void prune(long nowMillis) {
        if (nowMillis <= 0) {
            return;
        }
        Iterator<Record> iterator = records.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtMillis <= nowMillis) {
                iterator.remove();
            }
        }
    }

    private static boolean isStrictReportShape(ClientBridgePayloads.Message message) {
        return message.protocol() == ClientBridgePayloads.PROTOCOL_VERSION
                && message.seq() > 0 && message.timestampMillis() > 0
                && !message.sessionId().isBlank()
                && message.clientVersion().isEmpty()
                && !message.clientVisuals() && !message.clientOverlay() && !message.clientShaderLike()
                && !message.trueIrisShader() && message.supportedEffects().isEmpty()
                && message.shaderpack().isEmpty() && message.durationMillis() == 0
                && Float.floatToRawIntBits(message.intensity()) == Float.floatToRawIntBits(0.0f)
                && message.fadeInMillis() == 0 && message.fadeOutMillis() == 0
                && message.mode().equals("CLIENT_MOD") && message.clearPolicy().isEmpty()
                && message.source().equals("SYSTEM");
    }

    private static boolean reportMatches(NarcoticsBridgeBodyCodec.Body body,
            ClientBridgePayloads.Message report, boolean startedAckAccepted) {
        String expectedEffect = body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL
                ? "" : body.effectId();
        if (!expectedEffect.equals(report.effectId())) {
            return false;
        }
        return switch (report.type()) {
            case ClientBridgePayloads.VISUAL_ACK -> switch (body.type()) {
                case VISUAL_START -> report.reason().isEmpty()
                        && (report.status().equals("STARTED") || STARTED_ROUTE.matcher(report.status()).matches());
                case VISUAL_STOP -> report.status().equals("STOPPED") && report.reason().isEmpty();
                case VISUAL_CLEAR_ALL -> report.status().equals("CLEARED") && report.reason().isEmpty();
            };
            case ClientBridgePayloads.VISUAL_FINISHED -> body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START
                    && startedAckAccepted && report.status().isEmpty() && REASON.matcher(report.reason()).matches();
            case ClientBridgePayloads.VISUAL_ERROR -> body.type() == NarcoticsBridgeBodyCodec.Type.VISUAL_START
                    && report.status().equals("ERROR") && REASON.matcher(report.reason()).matches();
            default -> false;
        };
    }

    private static long safeAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record Key(UUID playerUuid, UUID sessionUuid, long commandSeq) {
    }

    private record SessionKey(UUID playerUuid, UUID sessionUuid) {
    }

    private static final class Record {
        final Key key;
        final String streamId;
        final NarcoticsBridgeBodyCodec.Body body;
        final long firstSentAtMillis;
        final long issueOrder;
        long expiresAtMillis;
        boolean ackAccepted;
        boolean startedAckAccepted;

        Record(Key key, String streamId, NarcoticsBridgeBodyCodec.Body body,
               long firstSentAtMillis, long expiresAtMillis, long issueOrder) {
            this.key = key;
            this.streamId = streamId;
            this.body = body;
            this.firstSentAtMillis = firstSentAtMillis;
            this.expiresAtMillis = expiresAtMillis;
            this.issueOrder = issueOrder;
        }
    }

    public enum ReportKind {
        ACK,
        FINISHED,
        ERROR
    }

    public record CorrelatedReport(NarcoticsBridgeBodyCodec.Body body, ReportKind kind,
            String status, String reason) {
        public CorrelatedReport {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
