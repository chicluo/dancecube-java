package com.chicluo.dancecube;

import com.chicluo.dancecube.analysis.RatioAnalyzer;
import com.chicluo.dancecube.analysis.RatioResult;
import com.chicluo.dancecube.client.DanceApi;
import com.chicluo.dancecube.client.DancePlayer;
import com.chicluo.dancecube.export.CatalogExporter;
import com.chicluo.dancecube.export.ScoreExporter;
import com.chicluo.dancecube.lib.DurationStore;
import com.chicluo.dancecube.lib.MusicLibrary;
import com.chicluo.dancecube.lib.SongFormatter;
import com.chicluo.dancecube.lib.TokenStore;
import com.chicluo.dancecube.model.PlayerSnapshot;
import com.chicluo.dancecube.model.Score;
import com.chicluo.dancecube.model.Song;
import com.chicluo.dancecube.render.ProfileRenderer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 门面：把曲库、玩家数据、战力分析、图片渲染、导出串起来。
 *
 * <p>不绑定任何 bot 框架——调用方把 {@link Path} 数据目录传进来，拿文本/图片路径/文件路径自己发。</p>
 */
public class DanceCube {

    private final MusicLibrary library;
    private final TokenStore tokens;
    private final com.chicluo.dancecube.lib.PushStore push;
    private final DurationStore durations;

    public DanceCube(Path dataDir) {
        this.library = new MusicLibrary(dataDir);
        this.tokens = new TokenStore(dataDir);
        this.push = new com.chicluo.dancecube.lib.PushStore(dataDir.resolve("push_targets.json"));
        // 音频时长/码率由 AstrBot 插件维护，文件缺失时自动退化为空表
        this.durations = DurationStore.load(dataDir);
    }

    public MusicLibrary library() {
        return library;
    }

    public TokenStore tokens() {
        return tokens;
    }

    /** 音频时长/码率数据（读自数据目录下的 music_duration*.json / music_bitrate.json）。 */
    public DurationStore durationStore() {
        return durations;
    }

    public com.chicluo.dancecube.lib.PushStore pushStore() {
        return push;
    }

    // ------------------------------------------------------------------ 曲库

    /** 双库搜索（官谱在前）。 */
    public List<Song> search(String keyword, int limit) {
        return library.search(keyword, limit);
    }

    /** 按 MusicID 精确查找（含官谱与自制谱）；没有返回 null。 */
    public Song findById(long musicId) {
        return library.findById(musicId);
    }

    /** 曲目详情文本；时长/码率先查缓存，自制谱再按需探测（下载音频读一次）并写回。 */
    public String formatSong(Song song, Integer durationSeconds, Integer kbps) {
        Integer dur = durationSeconds != null ? durationSeconds : durations.duration(song.musicId());
        Integer rate = kbps != null ? kbps : durations.kbps(song.musicId());
        if (dur == null && "user".equals(song.source())) {
            dur = durations.probeAndCache(song.musicId(), resolveAudioUrl(song));
        }
        return SongFormatter.format(song, dur, rate);
    }

    /**
     * 拿自制谱音频地址：有 audioUrl 直接用；没有就调 GetGoodsInfo 找 ListFile 里
     * FileType=2 的 Url（对齐 Python {@code music.py::probe_duration} 的兜底路径）。
     */
    private String resolveAudioUrl(Song song) {
        if (song.audioUrl() != null && !song.audioUrl().isBlank()) {
            return song.audioUrl();
        }
        for (Map.Entry<String, Map<String, String>> entry : tokens.all().entrySet()) {
            String access = entry.getValue().get("access_token");
            if (access == null || access.isBlank()) {
                continue;
            }
            try {
                DanceApi api = new DanceApi(access, entry.getKey());
                DanceApi.ApiResult result = api.get("/Dance/api/MusicData/GetGoodsInfo",
                        Map.of("musicId", String.valueOf(song.musicId())));
                if (result.ok()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : result.body().path("ListFile")) {
                        if (item.path("FileType").asInt(0) == 2) {
                            String url = item.path("Url").asText("");
                            if (!url.isBlank()) {
                                return url;
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // 该 token 不可用时换下一个
            }
            break;
        }
        return null;
    }

    // ------------------------------------------------------------------ 玩家

    /** 用已保存的 token 构造玩家；没有 token 返回 null。 */
    public DancePlayer player(String uid) {
        Map<String, String> token = tokens.get(uid);
        if (token == null) {
            return null;
        }
        return new DancePlayer(token, library);
    }

    public PlayerSnapshot snapshot(String uid) {
        return player(uid).snapshot();
    }

    // ------------------------------------------------------------------ 战力

    public RatioResult analyze(String uid, String mode) {
        DancePlayer player = player(uid);
        return RatioAnalyzer.analyze(player.scores(), mode);
    }

    // ------------------------------------------------------------------ 图片

    /** 个人信息卡片；返回 PNG 路径。 */
    public Path renderProfile(String uid, Path outDir) throws IOException {
        PlayerSnapshot snapshot = snapshot(uid);
        java.awt.image.BufferedImage image = ProfileRenderer.buildInfo(snapshot);
        java.nio.file.Files.createDirectories(outDir);
        Path out = outDir.resolve("info_" + uid + "_" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(image, "png", out.toFile());
        return out;
    }

    // ------------------------------------------------------------------ 导出

    /** 导出全部成绩（基础 14 列 + 时长 / 码率）。 */
    public Path exportScores(String uid, Path outDir) throws IOException {
        return exportScores(uid, outDir, durations);
    }

    /** 导出全部成绩，可传入自定义的音频信息；{@code null} 表示不补时长/码率两列。 */
    public Path exportScores(String uid, Path outDir, DurationStore durationStore) throws IOException {
        var player = player(uid);
        String roleId = player.info().path("UserID").asText("");
        if (roleId.isBlank()) {
            roleId = uid;
        }
        return ScoreExporter.exportScores(player.scores(), outDir, roleId, durationStore);
    }

    public Path exportCatalog(String kind, Path outDir) throws IOException {
        java.nio.file.Files.createDirectories(outDir);
        com.chicluo.dancecube.lib.DurationStore durations = durationStore();
        if ("official".equals(kind)) {
            return CatalogExporter.buildOfficial(
                    library.loadOfficial(), outDir.resolve("官谱全表.xlsx"), durations);
        }
        return CatalogExporter.buildUser(library.loadUser(), outDir.resolve("自制谱全表.xlsx"), durations);
    }
}
