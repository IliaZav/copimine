package me.copimine.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict fixed-record codec for signed End Event v4 bodies. */
public final class EndEventBridgeBodyCodec {
    public static final int MAX_BODY_BYTES = BridgeSigningPolicy.MAX_BODY_BYTES;
    public static final int MESSAGE_SCHEMA = 4;
    public static final long MAX_GENERATION = Long.MAX_VALUE - 1;

    private static final int SCHEMA = 1;
    private static final int MAX_GENERAL_STRING_BYTES = 4_096;
    private static final int MAX_IDENTIFIER_BYTES = 128;
    private static final int MAX_PHASE_BYTES = 256;
    private static final long MAX_DURATION_MILLIS = 600_000L;
    private static final Set<String> END_DIMENSIONS = Set.of("overworld", "the_nether", "the_end");
    private static final Set<String> BOSS_PHASES = Set.of(
            "IDLE_BREATH", "RUN", "MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM", "MARK_CONTROL",
            "SUMMON_CHANNEL", "HURT", "PHASE_TRANSITION", "FINAL_STRIKE", "DYING", "TELEPORT_RIP",
            "CAST_CHARGE", "CAST_RELEASE", "CAST_IMPACT", "RECOVERY", "SPELL_VOID_BLAST",
            "SPELL_RIFT_PROJECTILE", "SPELL_RIFT_ARROWS", "SPELL_ARROW_SALVO", "SPELL_VOID_MARK",
            "SPELL_SUMMON_SERVANTS", "SPELL_SUMMON", "SPELL_RIFT_OBELISKS", "SPELL_ARENA_INFERNO");
    private static final Set<String> WAVE_POSES = Set.of(
            "WAVE_COMBAT", "WAVE_WINDUP", "WAVE_RELEASE", "WAVE_RECOVER", "WAVE_FROZEN");
    private static final Set<String> RITUAL_PHASES = Set.of(
            "RITUAL_CHANNEL", "RITUAL_WINDUP", "RITUAL_RELEASE", "RITUAL_COMBAT");
    private static final Set<String> TENTACLE_PHASES = Set.of(
            "EMERGING", "READY", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD", "THROW", "MISS_RECOVERY",
            "HIT_RECOVERY", "DYING", "DEAD_RESPAWN", "RETRACT", "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL",
            "RECOVERY", "IDLE", "EMERGE", "GRAB_MISS", "HURT", "DEATH");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern WORLD_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern RESOURCE_LOCATION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    private static final Pattern CANONICAL_NONNEGATIVE = Pattern.compile("(?:0|[1-9][0-9]*)");
    private static final Pattern CANONICAL_POSITIVE = Pattern.compile("[1-9][0-9]*");

    private EndEventBridgeBodyCodec() {
    }

    public static byte[] encode(Body body) {
        return encode(body, null);
    }

    /** Encodes while also checking recipient-bound prisoner fields when the recipient is known. */
    public static byte[] encode(Body body, UUID envelopeRecipient) {
        validate(body, envelopeRecipient);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(SCHEMA);
            output.writeByte(body.type().code());
            output.writeInt(MESSAGE_SCHEMA);
            output.writeLong(body.generation());
            output.writeLong(body.bodyTimestampMillis());
            writeUuid(output, body.eventUuid());
            writeString(output, body.instanceId(), MAX_IDENTIFIER_BYTES, "instance_id");
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
            output.writeInt(0);
            writeString(output, body.controlId(), MAX_IDENTIFIER_BYTES, "control_id");
            writeString(output, body.visualId(), MAX_PHASE_BYTES, "visual_id");
            writeUInt32(output, body.durationMillis(), "duration_ms");
            output.writeFloat(body.progressOrIntensity());
            writeUInt32(output, body.health(), "health");
            writeUInt32(output, body.maxHealth(), "max_health");
            writeString(output, body.subjectOrMode(), MAX_GENERAL_STRING_BYTES, "subject_or_mode");
            writeString(output, body.clearPolicy(), MAX_GENERAL_STRING_BYTES, "clear_policy");
            writeString(output, body.reservedOne(), MAX_GENERAL_STRING_BYTES, "reserved field");
            writeString(output, body.reservedTwo(), MAX_GENERAL_STRING_BYTES, "reserved field");
            writeString(output, body.status(), MAX_GENERAL_STRING_BYTES, "status");
            output.flush();
            if (bytes.size() > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("End Event body exceeds the protocol body limit");
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode End Event bridge body", exception);
        }
    }

    public static Body decode(byte[] encoded) {
        return decode(encoded, null);
    }

    /** Decodes exactly one body and checks recipient-bound prisoner fields when supplied. */
    public static Body decode(byte[] encoded, UUID envelopeRecipient) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Invalid End Event body length");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int schema = input.readUnsignedByte();
            int typeCode = input.readUnsignedByte();
            int messageSchema = input.readInt();
            if (schema != SCHEMA || messageSchema != MESSAGE_SCHEMA) {
                throw new IllegalArgumentException("Unsupported End Event body schema");
            }
            Type type = Type.fromCode(typeCode);
            long generation = input.readLong();
            long timestamp = input.readLong();
            UUID eventUuid = readUuid(input);
            String instance = readString(input, MAX_IDENTIFIER_BYTES, "instance_id");
            for (int index = 0; index < 4; index++) {
                if (input.readUnsignedByte() != 0) {
                    throw new IllegalArgumentException("Nonzero reserved End Event flag");
                }
            }
            if (input.readInt() != 0) {
                throw new IllegalArgumentException("Nonzero supported-effect count is reserved in v4");
            }
            String control = readString(input, MAX_IDENTIFIER_BYTES, "control_id");
            String visual = readString(input, MAX_PHASE_BYTES, "visual_id");
            long duration = Integer.toUnsignedLong(input.readInt());
            float progress = input.readFloat();
            long health = Integer.toUnsignedLong(input.readInt());
            long maxHealth = Integer.toUnsignedLong(input.readInt());
            String subject = readString(input, MAX_GENERAL_STRING_BYTES, "subject_or_mode");
            String clearPolicy = readString(input, MAX_GENERAL_STRING_BYTES, "clear_policy");
            String reservedOne = readString(input, MAX_GENERAL_STRING_BYTES, "reserved field");
            String reservedTwo = readString(input, MAX_GENERAL_STRING_BYTES, "reserved field");
            String status = readString(input, MAX_GENERAL_STRING_BYTES, "status");
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing End Event body bytes");
            }
            Body body = new Body(type, generation, timestamp, eventUuid, instance, control, visual,
                    duration, progress, health, maxHealth, subject, clearPolicy, reservedOne, reservedTwo, status);
            validate(body, envelopeRecipient);
            return body;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Truncated End Event body", exception);
        }
    }

    /** Verifies that the fixed-record body timestamp equals the signed envelope issued-at time. */
    public static void validateTimestampMatches(Body body, long issuedAtMillis) {
        validate(body, null);
        if (body.bodyTimestampMillis() != issuedAtMillis) {
            throw new IllegalArgumentException("End Event body timestamp does not match its envelope");
        }
    }

    public static ArenaBounds arenaBoundsFromContextBind(Body body) {
        if (body == null || body.type() != Type.CONTEXT_BIND) {
            throw new IllegalArgumentException("Expected CONTEXT_BIND body");
        }
        validate(body, null);
        String[] parts = body.subjectOrMode().split("\\|", -1);
        String[] minimum = parts[1].split(",", -1);
        String[] maximum = parts[2].split(",", -1);
        return new ArenaBounds(body.eventUuid(), body.generation(), parts[0],
                parseLong(minimum[0]), parseLong(minimum[1]), parseLong(minimum[2]),
                parseLong(maximum[0]), parseLong(maximum[1]), parseLong(maximum[2]));
    }

    /** Verifies both beam endpoints against the exact signed context AABB. */
    public static boolean beamWithinBounds(Body beam, ArenaBounds bounds) {
        if (beam == null || bounds == null || beam.type() != Type.END_WORLD_BEAM
                || !beam.eventUuid().equals(bounds.eventUuid()) || beam.generation() != bounds.generation()) {
            return false;
        }
        try {
            validate(beam, null);
            validateBounds(bounds);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String[] parts = beam.subjectOrMode().split("\\|", -1);
        String[] start = parts[2].split(",", -1);
        String endField = beam.clearPolicy().split("\\|", -1)[0];
        String[] end = endField.split(",", -1);
        if (!bounds.dimension().equals(parts[0])) {
            return false;
        }
        return inside(start, bounds) && inside(end, bounds);
    }

    private static boolean inside(String[] point, ArenaBounds bounds) {
        long x = floorCoordinate(point[0]);
        long y = floorCoordinate(point[1]);
        long z = floorCoordinate(point[2]);
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static void validateBounds(ArenaBounds bounds) {
        if (bounds.eventUuid() == null || bounds.generation() <= 0 || bounds.generation() > MAX_GENERATION
                || !END_DIMENSIONS.contains(bounds.dimension()) || bounds.minX() > bounds.maxX()
                || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) {
            throw new IllegalArgumentException("Invalid End Event arena bounds");
        }
        Body bind = new Body(Type.CONTEXT_BIND, bounds.generation(), 1L, bounds.eventUuid(), "", "", "",
                0, 0, 0, 0, bounds.dimension() + "|" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                + "|" + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ(), "", "", "", "");
        validate(bind, null);
    }

    private static long floorCoordinate(String value) {
        return new BigDecimal(value).setScale(0, java.math.RoundingMode.FLOOR).longValueExact();
    }

    private static void validate(Body body, UUID envelopeRecipient) {
        if (body == null || body.type() == null || body.generation() <= 0
                || body.generation() > MAX_GENERATION || body.bodyTimestampMillis() <= 0
                || body.eventUuid() == null) {
            throw new IllegalArgumentException("Invalid common End Event body fields");
        }
        requireString(body.instanceId(), MAX_IDENTIFIER_BYTES, "instance_id");
        requireString(body.controlId(), MAX_IDENTIFIER_BYTES, "control_id");
        requireString(body.visualId(), MAX_PHASE_BYTES, "visual_id");
        requireString(body.subjectOrMode(), MAX_GENERAL_STRING_BYTES, "subject_or_mode");
        requireString(body.clearPolicy(), MAX_GENERAL_STRING_BYTES, "clear_policy");
        requireString(body.reservedOne(), MAX_GENERAL_STRING_BYTES, "reserved field");
        requireString(body.reservedTwo(), MAX_GENERAL_STRING_BYTES, "reserved field");
        requireString(body.status(), MAX_GENERAL_STRING_BYTES, "status");
        if (!body.reservedOne().isEmpty() || !body.reservedTwo().isEmpty()) {
            throw new IllegalArgumentException("End Event reserved strings must be empty");
        }
        if (!Float.isFinite(body.progressOrIntensity()) || isNegativeZero(body.progressOrIntensity())
                || body.durationMillis() < 0
                || body.durationMillis() > MAX_DURATION_MILLIS || body.health() < 0 || body.health() > 2_000_000_000L
                || body.maxHealth() < 0 || body.maxHealth() > 2_000_000_000L
                || body.progressOrIntensity() < 0.0f || body.progressOrIntensity() > 1.0f) {
            throw new IllegalArgumentException("End Event numeric field is outside its bounds");
        }

        switch (body.type()) {
            case CONTEXT_BIND -> validateContextBind(body);
            case CONTEXT_UNBIND -> requireEmptyDefaults(body);
            case END_BOSS_BIND -> {
                requireIdentifier(body.instanceId(), "boss instance");
                requireCanonicalUuid(body.subjectOrMode(), "boss UUID");
                requireIdentifier(body.visualId(), "boss visual ID");
                requireEmptyNumeric(body, "boss bind");
                requireEmptyControlAndClear(body);
            }
            case END_BOSS_UNBIND -> {
                requireIdentifier(body.instanceId(), "boss instance");
                requireEmptyExceptInstance(body);
            }
            case END_BOSS_PHASE -> {
                requireIdentifier(body.instanceId(), "boss instance");
                requireCanonicalUuid(body.subjectOrMode(), "boss UUID");
                if (!BOSS_PHASES.contains(body.visualId())) {
                    throw new IllegalArgumentException("Unknown End Boss phase");
                }
                requireDurationOnly(body);
            }
            case END_BOSS_BAR -> {
                requireIdentifier(body.instanceId(), "boss instance");
                requireCanonicalUuid(body.subjectOrMode(), "boss UUID");
                requireBossBarVisual(body.visualId());
                if (body.maxHealth() <= 0 || body.health() > body.maxHealth()) {
                    throw new IllegalArgumentException("Invalid End Boss bar health");
                }
                requireEmptyControlAndClear(body);
            }
            case END_ENTITY_BIND -> {
                requireIdentifier(body.instanceId(), "entity instance");
                requireCanonicalUuid(body.subjectOrMode(), "entity UUID");
                requireIdentifier(body.visualId(), "entity visual ID");
                requireEmptyNumeric(body, "entity bind");
                requireEmptyControlAndClear(body);
            }
            case END_ENTITY_PHASE -> {
                requireIdentifier(body.instanceId(), "entity instance");
                requireCanonicalUuid(body.subjectOrMode(), "entity UUID");
                requireEntityPhase(body.visualId());
                requireDurationOnly(body);
            }
            case END_ENTITY_UNBIND -> {
                requireIdentifier(body.instanceId(), "entity instance");
                requireCanonicalUuid(body.subjectOrMode(), "entity UUID");
                requireEmptyExceptInstanceAndSubject(body);
            }
            case END_CONTROL_START -> validateControlStart(body);
            case END_CONTROL_STOP -> {
                requireCanonicalUuid(body.instanceId(), "control lease UUID");
                requireEmptyExceptInstance(body);
            }
            case END_PLAYER_STATE -> validatePlayerState(body);
            case END_FOG_STATE -> validateFogState(body);
            case END_PRESENTATION_RESUME -> {
                if (!"PRESENTATION_RESUME_V1".equals(body.visualId())) {
                    throw new IllegalArgumentException("Unknown presentation-resume token");
                }
                requireDimension(body.subjectOrMode());
                requireEmptyExceptVisualAndSubject(body);
            }
            case END_PRISONER_RESUME -> {
                requireCanonicalUuid(body.instanceId(), "prisoner recipient UUID");
                if (!"PRISONER_SESSION_V1".equals(body.visualId())) {
                    throw new IllegalArgumentException("Unknown prisoner-resume token");
                }
                requireDimension(body.subjectOrMode());
                requireEmptyExceptInstanceVisualAndSubject(body);
                requireRecipient(body.instanceId(), envelopeRecipient);
            }
            case END_PRISONER_STATE -> validatePrisonerState(body, envelopeRecipient);
            case END_PRISONER_CLEAR -> {
                requireCanonicalUuid(body.instanceId(), "prisoner recipient UUID");
                requireEmptyExceptInstance(body);
                requireRecipient(body.instanceId(), envelopeRecipient);
            }
            case END_WORLD_BEAM -> validateWorldBeam(body);
            case END_WORLD_VFX_CLEAR -> {
                requireWorldBeamInstance(body);
                if (!"RIFT_BEAM".equals(body.visualId())) {
                    throw new IllegalArgumentException("Unknown world VFX clear token");
                }
                requireEmptyExceptInstanceAndVisual(body);
            }
            case END_ECHO_BIND, END_ECHO_STATE, END_ECHO_REMOVE -> validateEcho(body);
        }
    }

    private static void validateContextBind(Body body) {
        requireEmptyExceptSubject(body);
        String[] parts = body.subjectOrMode().split("\\|", -1);
        if (parts.length != 3 || !END_DIMENSIONS.contains(parts[0])) {
            throw new IllegalArgumentException("Invalid End Event context dimension or bounds");
        }
        String[] min = parts[1].split(",", -1);
        String[] max = parts[2].split(",", -1);
        if (min.length != 3 || max.length != 3) {
            throw new IllegalArgumentException("Invalid End Event context AABB");
        }
        long[] minima = new long[3];
        long[] maxima = new long[3];
        for (int index = 0; index < 3; index++) {
            minima[index] = parseCanonicalSignedInteger(min[index]);
            maxima[index] = parseCanonicalSignedInteger(max[index]);
            if (minima[index] > maxima[index]) {
                throw new IllegalArgumentException("Inverted End Event context AABB");
            }
            long coordinateMin = index == 1 ? -4_096 : -29_999_998;
            long coordinateMax = index == 1 ? 4_096 : 29_999_998;
            if (minima[index] < coordinateMin || maxima[index] > coordinateMax) {
                throw new IllegalArgumentException("End Event context coordinate exceeds its bounds");
            }
        }
        long[] span = new long[3];
        try {
            for (int index = 0; index < 3; index++) {
                span[index] = Math.addExact(Math.subtractExact(maxima[index], minima[index]), 1L);
                if (span[index] <= 0) {
                    throw new IllegalArgumentException("Invalid End Event context span");
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("End Event context span overflow", exception);
        }
        final long maxVolume = 262_144L;
        if (span[0] > maxVolume / span[1]) {
            throw new IllegalArgumentException("End Event context volume exceeds limit");
        }
        long xy = span[0] * span[1];
        if (xy > maxVolume / span[2] || xy * span[2] <= 0) {
            throw new IllegalArgumentException("End Event context volume exceeds limit");
        }
    }

    private static void validateControlStart(Body body) {
        requireCanonicalUuid(body.instanceId(), "control lease UUID");
        if (!body.controlId().equals("REVERSE") && !body.controlId().equals("SWAP")) {
            throw new IllegalArgumentException("Unknown control lease type");
        }
        if (body.durationMillis() < 1 || body.durationMillis() > MAX_DURATION_MILLIS
                || !body.visualId().isEmpty() || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0 || !body.clearPolicy().isEmpty()
                || !body.status().isEmpty()) {
            throw new IllegalArgumentException("Invalid control-start fields");
        }
        if (body.controlId().equals("REVERSE")) {
            if (!body.subjectOrMode().isEmpty()) {
                throw new IllegalArgumentException("REVERSE does not carry a target UUID");
            }
        } else {
            requireCanonicalUuid(body.subjectOrMode(), "SWAP target UUID");
        }
    }

    private static void validatePlayerState(Body body) {
        if (!body.visualId().equals("WAVE1_CARRIER_V1") && !body.visualId().equals("WAVE2_HUNT_MARK_V1")) {
            throw new IllegalArgumentException("Unknown End Event player-state visual");
        }
        requireIdentifier(body.instanceId(), "player-state instance");
        requireWaveSubject(body.subjectOrMode(), body.visualId().equals("WAVE1_CARRIER_V1") ? 1 : 2);
        if (!body.controlId().isEmpty() || !body.clearPolicy().isEmpty()
                || body.health() != 0 || body.maxHealth() != 0
                || (!body.status().equals("ACTIVE") && !body.status().equals("CLEAR"))) {
            throw new IllegalArgumentException("Invalid End Event player-state fields");
        }
    }

    private static void validateFogState(Body body) {
        if (!body.instanceId().isEmpty() || !body.controlId().isEmpty()
                || !body.visualId().equals("WAVE5_FOG_V1") || body.health() != 0 || body.maxHealth() != 0
                || !body.clearPolicy().isEmpty()) {
            throw new IllegalArgumentException("Invalid End Event fog fields");
        }
        if (body.status().equals("ACTIVE")) {
            requireWaveSubject(body.subjectOrMode(), 5);
        } else if (body.status().equals("CLEAR") || body.status().equals("SUSPEND")) {
            if (!body.subjectOrMode().isEmpty() || body.durationMillis() != 0
                    || body.progressOrIntensity() != 0.0f) {
                throw new IllegalArgumentException("CLEAR/SUSPEND fog state carries active fields");
            }
        } else {
            throw new IllegalArgumentException("Unknown End Event fog status");
        }
    }

    private static void validatePrisonerState(Body body, UUID envelopeRecipient) {
        requireCanonicalUuid(body.instanceId(), "prisoner recipient UUID");
        if (!"PRISONER_V1".equals(body.visualId())) {
            throw new IllegalArgumentException("Unknown prisoner-state token");
        }
        parseCanonicalDecimal(body.subjectOrMode(), false, "prisoner death count");
        parseCooldowns(body.clearPolicy());
        parsePrisonerTargets(body.status());
        if (!body.controlId().isEmpty() || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0) {
            throw new IllegalArgumentException("Invalid prisoner-state fields");
        }
        requireRecipient(body.instanceId(), envelopeRecipient);
    }

    private static void validateWorldBeam(Body body) {
        requireWorldBeamInstance(body);
        if (!"RIFT_BEAM".equals(body.visualId()) || body.durationMillis() < 1 || body.durationMillis() > 2_000
                || body.progressOrIntensity() < 0.02f || body.progressOrIntensity() > 0.45f
                || body.health() != 0 || body.maxHealth() != 0 || !body.controlId().isEmpty()
                || !body.status().isEmpty()) {
            throw new IllegalArgumentException("Invalid End Event world beam fields");
        }
        String[] subject = body.subjectOrMode().split("\\|", -1);
        if (subject.length != 3 || !END_DIMENSIONS.contains(subject[0]) || !WORLD_KEY.matcher(subject[1]).matches()) {
            throw new IllegalArgumentException("Invalid End Event world beam start");
        }
        if (!worldVfxKey(body).equals(subject[1])) {
            throw new IllegalArgumentException("World beam key does not match its instance ID");
        }
        validatePoint(subject[2]);
        String[] clear = body.clearPolicy().split("\\|", -1);
        if (clear.length != 2 || !clear[1].matches("[0-9A-F]{6}")) {
            throw new IllegalArgumentException("Invalid End Event world beam endpoint or color");
        }
        validatePoint(clear[0]);
    }

    private static void validateEcho(Body body) {
        requireIdentifier(body.instanceId(), "Echo duel instance");
        if (!"END_RIFT_ECHO_V1".equals(body.visualId())) {
            throw new IllegalArgumentException("Unknown Echo probe token");
        }
        UUID actor = requireCanonicalUuid(body.subjectOrMode(), "Echo actor UUID");
        EchoFrame frame = parseEchoFrame(body.clearPolicy());
        if (frame.ownerUuid().equals(actor)) {
            throw new IllegalArgumentException("Echo owner and actor must differ");
        }
        if (!RESOURCE_LOCATION.matcher(body.status()).matches() || body.status().length() > 64) {
            throw new IllegalArgumentException("Invalid Echo frame dimension");
        }
        if (!body.controlId().isEmpty() || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0) {
            throw new IllegalArgumentException("Invalid Echo presentation fields");
        }
    }

    private static void requireWorldBeamInstance(Body body) {
        if (body.instanceId().length() > MAX_IDENTIFIER_BYTES) {
            throw new IllegalArgumentException("World VFX instance is too long");
        }
        worldVfxKey(body);
    }

    private static String worldVfxKey(Body body) {
        String prefix = body.eventUuid() + ":" + body.generation() + ":world:";
        if (!body.instanceId().startsWith(prefix)) {
            throw new IllegalArgumentException("World VFX instance is outside its event generation");
        }
        String key = body.instanceId().substring(prefix.length());
        if (!WORLD_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid world VFX key");
        }
        return key;
    }

    private static void validatePoint(String point) {
        String[] coordinates = point.split(",", -1);
        if (coordinates.length != 3) {
            throw new IllegalArgumentException("World beam point must have three coordinates");
        }
        BigDecimal[] values = new BigDecimal[3];
        for (int index = 0; index < 3; index++) {
            String value = coordinates[index];
            if (!value.matches("-?(?:0|[1-9][0-9]*)\\.[0-9]{3}") || value.startsWith("-0.000")) {
                throw new IllegalArgumentException("Noncanonical world beam coordinate");
            }
            values[index] = new BigDecimal(value);
        }
        if (values[0].abs().compareTo(BigDecimal.valueOf(30_000_000L)) > 0
                || values[2].abs().compareTo(BigDecimal.valueOf(30_000_000L)) > 0
                || values[1].compareTo(BigDecimal.valueOf(-4_096L)) < 0
                || values[1].compareTo(BigDecimal.valueOf(4_097L)) >= 0) {
            throw new IllegalArgumentException("World beam point outside global coordinate limits");
        }
    }

    private static EchoFrame parseEchoFrame(String text) {
        String[] fields = text.split("\\|", -1);
        if (fields.length != 12) {
            throw new IllegalArgumentException("Malformed Echo frame");
        }
        UUID owner = requireCanonicalUuid(fields[0], "Echo owner UUID");
        long epoch = parseCanonicalDecimal(fields[1], true, "Echo epoch");
        long frameSequence = parseCanonicalDecimal(fields[2], true, "Echo frame sequence");
        if (!fields[3].equals("STANDING") && !fields[3].equals("CROUCHING")) {
            throw new IllegalArgumentException("Invalid Echo pose");
        }
        if (!fields[4].equals("0") && !fields[4].equals("1")) {
            throw new IllegalArgumentException("Invalid Echo sprinting flag");
        }
        if (!fields[5].equals("NONE") && !fields[5].equals("MAIN") && !fields[5].equals("OFF")) {
            throw new IllegalArgumentException("Invalid Echo hand");
        }
        long useElapsed = parseCanonicalDecimal(fields[6], false, "Echo use elapsed");
        long useDuration = parseCanonicalDecimal(fields[7], false, "Echo use duration");
        long swing = parseCanonicalDecimal(fields[8], false, "Echo swing serial");
        long hurt = parseCanonicalDecimal(fields[9], false, "Echo hurt serial");
        long death = parseCanonicalDecimal(fields[10], false, "Echo death ticks");
        long equipment = parseCanonicalDecimal(fields[11], false, "Echo equipment version");
        if (useDuration > 72_000 || useElapsed > useDuration || (fields[5].equals("NONE") && useDuration != 0)
                || death > 20 || equipment > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Echo frame counter is outside its bounds");
        }
        return new EchoFrame(owner, epoch, frameSequence);
    }

    private static void parseCooldowns(String value) {
        String[] values = value.split("\\|", -1);
        if (values.length != 4) {
            throw new IllegalArgumentException("Prisoner cooldown state must have four fields");
        }
        for (String cooldown : values) {
            parseCanonicalDecimal(cooldown, false, "prisoner cooldown");
        }
    }

    private static void parsePrisonerTargets(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3 || !parts[0].equals("PRISONER_TARGETS_V1")) {
            throw new IllegalArgumentException("Malformed prisoner target lists");
        }
        parseUuidList(parts[1]);
        parseUuidList(parts[2]);
    }

    private static List<UUID> parseUuidList(String value) {
        if (value.isEmpty()) {
            return List.of();
        }
        String[] entries = value.split(",", -1);
        if (entries.length > 32) {
            throw new IllegalArgumentException("Prisoner target list exceeds its item cap");
        }
        List<UUID> values = new ArrayList<>(entries.length);
        Set<UUID> unique = new HashSet<>();
        for (String entry : entries) {
            UUID uuid = requireCanonicalUuid(entry, "prisoner target UUID");
            if (!unique.add(uuid)) {
                throw new IllegalArgumentException("Duplicate prisoner target UUID");
            }
            values.add(uuid);
        }
        return values;
    }

    private static void requireWaveSubject(String value, int expectedWave) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3 || !END_DIMENSIONS.contains(parts[0])
                || !parts[1].equals("wave" + expectedWave)) {
            throw new IllegalArgumentException("Invalid wave visual context");
        }
        parseCanonicalDecimal(parts[2], true, "wave cycle");
    }

    private static void requireBossBarVisual(String visual) {
        String[] parts = visual.split("\\|", -1);
        Set<String> phases = Set.of("AWAKENING", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL");
        Set<String> casts = Set.of("NONE", "TELEGRAPHING", "EXECUTING", "RECOVERY");
        if (parts.length != 2 || !phases.contains(parts[0]) || !casts.contains(parts[1])) {
            throw new IllegalArgumentException("Invalid End Boss bar phase/cast state");
        }
    }

    private static void requireEntityPhase(String visual) {
        if (BOSS_PHASES.contains(visual) || WAVE_POSES.contains(visual) || RITUAL_PHASES.contains(visual)) {
            return;
        }
        String[] parts = visual.split("\\|", -1);
        if (parts.length < 3 || parts.length > 4 || !TENTACLE_PHASES.contains(parts[0])
                || !parts[1].matches("t=(?:0|[1-9][0-9]*)")
                || !parts[2].matches("health=(?:FULL|DAMAGED|CRITICAL|DEAD)")) {
            throw new IllegalArgumentException("Unknown End Event entity phase");
        }
        if (parts.length == 4) {
            if (!parts[3].startsWith("target=")) {
                throw new IllegalArgumentException("Malformed tentacle target");
            }
            requireCanonicalUuid(parts[3].substring("target=".length()), "tentacle target UUID");
        }
    }

    private static void requireEmptyDefaults(Body body) {
        if (!body.instanceId().isEmpty() || !body.controlId().isEmpty() || !body.visualId().isEmpty()
                || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0 || !body.clearPolicy().isEmpty()
                || !body.status().isEmpty() || !body.subjectOrMode().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static boolean isNegativeZero(float value) {
        return value == 0.0f && Float.floatToRawIntBits(value) != 0;
    }

    private static void requireEmptyExceptInstance(Body body) {
        if (!body.controlId().isEmpty() || !body.visualId().isEmpty() || body.durationMillis() != 0
                || body.progressOrIntensity() != 0.0f || body.health() != 0 || body.maxHealth() != 0
                || !body.subjectOrMode().isEmpty() || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyExceptInstanceAndSubject(Body body) {
        if (!body.controlId().isEmpty() || !body.visualId().isEmpty() || body.durationMillis() != 0
                || body.progressOrIntensity() != 0.0f || body.health() != 0 || body.maxHealth() != 0
                || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyExceptInstanceAndVisual(Body body) {
        if (!body.controlId().isEmpty() || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0 || !body.subjectOrMode().isEmpty()
                || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyExceptSubject(Body body) {
        if (!body.instanceId().isEmpty() || !body.controlId().isEmpty() || !body.visualId().isEmpty()
                || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f || body.health() != 0
                || body.maxHealth() != 0 || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyExceptVisualAndSubject(Body body) {
        if (!body.instanceId().isEmpty() || !body.controlId().isEmpty() || body.durationMillis() != 0
                || body.progressOrIntensity() != 0.0f || body.health() != 0 || body.maxHealth() != 0
                || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyExceptInstanceVisualAndSubject(Body body) {
        if (!body.controlId().isEmpty() || body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f
                || body.health() != 0 || body.maxHealth() != 0 || !body.clearPolicy().isEmpty()
                || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireDurationOnly(Body body) {
        if (!body.controlId().isEmpty() || body.progressOrIntensity() != 0.0f || body.health() != 0
                || body.maxHealth() != 0 || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireEmptyNumeric(Body body, String field) {
        if (body.durationMillis() != 0 || body.progressOrIntensity() != 0.0f || body.health() != 0
                || body.maxHealth() != 0) {
            throw new IllegalArgumentException("Invalid " + field + " numeric fields");
        }
    }

    private static void requireEmptyControlAndClear(Body body) {
        if (!body.controlId().isEmpty() || !body.clearPolicy().isEmpty() || !body.status().isEmpty()) {
            throw new IllegalArgumentException("End Event body contains non-default fields");
        }
    }

    private static void requireIdentifier(String value, String fieldName) {
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }

    private static UUID requireCanonicalUuid(String value, String fieldName) {
        if (value == null || value.length() != 36) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("Noncanonical " + fieldName);
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName, exception);
        }
    }

    private static void requireRecipient(String instanceId, UUID recipient) {
        if (recipient != null && !requireCanonicalUuid(instanceId, "prisoner recipient UUID").equals(recipient)) {
            throw new IllegalArgumentException("Prisoner body recipient does not match envelope recipient");
        }
    }

    private static void requireDimension(String value) {
        if (!END_DIMENSIONS.contains(value)) {
            throw new IllegalArgumentException("Invalid End Event dimension");
        }
    }

    private static long parseCanonicalSignedInteger(String value) {
        if (value == null || !value.matches("(?:0|-[1-9][0-9]*|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Noncanonical signed integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Signed integer out of range", exception);
        }
    }

    private static long parseCanonicalDecimal(String value, boolean positive, String fieldName) {
        Pattern pattern = positive ? CANONICAL_POSITIVE : CANONICAL_NONNEGATIVE;
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Noncanonical " + fieldName);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " out of range", exception);
        }
    }

    private static long parseLong(String value) {
        return Long.parseLong(value);
    }

    private static void requireString(String value, int maximumBytes, String fieldName) {
        if (value == null || value.length() > maximumBytes) {
            throw new IllegalArgumentException("Invalid " + fieldName + " length");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException(fieldName + " must be printable ASCII");
            }
        }
    }

    private static void writeString(DataOutputStream output, String value, int maximumBytes, String fieldName)
            throws IOException {
        requireString(value, maximumBytes, fieldName);
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximumBytes, String fieldName) throws IOException {
        int length = input.readUnsignedShort();
        if (length > maximumBytes) {
            throw new IllegalArgumentException("Overlong " + fieldName);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated " + fieldName);
        }
        for (byte value : bytes) {
            int character = Byte.toUnsignedInt(value);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException(fieldName + " must be printable ASCII");
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void writeUInt32(DataOutputStream output, long value, String fieldName) throws IOException {
        if (value < 0 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        output.writeInt((int) value);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private record EchoFrame(UUID ownerUuid, long epoch, long frameSequence) {
    }

    public record Body(Type type, long generation, long bodyTimestampMillis, UUID eventUuid,
            String instanceId, String controlId, String visualId, long durationMillis,
            float progressOrIntensity, long health, long maxHealth, String subjectOrMode,
            String clearPolicy, String reservedOne, String reservedTwo, String status) {
    }

    public record ArenaBounds(UUID eventUuid, long generation, String dimension, long minX, long minY,
            long minZ, long maxX, long maxY, long maxZ) {
    }

    public enum Type {
        CONTEXT_BIND(1),
        CONTEXT_UNBIND(2),
        END_BOSS_BIND(10),
        END_BOSS_UNBIND(11),
        END_BOSS_PHASE(12),
        END_BOSS_BAR(13),
        END_ENTITY_BIND(20),
        END_ENTITY_PHASE(21),
        END_ENTITY_UNBIND(22),
        END_CONTROL_START(30),
        END_CONTROL_STOP(31),
        END_PLAYER_STATE(40),
        END_FOG_STATE(41),
        END_PRESENTATION_RESUME(42),
        END_PRISONER_RESUME(50),
        END_PRISONER_STATE(51),
        END_PRISONER_CLEAR(52),
        END_WORLD_BEAM(60),
        END_WORLD_VFX_CLEAR(61),
        END_ECHO_BIND(70),
        END_ECHO_STATE(71),
        END_ECHO_REMOVE(72);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        private static Type fromCode(int code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown End Event bridge body type");
        }
    }
}
