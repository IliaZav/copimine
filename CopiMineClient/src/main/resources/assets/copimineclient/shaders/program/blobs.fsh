#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 centerA = vec2(0.35, 0.42);
    vec2 centerB = vec2(0.68, 0.58);
    vec2 diffA = uv - centerA;
    vec2 diffB = uv - centerB;
    float influenceA = smoothstep(0.25, 0.0, length(diffA));
    float influenceB = smoothstep(0.22, 0.0, length(diffB));
    uv += normalize(diffA + vec2(0.0001)) * influenceA * 0.035 * Intensity;
    uv -= normalize(diffB + vec2(0.0001)) * influenceB * 0.03 * Intensity;
    vec3 color = texture(DiffuseSampler, clamp(uv, 0.0, 1.0)).rgb;
    color = mix(color, vec3(color.r * 0.9, color.g * 0.95, color.b * 1.08), 0.35 * Intensity);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
