package com.chicluo.dancecube.export;

import com.chicluo.dancecube.model.DanceLevel;
import com.chicluo.dancecube.model.Song;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 曲库全表导出，对标 Python 版 {@code core/catalog.py}。
 *
 * <p>官谱全表：音乐ID / 曲名 / 曲风 / 难度 / 定数 / 物量，每个谱面一行；
 * 自制谱全表：音乐ID / 曲名 / 作者ID / 作者 / 标签 / 价格 / 点赞 / 异常 / 难度1-3 /
 * 定数1-3 / 物量1-3 / show+难度 / show+定数，每个商品一行。</p>
 *
 * <p>与 Python 版的差异：模型 {@link Song} 没有时长字段，故两张表都不再输出「时长」列。</p>
 */
public final class CatalogExporter {

    /** 官谱全表表头。 */
    public static final List<String> OFFICIAL_HEADERS = List.of(
            "音乐ID", "曲名", "曲风", "难度", "定数", "物量");

    /** 自制谱全表表头，顺序与 Python 版一致（去掉「时长」后不再有该列）。 */
    public static final List<String> USER_HEADERS = List.of(
            "音乐ID", "曲名", "作者ID", "作者", "标签", "价格(金币)", "点赞", "异常",
            "难度1", "定数1", "物量1", "难度2", "定数2", "物量2", "难度3", "定数3", "物量3",
            "show+难度", "show+定数");

    private static final Set<String> OFFICIAL_NUMERIC = Set.of("音乐ID", "定数", "物量");
    private static final Set<String> USER_NUMERIC = Set.of(
            "音乐ID", "作者ID", "价格(金币)", "点赞",
            "定数1", "物量1", "定数2", "物量2", "定数3", "物量3", "show+定数");

    /** 曲风：MusicCategoryNew 1 国语 / 3 韩文 / 4 欧美 / 6 其他，未知也归「其他」。 */
    private static final Map<Integer, String> GENRE_NAMES = Map.of(
            1, "国语", 3, "韩文", 4, "欧美", 6, "其他");

    /** 官谱同定数排序辅键：高 → 中 → 初 → 秀 → S+。 */
    private static final Map<String, Integer> TAG_ORDER = Map.of(
            "[高]", 0, "[中]", 1, "[初]", 2, "[秀]", 3, "[S+]", 4);

    private CatalogExporter() {
    }

    // ------------------------------------------------------------------ 官谱全表

    /**
     * 生成官谱全表，每个谱面一行。
     *
     * <p>排序：定数倒序为主，同定数按 高 → 中 → 初 → 秀 → S+ 为辅，再按音乐 ID 升序。</p>
     *
     * @return 生成的 xlsx 路径
     */
    public static Path buildOfficial(List<Song> songs, Path path) {
        List<OfficialRow> rows = new ArrayList<>();
        if (songs != null) {
            for (Song song : songs) {
                List<DanceLevel> levels = song.levels() == null ? List.of() : song.levels();
                String name = ExcelSupport.safeText(song.name() == null ? "" : song.name());
                String genre = genreName(song.categoryNew());
                if (levels.isEmpty()) {
                    rows.add(new OfficialRow(song.musicId(), name, genre, "未填",
                            "", "", 0, 9));
                    continue;
                }
                for (DanceLevel level : levels) {
                    // 定数 < 0 且不是秀谱：占位条目，游戏里玩不到
                    if (level.level() < 0 && !level.isShow()) {
                        continue;
                    }
                    String label = level.label();
                    if (level.isBad()) {
                        label = "⚠️ " + label;
                    }
                    int tagRank = tagRank(label.replace("⚠️ ", ""));
                    int levelNum = level.level() > 0 ? level.level() : 0;
                    Object combo = level.combo() == 0 ? "" : level.combo();
                    rows.add(new OfficialRow(song.musicId(), name, genre, label,
                            level.level(), combo, levelNum, tagRank));
                }
            }
        }
        rows.sort(Comparator.comparingInt(OfficialRow::levelNum).reversed()
                .thenComparingInt(OfficialRow::tagRank)
                .thenComparingLong(OfficialRow::musicId));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("官谱全表");
            Row header = sheet.createRow(0);
            for (int i = 0; i < OFFICIAL_HEADERS.size(); i++) {
                ExcelSupport.writeCell(header, i, OFFICIAL_HEADERS.get(i));
            }
            int rowIndex = 1;
            for (OfficialRow row : rows) {
                Row sheetRow = sheet.createRow(rowIndex++);
                Object[] values = {
                        row.musicId(), row.name(), row.genre(),
                        row.label(), row.level(), row.combo(),
                };
                for (int col = 0; col < values.length; col++) {
                    ExcelSupport.writeCell(sheetRow, col, values[col]);
                }
            }
            ExcelSupport.finish(sheet, OFFICIAL_HEADERS, OFFICIAL_NUMERIC, Map.of());
            save(wb, path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("导出官谱全表失败", e);
        }
    }

    // ------------------------------------------------------------------ 自制谱全表

    /**
     * 生成自制谱全表，每个商品一行，按点赞倒序（同赞按音乐 ID 升序）。
     *
     * @return 生成的 xlsx 路径
     */
    public static Path buildUser(List<Song> songs, Path path) {
        List<Song> items = new ArrayList<>(songs == null ? List.of() : songs);
        items.sort(Comparator.comparingLong((Song s) -> s.like() == null ? 0L : s.like()).reversed()
                .thenComparingLong(Song::musicId));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("自制谱商店");
            Row header = sheet.createRow(0);
            for (int i = 0; i < USER_HEADERS.size(); i++) {
                ExcelSupport.writeCell(header, i, USER_HEADERS.get(i));
            }

            int rowIndex = 1;
            for (Song song : items) {
                List<DanceLevel> levels = song.levels() == null ? List.of() : song.levels();
                List<Object> values = new ArrayList<>(USER_HEADERS.size());
                values.add(song.musicId());
                values.add(ExcelSupport.safeCell(song.name() == null ? "" : song.name()));
                values.add(song.ownerId() == null || song.ownerId() == 0L ? "" : song.ownerId());
                values.add(ExcelSupport.safeCell(song.ownerName() == null ? "" : song.ownerName()));
                values.add(ExcelSupport.safeCell(String.join(" / ",
                        song.tags() == null ? List.of() : song.tags())));
                values.add(song.price() == null ? "" : song.price());
                values.add(song.like() == null ? "" : song.like());
                values.add(levels.stream().anyMatch(DanceLevel::isBad) ? "⚠️" : "");

                // 经典谱：按档位顺序、再按 MusicLevNew 升序，补齐到 3 组
                List<DanceLevel> classic = new ArrayList<>();
                for (DanceLevel level : levels) {
                    if (!level.isShow()) {
                        classic.add(level);
                    }
                }
                classic.sort(Comparator.comparingInt(DanceLevel::order)
                        .thenComparingInt(DanceLevel::levNew));
                for (int i = 0; i < 3; i++) {
                    if (i < classic.size()) {
                        DanceLevel level = classic.get(i);
                        values.add(level.label());
                        values.add(level.level());
                        values.add(level.combo() == 0 ? "" : level.combo());
                    } else {
                        values.add("");
                        values.add("");
                        values.add("");
                    }
                }

                // show+ 难度只取第一条
                DanceLevel show = null;
                for (DanceLevel level : levels) {
                    if (level.isShow()) {
                        show = level;
                        break;
                    }
                }
                if (show == null) {
                    values.add("");
                    values.add("");
                } else {
                    values.add(show.label());
                    values.add(show.level());
                }

                Row sheetRow = sheet.createRow(rowIndex++);
                for (int col = 0; col < values.size(); col++) {
                    ExcelSupport.writeCell(sheetRow, col, values.get(col));
                }
            }

            ExcelSupport.finish(sheet, USER_HEADERS, USER_NUMERIC, Map.of());
            save(wb, path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("导出自制谱全表失败", e);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void save(XSSFWorkbook wb, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            wb.write(out);
        }
    }

    private static String genreName(Integer categoryNew) {
        if (categoryNew == null) {
            return "其他";
        }
        return GENRE_NAMES.getOrDefault(categoryNew, "其他");
    }

    private static int tagRank(String cleanLabel) {
        for (Map.Entry<String, Integer> entry : TAG_ORDER.entrySet()) {
            if (cleanLabel.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 9;
    }

    /** 官谱排序用的中间行，level/combo 可能是空串（占位曲目）。 */
    private record OfficialRow(
            long musicId,
            String name,
            String genre,
            String label,
            Object level,
            Object combo,
            int levelNum,
            int tagRank) {
    }
}
