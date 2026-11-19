package com.chicluo.dancecube.analysis;

import com.chicluo.dancecube.model.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次战力分析的结果，字段命名对标 Python 版 {@code core/image.py::build()} 的内部量：
 * {@code songs}（选取列表，已按 rating 降序）、{@code total}（合计）、
 * {@code avg}（平均）、{@code sections}（分块）。
 *
 * <p>B15 模式下 Python 的 {@code build(mode="b15")} 是「官谱最佳 15 首」单榜；
 * 这里把「官谱最佳 15」与「自制最佳 15」分别放在 {@link #officialSongs()} /
 * {@link #userSongs()}，{@link #songs()}、{@link #total()}、{@link #average()}
 * 仍以官谱榜为准，保证数值与 Python 版一致。</p>
 *
 * @param mode          归一化后的模式（b15 / b30 / ap30）
 * @param songs         主榜：选取的 Score，按 rating 降序
 * @param officialSongs 官谱榜（ownerType == 1）；b30 / ap30 与主榜相同
 * @param userSongs     自制谱榜（ownerType != 1，最佳 15 首）；仅 b15 模式非空
 * @param total         主榜战力合计，对标 build() 的 total
 * @param average       主榜战力平均，对标 build() 的 avg（空榜为 0）
 * @param gradeCounts   评级计数：AP / FC / 未满，对标 build_level 的 ap / fc 统计
 * @param accBins       ACC 分档计数，对标 image.py 的 draw_acc_dist 六档
 */
public record RatioResult(
        String mode,
        List<Score> songs,
        List<Score> officialSongs,
        List<Score> userSongs,
        int total,
        double average,
        Map<String, Integer> gradeCounts,
        Map<String, Integer> accBins) {

    public RatioResult {
        Objects.requireNonNull(mode, "mode");
        songs = List.copyOf(songs);
        officialSongs = List.copyOf(officialSongs);
        userSongs = List.copyOf(userSongs);
        gradeCounts = Collections.unmodifiableMap(new LinkedHashMap<>(gradeCounts));
        accBins = Collections.unmodifiableMap(new LinkedHashMap<>(accBins));
    }

    /** 与 Python 版 {@code avg} 同名的便捷访问器。 */
    public double avg() {
        return average;
    }

    /** 主榜 AP 数。 */
    public int apCount() {
        return gradeCounts.getOrDefault("AP", 0);
    }

    /** 主榜 FC 数（不含 AP）。 */
    public int fcCount() {
        return gradeCounts.getOrDefault("FC", 0);
    }

    /** 主榜既非 AP 也非 FC 的数量。 */
    public int noneCount() {
        return gradeCounts.getOrDefault("未满", 0);
    }
}
