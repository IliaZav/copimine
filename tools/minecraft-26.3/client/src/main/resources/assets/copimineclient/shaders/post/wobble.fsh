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
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 26.0) * 0.02 * Intensity;
    uv.y += cos(uv.x * 24.0) * 0.018 * Intensity;
    vec3 color = texture(InSampler, clamp(uv, 0.0, 1.0)).rgb;
    color *= vec3(1.02, 0.98, 1.08);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
