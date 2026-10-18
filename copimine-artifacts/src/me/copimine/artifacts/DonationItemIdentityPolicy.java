package me.copimine.artifacts;

import java.util.Locale;

/** Resolves the retired donation cloak ID only when its durable binding and appearance agree. */
public final class DonationItemIdentityPolicy {
   private static final String LEGACY_CLOAK_ID = "night_cloak";
   private static final String DONATION_CLOAK_ID = "donation_night_cloak";
   private static final String DONATION_CLOAK_MATERIAL = "PHANTOM_MEMBRANE";
   private static final int DONATION_CLOAK_CUSTOM_MODEL_DATA = 20012;

   private DonationItemIdentityPolicy() {
   }

   /**
    * Returns the identity authenticated by both the physical item and the
    * instance database binding. A null result means those identities disagree.
    */
   public static String canonicalItemId(
         String pdcItemId,
         String boundItemId,
         String materialName,
         Integer customModelData) {
      String physicalId = normalize(pdcItemId);
      String durableId = normalize(boundItemId);
      if (physicalId.isBlank() || durableId.isBlank()) {
         return null;
      }
      if (physicalId.equals(durableId)) {
         return physicalId;
      }
      if (LEGACY_CLOAK_ID.equals(physicalId)
            && DONATION_CLOAK_ID.equals(durableId)
            && DONATION_CLOAK_MATERIAL.equalsIgnoreCase(materialName == null ? "" : materialName.trim())
            && customModelData != null
            && customModelData == DONATION_CLOAK_CUSTOM_MODEL_DATA) {
         return DONATION_CLOAK_ID;
      }
      return null;
   }

   /**
    * Donation ownership follows the catalog setting. Unknown donation-tagged
    * identities and admin-only rewards remain bound so stale or forged
    * metadata cannot make them transferable.
    */
   public static boolean isOwnerBound(
         String itemId,
         boolean donationCatalogItem,
         boolean configuredOwnerBound,
         String itemType,
         String source,
         boolean adminOnly) {
      if (adminOnly) {
         return true;
      }
      if (donationCatalogItem) {
         return configuredOwnerBound;
      }
      return "DONATION_SHOP_ITEM".equalsIgnoreCase(itemType == null ? "" : itemType.trim())
            || "DONATION_SHOP".equalsIgnoreCase(source == null ? "" : source.trim());
   }

   private static String normalize(String itemId) {
      return itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
   }
}
