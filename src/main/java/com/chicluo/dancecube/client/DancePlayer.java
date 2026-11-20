package com.chicluo.dancecube.client;

import com.chicluo.dancecube.model.Score;
import com.chicluo.dancecube.model.PlayerSnapshot;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家数据拉取，对标 Python 版 {@code core/client.py} 的 {@code Player}。
 * 只做「拿数据」，战力/图片/导出交给对应模块。
 */
public class DancePlayer {

    private static final Map<Integer, String> GENRE_NAMES = Map.of(
            1, "国语", 3, "韩文", 4, "欧美", 6, "其他");

    private final DanceApi api;
    private final Map<String, String> token;
    private final com.chicluo.dancecube.lib.MusicLibrary library;
    private JsonNode infoCache;
    private List<Score> scoresCache;
    private Map<Long, String> categoryCache;

    public DancePlayer(Map<String, String> token) {
        this(token, null);
    }

    public DancePlayer(Map<String, String> token, com.chicluo.dancecube.lib.MusicLibrary library) {
        this.token = token;
        this.library = library;
        String access = token.get("access_token");
        String uid = token.get("userId");
        if (access == null || access.isBlank()) {
            throw new IllegalArgumentException("token 里没有 access_token");
        }
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("token 里没有 userId");
        }
        this.api = new DanceApi(access, uid);
    }

    // ------------------------------------------------------------------ 基础信息

    public JsonNode info() {
        if (infoCache != null && infoCache.has("UserID")) {
            return infoCache;
        }
        var r = api.get("/Dance/api/User/GetInfo",
                Map.of("userId", api.uid(), "getNationRank", "true"));
        if (r.body() != null && r.body().has("UserID")) {
            infoCache = r.body();
        }
        return r.body() == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : r.body();
    }

    public Map<String, Object> infoMap() {
        return toMap(info());
    }

    public Map<String, Object> account() {
        return toMap(api.get("/Dance/api/User/GetAccountInfo", null).body());
    }

    public List<Map<String, Object>> statsItems() {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode body = api.get("/Dance/api/ReplyTextItem/GetAllList", Map.of("machineId", "0")).body();
        if (body != null && body.isArray()) {
            for (JsonNode item : body) {
                out.add(toMap(item));
            }
        }
        return out;
    }

    public List<Map<String, Object>> history() {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode body = api.get("/Dance/api/User/GetLvRatioHistory",
                Map.of("userId", api.uid())).body();
        if (body != null && body.isArray()) {
            for (JsonNode item : body) {
                out.add(toMap(item));
            }
        }
        return out;
    }

    /** 一次性拉齐个人信息（info/account/stats/history），给渲染层用。 */
    public PlayerSnapshot snapshot() {
        return new PlayerSnapshot(infoMap(), account(), statsItems(), history());
    }

    // ------------------------------------------------------------------ 成绩

    /** 全量成绩：musicIndex 2..6 五类合并。官方唯一全量入口。 */
    public List<Score> scores() {
        if (scoresCache != null) {
            return scoresCache;
        }
        List<Score> out = new ArrayList<>();
        for (int cls = 2; cls <= 6; cls++) {
            var r = api.get("/Dance/api/User/GetMyRankNew", Map.of("musicIndex", String.valueOf(cls)));
            JsonNode body = r.body();
            if (body == null || !body.isArray()) {
                continue;
            }
            for (JsonNode song : body) {
                String name = song.path("Name").asText("");
                long musicId = song.path("MusicID").asLong(0);
                int ownerType = song.path("OwnerType").asInt(0);
                String clsName = categoryOf(musicId);
                JsonNode list = song.path("ItemRankList");
                if (list == null || !list.isArray()) {
                    continue;
                }
                for (JsonNode rec : list) {
                    int lev = rec.path("MusicLev").asInt(0);
                    int level = rec.path("MusicRank").asInt(0);
                    int pct = rec.path("PlayerPercent").asInt(0);
                    double acc = pct / 100.0;
                    int old = rec.path("MusicLevOld").asInt(-1);
                    out.add(new Score(
                            musicId,
                            name,
                            ownerType,
                            cls,
                            clsName,
                            lev,
                            levelTypeName(lev, old),
                            old,
                            level,
                            acc,
                            rec.path("PlayerScore").asInt(0),
                            rec.path("ComboCount").asInt(0),
                            rec.path("PlayerMiss").asInt(0),
                            rec.path("RecordTime").asText(""),
                            com.chicluo.dancecube.analysis.RatingCalculator.computeRating(level, acc),
                            com.chicluo.dancecube.analysis.RatingCalculator.gradeOf(acc, rec.path("PlayerMiss").asInt(0))));
                }
            }
        }
        scoresCache = out;
        return out;
    }

    /** 最近游玩（GetLastPlay）。 */
    public List<Map<String, Object>> recent() {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode body = api.get("/Dance/api/User/GetLastPlay", null).body();
        if (body != null && body.isArray()) {
            for (JsonNode item : body) {
                out.add(toMap(item));
            }
        }
        return out;
    }

    /** 最近游玩（GetLastPlay）转成与 scores() 对齐的 Score，供 R15 图用。 */
    public List<Score> recentScores() {
        Map<Long, Integer> ownerMap = new HashMap<>();
        try {
            for (Score s : scores()) {
                ownerMap.put(s.musicId(), s.ownerType());
            }
        } catch (RuntimeException ignored) {
            // 拿不到全量成绩就不标 ownerType
        }
        List<Score> out = new ArrayList<>();
        for (Map<String, Object> r : recent()) {
            long mid = asLong(r.get("MusicID"));
            int ownerType = ownerMap.getOrDefault(mid, 0);
            int lev = (int) asLong(r.get("MusicLev"));
            int level = (int) asLong(r.get("MusicLevel"));
            double acc = asLong(r.get("PlayerPercent")) / 100.0;
            int miss = (int) asLong(r.get("PlayerMiss"));
            int old = (int) asLong(r.get("MusicLevOld"));
            String name = r.get("MusicName") == null ? "?" : String.valueOf(r.get("MusicName"));
            out.add(new Score(
                    mid, name, ownerType, 0, "-",
                    lev, levelTypeName(lev, old), old, level, acc,
                    (int) asLong(r.get("PlayerScore")),
                    (int) asLong(r.get("ComboCount")),
                    miss,
                    r.get("RecordTime"),
                    com.chicluo.dancecube.analysis.RatingCalculator.computeRating(level, acc),
                    com.chicluo.dancecube.analysis.RatingCalculator.gradeOf(acc, miss)));
        }
        return out;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return (long) Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    // ------------------------------------------------------------------ 工具

    /** 官谱曲风：用曲库 MusicCategoryNew 反查（GetMyRankNew 不带这个字段）。 */
    private String categoryOf(long musicId) {
        if (library == null) {
            return "";
        }
        if (categoryCache == null) {
            Map<Long, String> map = new HashMap<>();
            try {
                for (com.chicluo.dancecube.model.Song song : library.loadOfficial()) {
                    if (song.categoryNew() != null) {
                        map.put(song.musicId(), song.categoryName());
                    }
                }
            } catch (RuntimeException ignored) {
                // 曲库不可用时留空（不能返回 "-"，Excel 会把 - 开头当公式、加 ' 前缀显示成 '-）
            }
            categoryCache = map;
        }
        return categoryCache.getOrDefault(musicId, "");
    }

    private static int mapCategory(int categoryNew) {
        return categoryNew;
    }

    /** 天梯（全民星）段位与分数：(grade, point)；没有赛季返回 null。 */
    public int[] ladder() {
        JsonNode body = api.get("/Dance/api/Match/GetQuanMinSeasons", null).body();
        if (body == null || !body.isArray() || body.isEmpty()) {
            return null;
        }
        JsonNode pick = null;
        for (JsonNode s : body) {
            if (s.path("IsCurrent").asBoolean(false)) {
                pick = s;
                break;
            }
        }
        if (pick == null) {
            for (JsonNode s : body) {
                if (s.path("IsLast").asBoolean(false)) {
                    pick = s;
                    break;
                }
            }
        }
        if (pick == null) {
            pick = body.get(body.size() - 1);
        }
        return new int[]{pick.path("LevelGrade").asInt(0), pick.path("LevelPoint").asInt(0)};
    }

    private static String levelTypeName(int levNew, int old) {
        if (levNew >= 100) {
            String label = switch (levNew) {
                case 101 -> "基础";
                case 102 -> "进阶";
                case 103 -> "专家";
                case 104 -> "大师";
                case 105 -> "传奇";
                default -> String.valueOf(levNew);
            };
            return "[S+]" + label;
        }
        String raw = switch (levNew) {
            case 11 -> "基础";
            case 12 -> "进阶";
            case 13 -> "专家";
            case 14 -> "大师";
            case 15 -> "传奇";
            default -> "经典";
        };
        if (old == 0 || old == 1 || old == 2) {
            String shortName = switch (old) {
                case 0 -> "初";
                case 1 -> "中";
                default -> "高";
            };
            return "[" + shortName + "]" + raw;
        }
        return "[秀]" + raw;
    }

    private static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> out = new HashMap<>();
        if (node == null || !node.isObject()) {
            return out;
        }
        node.properties().forEach(e -> {
            JsonNode v = e.getValue();
            Object value;
            if (v.isNumber()) {
                value = v.isFloatingPointNumber() ? v.doubleValue() : v.longValue();
            } else if (v.isBoolean()) {
                value = v.booleanValue();
            } else if (v.isTextual()) {
                value = v.asText();
            } else {
                value = v.toString();
            }
            out.put(e.getKey(), value);
        });
        return out;
    }
}
