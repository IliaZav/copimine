package me.copimine.artifacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Narrow, data-preserving helpers for versioned runtime catalog migrations. */
public final class ArtifactCatalogMigrationPolicy {
   public static final Set<String> APPROVED_AR_ITEM_IDS = Set.of(
         "gravedigger_contract",
         "signal_bell"
   );
   public static final Set<String> APPROVED_DONATION_ITEM_IDS = Set.of(
         "berserker_heart",
         "zhilorez_pickaxe",
         "donation_night_cloak"
   );

   private ArtifactCatalogMigrationPolicy() {
   }

   public static boolean shouldRunMigration(int appliedVersion, int targetVersion) {
      return targetVersion > 0 && appliedVersion < targetVersion;
   }

   /**
    * Adds approved catalog rows and fills only absent fields on rows already
    * present. Operator values, unknown keys, row order, and unapproved rows are
    * preserved. IDs are compared case-insensitively using the catalog's
    * normal key ({@code id} or {@code item-id}).
    *
    * @return the number of rows added or completed with default fields
    */
   public static int mergeApprovedRows(
         List<Map<String, Object>> runtimeRows,
         List<Map<String, Object>> canonicalRows,
         Set<String> approvedIds,
         String idField) {
      if (runtimeRows == null || canonicalRows == null || approvedIds == null
            || idField == null || idField.isBlank()) {
         throw new IllegalArgumentException("Catalog migration inputs are required");
      }

      Set<String> approved = new HashSet<>();
      for (String id : approvedIds) {
         String normalized = normalize(id);
         if (!normalized.isBlank()) {
            approved.add(normalized);
         }
      }

      Map<String, Map<String, Object>> existingById = new HashMap<>();
      for (Map<String, Object> row : runtimeRows) {
         if (row == null) {
            continue;
         }
         String id = normalize(row.get(idField));
         if (id.isBlank()) {
            continue;
         }
         if (existingById.putIfAbsent(id, row) != null) {
            throw new IllegalStateException("Duplicate runtime catalog item id: " + id);
         }
      }

      Set<String> seenCanonicalIds = new HashSet<>();
      int changedRows = 0;
      for (Map<String, Object> canonical : canonicalRows) {
         if (canonical == null) {
            continue;
         }
         String id = normalize(canonical.get(idField));
         if (id.isBlank() || !approved.contains(id)) {
            continue;
         }
         if (!seenCanonicalIds.add(id)) {
            throw new IllegalStateException("Duplicate canonical catalog item id: " + id);
         }

         Map<String, Object> existing = existingById.get(id);
         if (existing == null) {
            Map<String, Object> added = new java.util.LinkedHashMap<>(canonical);
            runtimeRows.add(added);
            existingById.put(id, added);
            changedRows++;
            continue;
         }

         boolean changed = false;
         for (Map.Entry<String, Object> entry : canonical.entrySet()) {
            if (!existing.containsKey(entry.getKey())) {
               existing.put(entry.getKey(), copyValue(entry.getValue()));
               changed = true;
            }
         }
         if (changed) {
            changedRows++;
         }
      }
      for (String requiredId : approved) {
         if (!seenCanonicalIds.contains(requiredId)) {
            throw new IllegalStateException("Canonical catalog is missing approved item: " + requiredId);
         }
      }
      return changedRows;
   }

   private static Object copyValue(Object value) {
      if (value instanceof List<?> list) {
         return new ArrayList<>(list);
      }
      if (value instanceof Map<?, ?> map) {
         return new java.util.LinkedHashMap<>(map);
      }
      return value;
   }

   private static String normalize(Object value) {
      return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
   }
}
