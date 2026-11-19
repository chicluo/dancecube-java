package com.chicluo.dancecube.export;

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
 * 评级 / 游玩时间 / 来源。文件名「&lt;角色ID&gt;全部成绩&lt;yyyyMMdd_HHmmss&gt;.xlsx」。</p>
 */
public final class ScoreExporter {

    /** 表头，顺序与 Python 版 HEADERS 完全一致。 */
    public static final List<String> HEADERS = List.of(
            "序号", "曲名", "MusicID", "定数", "难度", "曲风", "ACC",
            "分数", "Miss", "Combo", "战力", "评级", "游玩时间", "来源");

    private static final Set<String> NUMERIC_HEADERS = Set.of(
            "序号", "定数", "ACC", "分数", "Miss", "Combo", "战力");

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
     * 导出全部成绩。
     *
     * @param scores 成绩列表，不能为空
     * @param outDir 输出目录，不存在会自动创建
     * @param roleId 角色 ID，参与文件名
     * @return 生成的 xlsx 路径
     * @throws IllegalArgumentException 目录未指定或没有成绩（对齐 Python 的 RuntimeError）
     */
    public static Path exportScores(List<Score> scores, Path outDir, String roleId) {
        if (outDir == null || outDir.toString().isBlank()) {
            throw new IllegalArgumentException("导出目录未指定");
        }
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的成绩");
        }

        List<Score> rows = new ArrayList<>(scores);
        // 定数倒序 → 战力倒序 → 曲名升序，和 Python 的 sorted(key=(-level, -rating, name)) 一致
        rows.sort(Comparator.comparingInt(Score::level).reversed()
                .thenComparing(Comparator.comparingInt(Score::rating).reversed())
                .thenComparing(Score::name, Comparator.nullsFirst(Comparator.naturalOrder())));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("全部成绩");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                ExcelSupport.writeCell(header, i, HEADERS.get(i));
            }

            int rowIndex = 1;
            for (Score score : rows) {
                Row row = sheet.createRow(rowIndex);
                Object time = score.time() == null ? "" : String.valueOf(score.time());
                Object[] values = {
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
                        score.ownerType() == 1 ? "官谱" : "自制谱",
                };
                for (int col = 0; col < values.length; col++) {
                    ExcelSupport.writeCell(row, col, values[col]);
                }
                rowIndex++;
            }

            ExcelSupport.finish(sheet, HEADERS, NUMERIC_HEADERS, COLUMN_FORMATS);

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

    /** 保留两位小数，对齐 Python 的 {@code round(float(acc), 2)}。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
