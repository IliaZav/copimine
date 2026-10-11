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
    vec2 oneTexel = 1.0 / InSize;
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 cx = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb;
    vec3 cy = texture(InSampler, texCoord + vec2(0.0, oneTexel.y)).rgb;
    float base = dot(c, vec3(0.299, 0.587, 0.114));
    float edge = length(cx - c) + length(cy - c);
    float hatch = step(0.5, fract((texCoord.x + texCoord.y) * 180.0)) * 0.08 * Intensity;
    float sketch = clamp(base - edge * 1.6 - hatch, 0.0, 1.0);
    fragColor = vec4(vec3(sketch), 1.0);
}
