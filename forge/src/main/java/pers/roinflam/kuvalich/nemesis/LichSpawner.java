package pers.roinflam.kuvalich.nemesis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 专属玄骸的落点（纯服务端）
 *
 * <p>两种落点：</p>
 * <ul>
 *   <li><b>看不见的</b>（狩猎到场、卡住换位）：随机方向、一定距离，要么三条视线都被方块挡住，要么在玩家背后；</li>
 *   <li><b>面前的</b>（安魂通牒、管理命令）：视线方向左右 30° 的扇形、5~9 格，不要求看不见。</li>
 * </ul>
 * <p>站立判定两种一样：脚下方块上表面结实，玄骸的整个包围盒放得下、不碰液体、岩浆、火和细雪。
 * 不看亮度、不看时间，也不受怪物数量上限影响。</p>
 *
 * <p>幼体、奴仆小队也用这里找落点，传自己的碰撞箱尺寸（带 {@code halfWidth} / {@code height} 的那几个重载）。</p>
 */
public final class LichSpawner {

    /** 玄骸包围盒的宽、高（和实体注册的尺寸一致） */
    private static final double HALF_WIDTH = 1.75D / 2.0D;
    private static final double HEIGHT = 3.4D;
    /** 纵向找站立点：从玩家脚下往上几格、往下几格 */
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 10;
    /** 「在背后」的夹角下限（度） */
    private static final double BEHIND_ANGLE = 75.0D;
    /** 面前落点的扇形半角（度）和距离 */
    private static final double FRONT_HALF_ANGLE = 30.0D;
    private static final int FRONT_MIN = 5;
    private static final int FRONT_MAX = 9;

    private LichSpawner() {
    }

    /**
     * 找一个看不见的落点；找不到返回 null。
     *
     * @param tries   这一次最多试几个方向（每 tick 24 次，分几 tick 调）
     * @param relaxed 放宽：不要求看不见，但优先取在背后的；最近距离由调用方降低
     */
    @Nullable
    public static Vec3 findHidden(ServerPlayer owner, int minDist, int maxDist, int tries, boolean relaxed) {
        return findHidden(owner, minDist, maxDist, tries, relaxed, HALF_WIDTH, HEIGHT);
    }

    /** 同上，按给定的碰撞箱（半宽、高）判能不能站、挡不挡得住 */
    @Nullable
    public static Vec3 findHidden(ServerPlayer owner, int minDist, int maxDist, int tries, boolean relaxed,
                                  double halfWidth, double height) {
        ServerLevel level = owner.serverLevel();
        RandomSource random = owner.getRandom();
        Vec3 fallback = null;
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + random.nextDouble() * Math.max(0, maxDist - minDist);
            double x = owner.getX() + Math.cos(angle) * dist;
            double z = owner.getZ() + Math.sin(angle) * dist;
            Vec3 feet = findStanding(level, x, z, Mth.floor(owner.getY()), halfWidth, height);
            if (feet == null) {
                continue;
            }
            boolean behind = isBehind(owner, feet);
            if (relaxed) {
                if (behind) {
                    return feet;
                }
                if (fallback == null) {
                    fallback = feet;
                }
                continue;
            }
            if (behind || isOccluded(level, owner, feet, height)) {
                return feet;
            }
        }
        return fallback;
    }

    /**
     * 找一个面前的落点（视线方向 ±30°、5~9 格）；{@code preferred} 不为空时先试它（比如通牒点到的那一格外侧）。
     * 找不到返回 null，由调用方退回看不见的落点
     */
    @Nullable
    public static Vec3 findFront(ServerPlayer owner, @Nullable BlockPos preferred, int tries) {
        return findFront(owner, preferred, tries, HALF_WIDTH, HEIGHT);
    }

    /** 同上，按给定的碰撞箱 */
    @Nullable
    public static Vec3 findFront(ServerPlayer owner, @Nullable BlockPos preferred, int tries, double halfWidth,
                                 double height) {
        ServerLevel level = owner.serverLevel();
        if (preferred != null) {
            Vec3 feet = standingAt(level, preferred, halfWidth, height);
            if (feet != null) {
                return feet;
            }
        }
        RandomSource random = owner.getRandom();
        double yawRad = Math.toRadians(owner.getYRot());
        // MC 的朝向：yaw 0 朝 +Z，-sin / +cos
        for (int i = 0; i < tries; i++) {
            double off = Math.toRadians((random.nextDouble() * 2.0D - 1.0D) * FRONT_HALF_ANGLE);
            double dist = FRONT_MIN + random.nextDouble() * (FRONT_MAX - FRONT_MIN);
            double x = owner.getX() - Math.sin(yawRad + off) * dist;
            double z = owner.getZ() + Math.cos(yawRad + off) * dist;
            Vec3 feet = findStanding(level, x, z, Mth.floor(owner.getY()), halfWidth, height);
            if (feet != null) {
                return feet;
            }
        }
        return null;
    }

    /**
     * 在 (x, z) 这一列、玩家脚下 +6 到 -10 格之间从上往下找第一处能站的地方，返回脚底中心；没有返回 null
     */
    @Nullable
    static Vec3 findStanding(ServerLevel level, double x, double z, int ownerY) {
        return findStanding(level, x, z, ownerY, HALF_WIDTH, HEIGHT);
    }

    @Nullable
    public static Vec3 findStanding(ServerLevel level, double x, double z, int ownerY, double halfWidth, double height) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        BlockPos column = new BlockPos(bx, ownerY, bz);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) {
            return null;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = ownerY + SCAN_UP; y >= ownerY - SCAN_DOWN; y--) {
            pos.set(bx, y, bz);
            Vec3 feet = standingAt(level, pos, halfWidth, height);
            if (feet != null) {
                return feet;
            }
        }
        return null;
    }

    /** 玄骸能不能站在 {@code feet} 这一格（脚底所在方块）上；能就返回脚底中心 */
    @Nullable
    public static Vec3 standingAt(ServerLevel level, BlockPos feet) {
        return standingAt(level, feet, HALF_WIDTH, HEIGHT);
    }

    /** 同上，按给定的碰撞箱 */
    @Nullable
    public static Vec3 standingAt(ServerLevel level, BlockPos feet, double halfWidth, double height) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + Mth.ceil(height) >= level.getMaxBuildHeight()) {
            return null;
        }
        if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) {
            return null;
        }
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        Vec3 center = new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        AABB box = new AABB(center.x - halfWidth, center.y, center.z - halfWidth,
                center.x + halfWidth, center.y + height, center.z + halfWidth);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
            return null;
        }
        // 碰不得的方块：岩浆（containsAnyLiquid 已经算了）、火、细雪，以及脚下的岩浆块之类会伤人的
        boolean hazard = BlockPos.betweenClosedStream(box.expandTowards(0.0D, -0.5D, 0.0D))
                .anyMatch(p -> isHazard(level.getBlockState(p)));
        return hazard ? null : center;
    }

    private static boolean isHazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.CACTUS);
    }

    /** 主人水平朝向和「主人 → 落点」的夹角大于 75°（在背后） */
    static boolean isBehind(ServerPlayer owner, Vec3 feet) {
        double dx = feet.x - owner.getX();
        double dz = feet.z - owner.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            return false;
        }
        double yawRad = Math.toRadians(owner.getYRot());
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double cos = (dx * lookX + dz * lookZ) / len;
        return cos < Math.cos(Math.toRadians(BEHIND_ANGLE));
    }

    /** 主人眼睛到玄骸脚、腰、头三条线都被方块挡住 */
    static boolean isOccluded(ServerLevel level, ServerPlayer owner, Vec3 feet) {
        return isOccluded(level, owner, feet, HEIGHT);
    }

    /** 主人眼睛到脚、腰、头（按身高 {@code height} 取）三条线都被方块挡住 */
    static boolean isOccluded(ServerLevel level, ServerPlayer owner, Vec3 feet, double height) {
        Vec3 eye = owner.getEyePosition();
        double[] heights = {0.2D, height * 0.5D, height - 0.2D};
        for (double h : heights) {
            Vec3 to = feet.add(0.0D, h, 0.0D);
            HitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }
}
