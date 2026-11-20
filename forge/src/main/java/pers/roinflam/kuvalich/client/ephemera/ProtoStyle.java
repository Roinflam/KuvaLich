package pers.roinflam.kuvalich.client.ephemera;

/**
 * 正式画法的底：每个实体一份状态（动作 + 各款自己的拖尾、粒子），每帧先更新动作、备好画笔再交给各款画。
 * 颜色只用主色和它的深浅，渐变由渲染器画完后按顶点位置统一换（{@link #postGradient}）。
 *
 * <p>画法对象在渲染线程上复用一份，每帧的中间量放在字段里；测试要自己 new 一份，别和渲染线程共用。</p>
 */
public abstract class ProtoStyle<S extends ProtoStyle.State> implements EphemeraStyle {

    protected final EphemeraPen pen = new EphemeraPen();

    public static class State {
        public FxMotion m;
        Object owner;
    }

    protected abstract S newState();

    /** 新建、隔太久没画、换了体型时调一次，把自己的拖尾和粒子清掉 */
    protected abstract void reset(S st, FxMotion m);

    protected abstract void frame(S st, FxMotion m, EphemeraDraw d);

    @Override
    public boolean postGradient() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void draw(EphemeraInstance inst, EphemeraHost host, EphemeraDraw d) {
        S st;
        if (inst.styleState instanceof State s && s.owner == this) {
            st = (S) s;
        } else {
            st = newState();
            st.owner = this;
            st.m = new FxMotion(d.time);
            inst.styleState = st;
        }
        FxMotion m = st.m;
        m.update(inst, host, d);
        if (m.fresh) {
            reset(st, m);
        }
        pen.begin(d, m.color, m.glow);
        frame(st, m, d);
    }

    protected static float rnd(float a, float b) {
        return EphemeraFx.rand(a, b);
    }

    protected static float rnd() {
        return EphemeraFx.rand();
    }
}
