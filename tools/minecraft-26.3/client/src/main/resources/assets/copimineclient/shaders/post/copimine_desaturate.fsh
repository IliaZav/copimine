#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform IntensityConfig {
    float Intensity;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    float luminance = dot(original.rgb, vec3(0.299, 0.587, 0.114));
    vec3 color = mix(original.rgb, vec3(luminance), clamp(Intensity, 0.0, 1.0));
    fragColor = vec4(clamp(color, 0.0, 1.0), original.a);
}
