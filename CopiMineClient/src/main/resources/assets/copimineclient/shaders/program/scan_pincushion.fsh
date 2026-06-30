#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radial = dot(centered, centered);
    vec2 warped = centered * (1.0 + radial * 0.18 * Intensity);
    vec2 uv = warped * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 color = texture(DiffuseSampler, uv).rgb;
    float scan = 0.90 + sin(uv.y * 420.0) * 0.08 * Intensity;
    float vignette = smoothstep(1.1, 0.15, radial);
    color *= scan * vignette;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
