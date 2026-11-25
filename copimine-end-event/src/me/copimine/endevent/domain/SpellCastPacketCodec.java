package me.copimine.endevent.domain;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * N23-P1-07: the bounded wire codec for {@link SpellCastPacket}.
 *
 * <p>The packet is deliberately tiny: a fixed header, a capped spell id, one
 * quantised origin/yaw, and at most three frozen lanes. {@link #MAX_BYTES} is a
 * hard contract - a producer that cannot encode inside it throws instead of
 * shipping an unbounded frame - and {@link #decode} rejects anything malformed
 * rather than trusting a hostile length. This is the "small signed packet"
 * V6 asks for; the existing bridge signs the bytes this returns.</p>
 *
 * <p>{@code encode}/{@code decode} are pure and total over valid input, so the
 * same bytes round-trip identically on the server and the client.</p>
 */
public final class SpellCastPacketCodec {
    /** First body byte; a cheap framing guard. */
    public static final byte MAGIC = 0x43; // 'C'
    /** Current wire revision. */
    public static final byte VERSION = 1;
    /** Hard ceiling on one encoded cast packet. */
    public static final int MAX_BYTES = 256;
    /** Hard ceiling on the UTF-8 spell id. */
    public static final int MAX_SPELL_ID_BYTES = 32;
    /** Hard ceiling on frozen lanes. */
    public static final int MAX_LANES = 3;
    /** Bytes a lane always occupies on the wire. */
    private static final int LANE_BYTES = 32;
    private static final byte POINT_VERSION = 2;
    private static final int POINT_BYTES = 12;
    public static final int MAX_FANG_POINTS = 15;
    /** Bytes a minimal (no-id-length-yet) header plus trailer needs. */
    private static final int MIN_BYTES = 1 + 1 + 1 + 1 + 1 + 1 + 4 + 8 + 4 + 2 + 1 + 16 + 1;

    private SpellCastPacketCodec() {
    }

    /** The exact byte length {@link #encode} would produce; useful for budget logs. */
    public static int encodedSize(SpellCastPacket packet) {
        return encode(packet).length;
    }

    public static byte[] encode(SpellCastPacket packet) {
        if (packet == null) {
            throw new IllegalArgumentException("a null packet cannot be encoded");
        }
        byte[] id = packet.spellId().getBytes(StandardCharsets.UTF_8);
        if (id.length > MAX_SPELL_ID_BYTES) {
            throw new IllegalArgumentException("spell id exceeds " + MAX_SPELL_ID_BYTES + " bytes");
        }
        boolean points = isFangGeometry(packet.spellId());
        if (packet.lanes().size() > (points ? MAX_FANG_POINTS : MAX_LANES)) {
            throw new IllegalArgumentException("too many spell geometry elements");
        }
        validateGeometry(packet);
        ByteBuffer buffer = ByteBuffer.allocate(MAX_BYTES);
        buffer.put(MAGIC);
        buffer.put(points ? POINT_VERSION : VERSION);
        buffer.put((byte) packet.phase().ordinal());
        buffer.put((byte) packet.vfxLod());
        buffer.put((byte) packet.style());
        buffer.put((byte) packet.stage());
        buffer.putInt(packet.generation());
        buffer.putLong(packet.seed());
        buffer.putInt(packet.startTick());
        buffer.putShort((short) packet.durationTicks());
        buffer.put((byte) id.length);
        buffer.put(id);
        buffer.putFloat(packet.x());
        buffer.putFloat(packet.y());
        buffer.putFloat(packet.z());
        buffer.putFloat(packet.yaw());
        buffer.put((byte) packet.lanes().size());
        for (SpellCastPacket.Lane lane : packet.lanes()) {
            buffer.putFloat(lane.x());
            buffer.putFloat(lane.y());
            buffer.putFloat(lane.z());
            if (points) continue;
            buffer.putFloat(lane.dx());
            buffer.putFloat(lane.dy());
            buffer.putFloat(lane.dz());
            buffer.putFloat(lane.radius());
            buffer.putFloat(lane.length());
        }
        byte[] out = new byte[buffer.position()];
        buffer.flip();
        buffer.get(out);
        if (out.length > MAX_BYTES) {
            throw new IllegalStateException("encoded cast packet exceeds " + MAX_BYTES + " bytes");
        }
        return out;
    }

    public static SpellCastPacket decode(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("a null packet cannot be decoded");
        }
        if (bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("cast packet length out of range: " + bytes.length);
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (buffer.get() != MAGIC) {
                throw new IllegalArgumentException("bad cast packet magic");
            }
            byte schema = buffer.get();
            boolean points = schema == POINT_VERSION;
            if (!points && schema != VERSION) {
                throw new IllegalArgumentException("unsupported cast packet version");
            }
            int phaseOrdinal = buffer.get() & 0xFF;
            SpellCastPacket.Phase[] phases = SpellCastPacket.Phase.values();
            if (phaseOrdinal >= phases.length) {
                throw new IllegalArgumentException("bad cast packet phase: " + phaseOrdinal);
            }
            int vfxLod = buffer.get() & 0xFF;
            int style = buffer.get() & 0xFF;
            int stage = buffer.get() & 0xFF;
            int generation = buffer.getInt();
            long seed = buffer.getLong();
            int startTick = buffer.getInt();
            int durationTicks = buffer.getShort() & 0xFFFF;
            int idLength = buffer.get() & 0xFF;
            if (idLength > MAX_SPELL_ID_BYTES || buffer.remaining() < idLength) {
                throw new IllegalArgumentException("bad cast packet spell id length: " + idLength);
            }
            byte[] id = new byte[idLength];
            buffer.get(id);
            String spellId = new String(id, StandardCharsets.UTF_8);
            float x = buffer.getFloat();
            float y = buffer.getFloat();
            float z = buffer.getFloat();
            float yaw = buffer.getFloat();
            int laneCount = buffer.get() & 0xFF;
            if (points != isFangGeometry(spellId)
                    || laneCount > (points ? MAX_FANG_POINTS : MAX_LANES)
                    || buffer.remaining() != laneCount * (points ? POINT_BYTES : LANE_BYTES)) {
                throw new IllegalArgumentException("bad cast geometry length");
            }
            List<SpellCastPacket.Lane> lanes = new ArrayList<>(laneCount);
            for (int i = 0; i < laneCount; i++) {
                float lx = buffer.getFloat();
                float ly = buffer.getFloat();
                float lz = buffer.getFloat();
                lanes.add(points ? new SpellCastPacket.Lane(lx, ly, lz, 0, 1, 0, 0.5f, 0)
                        : new SpellCastPacket.Lane(lx, ly, lz, buffer.getFloat(), buffer.getFloat(),
                                buffer.getFloat(), buffer.getFloat(), buffer.getFloat()));
            }
            SpellCastPacket packet = new SpellCastPacket(phases[phaseOrdinal], generation, spellId,
                    seed, startTick, durationTicks, stage, style, vfxLod, x, y, z, yaw, lanes);
            validateGeometry(packet);
            return packet;
        } catch (BufferUnderflowException underflow) {
            throw new IllegalArgumentException("truncated cast packet", underflow);
        }
    }

    public static boolean isFangGeometry(String spellId) {
        return "circle".equals(spellId) || "crown".equals(spellId);
    }

    private static void validateGeometry(SpellCastPacket packet) {
        if (!Float.isFinite(packet.x()) || !Float.isFinite(packet.y()) || !Float.isFinite(packet.z())
                || !Float.isFinite(packet.yaw()) || packet.startTick() < 0
                || !packet.spellId().matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("invalid cast origin, tick or spell id");
        }
        for (SpellCastPacket.Lane lane : packet.lanes()) {
            if (!Float.isFinite(lane.x()) || !Float.isFinite(lane.y()) || !Float.isFinite(lane.z())
                    || !Float.isFinite(lane.dx()) || !Float.isFinite(lane.dy()) || !Float.isFinite(lane.dz())
                    || !Float.isFinite(lane.radius()) || !Float.isFinite(lane.length())
                    || lane.radius() < 0 || lane.radius() > 32 || lane.length() < 0 || lane.length() > 256) {
                throw new IllegalArgumentException("invalid cast lane");
            }
            if (isFangGeometry(packet.spellId()) && (lane.length() != 0 || lane.radius() != 0.5f
                    || lane.dx() != 0 || lane.dy() != 1 || lane.dz() != 0)) {
                throw new IllegalArgumentException("fang geometry requires canonical points");
            }
        }
    }

    public static boolean inside(SpellCastPacket packet, long minX, long minY, long minZ,
                                 long maxX, long maxY, long maxZ) {
        if (!insidePoint(packet.x(), packet.y(), packet.z(), minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        for (SpellCastPacket.Lane lane : packet.lanes()) {
            if (!insidePoint(lane.x(), lane.y(), lane.z(), minX, minY, minZ, maxX, maxY, maxZ)
                    || !insidePoint(lane.x() + lane.dx() * lane.length(), lane.y() + lane.dy() * lane.length(),
                            lane.z() + lane.dz() * lane.length(), minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
        }
        return true;
    }

    private static boolean insidePoint(double x, double y, double z, long minX, long minY, long minZ,
                                       long maxX, long maxY, long maxZ) {
        return x >= minX && y >= minY && z >= minZ
                && x <= maxX + 1.0 && y <= maxY + 1.0 && z <= maxZ + 1.0;
    }

}
