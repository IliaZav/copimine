#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
layout(std140) uniform Wave5VolumeConfig {
    vec4 InvClip0; vec4 InvClip1; vec4 InvClip2; vec4 InvClip3;
    vec4 InvView0; vec4 InvView1; vec4 InvView2; vec4 InvView3;
    vec4 EyeTime; vec4 SafeMin; vec4 SafeMax; vec4 VolumeState;
    vec4 ArenaMin; vec4 ArenaMax;
};

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float cloudNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(cell), hash31(cell+vec3(1,0,0)), f.x),
                   mix(hash31(cell+vec3(0,1,0)), hash31(cell+vec3(1,1,0)), f.x), f.y),
               mix(mix(hash31(cell+vec3(0,0,1)), hash31(cell+vec3(1,0,1)), f.x),
                   mix(hash31(cell+vec3(0,1,1)), hash31(cell+vec3(1,1,1)), f.x), f.y), f.z);
}
float safeExit(vec3 origin, vec3 direction) {
    if (VolumeState.z < 0.5 || any(lessThan(origin, SafeMin.xyz))
            || any(greaterThan(origin, SafeMax.xyz))) return 0.0;
    // Starting inside our own platform: skip exactly its clear volume, not a view radius.
    vec3 edge = mix(SafeMin.xyz, SafeMax.xyz, greaterThan(direction, vec3(0)));
    vec3 denominator = mix(-max(abs(direction), vec3(0.00001)),
            max(abs(direction), vec3(0.00001)), greaterThanEqual(direction, vec3(0)));
    vec3 travel = (edge - origin) / denominator;
    return max(0.0, min(travel.x, min(travel.y, travel.z)));
}
vec2 axisSpan(float origin, float direction, float low, float high) {
    if (abs(direction) < 0.00001) return origin < low || origin > high
            ? vec2(1.0, -1.0) : vec2(-100000.0, 100000.0);
    float a = (low - origin) / direction;
    float b = (high - origin) / direction;
    return vec2(min(a, b), max(a, b));
}
vec2 arenaSpan(vec3 origin, vec3 direction) {
    vec2 x = axisSpan(origin.x, direction.x, ArenaMin.x, ArenaMax.x);
    vec2 y = axisSpan(origin.y, direction.y, ArenaMin.y, ArenaMax.y);
    vec2 z = axisSpan(origin.z, direction.z, ArenaMin.z, ArenaMax.z);
    return vec2(max(0.0, max(x.x, max(y.x, z.x))), min(x.y, min(y.y, z.y)));
}
void main() {
    vec4 source = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    mat4 inverseClip = mat4(InvClip0, InvClip1, InvClip2, InvClip3);
    mat4 inverseView = mat4(InvView0, InvView1, InvView2, InvView3);
    // 26.3 uses zero-to-one reversed depth: zero is sky, one is the near plane.
    vec4 viewPoint = inverseClip * vec4(texCoord * 2.0 - 1.0, max(depth, 0.000001), 1.0);
    vec3 viewPosition = viewPoint.xyz / max(abs(viewPoint.w), 0.000001);
    float sceneDistance = depth <= 0.000001 ? VolumeState.w : min(length(viewPosition), VolumeState.w);
    // No near-camera visibility bubble: even touching geometry is attenuated
    // by its real optical path through the smoke. Safe chambers omit this pass.
    if (VolumeState.x <= 0.0) { fragColor = source; return; }
    vec3 direction = normalize((inverseView * vec4(normalize(viewPosition), 0.0)).xyz);
    vec2 arena = arenaSpan(EyeTime.xyz, direction);
    float begin = max(arena.x, safeExit(EyeTime.xyz, direction));
    float end = min(arena.y, min(sceneDistance, begin + 4.5));
    if (end <= begin) { fragColor = source; return; }
    float stride = (end - begin) / 12.0;
    float transmittance = 1.0;
    vec3 scattered = vec3(0);
    for (int i = 0; i < 12; ++i) {
        float distanceAlongRay = begin + (float(i) + 0.5) * stride;
        vec3 point = EyeTime.xyz + direction * distanceAlongRay;
        if (any(lessThan(point, ArenaMin.xyz)) || any(greaterThan(point, ArenaMax.xyz))) continue;
        // World-locked three-dimensional rolling eddies, not screen-space grain or particles.
        vec3 drift = vec3(EyeTime.w * 0.065, -EyeTime.w * 0.025, EyeTime.w * 0.04);
        float eddy = 0.72 * cloudNoise(point * 0.54 + drift)
                + 0.28 * cloudNoise(point * 1.47 - drift * 1.6);
        float density = VolumeState.y * mix(0.72, 1.38, eddy);
        float extinction = 1.0 - exp(-density * stride);
        vec3 ink = mix(vec3(0.004,0.006,0.011), vec3(0.021,0.028,0.039), eddy * eddy);
        scattered += transmittance * extinction * ink;
        transmittance *= 1.0 - extinction;
        if (transmittance < 0.004) break;
    }
    vec3 fogged = source.rgb * transmittance + scattered;
    fragColor = vec4(mix(source.rgb, fogged, VolumeState.x), source.a);
}
