package com.chicluo.dancecube;

import com.chicluo.dancecube.analysis.RatioAnalyzer;
import com.chicluo.dancecube.analysis.RatioResult;
import com.chicluo.dancecube.client.DancePlayer;
import com.chicluo.dancecube.export.CatalogExporter;
import com.chicluo.dancecube.export.ScoreExporter;
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

    public DanceCube(Path dataDir) {
        this.library = new MusicLibrary(dataDir);
        this.tokens = new TokenStore(dataDir);
    }

    public MusicLibrary library() {
        return library;
    }

    public TokenStore tokens() {
        return tokens;
    }

    // ------------------------------------------------------------------ 曲库

    /** 双库搜索（官谱在前）。 */
    public List<Song> search(String keyword, int limit) {
        return library.search(keyword, limit);
    }

    /** 曲目详情文本；duration/kbps 未知传 null。 */
    public String formatSong(Song song, Integer durationSeconds, Integer kbps) {
        return SongFormatter.format(song, durationSeconds, kbps);
    }

    // ------------------------------------------------------------------ 玩家

    /** 用已保存的 token 构造玩家；没有 token 返回 null。 */
    public DancePlayer player(String uid) {
        Map<String, String> token = tokens.get(uid);
        if (token == null) {
            return null;
        }
        return new DancePlayer(token);
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

    public Path exportScores(String uid, Path outDir) throws IOException {
        return ScoreExporter.exportScores(player(uid).scores(), outDir);
    }

    public Path exportCatalog(String kind, Path outDir) throws IOException {
        if ("official".equals(kind)) {
            return CatalogExporter.buildOfficial(library.loadOfficial(), outDir);
        }
        return CatalogExporter.buildUser(library.loadUser(), outDir);
    }
}
