package com.chicluo.dancecube.export;

import com.chicluo.dancecube.lib.DurationStore;
import com.chicluo.dancecube.model.DanceLevel;
import com.chicluo.dancecube.model.Score;
import com.chicluo.dancecube.model.Song;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 导出模块测试：写到 build/tmp，再用 POI 读回来核对行数与表头。 */
class ExportTest {

    private static final Path OUT_DIR = Path.of("build", "tmp", "export-test");

    // ------------------------------------------------------------------ 成绩导出

    @Test
    void exportsScoresWithFormattingAndFormulaGuard() throws IOException {
        List<Score> scores = List.of(
                new Score(291, "Calavera", 1, 4, "欧美", 2, "高级", -1, 14, 99.536,
                        980000, 120, 1, LocalDateTime.of(2026, 11, 1, 12, 0), 340, "SSS"),
                new Score(6482, "=SUM(A1)", 0, 2, "其他", 1, "中级", -1, 13, 88.0,
                        800000, 50, 5, "2026-11-02 09:30:00", 200, "+S"));

        Path path = ScoreExporter.exportScores(scores, OUT_DIR, "10001");

        assertTrue(Files.isRegularFile(path), "导出文件应存在: " + path);
        String fileName = path.getFileName().toString();
        assertTrue(fileName.startsWith("10001全部成绩"), "文件名应带角色 ID: " + fileName);
        assertTrue(fileName.endsWith(".xlsx"));

        try (XSSFWorkbook wb = open(path)) {
            Sheet sheet = wb.getSheet("全部成绩");
            assertNotNull(sheet, "工作表名应为「全部成绩」");
            assertEquals(ScoreExporter.HEADERS, readHeader(sheet));
            assertEquals(2, sheet.getLastRowNum(), "表头 + 2 条成绩");
            assertEquals(ScoreExporter.HEADERS.size(), sheet.getRow(0).getPhysicalNumberOfCells());

            // 按定数倒序：14 在前，13 在后
            Row first = sheet.getRow(1);
            assertEquals("Calavera", text(first, 1));
            assertEquals(291, number(first, 2), 1e-9);
            assertEquals(14, number(first, 3), 1e-9);
            assertEquals("高级", text(first, 4));
            assertEquals("欧美", text(first, 5));
            assertEquals(99.54, number(first, 6), 1e-9);
            assertEquals("0.00\"%\"", first.getCell(6).getCellStyle().getDataFormatString());
            assertEquals(980000, number(first, 7), 1e-9);
            assertEquals(340, number(first, 10), 1e-9);
            assertEquals("SSS", text(first, 11));
            assertEquals("官谱", text(first, 13));

            Row second = sheet.getRow(2);
            assertEquals("'=SUM(A1)", text(second, 1), "公式前缀必须被单引号破坏");
            assertEquals("'+S", text(second, 11), "评级同样走 safe_cell");
            assertEquals("自制谱", text(second, 13));

            assertNotNull(sheet.getPaneInformation(), "应冻结首行");
        }
    }

    @Test
    void exportsScoresWithDurationAndBitrateColumns() throws IOException {
        List<Score> scores = List.of(
                new Score(291, "Calavera", 1, 4, "欧美", 2, "高级", -1, 14, 99.536,
                        980000, 120, 1, "2026-11-01 12:00:00", 340, "SSS"),
                new Score(6482, "自制曲", 0, 2, "其他", 1, "中级", -1, 13, 88.0,
                        800000, 50, 5, "2026-11-02 09:30:00", 200, "+S"),
                // 没有音频记录的曲目：两列留空
                new Score(7000, "无声", 0, 2, "其他", 1, "初级", -1, 12, 70.0,
                        600000, 10, 9, "2026-11-03 08:00:00", 100, "A"));

        DurationStore store = DurationStore.of(
                java.util.Map.of(291L, 185.6, 6482L, 127.7),
                java.util.Map.of(291L, 3070717L, 6482L, 2261165L));

        Path path = ScoreExporter.exportScores(scores, OUT_DIR, "10001", store);

        try (XSSFWorkbook wb = open(path)) {
            Sheet sheet = wb.getSheet("全部成绩");
            assertNotNull(sheet);
            assertEquals(ScoreExporter.HEADERS_WITH_AUDIO, readHeader(sheet));
            assertEquals(16, sheet.getRow(0).getPhysicalNumberOfCells());
            assertEquals("时长", text(sheet.getRow(0), 14));
            assertEquals("码率", text(sheet.getRow(0), 15));

            // 185.6 → 186 秒 → 3:06；3070717 * 8 / 185.6 / 1000 ≈ 132.36 → 132
            Row first = sheet.getRow(1);
            assertEquals("3:06", text(first, 14));
            assertEquals(132, number(first, 15), 1e-9);
            assertEquals(HorizontalAlignment.LEFT, first.getCell(14).getCellStyle().getAlignment());
            assertEquals(HorizontalAlignment.RIGHT, first.getCell(15).getCellStyle().getAlignment());

            // 127.7 → 128 秒 → 2:08；2261165 * 8 / 127.7 / 1000 ≈ 141.65 → 142
            Row second = sheet.getRow(2);
            assertEquals("2:08", text(second, 14));
            assertEquals(142, number(second, 15), 1e-9);

            Row third = sheet.getRow(3);
            assertEquals("", text(third, 14));
            assertEquals("", text(third, 15));
        }
    }

    @Test
    void nullDurationStoreKeepsBaseColumns() throws IOException {
        List<Score> scores = List.of(
                new Score(291, "Calavera", 1, 4, "欧美", 2, "高级", -1, 14, 99.536,
                        980000, 120, 1, "2026-11-01 12:00:00", 340, "SSS"));

        Path path = ScoreExporter.exportScores(scores, OUT_DIR, "10002", null);

        try (XSSFWorkbook wb = open(path)) {
            Sheet sheet = wb.getSheet("全部成绩");
            assertEquals(ScoreExporter.HEADERS, readHeader(sheet));
        }
    }

    @Test
    void rejectsEmptyScores() {
        assertThrows(IllegalArgumentException.class,
                () -> ScoreExporter.exportScores(List.of(), OUT_DIR));
        assertThrows(IllegalArgumentException.class,
                () -> ScoreExporter.exportScores(List.of(), OUT_DIR, "1"));
    }

    // ------------------------------------------------------------------ 官谱全表

    @Test
    void buildsOfficialCatalogSortedByLevelThenTier() throws IOException {
        List<Song> songs = List.of(
                new Song("official", "Calavera", 291,
                        List.of(new DanceLevel("高级", "传奇", 14, 1200, false, 15, 2, false),
                                new DanceLevel("", "传奇", 17, 1500, true, 105, 9, false),
                                new DanceLevel("中级", "专家", 13, 900, false, 13, 1, true)),
                        null, null, null, "", List.of(), 4, "", ""),
                // 没有谱面的曲目：一行「未填」
                new Song("official", "空曲", 300, List.of(), null, null, null, "", List.of(), 1, "", ""));

        Path path = OUT_DIR.resolve("official.xlsx");
        Path returned = CatalogExporter.buildOfficial(songs, path, null);

        assertEquals(path, returned);
        assertTrue(Files.isRegularFile(path));
        try (XSSFWorkbook wb = open(path)) {
            Sheet sheet = wb.getSheet("官谱全表");
            assertNotNull(sheet);
            assertEquals(CatalogExporter.OFFICIAL_HEADERS, readHeader(sheet));
            assertEquals(4, sheet.getLastRowNum(), "3 个谱面 + 1 条未填");

            // 列序：音乐ID 0 / 曲名 1 / 曲风 2 / 时长 3 / 难度 4 / 定数 5 / 物量 6
            assertEquals("[S+]传奇(17)", text(sheet.getRow(1), 4));
            assertEquals("[高]传奇(14)", text(sheet.getRow(2), 4));
            assertEquals("⚠️ [中]专家(13)", text(sheet.getRow(3), 4));
            assertEquals(291, number(sheet.getRow(1), 0), 1e-9);
            assertEquals("Calavera", text(sheet.getRow(1), 1));
            assertEquals("欧美", text(sheet.getRow(1), 2));
            assertEquals(17, number(sheet.getRow(1), 5), 1e-9);
            assertEquals(1500, number(sheet.getRow(1), 6), 1e-9);
            assertEquals("未填", text(sheet.getRow(4), 4));
            assertEquals("国语", text(sheet.getRow(4), 2));
        }
    }

    // ------------------------------------------------------------------ 自制谱全表

    @Test
    void buildsUserCatalogWithThreeClassicSlotsAndShow() throws IOException {
        Song song = new Song("user", "自制曲", 6482,
                List.of(new DanceLevel("高级", "专家", 12, 900, false, 13, 2, false),
                        new DanceLevel("初级", "基础", 10, 500, false, 11, 0, false),
                        new DanceLevel("中级", "进阶", 11, 700, false, 12, 1, true),
                        new DanceLevel("show+", "传奇", 15, 1100, true, 105, 9, false)),
                100, 42, 7L, "作者", List.of("流行", "电音"), null, "", "");
        Song empty = new Song("user", "空商品", 7000, List.of(),
                null, null, null, "", List.of(), null, "", "");

        List<Song> songs = new ArrayList<>(List.of(empty, song)); // 故意打乱，验证按点赞倒序
        Path path = OUT_DIR.resolve("user.xlsx");
        // 6482：127.7s / 2261165B → 时长 2:08、码率 142；空商品没有音频记录，两列留空
        DurationStore store = DurationStore.of(
                java.util.Map.of(6482L, 127.7),
                java.util.Map.of(6482L, 2261165L));
        CatalogExporter.buildUser(songs, path, store);

        assertTrue(Files.isRegularFile(path));
        try (XSSFWorkbook wb = open(path)) {
            Sheet sheet = wb.getSheet("自制谱商店");
            assertNotNull(sheet);
            assertEquals(CatalogExporter.USER_HEADERS, readHeader(sheet));
            assertEquals(21, sheet.getRow(0).getPhysicalNumberOfCells());
            assertEquals(2, sheet.getLastRowNum());

            // 列序：音乐ID 0 / 曲名 1 / 时长 2 / 码率 3 / 作者ID 4 / 作者 5 / 标签 6 / 价格 7 / 点赞 8 / 异常 9
            //       难度1 10 / 定数1 11 / 物量1 12 / 难度2 13 / 定数2 14 / 物量2 15
            //       难度3 16 / 定数3 17 / 物量3 18 / show+难度 19 / show+定数 20
            Row row = sheet.getRow(1);
            assertEquals(6482, number(row, 0), 1e-9);
            assertEquals("自制曲", text(row, 1));
            assertEquals("2:08", text(row, 2));
            assertEquals(142, number(row, 3), 1e-9);
            assertEquals(7, number(row, 4), 1e-9);
            assertEquals("作者", text(row, 5));
            assertEquals("流行 / 电音", text(row, 6));
            assertEquals(100, number(row, 7), 1e-9);
            assertEquals(42, number(row, 8), 1e-9);
            assertEquals("⚠️", text(row, 9));
            assertEquals("[初]基础(10)", text(row, 10));
            assertEquals("[中]进阶(11)", text(row, 13));
            assertEquals("[高]专家(12)", text(row, 16));
            assertEquals("[S+]传奇(15)", text(row, 19));
            assertEquals(15, number(row, 20), 1e-9);

            // 空商品：价格/点赞/标签订位都是空文本，三组难度补齐为空
            Row emptyRow = sheet.getRow(2);
            assertEquals("空商品", text(emptyRow, 1));
            assertEquals("", text(emptyRow, 2), "没有音频记录时时长留空");
            assertEquals("", text(emptyRow, 3), "没有音频记录时码率留空");
            assertEquals(CellType.STRING, emptyRow.getCell(5).getCellType());
            assertEquals("", text(emptyRow, 5));
            assertEquals("", text(emptyRow, 6));
            assertEquals("", text(emptyRow, 7));
            assertEquals("", text(emptyRow, 9));
            assertEquals("", text(emptyRow, 18));
        }
    }

    // ------------------------------------------------------------------ 工具

    private static XSSFWorkbook open(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new XSSFWorkbook(in);
        }
    }

    private static List<String> readHeader(Sheet sheet) {
        Row row = sheet.getRow(0);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < row.getPhysicalNumberOfCells(); i++) {
            out.add(row.getCell(i).getStringCellValue());
        }
        return out;
    }

    private static String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return cell.getStringCellValue();
    }

    private static double number(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }
}
