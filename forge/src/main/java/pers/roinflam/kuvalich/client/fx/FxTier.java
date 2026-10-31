package pers.roinflam.kuvalich.client.fx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 演出的档位：决定粒子 / 几何的预算、远处的取舍，以及混音时谁让谁
 *
 * <p>S：稀有的大事（玄骸登场、安魂判定），会压制同处的 B / C 档；A：重要但不独占；B：常见战斗反馈；C：高频小事。</p>
 */
@OnlyIn(Dist.CLIENT)
public enum FxTier {
    S,
    A,
    B,
    C;

    /** 是 S 或 A 档（预算里有一部分额度只留给它们） */
    public boolean isMajor() {
        return this == S || this == A;
    }
}
