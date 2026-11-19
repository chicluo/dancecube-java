package com.chicluo.dancecube.render;

import com.chicluo.dancecube.model.PlayerSnapshot;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.chicluo.dancecube.render.ImageRenderer.W;
import static com.chicluo.dancecube.render.ImageRenderer.composite;
import static com.chicluo.dancecube.render.ImageRenderer.drawFooter;
import static com.chicluo.dancecube.render.ImageRenderer.drawHeader;
import static com.chicluo.dancecube.render.ImageRenderer.drawHistory;
import static com.chicluo.dancecube.render.ImageRenderer.drawStats;
import static com.chicluo.dancecube.render.ImageRenderer.fmtInt;
import static com.chicluo.dancecube.render.ImageRenderer.gradientBg;
import static com.chicluo.dancecube.render.ImageRenderer.newGraphics;
import static com.chicluo.dancecube.render.ImageRenderer.newOverlay;
import static com.chicluo.dancecube.render.ImageRenderer.pct;
import static com.chicluo.dancecube.render.ImageRenderer.statItem;

/**
 * 个人信息卡片，复刻 Python 版 {@code core/profile_image.py} 的 {@code build_info}。
 *
 * <p>版式：表头（头像 / 昵称 / 战力）→ 两行统计块（积分 / 全连率 / 排名 / 金币 / 游玩次数，
 * 天梯段位 / 天梯分 / 战力 / 城市 / 战队）→ 战力变化曲线 → footer。</p>
 */
public final class ProfileRenderer {

    private ProfileRenderer() {
    }

    /** 天梯段位名，与 Python 版 {@code LADDER_NAMES} 一致。 */
    public static final String[] LADDER_NAMES = {
            "未定级", "一星", "二星", "三星", "星钻", "表神", "里神"
    };

    /**
     * 生成个人信息卡片。
     *
     * <p>天梯段位/分数在 Python 版来自单独的接口 {@code GetQuanMinSeasons}，不在
     * {@link PlayerSnapshot} 里；这里先尝试 info 中的可选键（LadderGrade/LevelGrade、
     * LadderPoint/LevelPoint），取不到就显示 "-"。</p>
     */
    public static BufferedImage buildInfo(PlayerSnapshot snapshot) {
        Map<String, Object> info = snapshot == null ? Map.of()
                : (snapshot.info() == null ? Map.of() : snapshot.info());
        Object grade = info.get("LadderGrade") != null ? info.get("LadderGrade") : info.get("LevelGrade");
        Object point = info.get("LadderPoint") != null ? info.get("LadderPoint") : info.get("LevelPoint");
        return buildInfo(snapshot, grade, point);
    }

    /**
     * 生成个人信息卡片，显式传入天梯段位与天梯分。
     *
     * @param ladderGrade 段位编号（0..6），可为 {@code null}
     * @param ladderPoint 天梯分，可为 {@code null}
     */
    public static BufferedImage buildInfo(PlayerSnapshot snapshot, Object ladderGrade, Object ladderPoint) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }
        Map<String, Object> info = ImageRenderer.orEmpty(snapshot.info());
        Map<String, Object> account = ImageRenderer.orEmpty(snapshot.account());
        List<Map<String, Object>> statsItems = snapshot.statsItems() == null
                ? List.of() : snapshot.statsItems();
        List<Map<String, Object>> hist = snapshot.history() == null
                ? List.of() : snapshot.history();

        int statH = 84;
        int gap = 16;
        int y1 = 196;
        int y2 = y1 + statH + gap;
        int histY = y2 + statH + 12;
        int histH = 286;
        int height = histY + histH + 86;

        BufferedImage img = gradientBg(W, height);
        BufferedImage ov = newOverlay(W, height);
        Graphics2D g = newGraphics(ov);

        drawHeader(g, info);

        drawStats(g, y1, List.of(
                new ImageRenderer.Stat("积分", fmtInt(info.get("MusicScore"))),
                new ImageRenderer.Stat("全连率", pct(info.get("ComboPercent"))),
                new ImageRenderer.Stat("全国排名", fmtInt(info.get("RankNation"))),
                new ImageRenderer.Stat("金币", fmtInt(account.get("Gold"))),
                new ImageRenderer.Stat("游玩次数", statItem(statsItems, 5))));

        drawStats(g, y2, List.of(
                new ImageRenderer.Stat("天梯段位", ladderName(ladderGrade)),
                new ImageRenderer.Stat("天梯分", ladderPoint == null ? "-" : fmtInt(ladderPoint)),
                new ImageRenderer.Stat("战力", fmtInt(info.get("LvRatio"))),
                new ImageRenderer.Stat("城市", blankTo(info.get("CityName"), "-")),
                new ImageRenderer.Stat("战队", blankTo(info.get("TeamName"), "无"))));

        drawHistory(g, histY, histH, hist);

        drawFooter(g, height, "");

        g.dispose();
        return composite(img, ov);
    }

    /** 段位编号 → 中文名，越界或非法返回 "-"。 */
    public static String ladderName(Object grade) {
        if (grade == null) {
            return "-";
        }
        int g;
        if (grade instanceof Number n) {
            g = n.intValue();
        } else {
            try {
                g = Integer.parseInt(String.valueOf(grade).trim());
            } catch (NumberFormatException e) {
                return "-";
            }
        }
        if (g >= 0 && g < LADDER_NAMES.length) {
            return LADDER_NAMES[g];
        }
        return "-";
    }

    private static String blankTo(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }
}
