import me.copimine.endevent.domain.Phase5EchoCopyPresentationPolicy;
import me.copimine.endevent.domain.PlayerEchoCopyPolicy;
import me.copimine.endevent.domain.wave7.EchoFrameBroadcastPolicy;

/**
 * V6 Phase 5 Echo copies: the two owners must agree on one presentation.
 *
 * <p>The server gives the copy a reserved epoch band; the client turns that band
 * into a translucent player body. A drift between the two would either draw an
 * opaque twin (the very thing the owner rejected) or let a stale Wave 7 epoch
 * refuse every copy. Both are pure arithmetic over wire values, so they are
 * driven here without a server or a client.</p>
 */
public final class Phase5EchoCopyPresentationPolicyTest {
    public static void main(String[] args) {
        alphaMatchesTheOwnedPolicy();
        alphaIsBounded();
        translucentTintPreservesTheBodyColour();
        epochClearsTheWaveSevenScope();
        copyBandIsExactAndExcludesWaveSeven();
        keepaliveFitsTheClientLease();
        descriptionNamesTheCopy();
        System.out.println("Phase5EchoCopyPresentationPolicyTest OK");
    }

    private static void alphaMatchesTheOwnedPolicy() {
        check(Phase5EchoCopyPresentationPolicy.COPY_ALPHA == PlayerEchoCopyPolicy.COPY_ALPHA,
                "the Phase 5 alpha must be the same value the copy policy documents");
        check(Phase5EchoCopyPresentationPolicy.COPY_ALPHA > 0.0D
                        && Phase5EchoCopyPresentationPolicy.COPY_ALPHA < 1.0D,
                "a copy must be visible but never opaque, saw "
                        + Phase5EchoCopyPresentationPolicy.COPY_ALPHA);
        check(PlayerEchoCopyPolicy.isSemiTransparent(),
                "the copy policy must still declare semi-transparency");
    }

    private static void alphaIsBounded() {
        check(Phase5EchoCopyPresentationPolicy.alphaByte(0.55D) == 140,
                "0.55 alpha must quantise to 140, saw "
                        + Phase5EchoCopyPresentationPolicy.alphaByte(0.55D));
        check(Phase5EchoCopyPresentationPolicy.alphaByte(-3.0D) == 0, "negative alpha is invisible");
        check(Phase5EchoCopyPresentationPolicy.alphaByte(9.0D) == 255, "over-bright alpha is opaque");
        check(Phase5EchoCopyPresentationPolicy.alphaByte(Double.NaN) == 0, "NaN alpha fails closed");
    }

    private static void translucentTintPreservesTheBodyColour() {
        // The echo body tint the client already uses for a drained copy.
        int tint = 0xFF946BB8;
        int translated = Phase5EchoCopyPresentationPolicy.translucentTint(tint, 0.55D);
        check((translated >>> 24) == 140, "the alpha byte must be the copy alpha");
        check((translated & 0x00FFFFFF) == (tint & 0x00FFFFFF),
                "translucency must not recolour the authored body tint");
    }

    private static void epochClearsTheWaveSevenScope() {
        long wave7Epoch = 3L;
        long phase5Epoch = Phase5EchoCopyPresentationPolicy.epochFor(7L);
        check(phase5Epoch > wave7Epoch,
                "a Phase 5 bind must outrank a finished Wave 7 duel epoch");
        check(Phase5EchoCopyPresentationPolicy.epochFor(7L)
                        < Phase5EchoCopyPresentationPolicy.epochFor(8L),
                "a newer generation must outrank the previous one");
        check(Phase5EchoCopyPresentationPolicy.epochFor(-5L)
                        == Phase5EchoCopyPresentationPolicy.EPOCH_BASE,
                "an absent generation still yields a valid positive epoch");
    }

    private static void copyBandIsExactAndExcludesWaveSeven() {
        check(Phase5EchoCopyPresentationPolicy.isCopyEpoch(
                        Phase5EchoCopyPresentationPolicy.epochFor(1L)),
                "a copy epoch must be recognised");
        check(Phase5EchoCopyPresentationPolicy.isCopyEpoch(
                        Phase5EchoCopyPresentationPolicy.EPOCH_BASE),
                "the band starts exactly at its base");
        check(!Phase5EchoCopyPresentationPolicy.isCopyEpoch(
                        Phase5EchoCopyPresentationPolicy.EPOCH_BASE - 1L),
                "the epoch below the band is an ordinary Wave 7 echo");
        for (long wave7Epoch : new long[] {1L, 3L, 17L, 4_096L, 999_999L}) {
            check(!Phase5EchoCopyPresentationPolicy.isCopyEpoch(wave7Epoch),
                    "a Wave 7 duel epoch must stay opaque, saw " + wave7Epoch);
        }
    }

    private static void keepaliveFitsTheClientLease() {
        long gap = EchoFrameBroadcastPolicy.KEEPALIVE_TICKS * 50L;
        check(gap < Phase5EchoCopyPresentationPolicy.CLIENT_LEASE_MILLIS,
                "an unchanged copy must refresh inside the two-second lease, gap=" + gap);
        check(gap * 2L <= Phase5EchoCopyPresentationPolicy.CLIENT_LEASE_MILLIS,
                "at least one keepalive may be lost, gap=" + gap);
    }

    private static void descriptionNamesTheCopy() {
        String text = Phase5EchoCopyPresentationPolicy.describe(
                2, "MELEE_SWIPE", Phase5EchoCopyPresentationPolicy.COPY_ALPHA,
                "00000000-0000-0000-0000-00000000000a");
        check(text.contains("index=2"), "the log line must name the copy index");
        check(text.contains("MELEE_SWIPE"), "the log line must name the copied family");
        check(text.contains("alpha=0.55"), "the log line must record the real alpha");
        check(text.contains("epoch_base=" + Phase5EchoCopyPresentationPolicy.EPOCH_BASE),
                "the log line must record the epoch band the client keys on");
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
