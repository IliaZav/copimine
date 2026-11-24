import me.copimine.endevent.domain.SpellCastPacket;
import me.copimine.endevent.domain.SpellCastPacket.Lane;
import me.copimine.endevent.domain.SpellCastPacket.Phase;
import me.copimine.endevent.domain.SpellCastPacketCodec;

import java.util.List;

/**
 * N23-P1-07: the bounded wire codec for one authored spell cast.
 *
 * <p>V6 asks for a small signed cast-start/stage/cancel packet. These checks pin
 * the parts that must hold without a live server: an exact round-trip for valid
 * packets, a hard byte ceiling, and a decode that rejects a hostile length,
 * magic, version, phase or lane count instead of trusting it. Signing is the
 * bridge's job; this codec only owns the body.</p>
 */
public final class SpellCastPacketCodecTest {

    public static void main(String[] args) {
        roundTripsAStartedCastWithLanes();
        roundTripsACancelWithNoLanes();
        staysWithinTheByteBudget();
        clampsCosmeticFields();
        rejectsMalformedInput();
        System.out.println("SpellCastPacketCodecTest OK");
    }

    private static SpellCastPacket sample(Phase phase, List<Lane> lanes) {
        return new SpellCastPacket(phase, 4242, "guardian_chest_beam", 0x5EEDL, 120, 40, 2, 3, 1,
                100.5F, 64.0F, -220.5F, 137.5F, lanes);
    }

    private static void roundTripsAStartedCastWithLanes() {
        SpellCastPacket original = sample(Phase.START, List.of(
                new Lane(0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.7F, 26.0F),
                new Lane(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.7F, 26.0F),
                new Lane(0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.5F, 1.7F, 26.0F)));
        SpellCastPacket decoded = SpellCastPacketCodec.decode(SpellCastPacketCodec.encode(original));
        check(decoded.equals(original), "a valid START packet round-trips exactly");
        check(decoded.lanes().size() == 3, "all three locked lanes survive the round-trip");
    }

    private static void roundTripsACancelWithNoLanes() {
        SpellCastPacket original = sample(Phase.CANCEL, List.of());
        SpellCastPacket decoded = SpellCastPacketCodec.decode(SpellCastPacketCodec.encode(original));
        check(decoded.equals(original), "a lane-free CANCEL round-trips exactly");
        check(decoded.lanes().isEmpty(), "a cancel carries no lanes");
        check(decoded.phase() == Phase.CANCEL, "the phase survives the round-trip");
    }

    private static void staysWithinTheByteBudget() {
        StringBuilder id = new StringBuilder();
        while (id.length() < SpellCastPacketCodec.MAX_SPELL_ID_BYTES) {
            id.append('x');
        }
        SpellCastPacket widest = new SpellCastPacket(Phase.START, Integer.MAX_VALUE, id.toString(),
                Long.MAX_VALUE, Integer.MAX_VALUE, 600, 255, 15, 2, 1.0F, 2.0F, 3.0F, 4.0F, List.of(
                        new Lane(1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F),
                        new Lane(1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F),
                        new Lane(1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F)));
        int size = SpellCastPacketCodec.encodedSize(widest);
        check(size <= SpellCastPacketCodec.MAX_BYTES,
                "the widest authored packet fits the byte budget");
        check(size == SpellCastPacketCodec.encode(widest).length,
                "encodedSize agrees with encode");
        SpellCastPacket decoded = SpellCastPacketCodec.decode(SpellCastPacketCodec.encode(widest));
        check(decoded.equals(widest), "the widest packet still round-trips");
    }

    private static void clampsCosmeticFields() {
        SpellCastPacket loose = new SpellCastPacket(Phase.STAGE, 1, "  spell  ", 0L, 0, 9999, 999, 99, 9,
                0.0F, 0.0F, 0.0F, 0.0F, null);
        check(loose.durationTicks() == 600, "duration is clamped to its ceiling");
        check(loose.stage() == 255, "stage is clamped to its byte ceiling");
        check(loose.style() == 15, "style is clamped to its ceiling");
        check(loose.vfxLod() == 2, "vfx lod is clamped to its ceiling");
        check(loose.spellId().equals("spell"), "a spell id is trimmed");
        check(loose.lanes().isEmpty(), "a null lane array becomes empty, never NPE");
        SpellCastPacket decoded = SpellCastPacketCodec.decode(SpellCastPacketCodec.encode(loose));
        check(decoded.equals(loose), "the clamped packet round-trips");
    }

    private static void rejectsMalformedInput() {
        byte[] good = SpellCastPacketCodec.encode(sample(Phase.START, List.of(
                new Lane(0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.7F, 26.0F))));

        StringBuilder longId = new StringBuilder();
        while (longId.length() <= SpellCastPacketCodec.MAX_SPELL_ID_BYTES) {
            longId.append('y');
        }
        expectRejected(() -> SpellCastPacketCodec.encode(null), "a null packet is rejected");
        expectRejected(() -> SpellCastPacketCodec.encode(new SpellCastPacket(
                Phase.START, 1, longId.toString(), 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of())),
                "an over-long spell id is rejected");
        expectRejected(() -> SpellCastPacketCodec.encode(new SpellCastPacket(
                Phase.START, 1, "s", 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(
                        lane(), lane(), lane(), lane()))),
                "a fourth lane is rejected");

        expectRejected(() -> SpellCastPacketCodec.decode(null), "a null frame is rejected");
        expectRejected(() -> SpellCastPacketCodec.decode(new byte[2]), "a tiny frame is rejected");
        expectRejected(() -> SpellCastPacketCodec.decode(new byte[SpellCastPacketCodec.MAX_BYTES + 1]),
                "an over-long frame is rejected");

        byte[] badMagic = good.clone();
        badMagic[0] = 0x00;
        expectRejected(() -> SpellCastPacketCodec.decode(badMagic), "a bad magic is rejected");
        byte[] badVersion = good.clone();
        badVersion[1] = 0x7F;
        expectRejected(() -> SpellCastPacketCodec.decode(badVersion), "a bad version is rejected");
        byte[] badPhase = good.clone();
        badPhase[2] = 0x7F;
        expectRejected(() -> SpellCastPacketCodec.decode(badPhase), "a bad phase is rejected");
        byte[] badLanes = good.clone();
        int idLength = good[24] & 0xFF;
        badLanes[41 + idLength] = 0x05;
        expectRejected(() -> SpellCastPacketCodec.decode(badLanes), "an over-count lane field is rejected");
        byte[] truncated = new byte[good.length - 4];
        System.arraycopy(good, 0, truncated, 0, truncated.length);
        expectRejected(() -> SpellCastPacketCodec.decode(truncated), "a truncated frame is rejected");
        check(SpellCastPacketCodec.encode(new SpellCastPacket(
                Phase.START, 1, "z", 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of())).length > 0,
                "a minimal valid packet still encodes");
    }

    private static Lane lane() {
        return new Lane(0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    private static void expectRejected(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("FAIL: " + message + " but nothing was thrown");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
