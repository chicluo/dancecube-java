package com.chicluo.dancecube.model;

import java.util.List;

/**
 * 一首曲目（官谱或自制谱）。
 *
 * @param source   official / user
 * @param name     曲名
 * @param musicId  音乐 ID
 * @param levels   谱面列表
 * @param price    自制谱价格（官谱为 null）
 * @param like     点赞数（官谱为 null）
 * @param ownerId  作者 ID（官谱为 null）
 * @param ownerName 作者昵称
 * @param tags     标签
 * @param categoryNew 曲风（官谱：1 国语 / 3 韩文 / 4 欧美 / 6 其他）
 * @param intro    简介（自制谱）
 * @param audioUrl 音频地址（自制谱）
 */
public record Song(
        String source,
        String name,
        long musicId,
        List<DanceLevel> levels,
        Integer price,
        Integer like,
        Long ownerId,
        String ownerName,
        List<String> tags,
        Integer categoryNew,
        String intro,
        String audioUrl) {

    public boolean isOfficial() {
        return "official".equals(source);
    }

    public String sourceLabel() {
        return isOfficial() ? "官谱" : "自制谱";
    }

    /** 曲风名（官谱用）。 */
    public String categoryName() {
        if (categoryNew == null) {
            return "";
        }
        return switch (categoryNew) {
            case 1 -> "国语";
            case 3 -> "韩文";
            case 4 -> "欧美";
            case 6 -> "其他";
            default -> "";
        };
    }
}
