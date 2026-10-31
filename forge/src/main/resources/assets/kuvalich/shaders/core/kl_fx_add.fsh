#version 150

#moj_import <fog.glsl>

// 几何图元的加法发光：软边全靠顶点色插值，雾里按距离熄灭

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(color.rgb * fade, color.a);
}
