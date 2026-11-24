package me.copimine.dungeon.editor;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.OverlayType;

import java.util.Objects;
import java.util.UUID;

/**
 * Per-builder editing session state (spec OV4.3). Holds the two-corner
 * selection for the current mode, the selected room, and the overlay toggles.
 *
 * <p>No Bukkit types: a session is pure data so it can be unit-tested and so
 * the editor snapshot pushed to clients is permission-checked versioned data,
 * not a live reference to world objects.</p>
 */
public final class EditorSession {

    private final UUID actorId;
    private final String actorName;
    private EditorMode mode = EditorMode.ROOM;
    private int[] corner1;
    private int[] corner2;
    private String selectedRoomId;
    private String world;
    private boolean overlayVisible = true;
    private OverlayType overlayFilter;
    private int overlayRangeBlocks = 32;
    private boolean occlusion = true;
    private boolean draftPreview = false;

    public EditorSession(UUID actorId, String actorName) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = actorName == null ? "" : actorName;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorName() {
        return actorName;
    }

    public EditorMode mode() {
        return mode;
    }

    public void setMode(EditorMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public String world() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int[] corner1() {
        return corner1 == null ? null : corner1.clone();
    }

    public int[] corner2() {
        return corner2 == null ? null : corner2.clone();
    }

    public void setCorner1(int x, int y, int z) {
        this.corner1 = new int[]{x, y, z};
    }

    public void setCorner2(int x, int y, int z) {
        this.corner2 = new int[]{x, y, z};
    }

    public boolean hasBothCorners() {
        return corner1 != null && corner2 != null;
    }

    /** Normalized cuboid for the current two-corner selection, or {@code null}. */
    public Cuboid selection() {
        if (!hasBothCorners()) {
            return null;
        }
        return Cuboid.fromCorners(corner1, corner2);
    }

    public String selectedRoomId() {
        return selectedRoomId;
    }

    public void selectRoom(String roomId) {
        this.selectedRoomId = roomId;
    }

    public void clearSelection() {
        corner1 = null;
        corner2 = null;
    }

    public boolean overlayVisible() {
        return overlayVisible;
    }

    public void setOverlayVisible(boolean visible) {
        this.overlayVisible = visible;
    }

    public OverlayType overlayFilter() {
        return overlayFilter;
    }

    public void setOverlayFilter(OverlayType filter) {
        this.overlayFilter = filter;
    }

    public int overlayRangeBlocks() {
        return overlayRangeBlocks;
    }

    /** Range is capped so a builder can never request a whole-world overlay. */
    public void setOverlayRangeBlocks(int blocks) {
        this.overlayRangeBlocks = Math.max(16, Math.min(96, blocks));
    }

    public boolean occlusion() {
        return occlusion;
    }

    public void setOcclusion(boolean occlusion) {
        this.occlusion = occlusion;
    }

    public boolean draftPreview() {
        return draftPreview;
    }

    public void setDraftPreview(boolean draftPreview) {
        this.draftPreview = draftPreview;
    }
}
