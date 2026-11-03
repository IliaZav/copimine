package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable owner of the Wave 6 stage sequence.
 *
 * <p>Spec W6-001/W6-002 [U] and W6-005/W6-006 [A]: exactly one mage and its
 * assigned guards are active at a time. The five identities run in one frozen
 * shuffled order, every stage carries its own ordinary reinforcement pack, and
 * the fifth death releases the prisoner before the final pack resolves.</p>
 *
 * <p>This class is pure state: it owns no entity, task, packet or file. It
 * answers questions ("which mage is next?", "may this guard defeat unshield?",
 * "is the encounter complete?") and returns a new state, so the server adapter
 * can persist and replay it. An inability to continue is always an explicit
 * {@link Blocker} rather than a silent {@code true}.</p>
 */
public final class WaveSixSequencePolicy {
    /** Stage order of an attempt is fixed at five identities. */
    public static final int STAGE_COUNT = MageSignaturePolicy.MAGE_COUNT;

    private final List<MageSignaturePolicy.MageId> order;
    private final int stageIndex;
    private final List<MageSignaturePolicy.MageId> defeated;
    private final boolean prisonBroken;
    private final int guardSlots;
    private final Set<Integer> guardSlotsFilled;

    private WaveSixSequencePolicy(List<MageSignaturePolicy.MageId> order, int stageIndex,
                                  List<MageSignaturePolicy.MageId> defeated, boolean prisonBroken,
                                  int guardSlots, Set<Integer> guardSlotsFilled) {
        this.order = order;
        this.stageIndex = stageIndex;
        this.defeated = defeated;
        this.prisonBroken = prisonBroken;
        this.guardSlots = guardSlots;
        this.guardSlotsFilled = guardSlotsFilled;
    }

    /**
     * Begin a fresh attempt. The caller persists {@code order}; a restart must
     * pass the same list so the run cannot be rerolled.
     *
     * @param order      frozen shuffled identity order (exactly five, distinct)
     * @param guardSlots guards assigned to the <em>current</em> mage (1-5)
     */
    public static WaveSixSequencePolicy start(List<MageSignaturePolicy.MageId> order, int guardSlots) {
        MageSignaturePolicy.requireValidOrder(order);
        if (guardSlots < 1 || guardSlots > RitualSphereScalingPolicy.GUARDS_PER_CASTER) {
            throw new IllegalArgumentException("guard slots must be in [1,5]: " + guardSlots);
        }
        return new WaveSixSequencePolicy(List.copyOf(order), 0, List.of(), false, guardSlots, Set.of());
    }

    /** Restore a persisted attempt. Every argument is revalidated. */
    public static WaveSixSequencePolicy restore(List<MageSignaturePolicy.MageId> order, int stageIndex,
                                               List<MageSignaturePolicy.MageId> defeated,
                                               boolean prisonBroken, int guardSlots,
                                               Set<Integer> guardSlotsFilled) {
        MageSignaturePolicy.requireValidOrder(order);
        if (stageIndex < 0 || stageIndex > STAGE_COUNT) {
            throw new IllegalArgumentException("stage index must be in [0,5]: " + stageIndex);
        }
        if (guardSlots < 1 || guardSlots > RitualSphereScalingPolicy.GUARDS_PER_CASTER) {
            throw new IllegalArgumentException("guard slots must be in [1,5]: " + guardSlots);
        }
        List<MageSignaturePolicy.MageId> defeatedCopy = defeated == null ? List.of() : List.copyOf(defeated);
        if (defeatedCopy.size() != stageIndex) {
            throw new IllegalArgumentException("defeated count must equal the stage index");
        }
        for (int i = 0; i < defeatedCopy.size(); i++) {
            if (!order.get(i).equals(defeatedCopy.get(i))) {
                throw new IllegalArgumentException("defeated prefix must follow the frozen order");
            }
        }
        if (prisonBroken != (stageIndex == STAGE_COUNT)) {
            throw new IllegalArgumentException("the prison breaks only after the fifth death");
        }
        Set<Integer> slots = new LinkedHashSet<>();
        for (int slot : guardSlotsFilled == null ? Set.<Integer>of() : guardSlotsFilled) {
            if (slot < 0 || slot >= guardSlots) {
                throw new IllegalArgumentException("guard slot outside the current roster: " + slot);
            }
            slots.add(slot);
        }
        return new WaveSixSequencePolicy(List.copyOf(order), stageIndex, defeatedCopy, prisonBroken,
                guardSlots, Set.copyOf(slots));
    }

    public List<MageSignaturePolicy.MageId> order() {
        return order;
    }

    public int stageIndex() {
        return stageIndex;
    }

    public List<MageSignaturePolicy.MageId> defeated() {
        return defeated;
    }

    public boolean prisonBroken() {
        return prisonBroken;
    }

    public int guardSlots() {
        return guardSlots;
    }

    public Set<Integer> guardSlotsFilled() {
        return guardSlotsFilled;
    }

    /** True while a mage stage is still running; the final pack uses the fifth state. */
    public boolean mageStageActive() {
        return stageIndex < STAGE_COUNT;
    }

    /** The one mage that may exist right now, or {@code null} after the fifth death. */
    public MageSignaturePolicy.MageId currentMage() {
        return mageStageActive() ? order.get(stageIndex) : null;
    }

    public MageSignaturePolicy.Signature currentSignature() {
        MageSignaturePolicy.MageId current = currentMage();
        return current == null ? null : MageSignaturePolicy.signatureFor(current);
    }

    /** Signatures the sphere owns; grows by identity, never by death ordinal. */
    public List<MageSignaturePolicy.Signature> ownedSignatures() {
        return MageSignaturePolicy.ownedSignatures(defeated);
    }

    /**
     * W6-011 [U]: the shield holds while at least one <em>assigned</em> guard of
     * the current mage is genuinely alive. Optional pressure, summons and other
     * stages' creatures never contribute.
     */
    public boolean shieldHolds(int livingAssignedGuards) {
        if (!mageStageActive()) {
            throw new IllegalStateException("no mage stage is active");
        }
        return livingAssignedGuards > 0;
    }

    /** W6-003 [A]: the same actor switches GUARDED -> EXPOSED when its last guard dies. */
    public Lens lens(int livingAssignedGuards) {
        if (!mageStageActive()) {
            return Lens.COMPLETE;
        }
        return livingAssignedGuards > 0 ? Lens.GUARDED : Lens.EXPOSED;
    }

    /**
     * Record a guard slot of the current stage as filled.
     *
     * <p>Idempotent: an already filled slot returns this same state, so a
     * retried spawn is never counted twice. A slot outside the roster throws
     * instead of silently succeeding.</p>
     */
    public WaveSixSequencePolicy guardFilled(int slot) {
        if (!mageStageActive()) {
            throw new IllegalStateException("no mage stage is active");
        }
        if (slot < 0 || slot >= guardSlots) {
            throw new IllegalArgumentException("guard slot outside the roster: " + slot);
        }
        if (guardSlotsFilled.contains(slot)) {
            return this;
        }
        Set<Integer> next = new LinkedHashSet<>(guardSlotsFilled);
        next.add(slot);
        return new WaveSixSequencePolicy(order, stageIndex, defeated, prisonBroken, guardSlots, Set.copyOf(next));
    }

    /** All assigned guards of the current stage were successfully spawned. */
    public boolean stageRosterComplete() {
        return mageStageActive() && guardSlotsFilled.size() == guardSlots;
    }

    /**
     * W6-002/W6-007 [A]: a legitimate defeat of the current mage advances the
     * stage exactly once. A duplicate, foreign or post-final callback returns a
     * {@link Blocker} and never grants a second signature.
     */
    public Result defeatCurrent(MageSignaturePolicy.MageId mage) {
        if (mage == null) {
            return Result.rejected(Blocker.UNKNOWN_IDENTITY, this);
        }
        if (!mageStageActive()) {
            return Result.rejected(Blocker.SEQUENCE_FINISHED, this);
        }
        if (!order.get(stageIndex).equals(mage)) {
            return Result.rejected(Blocker.NOT_CURRENT_MAGE, this);
        }
        if (!stageRosterComplete()) {
            // A mage cannot be legitimately defeated before its own stage finished
            // spawning; an early defeat would orphan its guards.
            return Result.rejected(Blocker.STAGE_INCOMPLETE, this);
        }
        List<MageSignaturePolicy.MageId> nextDefeated = new ArrayList<>(defeated);
        nextDefeated.add(mage);
        int nextStage = stageIndex + 1;
        boolean broken = nextStage == STAGE_COUNT;
        WaveSixSequencePolicy advanced = new WaveSixSequencePolicy(order, nextStage,
                List.copyOf(nextDefeated), broken, guardSlots, Set.of());
        return Result.accepted(advanced, mage, MageSignaturePolicy.signatureFor(mage), broken);
    }

    /**
     * W6-005 [A]: every mage death owes exactly one ordinary reinforcement pack.
     * The final pack is owed by the fifth death and gated only by that receipt.
     */
    public int reinforcementPackReceipts() {
        return defeated.size();
    }

    /**
     * W6-006 [A]: the final closure may only be accepted once the fifth death
     * happened, the prison was released, and the final pack fully resolved.
     */
    public boolean mayComplete(int resolvedPackReceipts, boolean runtimeClean) {
        return stageIndex == STAGE_COUNT && prisonBroken && runtimeClean
                && resolvedPackReceipts >= STAGE_COUNT;
    }

    /** Human-readable diagnostic identity; stable across restarts. */
    public String describe() {
        StringBuilder text = new StringBuilder()
                .append("stage=").append(stageIndex).append('/').append(STAGE_COUNT)
                .append(" order=").append(order)
                .append(" defeated=").append(defeated)
                .append(" guards=").append(guardSlotsFilled.size()).append('/').append(guardSlots);
        MageSignaturePolicy.MageId current = currentMage();
        if (current != null) {
            text.append(" current=").append(current)
                    .append(" signature=").append(MageSignaturePolicy.signatureFor(current));
        }
        text.append(" prisonBroken=").append(prisonBroken);
        return text.toString();
    }

    /** Visible stage of the current mage. */
    public enum Lens {
        GUARDED,
        EXPOSED,
        COMPLETE
    }

    /** Reasons a transition cannot be committed. */
    public enum Blocker {
        UNKNOWN_IDENTITY,
        NOT_CURRENT_MAGE,
        SEQUENCE_FINISHED,
        STAGE_INCOMPLETE
    }

    /** Typed outcome of {@link #defeatCurrent}. */
    public record Result(WaveSixSequencePolicy state, Blocker blocker,
                         MageSignaturePolicy.MageId defeatedMage,
                         MageSignaturePolicy.Signature acquiredSignature,
                         boolean prisonBroken) {
        public Result {
            if (state == null) throw new IllegalArgumentException("result state is required");
        }

        static Result accepted(WaveSixSequencePolicy state, MageSignaturePolicy.MageId mage,
                               MageSignaturePolicy.Signature signature, boolean prisonBroken) {
            return new Result(state, null, mage, signature, prisonBroken);
        }

        static Result rejected(Blocker blocker, WaveSixSequencePolicy state) {
            return new Result(state, blocker, null, null, false);
        }

        public boolean accepted() {
            return blocker == null;
        }
    }
}
