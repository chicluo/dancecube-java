package com.chicluo.dancecube.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 战力公式与评级阈值对拍。
 *
 * <p>所有期望值都是 Python 3.12 直接 import 参考实现
 * {@code core.client.compute_rating / grade_of} 实跑得到的，不是手推。</p>
 */
class RatingCalculatorTest {

    @Test
    @DisplayName("compute_rating：各插值档位与 Python 版逐值一致")
    void computeRatingMatchesPythonBaseline() {
        assertAll(
                // 98 档：（15+1)*100 + (9950-9800)/200*100 = 1675
                () -> assertRating(15, 100.0, 1700),
                () -> assertRating(15, 99.5, 1675),
                () -> assertRating(15, 98.0, 1600),
                // 95 档：1500 + (9750-9500)/300*100 = 1583.33 → 截断 1583
                () -> assertRating(15, 97.5, 1583),
                () -> assertRating(15, 95.0, 1500),
                () -> assertRating(15, 92.0, 1440),
                () -> assertRating(15, 88.0, 1368),
                () -> assertRating(15, 82.0, 1273),
                () -> assertRating(15, 77.0, 1194),
                () -> assertRating(15, 72.0, 1115),
                // 70 以下直接 0
                () -> assertRating(15, 60.0, 0),
                // 定数 19/20：k=0，档位纯整数
                () -> assertRating(19, 90.0, 1800),
                () -> assertRating(20, 85.0, 1800),
                () -> assertRating(20, 100.0, 2200),
                // 定数越界
                () -> assertRating(0, 100.0, 0),
                () -> assertRating(21, 100.0, 0),
                // level=1 的 b[4] 为负：Python 截断得 -5，刻意保留
                () -> assertRating(1, 85.0, -5),
                // 银行家舍入：95.005*100 在二进制里略小于 9500.5，round 得 9500 档
                () -> assertRating(12, 95.005, 1200),
                // 99.995 → 9999.5 取偶得 10000，走满分档；99.994 → 9999 走 98 档
                () -> assertRating(13, 99.995, 1500),
                () -> assertRating(13, 99.994, 1499),
                () -> assertRating(13, 99.999, 1500),
                // 各档位边界：89.99 / 90.0 / 94.99 / 95.0 / 97.99 / 98.0 / 99.99
                () -> assertRating(18, 89.99, 1699),
                () -> assertRating(18, 90.0, 1700),
                () -> assertRating(18, 94.99, 1799),
                () -> assertRating(18, 95.0, 1800),
                () -> assertRating(18, 97.99, 1899),
                () -> assertRating(18, 98.0, 1900),
                () -> assertRating(18, 99.99, 1999),
                () -> assertRating(18, 84.995, 1605),
                () -> assertRating(18, 89.995, 1700),
                // 低定数的 k 加权
                () -> assertRating(2, 70.0, 57),
                () -> assertRating(2, 69.99, 0),
                () -> assertRating(10, 75.0, 742),
                () -> assertRating(10, 74.99, 742),
                // acc 夹取：>100 按 100 算，负数按 0 算
                () -> assertRating(12, 100.004, 1400),
                () -> assertRating(12, 100.006, 1400),
                () -> assertRating(12, 99.999, 1400),
                () -> assertRating(12, -0.5, 0));
    }

    @Test
    @DisplayName("grade_of：AP / FC / 空串的阈值")
    void gradeOfMatchesPythonBaseline() {
        assertAll(
                () -> assertGrade(100.0, 0, "AP"),
                // 满分但有 miss 不给 AP
                () -> assertGrade(100.0, 1, ""),
                () -> assertGrade(100.01, 0, "AP"),
                // 不到 100 一律 FC
                () -> assertGrade(99.99, 0, "FC"),
                () -> assertGrade(99.995, 0, "FC"),
                () -> assertGrade(95.0, 0, "FC"),
                () -> assertGrade(99.99, 1, ""),
                () -> assertGrade(100.0, 5, ""));
    }

    private static void assertRating(int level, double acc, int expected) {
        assertEquals(expected, RatingCalculator.computeRating(level, acc),
                "computeRating(" + level + ", " + acc + ")");
    }

    private static void assertGrade(double acc, int miss, String expected) {
        assertEquals(expected, RatingCalculator.gradeOf(acc, miss),
                "gradeOf(" + acc + ", " + miss + ")");
    }
}
