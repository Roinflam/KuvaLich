#version 150

#moj_import <fog.glsl>

// 加法粒子：不按低透明度丢片元（软边、淡出的尾巴不会被切成硬边），
// 雾里按距离把颜色压到 0，加法混合下就是熄灭，不会往上加一层雾色

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(color.rgb * fade, color.a);
}
