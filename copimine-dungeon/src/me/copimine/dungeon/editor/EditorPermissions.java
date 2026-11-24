package me.copimine.dungeon.editor;

import java.util.Locale;
import java.util.Set;

/**
 * Permission gate for the dungeon editor (spec OV4.3).
 *
 * <p>Security contract: builder privileges are decided by explicit server
 * permissions, never by {@code player.isOp()} alone. Where a permission system
 * exists, an operator without {@code copimine.dungeon.admin} (or
 * {@code copimine.dungeon.builder}) must be denied, so a stray OP grant cannot
 * silently unlock live-world mutation. {@code op} is only ever an additional
 * hint; it can never substitute for the permission.</p>
 *
 * <p>Pure Java so the denial behaviour is unit-testable without a server.</p>
 */
public final class EditorPermissions {

    public static final String ADMIN = "copimine.dungeon.admin";
    public static final String BUILDER = "copimine.dungeon.builder";

    /** The coarse capabilities the command tree checks before acting. */
    public enum Capability {
        /** Publish production changes / approve live-world mutation. */
        ADMIN_PUBLISH,
        /** Edit owned draft definitions. */
        EDIT_DRAFT,
        /** Grant or revoke the builder permission. */
        WAND_GRANT,
        /** Receive hidden secret-passage metadata / exact coordinates. */
        SECRET_VIEW,
        /** Toggle the editor overlay. */
        OVERLAY
    }

    public enum Decision {
        GRANTED,
        DENIED_NO_PERMISSION
    }

    private EditorPermissions() {
    }

    public static boolean isAdmin(Set<String> granted) {
        return granted != null && granted.contains(ADMIN);
    }

    public static boolean isBuilder(Set<String> granted) {
        return granted != null && (granted.contains(ADMIN) || granted.contains(BUILDER));
    }

    /**
     * Authorizes a capability. {@code op} is passed for completeness and audit
     * only; it never grants a capability on its own.
     */
    public static Decision authorize(Set<String> granted, boolean op, Capability capability) {
        if (capability == null) {
            return Decision.DENIED_NO_PERMISSION;
        }
        boolean hasAdmin = isAdmin(granted);
        boolean hasBuilder = isBuilder(granted);
        boolean allowed = switch (capability) {
            case ADMIN_PUBLISH, WAND_GRANT -> hasAdmin;
            case EDIT_DRAFT, OVERLAY, SECRET_VIEW -> hasBuilder;
        };
        // Deliberate: `op` alone is never sufficient when server permissions exist.
        return allowed ? Decision.GRANTED : Decision.DENIED_NO_PERMISSION;
    }

    public static boolean allows(Set<String> granted, boolean op, Capability capability) {
        return authorize(granted, op, capability) == Decision.GRANTED;
    }

    /** Human-readable Russian denial reason for command feedback. */
    public static String describeDenial(Capability capability) {
        String required = switch (capability) {
            case ADMIN_PUBLISH, WAND_GRANT -> ADMIN;
            case EDIT_DRAFT, OVERLAY, SECRET_VIEW -> BUILDER + " (или " + ADMIN + ")";
        };
        return "Недостаточно прав: требуется " + required + ". OP без явного разрешения не считается.";
    }

    /** Normalizes a raw permission node for stable comparison. */
    public static String normalize(String node) {
        return node == null ? "" : node.trim().toLowerCase(Locale.ROOT);
    }
}
