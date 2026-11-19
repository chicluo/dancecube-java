package com.chicluo.dancecube.lib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 音频时长/码率读取，对齐 Python 版 {@code music.py::load_duration / load_bitrate}。 */
class DurationStoreTest {

    @TempDir
    Path dir;

    @Test
    void loadsFromMusicSubdirectoryAndMergesUserOverrides() throws IOException {
        Path music = Files.createDirectories(dir.resolve("music"));
        Files.writeString(music.resolve("music_duration.json"),
                "{\"291\": 185.6, \"101\": 142.0}");
        Files.writeString(music.resolve("music_duration_user.json"),
                "{\"6482\": 127.7, \"291\": 190.2}");
        Files.writeString(music.resolve("music_bitrate.json"),
                "{\"6482\": 2261165, \"291\": 3070717, \"101\": 0}");

        DurationStore store = DurationStore.load(dir);

        assertEquals(3, store.durationCount());
        assertEquals(2, store.bitrateCount());
        // 四舍五入：185.6 → 186，190.2 → 190；user 文件覆盖官谱同名键
        assertEquals(190, store.duration(291));
        assertEquals(142, store.duration(101));
        assertEquals(128, store.duration(6482));
        assertNull(store.duration(9999), "没有记录的曲目返回 null");
        // round(size * 8 / duration / 1000)
        assertEquals(129, store.kbps(291));
        assertEquals(142, store.kbps(6482));
        assertNull(store.kbps(101), "体积为 0 时算不出码率");
        assertNull(store.kbps(9999));
    }

    @Test
    void readsJsonDirectlyInDataDirectory() throws IOException {
        Files.writeString(dir.resolve("music_duration.json"), "{\"1\": 60.4}");
        Files.writeString(dir.resolve("music_bitrate.json"), "{\"1\": 120000}");

        DurationStore store = DurationStore.load(dir);

        assertEquals(60, store.duration(1));
        assertEquals(16, store.kbps(1));
    }

    @Test
    void roundsHalfToEvenLikePython() throws IOException {
        Files.writeString(dir.resolve("music_duration.json"), "{\"1\": 60.5, \"2\": 61.5}");
        DurationStore store = DurationStore.load(dir);

        assertEquals(60, store.duration(1), "Python round(60.5) = 60");
        assertEquals(62, store.duration(2), "Python round(61.5) = 62");
    }

    @Test
    void toleratesMissingAndBrokenFiles() throws IOException {
        assertEquals(0, DurationStore.load(dir).durationCount());
        assertEquals(0, DurationStore.load(dir).bitrateCount());
        assertNull(DurationStore.load(null).duration(1));
        assertEquals(0, DurationStore.load(null).bitrateCount());

        Files.writeString(dir.resolve("music_duration.json"), "{not json");
        assertEquals(0, DurationStore.load(dir).durationCount(), "损坏的 JSON 按空表处理");

        Files.writeString(dir.resolve("music_duration.json"), "[]");
        assertEquals(0, DurationStore.load(dir).durationCount(), "顶层不是对象也按空表处理");
    }

    @Test
    void acceptsTextualNumbersAndSkipsBadKeys() throws IOException {
        Files.writeString(dir.resolve("music_duration.json"),
                "{\"1\": \"60.4\", \"bad\": 30, \"3\": null, \"4\": -1}");

        DurationStore store = DurationStore.load(dir);

        assertEquals(60, store.duration(1));
        assertEquals(1, store.durationCount());
        assertNull(store.duration(3));
        assertNull(store.duration(4));
    }

    @Test
    void kbpsUsesRawFractionalDuration() {
        DurationStore store = DurationStore.of(
                java.util.Map.of(291L, 185.6), java.util.Map.of(291L, 3070717L));

        assertEquals(132, store.kbps(291));
        assertNull(store.kbps(1));
        assertEquals(0, DurationStore.empty().durationCount());
    }
}
