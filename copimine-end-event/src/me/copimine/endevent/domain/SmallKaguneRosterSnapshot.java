package me.copimine.endevent.domain;

import java.util.*;

/** Real living small enemies, independent of guardian slots and cast visuals. */
public final class SmallKaguneRosterSnapshot {
    public static final String PREFIX = "boss-small-roster.";
    private SmallKaguneRosterSnapshot() { }

    public record Actor(UUID identity, UUID world, double x, double y, double z,
                        float yaw, double health, double maximum) {
        public Actor {
            if (identity == null || world == null || !finite(x, 30_000_000D)
                    || !finite(z, 30_000_000D) || !finite(y, 4096D)
                    || !Float.isFinite(yaw) || !Double.isFinite(health)
                    || !Double.isFinite(maximum) || maximum <= 0D || maximum > 20_000D
                    || health <= 0D || health > maximum)
                throw new IllegalArgumentException("INVALID_SMALL_KAGUNE_ACTOR");
            yaw = (float) Math.IEEEremainder(yaw, 360D);
        }
        private static boolean finite(double value, double bound) {
            return Double.isFinite(value) && Math.abs(value) <= bound;
        }
    }

    private static List<Actor> checked(List<Actor> actors) {
        if (actors == null || actors.size() > SmallKaguneSpawnPolicy.MAX_LIVING)
            throw new IllegalArgumentException("INVALID_SMALL_KAGUNE_ROSTER_SIZE");
        Set<UUID> identities = new HashSet<>();
        for (Actor actor : actors) if (actor == null || !identities.add(actor.identity()))
            throw new IllegalArgumentException("DUPLICATE_SMALL_KAGUNE_ACTOR");
        return List.copyOf(actors);
    }

    public static Map<String, String> encode(String event, long generation, List<Actor> actors) {
        if (event == null || event.isBlank() || generation <= 0L)
            throw new IllegalArgumentException("INVALID_SMALL_KAGUNE_SCOPE");
        List<Actor> roster = checked(actors);
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put(PREFIX + "schema", "1"); saved.put(PREFIX + "event", event);
        saved.put(PREFIX + "generation", Long.toString(generation));
        saved.put(PREFIX + "count", Integer.toString(roster.size()));
        for (int i = 0; i < roster.size(); i++) {
            Actor a = roster.get(i);
            saved.put(PREFIX + i, a.identity() + "|" + a.world() + "|"
                    + Double.toHexString(a.x()) + "|" + Double.toHexString(a.y()) + "|"
                    + Double.toHexString(a.z()) + "|" + Float.toHexString(a.yaw()) + "|"
                    + Double.toHexString(a.health()) + "|" + Double.toHexString(a.maximum()));
        }
        return Map.copyOf(saved);
    }

    public static List<Actor> decode(Map<String, String> saved, String event, long generation) {
        if (saved == null || saved.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) return List.of();
        try {
            if (!"1".equals(saved.get(PREFIX + "schema")) || event == null || event.isBlank()
                    || !event.equals(saved.get(PREFIX + "event")) || generation <= 0L
                    || generation != Long.parseLong(saved.get(PREFIX + "generation")))
                throw new IllegalArgumentException("scope");
            int count = Integer.parseInt(saved.get(PREFIX + "count"));
            if (count < 0 || count > SmallKaguneSpawnPolicy.MAX_LIVING
                    || saved.keySet().stream().filter(key -> key.startsWith(PREFIX)).count() != count + 4L)
                throw new IllegalArgumentException("size/fields");
            List<Actor> actors = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String[] v = Objects.requireNonNull(saved.get(PREFIX + i)).split("\\|", -1);
                if (v.length != 8) throw new IllegalArgumentException("actor fields");
                actors.add(new Actor(UUID.fromString(v[0]), UUID.fromString(v[1]),
                        Double.parseDouble(v[2]), Double.parseDouble(v[3]), Double.parseDouble(v[4]),
                        Float.parseFloat(v[5]), Double.parseDouble(v[6]), Double.parseDouble(v[7])));
            }
            return checked(actors);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("INVALID_SMALL_KAGUNE_ROSTER", invalid);
        }
    }
}
