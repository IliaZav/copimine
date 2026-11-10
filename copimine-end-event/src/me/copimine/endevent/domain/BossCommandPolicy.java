package me.copimine.endevent.domain;

/** Equivalent manual-spawn spelling; authorization remains in the command adapter. */
public final class BossCommandPolicy {
    private BossCommandPolicy() { }

    public static String[] normalize(String[] args) {
        if (args == null) return new String[0];
        if (args.length < 2 || !"spawn".equalsIgnoreCase(args[0])
                || !"boss".equalsIgnoreCase(args[1])) return args;
        String[] normalized = args.clone();
        normalized[0] = "boss";
        normalized[1] = "spawn";
        return normalized;
    }
}
