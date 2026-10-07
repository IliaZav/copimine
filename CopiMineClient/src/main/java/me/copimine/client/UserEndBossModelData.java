package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable source-asset metadata for the End Rift guardian.
 *
 * <p>The renderer receives an {@code EndermanEntityModel} for compatibility
 * with vanilla feature dispatch, but its cuboid carrier is deliberately not
 * the guardian. {@link ChameleonGuardianGeometry} parses the supplied
 * Chameleon JSON and {@link ChameleonGuardianRenderer} emits every source face
 * directly. Keeping the old Bedrock-to-{@code ModelPart} importer out of this
 * class prevents absolute source pivots and signed UV rectangles from being
 * decomposed into a lossy vanilla hierarchy.</p>
 */
final class UserEndBossModelData {
    static final String RESOURCE = "/assets/copimineclient/models/entity/end_rift_guardian/geometry.json";
    static final int SOURCE_TEXTURE_WIDTH = 16;
    static final int SOURCE_TEXTURE_HEIGHT = 16;
    static final int TEXTURE_WIDTH = 128;
    static final int TEXTURE_HEIGHT = 128;
    private static final String TEXTURE_RESOURCE =
            "/assets/copimineclient/textures/entity/end_rift_user_boss.png";

    private UserEndBossModelData() {
    }

    /**
     * Creates only the carrier required by {@code EndermanEntityModel}. It is
     * never rendered for a bound guardian; direct source geometry is used in
     * {@link RiftGuardianModel#render}.
     */
    static TexturedModelData create() {
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, -14.0F);
        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    static Set<String> sourceBoneNames() {
        JsonArray bones = readGeometry().getAsJsonArray("bones");
        Set<String> names = new LinkedHashSet<>();
        for (JsonElement element : bones) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return Set.copyOf(names);
    }

    private static JsonObject readGeometry() {
        try (InputStream stream = UserEndBossModelData.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing supplied End Rift geometry resource: " + RESOURCE);
            }
            JsonObject document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray geometries = document.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.size() != 1) {
                throw new IllegalStateException("Expected one supplied End Rift geometry definition");
            }
            BedrockAssetValidator.validateGeometry(document, RESOURCE);
            try (InputStream texture = UserEndBossModelData.class.getResourceAsStream(TEXTURE_RESOURCE)) {
                BedrockAssetValidator.validateTexture(texture, TEXTURE_RESOURCE,
                        TEXTURE_WIDTH, TEXTURE_HEIGHT);
            }
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description.get("texture_width").getAsInt() != SOURCE_TEXTURE_WIDTH
                    || description.get("texture_height").getAsInt() != SOURCE_TEXTURE_HEIGHT) {
                throw new IllegalStateException("Unexpected supplied End Rift texture grid");
            }
            return geometry;
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Unable to load supplied End Rift geometry", error);
        }
    }
}
