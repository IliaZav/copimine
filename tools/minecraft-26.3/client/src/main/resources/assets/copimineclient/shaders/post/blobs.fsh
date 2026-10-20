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
    vec2 centerA = vec2(0.35, 0.42);
    vec2 centerB = vec2(0.68, 0.58);
    vec2 diffA = uv - centerA;
    vec2 diffB = uv - centerB;
    float influenceA = 1.0 - smoothstep(0.0, 0.25, length(diffA));
    float influenceB = 1.0 - smoothstep(0.0, 0.22, length(diffB));
    uv += normalize(diffA + vec2(0.0001)) * influenceA * 0.035 * Intensity;
    uv -= normalize(diffB + vec2(0.0001)) * influenceB * 0.03 * Intensity;
    vec3 color = texture(InSampler, clamp(uv, 0.0, 1.0)).rgb;
    color = mix(color, vec3(color.r * 0.9, color.g * 0.95, color.b * 1.08), 0.35 * Intensity);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
