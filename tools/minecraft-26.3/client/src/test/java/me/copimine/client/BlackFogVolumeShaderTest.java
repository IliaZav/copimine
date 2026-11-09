package me.copimine.client;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogVolumeShaderTest {
    @Test void minecraftParsesDepthInputAndTheWholeUniformBlock() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/copimineclient/post_effect/wave5_volume.json")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
            var config = net.minecraft.client.renderer.PostChainConfig.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            var first = config.passes().getFirst();
            var depth = (net.minecraft.client.renderer.PostChainConfig.TargetInput) first.inputs().get(1);
            assertTrue(depth.useDepthBuffer());
            assertEquals("Depth", depth.samplerName());
            assertEquals(14, first.uniforms().get("Wave5VolumeConfig").size());
        }
    }

    @Test void volumeShaderCompilesWithMinecraftShaderCompilerBackend() throws Exception {
        String source;
        try (var stream = getClass().getResourceAsStream("/assets/copimineclient/shaders/post/wave5_volume.fsh")) {
            assertNotNull(stream);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        long result = 0;
        try {
            Shaderc.shaderc_compile_options_set_forced_version_profile(options, 450, Shaderc.shaderc_profile_core);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            result = Shaderc.shaderc_compile_into_spv(compiler, source, Shaderc.shaderc_glsl_fragment_shader,
                    "wave5_volume.fsh", "main", options);
            assertNotEquals(0, result);
            assertEquals(Shaderc.shaderc_compilation_status_success, Shaderc.shaderc_result_get_compilation_status(result),
                    Shaderc.shaderc_result_get_error_message(result));
            assertTrue(Shaderc.shaderc_result_get_length(result) > 0);
        } finally {
            if (result != 0) Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
