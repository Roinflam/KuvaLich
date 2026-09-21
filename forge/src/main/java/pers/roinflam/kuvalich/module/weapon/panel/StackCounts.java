package pers.roinflam.kuvalich.module.weapon.panel;

import pers.roinflam.kuvalich.module.KillStackManager.StackType;

/**
 * 击杀叠层的数据来源
 *
 * <p>抽成接口是为了让数据层不直接依赖 {@code KillStackManager} 的客户端 / 服务端分支：
 * tooltip 传入的是客户端镜像，战斗端传入的是服务端权威数据，JEI 配方页传入的是 {@link #NONE}。</p>
 *
 * @author RoinFlam
 */
public interface StackCounts {

    /** 完全没有叠层数据（JEI 配方页、创造栏、非本地玩家） */
    StackCounts NONE = new StackCounts() {
        @Override
        public int stacks(StackType type) {
            return 0;
        }

        @Override
        public int maxStacks(StackType type) {
            return type.getMaxStacks();
        }

        @Override
        public boolean available() {
            return false;
        }
    };

    /** 当前层数 */
    int stacks(StackType type);

    /** 上限层数 */
    int maxStacks(StackType type);

    /**
     * 叠层数据是否可用。
     *
     * <p>⭐ 必须与「层数为 0」区分开：{@code ○○○○○ 0/5} 表示「确实掉光了」，
     * {@code ????? —/5} 表示「不知道」。两者混为一谈的话，玩家会把
     * 「服务端没装这个 mod」误读成「机制坏了」。</p>
     */
    boolean available();

    /** 距离掉一层还有多少 tick；未知返回 -1 */
    default int decayTicks(StackType type) {
        return -1;
    }
}
