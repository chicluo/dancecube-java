package com.chicluo.dancecube.analysis;

/**
 * 战力公式与评级阈值，逐行对标 Python 版 {@code core/client.py} 的
 * {@code compute_rating} / {@code grade_of}。
 *
 * <p>单曲战力 = f(定数, ACC)：100% 时 (定数+2)*100，再按 98/95/90/85/80/75/70
 * 分档线性插值；ACC 先按百分之一取整（银行家舍入）并夹在 0..10000。
 * 定数上限按 19 归一（{@code k = max(0, 19 - level) / 19}），所以 20 级谱面的
 * 低 ACC 档位不会继续上抬。</p>
 *
 * <p>注意 Python 版的 {@code int(...)} 是向零截断、{@code round()} 是
 * half-to-even，Java 侧分别用 {@code (int)} 与 {@link Math#rint(double)} 对齐。
 * level=1 且 ACC&lt;98% 时 b[4] 为负，结果可能是负数（如 level=1, acc=85 → -5），
 * 这里刻意保留，与 Python 版一致。</p>
 */
public final class RatingCalculator {

    /** 定数合法区间（含端点），与 Python 版 {@code level < 1 or level > 20} 对应。 */
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 20;
    /** 定数归一上限：19 级及以上 k = 0。 */
    private static final double LEVEL_NORM = 19.0;

    private RatingCalculator() {
    }

    /**
     * 计算单曲战力。
     *
     * @param level 定数（1..20，越界返回 0）
     * @param acc   达成率（0..100，超界按 0 / 100 处理，精确到 0.01）
     * @return 单曲战力，截断取整
     */
    public static int computeRating(int level, double acc) {
        // Python: max(0, min(int(round(acc * 100)), 10000))
        // round() 是 half-to-even，Math.rint 同为 IEEE round-to-nearest-even。
        int accInt = (int) Math.rint(acc * 100.0);
        accInt = Math.max(0, Math.min(accInt, 10000));
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            return 0;
        }
        double k = Math.max(0.0, LEVEL_NORM - level) / LEVEL_NORM;
        double[] b = {
                (level + 2) * 100.0,
                (level + 1) * 100.0,
                level * 100.0,
                (level - 1) * 100.0,
                (level - 2 + k) * 100.0,
                (level - 3 + 2 * k) * 100.0,
                (level - 4 + 3 * k) * 100.0,
                (level - 5 + 4 * k) * 100.0,
        };
        double base;
        double off;
        if (accInt == 10000) {
            base = b[0];
            off = 0.0;
        } else if (accInt >= 9800) {
            base = b[1];
            off = (accInt - 9800) / 200.0 * 100;
        } else if (accInt >= 9500) {
            base = b[2];
            off = (accInt - 9500) / 300.0 * 100;
        } else if (accInt >= 9000) {
            base = b[3];
            off = (accInt - 9000) / 500.0 * 100;
        } else if (accInt >= 8500) {
            base = b[4];
            off = (accInt - 8500) / 500.0 * (b[3] - b[4]);
        } else if (accInt >= 8000) {
            base = b[5];
            off = (accInt - 8000) / 500.0 * (b[4] - b[5]);
        } else if (accInt >= 7500) {
            base = b[6];
            off = (accInt - 7500) / 500.0 * (b[5] - b[6]);
        } else if (accInt >= 7000) {
            base = b[7];
            off = (accInt - 7000) / 500.0 * (b[6] - b[7]);
        } else {
            base = 0.0;
            off = 0.0;
        }
        return (int) (base + off);
    }

    /**
     * 单曲评级：满分且零 miss 为 AP，零 miss 为 FC，否则空串。
     *
     * <p>与 Python 版 {@code grade_of(acc, miss)} 一致：阈值用的是 100.0，
     * 不是 99.995（后者只用于 ACC 分布图的分档）。</p>
     *
     * @param acc  达成率
     * @param miss miss 数
     * @return "AP" / "FC" / ""
     */
    public static String gradeOf(double acc, int miss) {
        if (acc >= 100.0 && miss == 0) {
            return "AP";
        }
        if (miss == 0) {
            return "FC";
        }
        return "";
    }
}
