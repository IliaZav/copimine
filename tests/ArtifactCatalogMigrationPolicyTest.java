import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.copimine.artifacts.ArtifactCatalogMigrationPolicy;

public final class ArtifactCatalogMigrationPolicyTest {
    private ArtifactCatalogMigrationPolicyTest() {
    }

    public static void main(String[] args) {
        check(ArtifactCatalogMigrationPolicy.shouldRunMigration(0, 1), "unmigrated catalogs need migration");
        check(!ArtifactCatalogMigrationPolicy.shouldRunMigration(1, 1), "version-one catalogs are not migrated twice");
        check(!ArtifactCatalogMigrationPolicy.shouldRunMigration(4, 1), "newer catalog versions are never downgraded");
        check(ArtifactCatalogMigrationPolicy.APPROVED_AR_ITEM_IDS.equals(
                Set.of("gravedigger_contract", "signal_bell")), "only two approved AR IDs are migrated");
        check(ArtifactCatalogMigrationPolicy.APPROVED_DONATION_ITEM_IDS.equals(
                Set.of("berserker_heart", "zhilorez_pickaxe", "donation_night_cloak")),
                "only three approved donation IDs are migrated");

        List<Map<String, Object>> oldArCatalog = new ArrayList<>();
        oldArCatalog.add(row("operator_custom_item", "material", "PAPER", "price_ar", 17));
        oldArCatalog.add(row(
                "Signal_Bell",
                "name", "&aOperator Bell",
                "price_ar", 777,
                "operator_note", "keep this"
        ));
        List<Map<String, Object>> canonicalArRows = List.of(
                row(
                        "gravedigger_contract",
                        "category", "TOOL",
                        "material", "PAPER",
                        "source", "AR_SHOP",
                        "custom_model_data", 10029,
                        "price_ar", 300,
                        "effect", "GRAVEDIGGER_CONTRACT"
                ),
                row(
                        "signal_bell",
                        "category", "TOOL",
                        "material", "BELL",
                        "source", "AR_SHOP",
                        "custom_model_data", 10030,
                        "price_ar", 150,
                        "effect", "SIGNAL_BELL"
                ),
                row("not_approved", "material", "STONE")
        );

        int arChanges = ArtifactCatalogMigrationPolicy.mergeApprovedRows(
                oldArCatalog,
                canonicalArRows,
                ArtifactCatalogMigrationPolicy.APPROVED_AR_ITEM_IDS,
                "id"
        );
        check(arChanges > 0, "an old AR catalog gains approved canonical defaults");
        check(oldArCatalog.size() == 3, "unknown operator rows stay and only the two approved items are added");
        Map<String, Object> migratedGravedigger = find(oldArCatalog, "gravedigger_contract", "id");
        check("GRAVEDIGGER_CONTRACT".equals(migratedGravedigger.get("effect")), "gravedigger runtime effect is supplied");
        check(Integer.valueOf(10029).equals(migratedGravedigger.get("custom_model_data")), "gravedigger model id is supplied");
        Map<String, Object> editedBell = find(oldArCatalog, "signal_bell", "id");
        check("&aOperator Bell".equals(editedBell.get("name")), "operator-edited bell name is preserved");
        check(Integer.valueOf(777).equals(editedBell.get("price_ar")), "operator-edited bell price is preserved");
        check("keep this".equals(editedBell.get("operator_note")), "unknown keys on an approved item are preserved");
        check(!contains(oldArCatalog, "not_approved", "id"), "canonical rows outside the allowlist are ignored");
        check("operator_custom_item".equals(oldArCatalog.get(0).get("id")), "unknown custom rows keep their order and identity");

        List<Map<String, Object>> oldDonationCatalog = new ArrayList<>();
        oldDonationCatalog.add(donationRow("operator_donation", "base-material", "PAPER", "price-donation", 9));
        oldDonationCatalog.add(donationRow(
                "berserker_heart",
                "display-name", "Operator's heart",
                "price-donation", 321,
                "operator_flag", true
        ));
        List<Map<String, Object>> canonicalDonationRows = List.of(
                donationRow(
                        "berserker_heart",
                        "base-material", "NETHER_STAR",
                        "price-donation", 200,
                        "effect-profile-id", "BERSERKER_HEART"
                ),
                donationRow(
                        "zhilorez_pickaxe",
                        "base-material", "NETHERITE_PICKAXE",
                        "price-donation", 250,
                        "effect-profile-id", "VEIN_MINER"
                ),
                donationRow(
                        "donation_night_cloak",
                        "base-material", "PHANTOM_MEMBRANE",
                        "price-donation", 150,
                        "effect-profile-id", "NIGHT_CLOAK"
                ),
                donationRow("another_canonical_donation", "base-material", "GOLD_INGOT")
        );
        int donationChanges = ArtifactCatalogMigrationPolicy.mergeApprovedRows(
                oldDonationCatalog,
                canonicalDonationRows,
                ArtifactCatalogMigrationPolicy.APPROVED_DONATION_ITEM_IDS,
                "item-id"
        );
        check(donationChanges > 0, "the old donation catalog gains the three approved canonical defaults");
        check(oldDonationCatalog.size() == 4, "the operator donation row stays and only three approved items are added");
        Map<String, Object> migratedHeart = find(oldDonationCatalog, "berserker_heart", "item-id");
        check("Operator's heart".equals(migratedHeart.get("display-name")), "operator-edited donation name is preserved");
        check(Integer.valueOf(321).equals(migratedHeart.get("price-donation")), "operator-edited donation price is preserved");
        check(Boolean.TRUE.equals(migratedHeart.get("operator_flag")), "unknown donation keys are preserved");
        check("BERSERKER_HEART".equals(migratedHeart.get("effect-profile-id")), "missing approved behavior is filled from canonical defaults");
        check("VEIN_MINER".equals(find(oldDonationCatalog, "zhilorez_pickaxe", "item-id").get("effect-profile-id")), "Vein Miner is added");
        check("NIGHT_CLOAK".equals(find(oldDonationCatalog, "donation_night_cloak", "item-id").get("effect-profile-id")), "donation cloak is added");
        check(!contains(oldDonationCatalog, "another_canonical_donation", "item-id"), "unapproved donation rows are ignored");

        int repeatedChanges = ArtifactCatalogMigrationPolicy.mergeApprovedRows(
                oldDonationCatalog,
                canonicalDonationRows,
                ArtifactCatalogMigrationPolicy.APPROVED_DONATION_ITEM_IDS,
                "item-id"
        );
        check(repeatedChanges == 0, "reapplying the merge is idempotent");
        check(oldDonationCatalog.size() == 4, "reapplying does not duplicate catalog items");

        boolean missingCanonicalRejected = false;
        try {
            ArtifactCatalogMigrationPolicy.mergeApprovedRows(
                    new ArrayList<>(),
                    List.of(row("gravedigger_contract", "effect", "GRAVEDIGGER_CONTRACT")),
                    ArtifactCatalogMigrationPolicy.APPROVED_AR_ITEM_IDS,
                    "id"
            );
        } catch (IllegalStateException expected) {
            missingCanonicalRejected = true;
        }
        check(missingCanonicalRejected, "a migration cannot mark complete without every approved canonical row");

        System.out.println("ArtifactCatalogMigrationPolicyTest OK");
    }

    private static Map<String, Object> row(String id, Object... fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        for (int index = 0; index < fields.length; index += 2) {
            row.put((String) fields[index], fields[index + 1]);
        }
        return row;
    }

    private static Map<String, Object> donationRow(String itemId, Object... fields) {
        Map<String, Object> row = row(itemId, fields);
        row.put("item-id", row.remove("id"));
        return row;
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, String id, String idField) {
        return rows.stream()
                .filter(row -> id.equalsIgnoreCase(String.valueOf(row.get(idField))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing catalog row " + id));
    }

    private static boolean contains(List<Map<String, Object>> rows, String id, String idField) {
        return rows.stream().anyMatch(row -> id.equalsIgnoreCase(String.valueOf(row.get(idField))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
