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
    vec2 centered = texCoord - vec2(0.5);
    float radial = length(centered);
    vec2 wobble = vec2(
        sin((texCoord.y + radial) * 34.0),
        cos((texCoord.x - radial) * 29.0)
    ) * 0.025 * Intensity;
    vec2 uv = clamp(texCoord + wobble, 0.0, 1.0);
    vec2 aberration = centered * 0.02 * Intensity;

    vec3 color;
    color.r = texture(InSampler, clamp(uv + aberration, 0.0, 1.0)).r;
    color.g = texture(InSampler, uv).g;
    color.b = texture(InSampler, clamp(uv - aberration, 0.0, 1.0)).b;
    color = mix(color, vec3(color.g, color.b, color.r), 0.25 + radial * 0.35 * Intensity);
    color += vec3(0.10, -0.02, 0.14) * Intensity;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
