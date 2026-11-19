package com.chicluo.dancecube.lib;

import com.chicluo.dancecube.model.DanceLevel;
import com.chicluo.dancecube.model.Song;
import com.chicluo.dancecube.util.TextNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 曲库：官谱 + 自制谱的加载与搜索，对标 Python 版 {@code core/music.py} 的 {@code MusicLib}。
 *
 * <p>刷新（联网）在 {@link MusicLibrary#refreshOfficial} / {@link MusicLibrary#refreshUser}，
 * 本地 JSON 读取与搜索不依赖网络。</p>
 */
public class MusicLibrary {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final java.util.Map<Integer, String> SHOW_TYPE = java.util.Map.of(
            101, "基础", 102, "进阶", 103, "专家", 104, "大师", 105, "传奇");
    private static final java.util.Map<Integer, String> CLASSIC_TIER = java.util.Map.of(
            0, "初级", 1, "中级", 2, "高级");
    private static final java.util.Map<Integer, String> CLASSIC_RAW = java.util.Map.of(
            11, "基础", 12, "进阶", 13, "专家", 14, "大师", 15, "传奇");

    private final Path officialPath;
    private final Path userPath;

    private List<Song> official;
    private List<Song> user;

    public MusicLibrary(Path dataDir) {
        this.officialPath = dataDir.resolve("music_library.json");
        this.userPath = dataDir.resolve("music_library_user.json");
    }

    public Path officialPath() {
        return officialPath;
    }

    public Path userPath() {
        return userPath;
    }

    // ------------------------------------------------------------------ 加载

    public synchronized List<Song> loadOfficial() {
        if (official == null) {
            official = readSongs(officialPath, true);
        }
        return official;
    }

    public synchronized List<Song> loadUser() {
        if (user == null) {
            user = readSongs(userPath, false);
        }
        return user;
    }

    private List<Song> readSongs(Path path, boolean isOfficial) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<Song> out = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                out.add(isOfficial ? parseOfficial(node) : parseUser(node));
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------ 解析

    private static Song parseOfficial(JsonNode node) {
        return new Song(
                "official",
                text(node, "Name", "?"),
                longValue(node, "MusicID"),
                parseLevels(node.path("MusicItemList")),
                null,
                null,
                null,
                "",
                List.of(),
                intValue(node, "MusicCategoryNew", null),
                "",
                "");
    }

    private static Song parseUser(JsonNode node) {
        return new Song(
                "user",
                text(node, "GoodsName", "?"),
                longValue(node, "MusicID"),
                parseLevels(node.path("LevelList")),
                intValue(node, "PriceSale", null),
                intValue(node, "LikeCount", null),
                longValue(node, "OwnerID") == 0 ? null : longValue(node, "OwnerID"),
                text(node, "OwnerName", ""),
                stringList(node.path("TagList")),
                null,
                text(node, "GoodsIntro", ""),
                text(node, "AudioUrl", ""));
    }

    private static List<DanceLevel> parseLevels(JsonNode list) {
        List<DanceLevel> out = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return out;
        }
        for (JsonNode it : list) {
            int levNew = intValue(it, "MusicLevNew", 0);
            int old = it.has("MusicLev") ? intValue(it, "MusicLev", -1)
                    : intValue(it, "MusicLevOld", -1);
            int level = intValue(it, it.has("MusicLevel") ? "MusicLevel" : "MusicRank", 0);
            int combo = intValue(it, it.has("MusicCombo") ? "MusicCombo" : "ComboCount", 0);

            String tier;
            String raw;
            boolean isShow = levNew >= 100;
            if (isShow) {
                tier = "show+";
                raw = SHOW_TYPE.getOrDefault(levNew, String.valueOf(levNew));
            } else if (CLASSIC_TIER.containsKey(old)) {
                tier = CLASSIC_TIER.get(old);
                raw = CLASSIC_RAW.getOrDefault(levNew, "经典");
            } else {
                if (level <= 0 && combo <= 0) {
                    continue; // 占位条目，游戏里玩不到
                }
                tier = "";
                raw = CLASSIC_RAW.getOrDefault(levNew, "经典");
            }
            out.add(new DanceLevel(
                    tier,
                    raw,
                    level,
                    combo,
                    isShow,
                    levNew,
                    (old == 0 || old == 1 || old == 2) ? old : 9,
                    it.path("IsBad").asBoolean(false)));
        }
        out.sort(Comparator.comparing(DanceLevel::isShow).thenComparingInt(DanceLevel::levNew));
        return out;
    }

    // ------------------------------------------------------------------ 搜索

    /** 双库合并搜索（官谱在前），只能搜到已加载的数据。 */
    public List<Song> search(String keyword, int limit) {
        String kw = TextNormalizer.normalize(keyword);
        if (kw.isEmpty()) {
            return List.of();
        }
        List<Song> hits = new ArrayList<>();
        for (Song song : loadOfficial()) {
            if (TextNormalizer.normalize(song.name()).contains(kw)) {
                hits.add(song);
                if (hits.size() >= limit) {
                    return hits;
                }
            }
        }
        for (Song song : loadUser()) {
            if (TextNormalizer.normalize(song.name()).contains(kw)) {
                hits.add(song);
                if (hits.size() >= limit) {
                    return hits;
                }
            }
        }
        return hits;
    }

    /** 按 MusicID 精确查找（双库）。 */
    public Song findById(long musicId) {
        for (Song song : loadOfficial()) {
            if (song.musicId() == musicId) {
                return song;
            }
        }
        for (Song song : loadUser()) {
            if (song.musicId() == musicId) {
                return song;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 刷新

    /** 联网刷新官谱；实现见后续版本（对齐 Python 版 refresh_official）。 */
    public boolean refreshOfficial() {
        throw new UnsupportedOperationException("refreshOfficial 尚未实现");
    }

    /** 联网刷新自制谱；实现见后续版本（对齐 Python 版 refresh_user）。 */
    public boolean refreshUser() {
        throw new UnsupportedOperationException("refreshUser 尚未实现");
    }

    public synchronized void invalidate() {
        official = null;
        user = null;
    }

    // ------------------------------------------------------------------ 工具

    private static String text(JsonNode node, String field, String def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asText(def);
    }

    private static int intValue(JsonNode node, String field, Integer def) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return def == null ? 0 : def;
        }
        return v.asInt(def == null ? 0 : def);
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? 0L : v.asLong(0L);
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                out.add(item.asText());
            }
        }
        return out;
    }
}
