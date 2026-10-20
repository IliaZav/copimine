package me.copimine.client;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Maps bounded legacy artifact identifiers to the fixed set of armor textures. */
public final class ArtifactArmorTexturePolicy {
    private static final int MAX_ARTIFACT_ID_LENGTH = 64;
    private static final String[] ARTIFACT_ID_KEYS = {
            "copimineartifacts:artifact_item_id", "copimine-artifacts:artifact_item_id",
            "copimine:artifact_item_id"
    };

    private ArtifactArmorTexturePolicy() { }

    public static String textureFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return "";
        Object readView = customData;
        if (readView instanceof ArtifactCustomDataReadAccess access) return textureFor(access);
        return "";
    }

    public static String textureFor(CompoundTag customData) {
        if (customData == null) return "";
        return textureForItemId(readItemId(customData));
    }

    static String textureFor(ArtifactCustomDataReadAccess customData) {
        if (customData == null) return "";
        return textureForItemId(readItemId(customData));
    }

    private static String textureForItemId(String itemId) {
        if (itemId.isEmpty() || itemId.length() > MAX_ARTIFACT_ID_LENGTH) return "";

        return switch (itemId.toLowerCase(Locale.ROOT)) {
            case "kaska_prorab_huev" -> "kaska_prorab_huev_layer_1";
            case "mne_pohuy_ya_v_tanke_vest" -> "mne_pohuy_ya_v_tanke_layer_1";
            case "treasurer_chestplate" -> "kaznacheyskiy_layer_1";
            case "kozyrny_tuz_pozdnyakova" -> "secret_items_layer_2";
            default -> "";
        };
    }

    private static String readItemId(CompoundTag customData) {
        for (String key : ARTIFACT_ID_KEYS) {
            String value = supportedCandidate(customData.getStringOr(key, ""));
            if (!value.isEmpty()) return value;
        }
        Tag publicValues = customData.get("PublicBukkitValues");
        if (publicValues instanceof CompoundTag nested) {
            for (String key : ARTIFACT_ID_KEYS) {
                String value = supportedCandidate(nested.getStringOr(key, ""));
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private static String readItemId(ArtifactCustomDataReadAccess customData) {
        for (String key : ARTIFACT_ID_KEYS) {
            String value = supportedCandidate(customData.getTopLevelString(key));
            if (!value.isEmpty()) return value;
        }
        for (String key : ARTIFACT_ID_KEYS) {
            String value = supportedCandidate(customData.getNestedString("PublicBukkitValues", key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String supportedCandidate(String value) {
        String candidate = boundedCandidate(value);
        return textureForItemId(candidate).isEmpty() ? "" : candidate;
    }

    private static String boundedCandidate(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() > MAX_ARTIFACT_ID_LENGTH) return value;
        return value.isBlank() ? "" : value;
    }
}
