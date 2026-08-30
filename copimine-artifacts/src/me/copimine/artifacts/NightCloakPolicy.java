package me.copimine.artifacts;

/** Pure policy used by the one-hertz Night Cloak and Berserker Heart logic. */
public final class NightCloakPolicy {
   public static final long COOLDOWN_SECONDS = 420L;

   private NightCloakPolicy() {
   }

   public static boolean isNight(long worldTime, String environmentName) {
      if (environmentName == null || !"NORMAL".equalsIgnoreCase(environmentName)) {
         return false;
      }
      long time = Math.floorMod(worldTime, 24000L);
      return time >= 13000L && time < 23000L;
   }

   public static boolean isBelowTenPercent(double projectedHealth, double maxHealth) {
      return maxHealth > 0.0D && projectedHealth < maxHealth * 0.10D;
   }
}
