package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderpackExporterTest {
    @Test
    void continuesExportingAfterAnIndividualProfileFails() {
        ShaderpackRegistry.ShaderpackProfile broken = profile("broken");
        ShaderpackRegistry.ShaderpackProfile valid = profile("valid");

        Map<String, ShaderpackExporter.ExportResult> results = ShaderpackExporter.exportProfiles(
                List.of(broken, valid),
                profile -> {
                    if (profile == broken) throw new IOException("invalid bundled profile");
                    return result(profile, true, "exported");
                },
                (profile, error) -> result(profile, false,
                        "export-failed:" + error.getClass().getSimpleName()));

        assertEquals(2, results.size());
        assertFalse(results.get("broken.zip").validZip());
        assertEquals("export-failed:IOException", results.get("broken.zip").status());
        assertTrue(results.get("valid.zip").validZip());
        assertEquals("exported", results.get("valid.zip").status());
    }

    @Test
    void continuesAfterFailureResultPathResolutionThrows() {
        ShaderpackRegistry.ShaderpackProfile broken = profile("broken");
        ShaderpackRegistry.ShaderpackProfile valid = profile("valid");
        IOException original = new IOException("profile export failed");
        IllegalArgumentException resolutionFailure = new IllegalArgumentException("invalid target path");

        Map<String, ShaderpackExporter.ExportResult> results = ShaderpackExporter.exportProfiles(
                List.of(broken, valid),
                profile -> {
                    if (profile == broken) throw original;
                    return result(profile, true, "exported");
                },
                (profile, error) -> {
                    if (profile == broken) throw resolutionFailure;
                    return result(profile, false, "unexpected");
                });

        assertEquals(2, results.size());
        assertFalse(results.get("broken.zip").validZip());
        assertEquals("export-failed:IOException", results.get("broken.zip").status());
        assertNull(results.get("broken.zip").target());
        assertNull(results.get("broken.zip").runtimeTarget());
        assertNull(results.get("broken.zip").runtimeName());
        assertSame(resolutionFailure, original.getSuppressed()[0]);
        assertTrue(results.get("valid.zip").validZip());
        assertEquals("exported", results.get("valid.zip").status());
    }

    private static ShaderpackRegistry.ShaderpackProfile profile(String id) {
        return new ShaderpackRegistry.ShaderpackProfile(
                id, id, id + ".zip", "assets/copimineclient/shaders/" + id + ".zip",
                ShaderpackRegistry.RuntimeKind.IRIS_SHADERPACK, "CHAOS", false, 1,
                List.of(), id, id + ".zip", "test profile");
    }

    private static ShaderpackExporter.ExportResult result(
            ShaderpackRegistry.ShaderpackProfile profile, boolean valid, String status) {
        return new ShaderpackExporter.ExportResult(
                null, null, "copimine_" + profile.id() + ".zip",
                valid, valid, false, false, "", status);
    }
}
