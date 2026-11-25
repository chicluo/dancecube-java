package com.chicluo.dancecube.lib;

import com.chicluo.dancecube.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 曲库音频全量补全：把缺「时长」或「码率」的曲目逐个下载音频探测，结果写回
 * {@code music_duration_user.json} / {@code music_bitrate.json}。
 *
 * <p>为什么需要：单曲查询才触发按需探测（{@link DurationStore#probeAndCache}），新上架的谱
 * 没人查过就一直是空的；全表导出又不能现场逐条下载（几千首会拖很久），所以提供一个显式的批量入口。</p>
 *
 * <p>并发约定：运行期间调用方应拦住单曲查询——探测方法在 {@code DurationStore} 上是
 * synchronized 的，和批量任务抢同一把锁会把查询卡住几十秒。判断用 {@link #isRunning()}。
 * 批量任务在独立后台线程跑，{@link #start()} 立即返回，不阻塞调用线程。</p>
 *
 * <p>只处理带 {@code audioUrl} 的曲目（自制谱）；官谱条目没有音频地址，补不了。</p>
 */
public class AudioBackfill {

    /** 每首之间歇一下，别把音频存储站点打爆。 */
    private static final long DEFAULT_GAP_MS = 200L;

    private static final System.Logger LOG = System.getLogger(AudioBackfill.class.getName());

    private final MusicLibrary library;
    private final DurationStore durations;
    private final long gapMs;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private volatile int total;
    private volatile long startedAt;
    private volatile String lastError = "";
    private volatile String lastSummary = "";

    public AudioBackfill(MusicLibrary library, DurationStore durations) {
        this(library, durations, DEFAULT_GAP_MS);
    }

    /** @param gapMs 每首之间的间隔，测试时传 0 */
    public AudioBackfill(MusicLibrary library, DurationStore durations, long gapMs) {
        this.library = library;
        this.durations = durations;
        this.gapMs = Math.max(0, gapMs);
    }

    /** 是否正在全量拉取。 */
    public boolean isRunning() {
        return running.get();
    }

    /** 待补条目数（缺时长或缺码率，且有音频地址）。 */
    public int pendingCount() {
        return pending().size();
    }

    /** 收集待补曲目。 */
    public List<Song> pending() {
        List<Song> out = new ArrayList<>();
        List<Song> songs;
        try {
            songs = library.loadUser();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "统计待补曲目失败：" + e);
            return out;
        }
        for (Song song : songs) {
            if (song.audioUrl() == null || song.audioUrl().isBlank()) {
                continue;
            }
            boolean missing = durations.duration(song.musicId()) == null
                    || durations.kbps(song.musicId()) == null;
            if (missing) {
                out.add(song);
            }
        }
        return out;
    }

    /**
     * 启动全量补全（后台线程）。
     *
     * @return 待补条数；{@code -1} 表示已经在跑；{@code 0} 表示没有要补的
     */
    public int start() {
        if (!running.compareAndSet(false, true)) {
            return -1;
        }
        List<Song> targets = pending();
        if (targets.isEmpty()) {
            running.set(false);
            return 0;
        }
        total = targets.size();
        done.set(0);
        failed.set(0);
        lastError = "";
        startedAt = System.currentTimeMillis();
        Thread worker = new Thread(() -> run(targets), "dancecube-backfill");
        worker.setDaemon(true);
        worker.start();
        LOG.log(System.Logger.Level.INFO, "曲库全量拉取已启动：待补 " + total + " 首");
        return total;
    }

    /** 请求停止；当前这一首会跑完再退出。 */
    public void stop() {
        if (running.get()) {
            running.set(false);
            LOG.log(System.Logger.Level.INFO, "曲库全量拉取已请求停止，已完成 " + done.get() + "/" + total);
        }
    }

    /** 一行状态文本，给命令和查询拦截用。 */
    public String statusText() {
        if (!running.get()) {
            return lastSummary.isEmpty() ? "空闲" : lastSummary;
        }
        long elapsed = Math.max(0, System.currentTimeMillis() - startedAt) / 1000;
        String tail = lastError.isEmpty() ? "" : "，最近失败：" + lastError;
        return String.format("进行中 %d/%d（失败 %d，已用 %ds%s）",
                done.get(), total, failed.get(), elapsed, tail);
    }

    private void run(List<Song> targets) {
        try {
            for (Song song : targets) {
                if (!running.get()) {
                    break;
                }
                try {
                    Integer seconds = durations.probeAndCache(song.musicId(), song.audioUrl());
                    if (seconds == null) {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    lastError = song.musicId() + " " + e.getMessage();
                    LOG.log(System.Logger.Level.WARNING, "探测 " + song.musicId() + " 失败：" + e);
                }
                done.incrementAndGet();
                if (gapMs > 0) {
                    Thread.sleep(gapMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            boolean finished = done.get() >= total;
            long elapsed = Math.max(0, System.currentTimeMillis() - startedAt) / 1000;
            running.set(false);
            lastSummary = String.format("上次：完成 %d/%d，失败 %d，耗时 %ds%s",
                    done.get(), total, failed.get(), elapsed,
                    finished ? "" : "（手动停止）");
            LOG.log(System.Logger.Level.INFO, "曲库全量拉取结束：完成 "
                    + done.get() + "/" + total + "，失败 " + failed.get());
        }
    }
}