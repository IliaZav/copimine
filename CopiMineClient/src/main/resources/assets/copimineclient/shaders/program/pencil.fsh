#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    vec3 cx = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb;
    vec3 cy = texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y)).rgb;
    float base = dot(c, vec3(0.299, 0.587, 0.114));
    float edge = length(cx - c) + length(cy - c);
    float hatch = step(0.5, fract((texCoord.x + texCoord.y) * 180.0)) * 0.08 * Intensity;
    float sketch = clamp(base - edge * 1.6 - hatch, 0.0, 1.0);
    fragColor = vec4(vec3(sketch), 1.0);
}
