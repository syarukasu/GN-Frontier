#version 150

uniform sampler2D Sampler0;
uniform vec4 LensBounds;
uniform vec4 EyeMotion;
uniform vec4 CopyRegion;
uniform int CircleMask;

out vec4 fragColor;

vec3 readLens(vec2 pixel) {
    vec2 insideCopy = clamp(pixel - CopyRegion.xy, vec2(0.5), CopyRegion.zw - 0.5);
    return texture(Sampler0, insideCopy / vec2(textureSize(Sampler0, 0))).rgb;
}

void main() {
    vec2 pixel = gl_FragCoord.xy;
    vec2 lens = (pixel - LensBounds.xy) / LensBounds.zw;
    if (CircleMask == 1 && dot(lens, lens) > 1.0) discard;

    vec3 shadow = vec3(0.008, 0.011, 0.014);
    float aim = EyeMotion.x;
    float stride = EyeMotion.y;
    vec2 stepPhase = EyeMotion.zw;
    float opening = smoothstep(0.60, 0.985, aim);
    vec2 pupil = vec2(0.32, -0.80) * (1.0 - opening) + vec2(0.38, 0.20) * stepPhase * stride;
    float pupilRadius = mix(0.18, 1.20, opening) - 0.16 * stride;
    float softEdge = mix(0.14, 0.035, opening) + 0.045 * stride;
    float visibility = smoothstep(0.0, 0.20, opening)
        * (1.0 - smoothstep(pupilRadius - softEdge, pupilRadius, length(lens - pupil)));
    if (visibility <= 0.0) {
        fragColor = vec4(shadow, 1.0);
        return;
    }

    vec3 scene = readLens(pixel);
    if (stride > 0.0) {
        // Symmetric taps keep the magnified sight picture centred on the reticle.
        float blurPixels = clamp(min(LensBounds.z, LensBounds.w) * 0.019, 0.8, 5.0);
        vec2 offset = vec2(mix(0.6, 1.0, abs(stepPhase.x)), 0.45 * stepPhase.y) * blurPixels * stride;
        scene = scene * 0.44 + (readLens(pixel - offset) + readLens(pixel + offset)) * 0.28;
    }
    fragColor = vec4(mix(shadow, scene, visibility), 1.0);
}
