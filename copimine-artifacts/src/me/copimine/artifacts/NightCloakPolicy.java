package me.copimine.artifacts;

/** Pure time and threshold rules shared by donation armor abilities. */
public final class NightCloakPolicy {
   public static final long BERSERKER_COOLDOWN_SECONDS = 420L;

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

   public static boolean shouldApplyNightEffects(
         String itemId,
         String effectId,
         long worldTime,
         String environmentName) {
      return "donation_night_cloak".equalsIgnoreCase(itemId == null ? "" : itemId.trim())
            && "NIGHT_CLOAK".equalsIgnoreCase(effectId == null ? "" : effectId.trim())
            && isNight(worldTime, environmentName);
   }
}
