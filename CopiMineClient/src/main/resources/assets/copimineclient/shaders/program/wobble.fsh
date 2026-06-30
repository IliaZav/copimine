#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 26.0) * 0.02 * Intensity;
    uv.y += cos(uv.x * 24.0) * 0.018 * Intensity;
    vec3 color = texture(DiffuseSampler, clamp(uv, 0.0, 1.0)).rgb;
    color *= vec3(1.02, 0.98, 1.08);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
