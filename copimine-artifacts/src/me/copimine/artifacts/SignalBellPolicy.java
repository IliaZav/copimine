package me.copimine.artifacts;

import java.util.Map;

/** Coordinate matching and duplicate-notification rules for a bound Signal Bell. */
public final class SignalBellPolicy {
   private SignalBellPolicy() {
   }

   public static boolean matches(
         String boundWorld,
         int boundX,
         int boundY,
         int boundZ,
         String openedWorld,
         int openedX,
         int openedY,
         int openedZ,
         boolean openedBlockIsChest) {
      if (boundWorld == null || openedWorld == null || !boundWorld.equals(openedWorld)
            || boundY != openedY) {
         return false;
      }
      long dx = Math.abs((long)boundX - openedX);
      long dz = Math.abs((long)boundZ - openedZ);
      return openedBlockIsChest && (dx == 0L && dz == 0L || dx + dz == 1L);
   }

   public static boolean isChestContainer(String materialName) {
      if (materialName == null) {
         return false;
      }
      return switch (materialName.toUpperCase(java.util.Locale.ROOT)) {
         case "CHEST", "TRAPPED_CHEST" -> true;
         default -> false;
      };
   }

   public static boolean shouldNotify(Long previousTick, long currentTick) {
      return previousTick == null || previousTick.longValue() != currentTick;
   }

   /**
    * Keep the per-tick duplicate guard bounded. A bell can be rebound to many
    * containers over the life of a server, but only events from the current
    * tick need to remain in the map.
    */
   public static long beginNotificationTick(Map<String, Long> notificationTicks, long activeTick, long currentTick) {
      if (activeTick != currentTick) {
         notificationTicks.clear();
      }
      return currentTick;
   }
}
