package com.chicluo.dancecube.analysis;

import com.chicluo.dancecube.model.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战力榜选取与统计对拍。
 *
 * <p>期望值按 Python 版口径手算：{@code Player.best(n)} 是
 * {@code sorted(official, key=-rating)[:n]}，{@code Player.ap(n)} 先筛
 * {@code grade == "AP"} 再降序取前 n；{@code build()} 的 total/average
 * 就是榜内 rating 求和 / 求平均。</p>
 */
class RatioAnalyzerTest {

    @Test
    @DisplayName("b30：官谱 rating 前 30、排除自制谱、总分/平均手算一致")
    void b30PicksTopThirtyOfficialScores() {
        List<Score> scores = new ArrayList<>();
        // 官谱 40 首：rating 101..140；其中 id<=20 为 AP、21..30 为 FC、31..40 无评级
        for (int i = 1; i <= 40; i++) {
            String grade = i <= 20 ? "AP" : (i <= 30 ? "FC" : "");
            double acc = i <= 20 ? 100.0 : (i <= 30 ? 99.0 : 95.0);
            scores.add(score(i, 1, 100 + i, grade, acc));
        }
        // 自制谱 5 首，rating 150..154，都比官谱高——b30 不能收进来
        for (int i = 0; i < 5; i++) {
            scores.add(score(1001 + i, 2, 150 + i, "AP", 100.0));
        }

        RatioResult result = RatioAnalyzer.analyze(scores, "b30");

        assertEquals("b30", result.mode());
        assertEquals(30, result.songs().size());
        assertEquals(140, result.songs().get(0).rating());
        assertEquals(111, result.songs().get(29).rating());
        assertEquals(0, result.userSongs().size());
        assertEquals(result.songs(), result.officialSongs());
        // 111+112+...+140 = (111+140)*30/2 = 3765，平均 125.5
        assertEquals(3765, result.total());
        assertEquals(125.5, result.average(), 1e-9);
        assertEquals(125.5, result.avg(), 1e-9);
        // 选中 30 首里 AP 10 / FC 10 / 未满 10
        assertEquals(10, result.apCount());
        assertEquals(10, result.fcCount());
        assertEquals(10, result.noneCount());
        assertEquals(10, result.gradeCounts().get("AP"));
        assertEquals(10, result.gradeCounts().get("FC"));
        assertEquals(10, result.gradeCounts().get("未满"));
        // ACC 分布：100% 10 个、99% 10 个、95% 10 个
        assertEquals(10, result.accBins().get("AP 100%"));
        assertEquals(10, result.accBins().get("98 - 100"));
        assertEquals(10, result.accBins().get("95 - 98"));
        assertEquals(0, result.accBins().get("90 - 95"));
        assertEquals(0, result.accBins().get("85 - 90"));
        assertEquals(0, result.accBins().get("< 85"));
        assertTrue(result.songs().stream().allMatch(Score::isOfficial));
    }

    @Test
    @DisplayName("b15：官谱最佳 15 为主榜，另附自制最佳 15")
    void b15AddsUserTop15() {
        List<Score> scores = new ArrayList<>();
        // 官谱 20 首：rating 101..120
        for (int i = 1; i <= 20; i++) {
            scores.add(score(100 + i, 1, 100 + i, "AP", 100.0));
        }
        // 自制 20 首：rating 301..320
        for (int i = 1; i <= 20; i++) {
            scores.add(score(200 + i, 2, 300 + i, "AP", 100.0));
        }

        RatioResult result = RatioAnalyzer.analyze(scores, " B15 ");

        assertEquals("b15", result.mode());
        // 主榜 = 官谱 15 首，rating 120..106
        assertEquals(15, result.songs().size());
        assertEquals(120, result.songs().get(0).rating());
        assertEquals(106, result.songs().get(14).rating());
        assertEquals(result.songs(), result.officialSongs());
        // 106+...+120 = (106+120)*15/2 = 1695，平均 113.0
        assertEquals(1695, result.total());
        assertEquals(113.0, result.average(), 1e-9);
        // 自制榜 15 首，rating 320..306
        assertEquals(15, result.userSongs().size());
        assertEquals(320, result.userSongs().get(0).rating());
        assertEquals(306, result.userSongs().get(14).rating());
        assertEquals(15, result.apCount());
        assertEquals(0, result.fcCount());
        assertEquals(0, result.noneCount());
    }

    @Test
    @DisplayName("ap30：只收 AP，高 rating 的 FC 也不能进，且上限 30")
    void ap30OnlyPerfectScores() {
        List<Score> scores = new ArrayList<>();
        // 31 首 AP：rating 500..530
        for (int i = 0; i < 31; i++) {
            scores.add(score(1 + i, 1, 500 + i, "AP", 100.0));
        }
        // 4 首 FC，rating 900+，故意比 AP 高
        for (int i = 0; i < 4; i++) {
            scores.add(score(100 + i, 1, 900 + i, "FC", 99.0));
        }
        // 自制 AP，rating 1000，同样不能进
        scores.add(score(500, 2, 1000, "AP", 100.0));

        RatioResult result = RatioAnalyzer.analyze(scores, "ap30");

        assertEquals("ap30", result.mode());
        assertEquals(30, result.songs().size());
        assertTrue(result.songs().stream().allMatch(s -> "AP".equals(s.grade())));
        assertEquals(530, result.songs().get(0).rating());
        assertEquals(501, result.songs().get(29).rating());
        // 501+...+530 = (501+530)*30/2 = 15465，平均 515.5
        assertEquals(15465, result.total());
        assertEquals(515.5, result.average(), 1e-9);
        assertEquals(30, result.apCount());
        assertEquals(0, result.userSongs().size());
    }

    @Test
    @DisplayName("成绩不足 N 首时全取，不补齐")
    void takesAllWhenFewerThanLimit() {
        List<Score> scores = List.of(
                score(1, 1, 10, "", 80.0),
                score(2, 1, 30, "AP", 100.0),
                score(3, 1, 20, "FC", 99.0),
                score(4, 2, 999, "AP", 100.0));

        RatioResult result = RatioAnalyzer.analyze(scores, "b30");

        assertEquals(3, result.songs().size());
        assertEquals(60, result.total());
        assertEquals(20.0, result.average(), 1e-9);
        assertEquals(List.of(30, 20, 10),
                result.songs().stream().map(Score::rating).toList());
    }

    @Test
    @DisplayName("空成绩：总分 0、平均 0，分档计数全 0")
    void emptyScoresYieldZero() {
        RatioResult result = RatioAnalyzer.analyze(List.of(), "b30");

        assertEquals(0, result.songs().size());
        assertEquals(0, result.total());
        assertEquals(0.0, result.average(), 1e-9);
        assertEquals(0, result.apCount());
        assertEquals(0, result.fcCount());
        assertEquals(0, result.noneCount());
        assertEquals(0, result.accBins().get("AP 100%"));
        assertEquals(0, result.accBins().get("< 85"));
    }

    @Test
    @DisplayName("ACC 六档计数与 draw_acc_dist 的分档一致")
    void accBinsFollowPythonBins() {
        List<Score> scores = List.of(
                score(1, 1, 60, "AP", 100.0),
                score(2, 1, 59, "FC", 99.0),
                score(3, 1, 58, "", 96.0),
                score(4, 1, 57, "", 92.0),
                score(5, 1, 56, "", 87.0),
                score(6, 1, 55, "", 80.0));

        RatioResult result = RatioAnalyzer.analyze(scores, "b30");

        assertEquals(1, result.accBins().get("AP 100%"));
        assertEquals(1, result.accBins().get("98 - 100"));
        assertEquals(1, result.accBins().get("95 - 98"));
        assertEquals(1, result.accBins().get("90 - 95"));
        assertEquals(1, result.accBins().get("85 - 90"));
        assertEquals(1, result.accBins().get("< 85"));
        assertEquals(6, result.accBins().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    @DisplayName("rating 并列时保持入参顺序（稳定排序，同 Python sorted）")
    void stableOrderOnTies() {
        Score first = score(1, 1, 500, "AP", 100.0);
        Score second = score(2, 1, 500, "AP", 100.0);
        Score third = score(3, 1, 400, "AP", 100.0);

        RatioResult result = RatioAnalyzer.analyze(List.of(first, second, third), "b15");

        assertEquals(List.of(1L, 2L, 3L),
                result.songs().stream().map(Score::musicId).toList());
        assertSame(first, result.songs().get(0));
    }

    @Test
    @DisplayName("未知 mode 抛异常；null 元素被跳过")
    void rejectsUnknownModeAndSkipsNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> RatioAnalyzer.analyze(List.of(), "b99"));
        assertThrows(NullPointerException.class,
                () -> RatioAnalyzer.analyze(null, "b30"));

        List<Score> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(score(1, 1, 42, "AP", 100.0));
        RatioResult result = RatioAnalyzer.analyze(withNull, "b30");
        assertEquals(1, result.songs().size());
        assertEquals(42, result.total());
    }

    private static Score score(long id, int ownerType, int rating, String grade, double acc) {
        int miss = grade.isEmpty() ? 5 : 0;
        return new Score(id, "曲目" + id, ownerType, 2, "国语", 13, "[高]专家", 0, 15, acc,
                980000, 800, miss, "2026-11-19 18:00:00", rating, grade);
    }
}
