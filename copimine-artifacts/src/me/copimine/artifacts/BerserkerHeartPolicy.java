package me.copimine.artifacts;

/** Pure threshold and cooldown decisions for the Berserker Heart. */
public final class BerserkerHeartPolicy {
   public static final long COOLDOWN_SECONDS = 420L;

   private BerserkerHeartPolicy() {
   }

   public static boolean shouldTrigger(
         double projectedHealth,
         double maxHealth,
         long cooldownUntil,
         long now,
         boolean damageCancelled) {
      return !damageCancelled
            && NightCloakPolicy.isBelowTenPercent(projectedHealth, maxHealth)
            && cooldownUntil <= now;
   }

   public static long nextCooldownUntil(long now) {
      return now + COOLDOWN_SECONDS;
   }
}
