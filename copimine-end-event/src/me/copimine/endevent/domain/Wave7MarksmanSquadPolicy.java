package me.copimine.endevent.domain;

/**
 * How many Mobile Marksman skeletons one trial fields.
 *
 * <p>Player report: "there is only one skeleton, there should be two or three".
 * The trial spawned exactly one actor and the pair logic then looked for a
 * partner that was never created, which made the whole "duo" presentation
 * decorative — every pairing call returned {@code null} and the lone archer
 * fought as a solo.</p>
 *
 * <p>The squad size is a bounded function of the admitted party, so a larger
 * party meets a wider firing line and a solo/small squad still gets the
 * readable two-archer crossfire. The count never exceeds three, because the
 * pair policy's lane geometry (one flank per archer) stops producing readable
 * lanes beyond that.</p>
 */
public final class Wave7MarksmanSquadPolicy {
    /** Hard floor: the contract asks for at least two marksmen. */
    public static final int MIN_SQUAD = 2;
    /** Hard ceiling: three lanes is the widest readable firing line. */
    public static final int MAX_SQUAD = 3;
    /** Party size at which the third marksman joins. */
    public static final int THIRD_MARKSMAN_PLAYERS = 5;
    /** Lane index of each member: 0 is the primary spawned actor. */
    public static final int PRIMARY_INDEX = 0;

    private Wave7MarksmanSquadPolicy() {
    }

    /**
     * Marksmen fielded for an admitted party.
     *
     * <p>Two for the minimum party, three once the party is large enough that
     * two archers no longer cover the room. The value is stable for a given
     * roster size, so a relog cannot add or remove a live skeleton mid-fight.</p>
     */
    public static int squadSize(int admittedPlayers) {
        int players = Math.max(0, admittedPlayers);
        return players >= THIRD_MARKSMAN_PLAYERS ? MAX_SQUAD : MIN_SQUAD;
    }

    /** Entity index of every member, in spawn order. Index 0 is the primary. */
    public static java.util.List<Integer> memberIndices(int squadSize) {
        int bounded = Math.max(MIN_SQUAD, Math.min(MAX_SQUAD, squadSize));
        java.util.List<Integer> indices = new java.util.ArrayList<>(bounded);
        for (int index = 0; index < bounded; index++) indices.add(index);
        return java.util.List.copyOf(indices);
    }

    /**
     * Persisted trial indexes of the PARTNER archers only.
     *
     * <p>The primary archer keeps the historical {@code -1} index so every
     * existing lookup and every checkpoint written before this change still
     * resolves it. Partners are numbered from {@link #FIRST_PARTNER_INDEX} and
     * are addressable individually, which is what lets a squad member reload
     * back into its own slot instead of respawning as a clone of the primary.</p>
     */
    public static java.util.List<Integer> partnerIndices(int squadSize) {
        int bounded = Math.max(MIN_SQUAD, Math.min(MAX_SQUAD, squadSize));
        java.util.List<Integer> indices = new java.util.ArrayList<>(bounded - 1);
        for (int index = FIRST_PARTNER_INDEX; index < bounded; index++) indices.add(index);
        return java.util.List.copyOf(indices);
    }

    /** First persisted index used by a partner archer; the primary keeps {@code -1}. */
    public static final int FIRST_PARTNER_INDEX = 1;

    /** Whether a persisted trial index denotes a partner (not the primary) archer. */
    public static boolean isPartnerIndex(int trialIndex) {
        return trialIndex >= FIRST_PARTNER_INDEX && trialIndex < MAX_SQUAD;
    }

    /**
     * Whether the trial may be marked complete.
     *
     * <p>Completion is gated on the whole squad being dead, not on the death of
     * whichever skeleton the party focused first. Without this the trial ended
     * on the first kill and the remaining archers were orphaned in a completed
     * room.</p>
     */
    public static boolean completesTrial(int squadSize, int liveMembers) {
        return Math.max(0, liveMembers) == 0;
    }
}