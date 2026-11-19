package com.chicluo.dancecube.lib;

import com.chicluo.dancecube.model.DanceLevel;
import com.chicluo.dancecube.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 曲目详情文本，对标 Python 版 {@code core/music.py} 的 {@code format}。
 */
public final class SongFormatter {

    /** 只有这几首是商店隐藏曲（未购买前无音频）。 */
    private static final Set<Long> HIDDEN_SONG_IDS =
            Set.of(3461L, 672L, 833L, 4023L, 4024L, 4025L, 6873L, 6874L, 6875L);

    private SongFormatter() {
    }

    /**
     * @param duration 音频时长（秒），null 表示未知
     * @param kbps     码率，仅自制谱显示
     */
    public static String format(Song song, Integer duration, Integer kbps) {
        StringBuilder sb = new StringBuilder();
        boolean hasBad = song.levels().stream().anyMatch(DanceLevel::isBad);
        sb.append("🎵 ").append(song.name() == null ? "?" : song.name())
                .append("（").append(song.sourceLabel()).append(" · 音乐 ID ").append(song.musicId()).append("）");
        if (hasBad) {
            sb.append("\n  ⚠️ 存在异常谱面，游戏内购买时会提示");
        }

        if (duration != null && duration > 0) {
            String label = (duration / 60) + ":" + String.format("%02d", duration % 60);
            if (!song.isOfficial()) {
                sb.append("\n  音频时长 ").append(label);
                if (kbps != null && kbps > 0) {
                    sb.append(" · ").append(kbps).append(" kbps");
                }
            } else {
                sb.append("\n  时长 ").append(label);
            }
        } else if (song.isOfficial() && HIDDEN_SONG_IDS.contains(song.musicId())) {
            sb.append("\n  时长：商店隐藏歌曲，无法拉取");
        }

        if ((song.ownerId() != null) || (song.ownerName() != null && !song.ownerName().isEmpty())) {
            sb.append("\n  作者：【").append(song.ownerId() == null ? "-" : song.ownerId())
                    .append("】").append(song.ownerName() == null ? "" : song.ownerName());
        }
        if (!song.tags().isEmpty()) {
            sb.append("\n  标签：").append(String.join(" / ", song.tags()));
        }

        for (DanceLevel lv : song.levels()) {
            if (!lv.isShow() && lv.level() < 0) {
                continue; // 定数未填按不存在处理
            }
            String label = (lv.isBad() ? "⚠️ " : "") + lv.label();
            if (lv.combo() > 0) {
                label += " · 物量 " + lv.combo();
            }
            sb.append("\n  ").append(label);
        }
        if (song.levels().isEmpty()) {
            sb.append("\n  暂无谱面数据");
        }

        List<String> extra = new ArrayList<>();
        if (song.price() != null) {
            extra.add(song.price() + " 金币");
        }
        if (song.like() != null) {
            extra.add(song.like() + " 赞");
        }
        if (!extra.isEmpty()) {
            sb.append("\n  ").append(String.join(" · ", extra));
        }
        return sb.toString();
    }
}
