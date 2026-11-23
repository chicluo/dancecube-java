package com.chicluo.dancecube.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 曲目音频信息（时长 / 码率），对标 Python 版 {@code core/music.py} 的
 * {@code load_duration()} 与 {@code load_bitrate()}。
 *
 * <p>数据文件由 AstrBot 插件在探测音频时维护，放在 {@code plugin_data/astrbot_plugin_dancecube/}
 * 下（正式布局在 {@code music/} 子目录，两个位置都会尝试）：</p>
 * <ul>
 *   <li>{@code music_duration.json} —— 官谱 musicId → 秒</li>
 *   <li>{@code music_duration_user.json} —— 自制谱 musicId → 秒，同名键覆盖官谱</li>
 *   <li>{@code music_bitrate.json} —— musicId → 音频体积（字节），用来推算码率</li>
 * </ul>
 *
 * <p>码率算法与 Python 一致：{@code round(size * 8 / duration / 1000)}（四舍六入五成双）。
 * 文件缺失或条目缺失一律返回 {@code null}，不抛异常。</p>
 */
public final class DurationStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(DurationStore.class.getName());

    private static final DurationStore EMPTY = new DurationStore(Map.of(), Map.of(), null);

    private final Map<Long, Double> durations;
    private final Map<Long, Long> sizes;
    private final Path dataDir;
    private static final Object WRITE_LOCK = new Object();

    private DurationStore(Map<Long, Double> durations, Map<Long, Long> sizes, Path dataDir) {
        // 全量拉取的后台线程会写、导出/查询线程会读，用并发 Map 避免读写竞争
        this.durations = new ConcurrentHashMap<>(durations);
        this.sizes = new ConcurrentHashMap<>(sizes);
        this.dataDir = dataDir;
    }

    /** 空存储：所有查询都返回 null。 */
    public static DurationStore empty() {
        return EMPTY;
    }

    /**
     * 从数据目录加载。目录可以是插件数据根目录，也可以直接是 {@code music} 目录。
     *
     * @param dataDir 数据目录，可为 null
     * @return 加载结果；任何文件缺失或损坏都按空表处理
     */
    public static DurationStore load(Path dataDir) {
        if (dataDir == null) {
            return EMPTY;
        }
        Map<Long, Double> durations = new HashMap<>();
        readDurations(durations, resolve(dataDir, "music_duration.json"));
        // 自制谱的探测结果写在 music_duration_user.json，和 Python 一样后读覆盖先读
        readDurations(durations, resolve(dataDir, "music_duration_user.json"));
        Map<Long, Long> sizes = new HashMap<>();
        readSizes(sizes, resolve(dataDir, "music_bitrate.json"));
        if (durations.isEmpty() && sizes.isEmpty()) {
            return EMPTY;
        }
        return new DurationStore(durations, sizes, dataDir);
    }

    /** 用现成映射构造，主要给测试与调用方自定义数据源用。 */
    public static DurationStore of(Map<Long, Double> durations, Map<Long, Long> sizes) {
        return new DurationStore(durations == null ? Map.of() : durations,
                sizes == null ? Map.of() : sizes, null);
    }

    /**
     * 缺时长时按需探测（下载音频读一次）并写回 {@code music_duration_user.json}。
     * 已知或探测失败都返回 null，不抛异常；写盘失败也不影响返回值。
     */
    public synchronized Integer probeAndCache(long musicId, String audioUrl) {
        Integer known = duration(musicId);
        if (known != null && kbps(musicId) != null) {
            return known;
        }
        // 有时长缺码率（体积没记录）也要再探一次，否则全量拉取永远补不上那一列
        AudioProbe.Result result = AudioProbe.probeFull(audioUrl);
        if (result == null || result.seconds() <= 0) {
            return known;
        }
        durations.put(musicId, result.seconds());
        if (result.bytes() > 0) {
            sizes.put(musicId, result.bytes());
        }
        writeUserDuration(musicId, result.seconds());
        if (result.bytes() > 0) {
            writeUserBitrate(musicId, result.bytes());
        }
        return (int) Math.max(1, Math.rint(result.seconds()));
    }

    /** 把新探测到的音频体积合并进 music_bitrate.json（读-改-写，加锁 + 临时文件替换）。 */
    private void writeUserBitrate(long musicId, long bytes) {
        if (dataDir == null) {
            return;
        }
        Path file = resolve(dataDir, "music_bitrate.json");
        if (file == null) {
            file = dataDir.resolve("music_bitrate.json");
        }
        synchronized (WRITE_LOCK) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
                if (Files.isRegularFile(file)) {
                    JsonNode existing = MAPPER.readTree(file.toFile());
                    if (existing != null && existing.isObject()) {
                        root = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
                    }
                }
                root.put(String.valueOf(musicId), bytes);
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "写回码率缓存失败：" + e);
            }
        }
    }

    /** 把新探测到的时长合并进 music_duration_user.json（读-改-写，加锁 + 临时文件替换）。 */
    private void writeUserDuration(long musicId, double seconds) {
        if (dataDir == null) {
            return;
        }
        Path file = resolve(dataDir, "music_duration_user.json");
        if (file == null) {
            file = dataDir.resolve("music_duration_user.json");
        }
        synchronized (WRITE_LOCK) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
                if (Files.isRegularFile(file)) {
                    JsonNode existing = MAPPER.readTree(file.toFile());
                    if (existing != null && existing.isObject()) {
                        root = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
                    }
                }
                root.put(String.valueOf(musicId), seconds);
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "写回时长缓存失败：" + e);
            }
        }
    }

    /** 音频时长的整秒数（四舍五入，对齐 Python {@code catalog.mmss}）；未知返回 null。 */
    public Integer duration(long musicId) {
        Double seconds = durations.get(musicId);
        if (seconds == null || seconds <= 0) {
            return null;
        }
        long rounded = (long) Math.rint(seconds);
        return rounded > 0 ? (int) Math.min(rounded, Integer.MAX_VALUE) : null;
    }

    /** 推算码率（kbps，整数，四舍六入五成双）；时长或体积缺失返回 null。 */
    public Integer kbps(long musicId) {
        Double seconds = durations.get(musicId);
        Long size = sizes.get(musicId);
        if (seconds == null || seconds <= 0 || size == null || size <= 0) {
            return null;
        }
        long kbps = (long) Math.rint(size * 8.0 / seconds / 1000.0);
        return kbps > 0 ? (int) Math.min(kbps, Integer.MAX_VALUE) : null;
    }

    /** 已加载的时长条目数（含无法算出码率的条目）。 */
    public int durationCount() {
        return durations.size();
    }

    /** 已加载的音频体积条目数。 */
    public int bitrateCount() {
        return sizes.size();
    }

    // ------------------------------------------------------------------ 读取

    private static Path resolve(Path dataDir, String name) {
        Path nested = dataDir.resolve("music").resolve(name);
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        Path direct = dataDir.resolve(name);
        return Files.isRegularFile(direct) ? direct : null;
    }

    private static void readDurations(Map<Long, Double> out, Path path) {
        JsonNode root = readJson(path);
        if (root == null) {
            return;
        }
        root.fields().forEachRemaining(entry -> {
            long id = parseId(entry.getKey());
            Double seconds = parseDouble(entry.getValue());
            if (id > 0 && seconds != null && seconds > 0) {
                out.put(id, seconds);
            }
        });
    }

    private static void readSizes(Map<Long, Long> out, Path path) {
        JsonNode root = readJson(path);
        if (root == null) {
            return;
        }
        root.fields().forEachRemaining(entry -> {
            long id = parseId(entry.getKey());
            Double size = parseDouble(entry.getValue());
            if (id > 0 && size != null && size > 0) {
                out.put(id, size.longValue());
            }
        });
    }

    private static JsonNode readJson(Path path) {
        if (path == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            return root != null && root.isObject() ? root : null;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "读取音频信息失败：" + path + "（" + e + "）");
            return null;
        }
    }

    private static long parseId(String key) {
        try {
            return Long.parseLong(key.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Double parseDouble(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
