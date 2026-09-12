package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleRigTest {
    @Test
    void hierarchyIsUniqueAndSocketHasNoGeometry() {
        assertEquals(512, EndRiftTentacleRig.TEXTURE_SIZE);
        Set<String> names = new HashSet<>();
        for (EndRiftTentacleRig.BoneDefinition definition : EndRiftTentacleRig.definitions()) {
            assertTrue(names.add(definition.name()), definition.name());
            if (definition.parent() != null) {
                assertTrue(EndRiftTentacleRig.REQUIRED_BONES.contains(definition.parent()));
            }
        }
        assertEquals(EndRiftTentacleRig.REQUIRED_BONES.size(), names.size());
        EndRiftTentacleRig.BoneDefinition socket = EndRiftTentacleRig.definitions().stream()
                .filter(definition -> "grab_socket".equals(definition.name()))
                .findFirst().orElseThrow();
        assertFalse(socket.hasGeometry());
        assertEquals("tip", socket.parent());
    }

    @Test
    void dimensionsMatchTheArtistBrief() {
        float height = EndRiftTentacleRig.definitions().stream()
                .filter(definition -> definition.hasGeometry()
                        && ("base".equals(definition.name())
                        || definition.name().startsWith("seg_")
                        || "tip".equals(definition.name())))
                .map(EndRiftTentacleRig.BoneDefinition::length)
                .reduce(0.0F, Float::sum);
        assertTrue(height >= 4.5F && height <= 5.0F, "height=" + height);
        EndRiftTentacleRig.BoneDefinition base = EndRiftTentacleRig.definitions().stream()
                .filter(definition -> "base".equals(definition.name()))
                .findFirst().orElseThrow();
        assertTrue(base.width() >= 1.1F && base.width() <= 1.3F);
    }
}
