package com.chicluo.dancecube.render;

import com.chicluo.dancecube.model.PlayerSnapshot;
import com.chicluo.dancecube.model.Score;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染模块的离线测试：只依赖 JDK AWT，不访问网络。
 * 生成的图片统一落在 {@code build/tmp/} 下，方便人工查看。
 */
class RendererTest {

    private static final Path OUT = Path.of("build", "tmp");

    @Test
    void writesProfileCardPng() throws Exception {
        BufferedImage img = ProfileRenderer.buildInfo(snapshot(), 4, 3210);
        Path out = write(img, "profile_info.png");

        assertTrue(Files.exists(out), "个人信息卡片没有生成");
        assertTrue(Files.size(out) > 0, "个人信息卡片是空文件");
        assertEquals(ImageRenderer.W, img.getWidth());
        assertTrue(img.getHeight() > 700);
        // 渐变背景生效：顶部和底部像素不应相同
        assertNotEquals(img.getRGB(5, 5), img.getRGB(5, img.getHeight() - 5));
    }

    @Test
    void writesB15AnalysisPng() throws Exception {
        BufferedImage img = ImageRenderer.buildAnalysis(snapshot(), songs(), "b15");
        Path out = write(img, "ratio_b15.png");

        assertTrue(Files.exists(out), "B15 分析图没有生成");
        assertTrue(Files.size(out) > 0, "B15 分析图是空文件");
        assertEquals(ImageRenderer.W, img.getWidth());
        assertTrue(img.getHeight() > 1200);
    }

    @Test
    void writesBothModePng() throws Exception {
        BufferedImage img = ImageRenderer.buildBoth(snapshot(), songs(), songs());
        Path out = write(img, "ratio_both.png");

        assertTrue(Files.exists(out), "双表分析图没有生成");
        assertTrue(Files.size(out) > 0, "双表分析图是空文件");
        assertEquals(ImageRenderer.W, img.getWidth());
    }

    @Test
    void emptyDataStillRenders() throws Exception {
        PlayerSnapshot empty = new PlayerSnapshot(
                Map.of("UserName", "空数据", "UserID", 1), Map.of(), List.of(), List.of());
        BufferedImage img = ImageRenderer.buildAnalysis(empty, List.of(), "b30");
        Path out = write(img, "ratio_empty.png");

        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    void formatsNumbersAndFooterLikePython() {
        assertEquals("1,234,567", ImageRenderer.fmtInt(1234567));
        assertEquals("0", ImageRenderer.fmtInt(0));
        assertEquals("-", ImageRenderer.fmtInt(null));
        assertEquals("99.50%", ImageRenderer.pct(9950));

        List<String> lines = ImageRenderer.footerLines("合计 12000 · 平均 800.0");
        assertEquals(3, lines.size(), "footer 固定三行");
        assertEquals(ImageRenderer.SIGNATURE, lines.get(0));
        assertTrue(lines.get(1).startsWith("合计 12000 · 平均 800.0 ｜ 项目开源："));
        assertTrue(lines.get(1).contains("生成时间："));
        assertEquals("体验QQ群：566567000", lines.get(2));
        assertTrue(lines.get(0).contains("洛因"));
        assertTrue(lines.get(0).contains("舞小鲸"));
    }

    // ------------------------------------------------------------------ 夹具

    private static Path write(BufferedImage img, String fileName) throws Exception {
        Files.createDirectories(OUT);
        Path out = OUT.resolve(fileName);
        assertTrue(ImageIO.write(img, "png", out.toFile()), "PNG 编码失败");
        return out;
    }

    private static PlayerSnapshot snapshot() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("UserName", "洛因");
        info.put("UserID", 1234567);
        info.put("MusicScore", 9876543);
        info.put("ComboPercent", 9950); // 接口里是 99.50 * 100
        info.put("RankNation", 4321);
        info.put("LvRatio", 23456);
        info.put("CityName", "上海");
        info.put("TeamName", "舞小鲸");

        Map<String, Object> account = Map.of("Gold", 123456789);
        List<Map<String, Object>> stats = List.of(
                Map.of("ReplyTextItemID", 5, "Content", "2468"));
        List<Map<String, Object>> history = List.of(
                hist("2026-11-01 10:00:00", 21000),
                hist("2026-11-05 19:30:00", 22400),
                hist("2026-11-09 21:10:00", 21950),
                hist("2026-11-12 20:00:00", 23456));
        return new PlayerSnapshot(info, account, stats, history);
    }

    private static Map<String, Object> hist(String logTime, int lvRatio) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("LogTime", logTime);
        m.put("LvRatio", lvRatio);
        return m;
    }

    private static List<Score> songs() {
        List<Score> list = new ArrayList<>();
        list.add(score(1001, "Calavera", 1, 3, "[高]专家(15)", 15, 99.87, 1520, "AP"));
        list.add(score(1002, "群青", 1, 2, "[中]进阶(12)", 12, 98.44, 1180, "FC"));
        list.add(score(1003, "B.B.K.K.B.K.K.", 1, 4, "[秀]大师(16)", 16, 97.21, 1400, "SS"));
        list.add(score(2001, "自制谱：极夜星轨（长曲版·特别加长混音）", 2, 5, "[S+]传奇(17)", 17, 100.0, 1600, "AP"));
        for (int i = 5; i <= 15; i++) {
            list.add(score(1000 + i, "测试曲目 " + i, 1, 2, "[中]进阶(12)", 12,
                    95.0 + i * 0.2, 1500 - i * 7, "S"));
        }
        return list;
    }

    private static Score score(long id, String name, int ownerType, int levelType, String levelTypeName,
                               int level, double acc, int rating, String grade) {
        return new Score(id, name, ownerType, 1, "国语", levelType, levelTypeName, -1, level,
                acc, (int) (acc * 10000), 1000, 0, "2026-11-12 20:00:00", rating, grade);
    }
}
