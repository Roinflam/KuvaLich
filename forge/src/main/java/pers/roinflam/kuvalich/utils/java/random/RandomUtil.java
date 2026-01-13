package pers.roinflam.kuvalich.utils.java.random;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数工具类
 * Random utility class
 */
public class RandomUtil {

    /**
     * 获取指定范围内的随机整数（包含边界）
     * Get random integer within range (inclusive)
     *
     * @param min 最小值 / minimum value
     * @param max 最大值 / maximum value
     * @return 随机整数 / random integer
     */
    public static int getInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * 概率判断（默认精度）
     * Probability check (default precision)
     *
     * @param probability 概率值（0-100）/ probability (0-100)
     * @return 是否命中 / whether hit
     */
    public static boolean percentageChance(double probability) {
        return percentageChance(probability, 8);
    }

    /**
     * 概率判断（指定精度）
     * Probability check (custom precision)
     *
     * @param probability 概率值（0-100）/ probability (0-100)
     * @param range 精度（小数位数）/ precision (decimal places)
     * @return 是否命中 / whether hit
     */
    private static boolean percentageChance(double probability, int range) {
        if (probability <= 0) {
            return false;
        }
        if (probability >= 100) {
            return true;
        }
        int multiplier = (int) Math.pow(10, range - 1);
        return ThreadLocalRandom.current().nextInt(100 * multiplier) < (int) (probability * multiplier);
    }

    /**
     * 将总数随机分配到指定数量的组中
     * Randomly distribute total number into specified groups
     *
     * @param totalNumber 总数 / total number
     * @param count 组数 / group count
     * @return 分配结果列表 / distribution result list
     */
    @Nonnull
    public static List<Integer> randomList(int totalNumber, int count) {
        @Nonnull List<Integer> list = new ArrayList<>(count);
        @Nonnull Random rand = ThreadLocalRandom.current();
        int leftNumber = totalNumber;
        int leftCount = count;

        for (int i = 0; i < count - 1; i++) {
            int number = 0;
            if (leftNumber > 0 && leftCount > 0) {
                int avg = leftNumber / leftCount;
                int maxAlloc = Math.max(1, avg * 2);
                number = rand.nextInt(maxAlloc) + 1;
                number = Math.min(number, leftNumber);
            }
            list.add(number);
            leftNumber -= number;
            leftCount--;
        }

        // 最后一组分配剩余的所有
        list.add(Math.max(0, leftNumber));
        return list;
    }
}