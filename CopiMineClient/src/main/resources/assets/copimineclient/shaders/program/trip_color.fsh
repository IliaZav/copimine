#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5, 0.5);
    float radial = length(centered);
    vec2 shift = centered * 0.015 * Intensity;
    float scan = sin(texCoord.y * 220.0) * 0.02 * Intensity;

    vec4 red = texture(DiffuseSampler, texCoord + shift + vec2(scan, 0.0));
    vec4 green = texture(DiffuseSampler, texCoord);
    vec4 blue = texture(DiffuseSampler, texCoord - shift - vec2(scan, 0.0));

    vec3 color = vec3(red.r, green.g, blue.b);
    color = mix(color, vec3(color.b, color.r, color.g), 0.18 + radial * 0.35 * Intensity);
    color += vec3(0.06, 0.02, 0.08) * Intensity;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
