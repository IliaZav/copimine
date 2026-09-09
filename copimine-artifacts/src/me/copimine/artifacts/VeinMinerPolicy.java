package me.copimine.artifacts;

import java.util.Locale;

/** Pure policy for the bounded, explicit ore-family vein miner. */
public final class VeinMinerPolicy {
   public static final int MAX_BLOCKS = 32;

   private VeinMinerPolicy() {
   }

   public static boolean isWhitelisted(String materialName) {
      return family(materialName) != null;
   }

   public static boolean sameFamily(String first, String second) {
      String firstFamily = family(first);
      return firstFamily != null && firstFamily.equals(family(second));
   }

   public static String family(String materialName) {
      if (materialName == null) {
         return null;
      }
      String name = materialName.toUpperCase(Locale.ROOT);
      return switch (name) {
         case "COAL_ORE", "DEEPSLATE_COAL_ORE" -> "COAL_ORE";
         case "IRON_ORE", "DEEPSLATE_IRON_ORE" -> "IRON_ORE";
         case "COPPER_ORE", "DEEPSLATE_COPPER_ORE" -> "COPPER_ORE";
         case "GOLD_ORE", "DEEPSLATE_GOLD_ORE" -> "GOLD_ORE";
         case "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE" -> "REDSTONE_ORE";
         case "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE" -> "LAPIS_ORE";
         case "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE" -> "DIAMOND_ORE";
         case "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE" -> "EMERALD_ORE";
         case "NETHER_QUARTZ_ORE" -> "NETHER_QUARTZ_ORE";
         case "NETHER_GOLD_ORE" -> "NETHER_GOLD_ORE";
         case "ANCIENT_DEBRIS" -> "ANCIENT_DEBRIS";
         default -> null;
      };
   }
}
