package com.chicluo.dancecube.model;

/**
 * 一个谱面难度（对标 Python 版 {@code _norm_levels} 产出的 dict）。
 *
 * @param tier  经典档位「初级/中级/高级」，show+ 为 "show+"，未标注为空串
 * @param raw   档位原始名（基础/进阶/专家/大师/传奇）
 * @param level 定数（0 表示未填）
 * @param combo 物量
 * @param isShow 是否秀谱（MusicLevNew >= 100）
 * @param levNew MusicLevNew 原始值
 * @param order  经典档位排序（0/1/2，其余 9）
 * @param isBad  是否标记为 bad
 */
public record DanceLevel(
        String tier,
        String raw,
        int level,
        int combo,
        boolean isShow,
        int levNew,
        int order,
        boolean isBad) {

    /** 生成 [初]基础(14) / [秀]大师(18) / [S+]传奇(17) 这样的标签。 */
    public String label() {
        String name;
        if (isShow) {
            name = "[S+]" + (raw == null ? "" : raw);
        } else if (!tier.isEmpty()) {
            name = "[" + switch (tier) {
                case "初级" -> "初";
                case "中级" -> "中";
                case "高级" -> "高";
                default -> "";
            } + "]" + raw;
        } else {
            name = "[秀]" + (raw == null ? "" : raw);
        }
        if (level > 0) {
            return name + "(" + level + ")";
        }
        if (level == 0) {
            return name + "(0)";
        }
        return name + "(未填)";
    }
}
