package me.copimine.dungeon.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Durable trigger on the exact selected block, optionally gated by a committed room clear. */
public record SecretTrigger(Action action, BlockPoint block, String milestoneRoom) {
    public enum Action {
        AUTO, RUNE, HIT, BUTTON, LEVER, PLATE, TARGET;

        public String token() { return name().toLowerCase(Locale.ROOT); }
        public static Action parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { return null; }
        }
        public static List<String> tokens() { return Arrays.stream(values()).map(Action::token).toList(); }
    }

    public record BlockPoint(int x, int y, int z) {
        public boolean at(int px, int py, int pz) { return x == px && y == py && z == pz; }
    }

    public static SecretTrigger decode(String rule) {
        String[] parts = rule.split(";", -1);
        if (parts.length == 1) {
            // Legacy secrets use right click directly on the concealed mask.
            return new SecretTrigger(Action.RUNE, null, "");
        }
        if (parts.length != 3) throw new IllegalArgumentException("Invalid secret trigger");
        Action action = Action.parse(parts[0]);
        if (action == null) throw new IllegalArgumentException("Unknown secret trigger " + parts[0]);
        BlockPoint point = null;
        if (!parts[1].isEmpty()) {
            String[] xyz = parts[1].split(",", -1);
            if (xyz.length != 3) throw new IllegalArgumentException("Invalid secret trigger coordinates");
            point = new BlockPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        }
        if (action != Action.AUTO && action != Action.RUNE && point == null)
            throw new IllegalArgumentException("Configured trigger needs a selected block");
        return new SecretTrigger(action, point, parts[2]);
    }

    public String encode() {
        return action.token() + ";" + (block == null ? "" : block.x() + "," + block.y() + "," + block.z())
                + ";" + milestoneRoom;
    }
}
