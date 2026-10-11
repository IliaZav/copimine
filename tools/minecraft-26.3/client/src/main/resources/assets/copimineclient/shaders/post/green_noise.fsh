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

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float grain = (hash(floor(texCoord * 800.0)) - 0.5) * 0.16 * Intensity;
    color = mix(color, vec3(dot(color, vec3(0.25, 0.65, 0.1))), 0.32 * Intensity);
    color.g += 0.14 * Intensity;
    color += vec3(grain * 0.4, grain, grain * 0.2);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
