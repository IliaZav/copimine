package me.copimine.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockAssetValidatorTest {
    @Test
    void acceptsTheMinimalSupportedGeometryAndSignedDownUv() {
        JsonObject document = validDocument();
        assertDoesNotThrow(() -> BedrockAssetValidator.validateGeometry(document, "fixture.json"));
    }

    @Test
    void rejectsUnknownDescriptionFieldsInsteadOfSilentlyIgnoringThem() {
        JsonObject document = validDocument();
        document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonObject("description").addProperty("unexpected", true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateGeometry(document, "fixture.json"));
        assertTrue(error.getMessage().contains("description.unexpected"));
    }

    @Test
    void rejectsUvOutsideTheDeclaredTextureGrid() {
        JsonObject document = validDocument();
        JsonObject north = firstCube(document).getAsJsonObject("uv").getAsJsonObject("north");
        north.add("uv", JsonParser.parseString("[15.5, 0]"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateGeometry(document, "fixture.json"));
        assertTrue(error.getMessage().contains("uv.north"));
    }

    @Test
    void rejectsImpossibleRotationAndDegenerateCubeSize() {
        JsonObject document = validDocument();
        firstBone(document).add("rotation", JsonParser.parseString("[361, 0, 0]"));

        IllegalStateException rotationError = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateGeometry(document, "fixture.json"));
        assertTrue(rotationError.getMessage().contains("rotation"));

        JsonObject degenerate = validDocument();
        firstCube(degenerate).add("size", JsonParser.parseString("[0, 1, 1]"));
        IllegalStateException sizeError = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateGeometry(degenerate, "fixture.json"));
        assertTrue(sizeError.getMessage().contains("size"));
    }

    @Test
    void acceptsDirectAnimationVectorsAndChecksBoneClosure() {
        assertDoesNotThrow(() -> BedrockAssetValidator.validateAnimation(
                validAnimationDocument(), "animation.fixture.json", Set.of("root")));
    }

    @Test
    void rejectsUnknownAnimationMetadataInsteadOfDroppingAKeyframe() {
        JsonObject document = validAnimationDocument();
        JsonObject frame = firstAnimationFrame(document);
        frame.addProperty("mystery_mode", "cubic");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateAnimation(
                        document, "animation.fixture.json", Set.of("root")));
        assertTrue(error.getMessage().contains("mystery_mode"));
    }

    @Test
    void rejectsPrePostKeyframesUntilTheirInterpolationIsImplemented() {
        JsonObject document = validAnimationDocument();
        JsonObject rotation = firstAnimationTrack(document).getAsJsonObject("rotation");
        rotation.add("0.0", JsonParser.parseString("{\"pre\":[0,0,0],\"post\":[1,0,0]}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateAnimation(
                        document, "animation.fixture.json", Set.of("root")));
        assertTrue(error.getMessage().contains("pre/post"));
    }

    @Test
    void checksTheSuppliedTextureDimensionsBeforeModelConstruction() throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        assertDoesNotThrow(() -> BedrockAssetValidator.validateTexture(
                new ByteArrayInputStream(bytes.toByteArray()), "texture.png", 128, 128));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BedrockAssetValidator.validateTexture(
                        new ByteArrayInputStream(bytes.toByteArray()), "texture.png", 64, 32));
        assertTrue(error.getMessage().contains("128x128"));
    }

    private static JsonObject validDocument() {
        return JsonParser.parseString("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.fixture",
                      "texture_width": 16,
                      "texture_height": 16
                    },
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [{
                        "origin": [0, 0, 0],
                        "size": [1, 1, 1],
                        "uv": {
                          "north": {"uv": [0, 0], "uv_size": [1, 1]},
                          "south": {"uv": [0, 0], "uv_size": [1, 1]},
                          "east": {"uv": [0, 0], "uv_size": [1, 1]},
                          "west": {"uv": [0, 0], "uv_size": [1, 1]},
                          "up": {"uv": [0, 0], "uv_size": [1, 1]},
                          "down": {"uv": [0, 1], "uv_size": [1, -1]}
                        }
                      }]
                    }]
                  }]
                }
                """).getAsJsonObject();
    }

    private static JsonObject firstBone(JsonObject document) {
        return document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject();
    }

    private static JsonObject firstCube(JsonObject document) {
        return firstBone(document).getAsJsonArray("cubes").get(0).getAsJsonObject();
    }

    private static JsonObject firstAnimationTrack(JsonObject document) {
        return document.getAsJsonObject("animations").getAsJsonObject("Test")
                .getAsJsonObject("bones").getAsJsonObject("root");
    }

    private static JsonObject firstAnimationFrame(JsonObject document) {
        return firstAnimationTrack(document).getAsJsonObject("rotation")
                .getAsJsonObject("0.0");
    }

    private static JsonObject validAnimationDocument() {
        return JsonParser.parseString("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "Test": {
                      "animation_length": 1.0,
                      "bones": {
                        "root": {
                          "rotation": {
                            "0.0": {"vector": [0, 0, 0]},
                            "1.0": {"vector": [1, 0, 0]}
                          }
                        }
                      }
                    }
                  }
                }
                """).getAsJsonObject();
    }
}
