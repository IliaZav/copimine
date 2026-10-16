#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform IntensityConfig {
    float Intensity;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radial = dot(centered, centered);
    vec2 warped = centered * (1.0 + radial * 0.18 * Intensity);
    vec2 uv = warped * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 color = texture(InSampler, uv).rgb;
    float scan = 0.90 + sin(uv.y * 420.0) * 0.08 * Intensity;
    float vignette = 1.0 - smoothstep(0.15, 1.1, radial);
    color *= scan * vignette;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
