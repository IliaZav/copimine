package me.copimine.endevent.domain;

/** Pure durability repair math for the post-Wave-7 recovery. */
public final class PostWaveRecoveryPolicy {
    private PostWaveRecoveryPolicy() {
    }

    public static int repairAmount(int maxDurability) {
        if (maxDurability <= 0) return 0;
        return (int) Math.round(maxDurability * 0.30D);
    }

    public static int repairedDamage(int currentDamage, int maxDurability) {
        if (maxDurability <= 0) return currentDamage;
        return Math.max(0, Math.max(0, currentDamage) - repairAmount(maxDurability));
    }
}
