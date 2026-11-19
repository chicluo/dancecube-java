package com.chicluo.dancecube.export;

import com.chicluo.dancecube.lib.DurationStore;
import com.chicluo.dancecube.model.Score;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全量成绩导出 xlsx，对标 Python 版 {@code core/export.py::export_scores}。
 *
 * <p>列：序号 / 曲名 / MusicID / 定数 / 难度 / 曲风 / ACC / 分数 / Miss / Combo / 战力 /
 * 评级 / 游玩时间 / 来源。传入 {@link DurationStore} 时再补「时长」「码率」两列
 * （时长 m:ss，码率 kbps 整数；查不到留空），对标 Python 版
 * {@code music.py::load_duration / load_bitrate} 的数据来源与算法。</p>
 *
 * <p>文件名「&lt;角色ID&gt;全部成绩&lt;yyyyMMdd_HHmmss&gt;.xlsx」。</p>
 */
public final class ScoreExporter {

    /** 基础表头，顺序与 Python 版 HEADERS 完全一致。 */
    public static final List<String> HEADERS = List.of(
            "序号", "曲名", "MusicID", "定数", "难度", "曲风", "ACC",
            "分数", "Miss", "Combo", "战力", "评级", "游玩时间", "来源");

    /** 补在末尾的音频两列。 */
    public static final List<String> AUDIO_HEADERS = List.of("时长", "码率");

    /** 带音频信息的表头：基础 14 列 + 时长 / 码率。 */
    public static final List<String> HEADERS_WITH_AUDIO = concat(HEADERS, AUDIO_HEADERS);

    private static final Set<String> NUMERIC_HEADERS = Set.of(
            "序号", "定数", "ACC", "分数", "Miss", "Combo", "战力");

    private static final Set<String> NUMERIC_HEADERS_WITH_AUDIO = Set.of(
            "序号", "定数", "ACC", "分数", "Miss", "Combo", "战力", "码率");

    /** ACC 的 Excel 数字格式：值按百分比显示两位小数。 */
    private static final Map<String, String> COLUMN_FORMATS = Map.of("ACC", "0.00\"%\"");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ScoreExporter() {
    }

    /**
     * 导出全部成绩，文件名使用 {@code unknown} 作为角色 ID（Python 版拿不到 ID 时的兜底值）。
     *
     * @return 生成的 xlsx 路径
     */
    public static Path exportScores(List<Score> scores, Path outDir) {
        return exportScores(scores, outDir, "unknown");
    }

    /**
     * 导出全部成绩（基础 14 列）。
     *
     * @param scores 成绩列表，不能为空
     * @param outDir 输出目录，不存在会自动创建
     * @param roleId 角色 ID，参与文件名
     * @return 生成的 xlsx 路径
     * @throws IllegalArgumentException 目录未指定或没有成绩（对齐 Python 的 RuntimeError）
     */
    public static Path exportScores(List<Score> scores, Path outDir, String roleId) {
        return doExport(scores, outDir, roleId, null);
    }

    /**
     * 导出全部成绩，并在末尾补「时长」「码率」两列。
     *
     * @param durations 音频信息；null 等同基础 14 列
     */
    public static Path exportScores(List<Score> scores, Path outDir, String roleId,
                                    DurationStore durations) {
        return doExport(scores, outDir, roleId, durations);
    }

    private static Path doExport(List<Score> scores, Path outDir, String roleId,
                                 DurationStore durations) {
        if (outDir == null || outDir.toString().isBlank()) {
            throw new IllegalArgumentException("导出目录未指定");
        }
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的成绩");
        }

        List<String> headers = durations == null ? HEADERS : HEADERS_WITH_AUDIO;
        Set<String> numericHeaders = durations == null
                ? NUMERIC_HEADERS : NUMERIC_HEADERS_WITH_AUDIO;

        List<Score> rows = new ArrayList<>(scores);
        // 定数倒序 → 战力倒序 → 曲名升序，和 Python 的 sorted(key=(-level, -rating, name)) 一致
        rows.sort(Comparator.comparingInt(Score::level).reversed()
                .thenComparing(Comparator.comparingInt(Score::rating).reversed())
                .thenComparing(Score::name, Comparator.nullsFirst(Comparator.naturalOrder())));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("全部成绩");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                ExcelSupport.writeCell(header, i, headers.get(i));
            }

            int rowIndex = 1;
            for (Score score : rows) {
                Row row = sheet.createRow(rowIndex);
                Object time = score.time() == null ? "" : String.valueOf(score.time());
                List<Object> values = new ArrayList<>(List.of(
                        rowIndex,
                        ExcelSupport.safeCell(score.name() == null ? "" : score.name()),
                        score.musicId(),
                        score.level(),
                        ExcelSupport.safeCell(score.levelTypeName() == null ? "" : score.levelTypeName()),
                        ExcelSupport.safeCell(score.clsName() == null ? "" : score.clsName()),
                        round2(score.acc()),
                        score.score(),
                        score.miss(),
                        score.combo(),
                        score.rating(),
                        ExcelSupport.safeCell(score.grade() == null ? "" : score.grade()),
                        ExcelSupport.safeCell(time),
                        score.ownerType() == 1 ? "官谱" : "自制谱"));
                if (durations != null) {
                    Integer seconds = durations.duration(score.musicId());
                    Integer kbps = durations.kbps(score.musicId());
                    values.add(seconds == null ? "" : mmss(seconds));
                    values.add(kbps == null ? "" : kbps);
                }
                for (int col = 0; col < values.size(); col++) {
                    ExcelSupport.writeCell(row, col, values.get(col));
                }
                rowIndex++;
            }

            ExcelSupport.finish(sheet, headers, numericHeaders, COLUMN_FORMATS);

            Files.createDirectories(outDir);
            Path path = outDir.resolve(ExcelSupport.safeFileName(roleId)
                    + "全部成绩" + LocalDateTime.now().format(STAMP) + ".xlsx");
            try (OutputStream out = Files.newOutputStream(path)) {
                wb.write(out);
            }
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("导出成绩失败", e);
        }
    }

    /** 秒 → m:ss；与 Python 版 {@code catalog.mmss} 一致。 */
    static String mmss(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** 保留两位小数，对齐 Python 的 {@code round(float(acc), 2)}。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> out = new ArrayList<>(first.size() + second.size());
        out.addAll(first);
        out.addAll(second);
        return List.copyOf(out);
    }
}
