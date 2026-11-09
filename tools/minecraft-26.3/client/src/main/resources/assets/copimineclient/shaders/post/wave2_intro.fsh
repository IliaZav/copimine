#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
layout(std140) uniform IntroConfig {
    float InvertStrength;
    float DarknessStrength;
};
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    // Same color inversion as vanilla minecraft:post/invert, faded during the scream.
    vec3 color = mix(original.rgb, vec3(1.0) - original.rgb, InvertStrength);
    fragColor = vec4(color * (1.0 - DarknessStrength), 1.0);
}
