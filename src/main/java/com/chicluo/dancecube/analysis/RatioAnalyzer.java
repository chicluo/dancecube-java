package com.chicluo.dancecube.analysis;

import com.chicluo.dancecube.model.Score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 战力榜分析：复刻 Python 版 {@code core/image.py::build()} 的选取与统计口径。
 *
 * <ul>
 *   <li><b>b15</b>：官谱（ownerType == 1）按 rating 降序取前 15；另附自制谱（ownerType != 1）
 *       最佳 15 首。主榜、总分、平均均按官谱 15 首算，与 Python 的 {@code Player.best(15)} 一致。</li>
 *   <li><b>b30</b>：官谱按 rating 降序取前 30，对应 {@code Player.best(30)} / {@code build("b30")}。</li>
 *   <li><b>ap30</b>：官谱中 grade 为 "AP" 的按 rating 降序取前 30，对应
 *       {@code Player.ap(30)} / {@code build("ap30")}。</li>
 * </ul>
 *
 * <p>排序用稳定排序（Java {@link List#sort} 是 TimSort），与 Python {@code sorted} 一致：
 * rating 并列时保留入参顺序。选取不足 N 首时按实际数量返回，不做补齐。</p>
 *
 * <p>与 Python 版的两点有意差异：mode 做 trim + 忽略大小写；未知 mode 抛
 * {@link IllegalArgumentException}，而不是像 Python 的 if/elif/else 那样静默落到 ap30。</p>
 */
public final class RatioAnalyzer {

    public static final String MODE_B15 = "b15";
    public static final String MODE_B30 = "b30";
    public static final String MODE_AP30 = "ap30";

    private static final int B15_LIMIT = 15;
    private static final int B30_LIMIT = 30;
    private static final int AP30_LIMIT = 30;

    /** rating 降序；稳定排序保证并列时保留原始顺序。 */
    private static final Comparator<Score> BY_RATING_DESC =
            Comparator.comparingInt(Score::rating).reversed();

    private RatioAnalyzer() {
    }

    /**
     * 分析成绩列表。
     *
     * @param scores 全量成绩（官谱 + 自制谱），允许为空；null 元素会被跳过
     * @param mode   b15 / b30 / ap30（忽略大小写与首尾空白）
     * @return 选取结果、总分、平均、评级与 ACC 分档计数
     * @throws IllegalArgumentException mode 不受支持
     */
    public static RatioResult analyze(List<Score> scores, String mode) {
        Objects.requireNonNull(scores, "scores");
        String normalized = normalizeMode(mode);
        List<Score> clean = new ArrayList<>(scores.size());
        for (Score score : scores) {
            if (score != null) {
                clean.add(score);
            }
        }
        return switch (normalized) {
            case MODE_B15 -> b15(clean);
            case MODE_B30 -> b30(clean);
            case MODE_AP30 -> ap30(clean);
            default -> throw new IllegalArgumentException(
                    "不支持的模式: " + mode + "（只支持 b15 / b30 / ap30）");
        };
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    /** 官谱最佳 15 + 自制最佳 15，主榜统计按官谱榜。 */
    private static RatioResult b15(List<Score> scores) {
        List<Score> officialSongs = best(scores, true, B15_LIMIT);
        List<Score> userSongs = best(scores, false, B15_LIMIT);
        return build(MODE_B15, officialSongs, officialSongs, userSongs);
    }

    /** 官谱最佳 30（含自制谱的旧版 --all 口径不在本方法范围）。 */
    private static RatioResult b30(List<Score> scores) {
        List<Score> songs = best(scores, true, B30_LIMIT);
        return build(MODE_B30, songs, songs, List.of());
    }

    /** 官谱中 AP 成绩按 rating 降序前 30。 */
    private static RatioResult ap30(List<Score> scores) {
        List<Score> aps = new ArrayList<>();
        for (Score score : scores) {
            if (score.isOfficial() && "AP".equals(score.grade())) {
                aps.add(score);
            }
        }
        aps.sort(BY_RATING_DESC);
        List<Score> songs = new ArrayList<>(aps.subList(0, Math.min(AP30_LIMIT, aps.size())));
        return build(MODE_AP30, songs, songs, List.of());
    }

    /**
     * 按来源过滤后取 rating 最高的 limit 首。
     *
     * @param official true 取官谱，false 取自制谱
     */
    private static List<Score> best(List<Score> scores, boolean official, int limit) {
        List<Score> pool = new ArrayList<>();
        for (Score score : scores) {
            if (score.isOfficial() == official) {
                pool.add(score);
            }
        }
        pool.sort(BY_RATING_DESC);
        return new ArrayList<>(pool.subList(0, Math.min(limit, pool.size())));
    }

    private static RatioResult build(
            String mode, List<Score> songs, List<Score> officialSongs, List<Score> userSongs) {
        int total = 0;
        for (Score score : songs) {
            total += score.rating();
        }
        // Python: total / len(songs)（真除，空榜为 0）
        double average = songs.isEmpty() ? 0.0 : (double) total / songs.size();
        return new RatioResult(
                mode, songs, officialSongs, userSongs, total, average,
                gradeCounts(songs), accBins(songs));
    }

    /** 评级计数：AP / FC / 未满，对标 build_level 里的 ap、fc 统计。 */
    private static Map<String, Integer> gradeCounts(List<Score> songs) {
        int ap = 0;
        int fc = 0;
        int none = 0;
        for (Score score : songs) {
            if ("AP".equals(score.grade())) {
                ap++;
            } else if ("FC".equals(score.grade())) {
                fc++;
            } else {
                none++;
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("AP", ap);
        counts.put("FC", fc);
        counts.put("未满", none);
        return counts;
    }

    /** ACC 六档计数，标签与 image.py::draw_acc_dist 的 bins 一致。 */
    private static Map<String, Integer> accBins(List<Score> songs) {
        String[] labels = {"AP 100%", "98 - 100", "95 - 98", "90 - 95", "85 - 90", "< 85"};
        int[] counts = new int[labels.length];
        for (Score score : songs) {
            double acc = score.acc();
            if (acc >= 99.995) {
                counts[0]++;
            } else if (acc >= 98) {
                counts[1]++;
            } else if (acc >= 95) {
                counts[2]++;
            } else if (acc >= 90) {
                counts[3]++;
            } else if (acc >= 85) {
                counts[4]++;
            } else if (acc < 85) {
                // NaN 不进任何档，与 Python 的 lambda 判定一致
                counts[5]++;
            }
        }
        Map<String, Integer> bins = new LinkedHashMap<>();
        for (int i = 0; i < labels.length; i++) {
            bins.put(labels[i], counts[i]);
        }
        return bins;
    }
}
