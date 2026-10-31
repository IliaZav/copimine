package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Wave 7 runs four trials <em>in sequence</em> on the open arena, not in four
 * concurrent rooms.
 *
 * <p>The arena contract is explicit: the trials are ordered
 * {@code WARDEN -> MOBILE_MARKSMAN -> RIFT_ARCHMAGE -> PERSONAL_ECHO}, every
 * admitted player fights every trial together, and no internal room partition
 * or personal wedge cell exists. The housing still needs a chamber index (it is
 * part of the persisted snapshot identity), but with a single open arena that
 * index is always zero — {@code ChamberIsolationPolicy} would otherwise hand
 * out two, three or four sectors and split the party.</p>
 *
 * <p>This class is pure: it holds the route and the derivation of "which stage
 * is live". It deliberately does not reference the runtime controller, so the
 * domain layer keeps no dependency on Paper-facing code.</p>
 */
public final class Wave7TrialRoutePolicy {

    /** Stable route identities; the declared order IS the sequential order. */
    public enum Stage {
        WARDEN,
        MOBILE_MARKSMAN,
        RIFT_ARCHMAGE,
        PERSONAL_ECHO
    }

    /**
     * The agreed engineering order. PERSONAL_ECHO is last because it is the only
     * stage where a player fights their own copy: it needs the combat profile
     * evidence gathered by the three minibosses that precede it.
     */
    public static final List<Stage> ORDER = List.of(
            Stage.WARDEN, Stage.MOBILE_MARKSMAN, Stage.RIFT_ARCHMAGE, Stage.PERSONAL_ECHO);

    /**
     * The arena houses exactly one trial at a time, so every trial shares
     * chamber 0 and every player shares its owner set.
     */
    public static final int OPEN_ARENA_CHAMBER = 0;

    private Wave7TrialRoutePolicy() { }

    /** Stage at a route position, or null past the end. */
    public static Stage stageAt(int index) {
        return index < 0 || index >= ORDER.size() ? null : ORDER.get(index);
    }

    /** Route position of a stage, or -1 when it is not part of the route. */
    public static int indexOf(Stage stage) {
        return stage == null ? -1 : ORDER.indexOf(stage);
    }

    /**
     * The stage that must be running now.
     *
     * <p>Derived only from completed stages: the first stage that is not yet
     * complete is the current one. A restart therefore resumes exactly where the
     * attempt stopped, with no separate counter that could drift out of sync
     * with durable receipts.</p>
     *
     * @param completed stages already persisted as complete
     * @return the successor stage, or null when the whole route is finished
     */
    public static Stage successor(List<Stage> completed) {
        for (Stage stage : ORDER) {
            if (completed == null || !completed.contains(stage)) return stage;
        }
        return null;
    }

    /** True when this stage may present an active encounter right now. */
    public static boolean isActive(Stage stage, List<Stage> completed) {
        return stage != null && stage == successor(completed);
    }

    /**
     * Show one stage at a time, in route order.
     *
     * <p>Stages still ahead keep their persisted ACTIVE state so a restore can
     * still find them; they are simply not presented yet. This is what makes the
     * route sequential without rewriting any durable receipt: the runtime only
     * ever spawns and updates the stage returned here.</p>
     */
    public static List<Stage> presentable(List<Stage> stages, List<Stage> completed) {
        Stage current = successor(completed);
        if (current == null || stages == null || !stages.contains(current)) return List.of();
        return Collections.singletonList(current);
    }

    /**
     * True when the persisted layout is a valid prefix of the route.
     *
     * <p>Legacy attempts stored a randomly sampled layout; anything that is not
     * a prefix must be migrated explicitly rather than resumed as if it were
     * sequential, because its rooms really did run in parallel.</p>
     */
    public static boolean isValidPrefix(List<Stage> layout) {
        if (layout == null || layout.isEmpty() || layout.size() > ORDER.size()) return false;
        return ORDER.subList(0, layout.size()).equals(List.copyOf(layout))
                && layout.stream().allMatch(Objects::nonNull);
    }

    /** Stages of a legacy layout that are missing from the sequential route. */
    public static List<Stage> missingFrom(List<Stage> layout) {
        List<Stage> missing = new ArrayList<>();
        for (Stage stage : ORDER) {
            if (layout == null || !layout.contains(stage)) missing.add(stage);
        }
        return List.copyOf(missing);
    }
}