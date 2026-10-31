#version 150

// 通讯窗合成：把离屏缓冲里画好的窗内画面贴到屏幕上，顺带做羽化、开收框和干扰。
// 坐标一律是窗内比例，左上角 (0, 0)、往下 v 变大；缓冲是 GL 纹理、原点在左下，取样时翻 v。
// 缓冲里的颜色是预乘过 alpha 的（往透明缓冲里正常混合出来的就是预乘色），输出也是预乘色，
// 混合方式由代码设成 ONE, ONE_MINUS_SRC_ALPHA。

uniform sampler2D Sampler0;   // 窗内画面
uniform sampler2D Sampler1;   // 溶解噪声，可平铺

uniform vec2 Feather;         // 四边 smoothstep 羽化宽度：x 占窗宽、y 占窗高
uniform float Alpha;          // 主体透明度（开框的分级淡入）
uniform float Flash;          // 整窗提亮倍数（玄骸收起前那一下）
uniform vec4 GhostA;          // 色差副本 A：rgb 色，w 往右挪多少（窗宽比例，负数往左）
uniform vec4 GhostB;          // 色差副本 B
uniform vec2 GhostAlpha;      // 两个副本的强度
uniform vec2 Drag;            // 开框拖影：x 往右挪多少，y 透明度
uniform vec4 Band;            // 水平错位带：x 中心、y 半高（窗高比例），z 横移、w 红青色差（窗宽比例）
uniform vec3 Dissolve;        // 溶解：x 阈值、y 亮边宽度（噪声值），z > 0.5 才开
uniform vec3 EdgeColor;       // 溶解亮边的颜色
uniform vec2 Glitch;          // 到场故障：x 下半窗染青的量，y 下半窗横移
uniform vec2 NoiseScale;      // 噪声铺几遍

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 tap(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return vec4(0.0);
    }
    vec2 f = smoothstep(vec2(0.0), Feather, uv) * smoothstep(vec2(0.0), Feather, vec2(1.0) - uv);
    return texture(Sampler0, vec2(uv.x, 1.0 - uv.y)) * (f.x * f.y);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = texCoord0;

    float inBand = 1.0 - step(Band.y, abs(uv.y - Band.x));
    uv.x -= Band.z * inBand;
    float lower = step(0.5, uv.y);
    uv.x -= Glitch.y * lower;

    vec4 c = tap(uv);
    if (inBand > 0.0 && Band.w != 0.0) {
        vec4 r = tap(uv + vec2(Band.w, 0.0));
        vec4 b = tap(uv - vec2(Band.w, 0.0));
        c = vec4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));
    }

    // 色差副本只往亮里叠（取较亮的那个），暗处不会被副本盖脏
    if (GhostAlpha.x > 0.0) {
        vec4 g = tap(uv - vec2(GhostA.w, 0.0));
        c.rgb = mix(c.rgb, max(c.rgb, GhostA.rgb * luma(g.rgb) * 1.6), GhostAlpha.x);
    }
    if (GhostAlpha.y > 0.0) {
        vec4 g = tap(uv - vec2(GhostB.w, 0.0));
        c.rgb = mix(c.rgb, max(c.rgb, GhostB.rgb * luma(g.rgb) * 1.6), GhostAlpha.y);
    }

    // 下半窗染青：按原来的明暗染，人像的轮廓还认得出，不是一块平的青色
    vec3 cyan = vec3(0.31, 0.91, 1.0) * (luma(c.rgb) * 1.8 + 0.1 * c.a);
    c.rgb = mix(c.rgb, cyan, Glitch.x * lower);

    c *= Alpha;
    if (Drag.y > 0.0) {
        vec4 d = tap(uv - vec2(Drag.x, 0.0)) * Drag.y;
        c += d * (1.0 - c.a);
    }

    if (Dissolve.z > 0.5) {
        float n = texture(Sampler1, texCoord0 * NoiseScale).r;
        if (n < Dissolve.x) {
            c = vec4(0.0);
        } else if (n < Dissolve.x + Dissolve.y) {
            c = vec4(EdgeColor, 1.0) * c.a;
        }
    }

    c.rgb *= Flash;
    fragColor = c * vertexColor.a;
}
