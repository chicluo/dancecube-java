package com.chicluo.dancecube.lib;

import com.chicluo.dancecube.model.DanceLevel;
import com.chicluo.dancecube.model.Song;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AudioBackfill 的统计与状态逻辑。
 *
 * <p>不碰网络：只构造带 / 不带 audioUrl 的曲目，验证 pending 的筛选口径与状态文案。
 * 真正的探测路径由 AudioProbeTest 与线上实测覆盖。</p>
 */
class AudioBackfillTest {

    private static Song userSong(long id, String audioUrl) {
        return new Song("user", "曲" + id, id,
                List.of(new DanceLevel("高级", "专家", 12, 900, false, 13, 2, false)),
                100, 10, 7L, "作者", List.of(), null, "", audioUrl);
    }

    private static DurationStore emptyStore() {
        return DurationStore.of(Map.of(), Map.of());
    }

    @Test
    void pendingSkipsSongsWithoutAudioUrl() {
        List<Song> songs = new ArrayList<>(List.of(
                userSong(1, "https://example.com/1.mp3"),
                userSong(2, ""),          // 没音频地址：补不了
                userSong(3, "https://example.com/3.mp3")));
        MusicLibrary library = new MusicLibrary(Path.of("build", "tmp", "backfill-test")) {
            @Override
            public synchronized List<Song> loadUser() {
                return songs;
            }
        };
        AudioBackfill backfill = new AudioBackfill(library, emptyStore(), 0);

        assertEquals(2, backfill.pendingCount(), "只有带 audioUrl 的两首进待补");
        assertFalse(backfill.isRunning());
    }

    @Test
    void pendingSkipsSongsThatAlreadyHaveDurationAndBitrate() {
        DurationStore store = DurationStore.of(
                Map.of(1L, 100.0, 2L, 200.0),
                Map.of(1L, 1_000_000L, 2L, 2_000_000L));
        List<Song> songs = List.of(
                userSong(1, "https://example.com/1.mp3"),   // 齐全
                userSong(2, "https://example.com/2.mp3"));  // 齐全
        MusicLibrary library = new MusicLibrary(Path.of("build", "tmp", "backfill-test")) {
            @Override
            public synchronized List<Song> loadUser() {
                return songs;
            }
        };
        AudioBackfill backfill = new AudioBackfill(library, store, 0);

        assertEquals(0, backfill.pendingCount());
        assertEquals(0, backfill.start(), "没有要补的，start 返回 0");
        assertFalse(backfill.isRunning());
    }

    @Test
    void pendingIncludesSongMissingOnlyBitrate() {
        // 有时长、缺体积：码率算不出来，也该重探一次
        DurationStore store = DurationStore.of(Map.of(9L, 120.0), Map.of());
        List<Song> songs = List.of(userSong(9, "https://example.com/9.mp3"));
        MusicLibrary library = new MusicLibrary(Path.of("build", "tmp", "backfill-test")) {
            @Override
            public synchronized List<Song> loadUser() {
                return songs;
            }
        };
        AudioBackfill backfill = new AudioBackfill(library, store, 0);

        assertEquals(1, backfill.pendingCount());
    }

    @Test
    void statusTextIsIdleBeforeAnyRun() {
        AudioBackfill backfill = new AudioBackfill(new MusicLibrary(Path.of("build", "tmp", "backfill-test")), emptyStore(), 0);
        assertEquals("空闲", backfill.statusText());
    }

    @Test
    void stopOnIdleIsHarmless() {
        AudioBackfill backfill = new AudioBackfill(new MusicLibrary(Path.of("build", "tmp", "backfill-test")), emptyStore(), 0);
        backfill.stop();
        assertFalse(backfill.isRunning());
        assertTrue(backfill.statusText().contains("空闲"));
    }
}