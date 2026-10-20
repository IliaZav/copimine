package me.copimine.client;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactArmorTexturePolicyTest {
    @Test
    void preservesKnownCaseInsensitiveArtifactIds() {
        CompoundTag customData = new CompoundTag();
        customData.putString("copimineartifacts:artifact_item_id", "KASKA_PRORAB_HUEV");

        assertEquals("kaska_prorab_huev_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void readsNestedLegacyPublicBukkitValues() {
        CompoundTag publicValues = new CompoundTag();
        publicValues.putString("copimine:artifact_item_id", "treasurer_chestplate");
        CompoundTag customData = new CompoundTag();
        customData.put("PublicBukkitValues", publicValues);

        assertEquals("kaznacheyskiy_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void skipsUnsupportedTopLevelIdsAndContinuesToLaterKnownKeys() {
        CompoundTag customData = new CompoundTag();
        customData.putString("copimineartifacts:artifact_item_id", "unknown_item");
        customData.putString("copimine-artifacts:artifact_item_id", "kaska_prorab_huev");

        assertEquals("kaska_prorab_huev_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void skipsUnsupportedNestedIdsAndContinuesToLaterKnownKeys() {
        CompoundTag publicValues = new CompoundTag();
        publicValues.putString("copimineartifacts:artifact_item_id", "unknown_item");
        publicValues.putString("copimine-artifacts:artifact_item_id", "treasurer_chestplate");
        CompoundTag customData = new CompoundTag();
        customData.put("PublicBukkitValues", publicValues);

        assertEquals("kaznacheyskiy_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void rejectsOverlongArtifactIdsBeforeNormalization() {
        CompoundTag customData = new CompoundTag();
        customData.putString("copimine:artifact_item_id", "kaska_prorab_huev".repeat(100));

        assertEquals("", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void leavesUnknownArtifactIdsOnVanillaTexture() {
        CompoundTag customData = new CompoundTag();
        customData.putString("copimine:artifact_item_id", "unknown_item");

        assertEquals("", ArtifactArmorTexturePolicy.textureFor(customData));
    }

    @Test
    void readsThroughTheReadOnlyComponentViewWithoutCopyingTheWholeTag() {
        class ReadOnlyData implements ArtifactCustomDataReadAccess {
            int topLevelReads;
            int nestedReads;

            @Override
            public String getTopLevelString(String key) {
                topLevelReads++;
                return key.equals("copimineartifacts:artifact_item_id") ? "KASKA_PRORAB_HUEV" : "";
            }

            @Override
            public String getNestedString(String compoundKey, String key) {
                nestedReads++;
                return "";
            }
        }
        ReadOnlyData customData = new ReadOnlyData();

        assertEquals("kaska_prorab_huev_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
        assertEquals(1, customData.topLevelReads);
        assertEquals(0, customData.nestedReads);
    }

    @Test
    void skipsUnsupportedReadOnlyCandidatesBeforeUsingALaterSupportedKey() {
        class ReadOnlyData implements ArtifactCustomDataReadAccess {
            int topLevelReads;

            @Override
            public String getTopLevelString(String key) {
                topLevelReads++;
                return switch (key) {
                    case "copimineartifacts:artifact_item_id" -> "unknown_item";
                    case "copimine-artifacts:artifact_item_id" -> "TREASURER_CHESTPLATE";
                    default -> "";
                };
            }

            @Override
            public String getNestedString(String compoundKey, String key) {
                return "";
            }
        }
        ReadOnlyData customData = new ReadOnlyData();

        assertEquals("kaznacheyskiy_layer_1", ArtifactArmorTexturePolicy.textureFor(customData));
        assertEquals(2, customData.topLevelReads);
    }
}
