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
    vec2 centered = texCoord - vec2(0.5, 0.5);
    float radial = length(centered);
    vec2 shift = centered * 0.015 * Intensity;
    float scan = sin(texCoord.y * 220.0) * 0.02 * Intensity;

    vec4 red = texture(InSampler, texCoord + shift + vec2(scan, 0.0));
    vec4 green = texture(InSampler, texCoord);
    vec4 blue = texture(InSampler, texCoord - shift - vec2(scan, 0.0));

    vec3 color = vec3(red.r, green.g, blue.b);
    color = mix(color, vec3(color.b, color.r, color.g), 0.18 + radial * 0.35 * Intensity);
    color += vec3(0.06, 0.02, 0.08) * Intensity;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
