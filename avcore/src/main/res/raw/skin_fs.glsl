#version 300 es
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#else
#define highp
#define mediump
#define lowp
#endif
in vec2 vTextureCoord;
out vec4 vFragColor;

uniform sampler2D inputImageTexture;
uniform sampler2D srcBlurImageTexture;
uniform sampler2D diffBlurImageTexture;
uniform float skin_alpha;

void main() {
    vec4 srcColor = texture(inputImageTexture, vTextureCoord);
    vec4 srcBlurColor = texture(srcBlurImageTexture, vTextureCoord);
    vec4 diffBlurColor = texture(diffBlurImageTexture, vTextureCoord);

    float p = clamp((min(srcColor.r, srcBlurColor.r - 0.1) - 0.2) * 4.0, 0.0, 1.0);
    float meanVar = (diffBlurColor.r + diffBlurColor.g + diffBlurColor.b) / 3.0;
    float theta = 0.2;
    float kMin = (1.0 - meanVar / (meanVar + theta)) * skin_alpha;
    vec3 resultColor = mix(srcColor.rgb, srcBlurColor.rgb, max(p, kMin));
    vFragColor = vec4(resultColor, srcColor.a);
}
