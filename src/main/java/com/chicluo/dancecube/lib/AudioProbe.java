package com.chicluo.dancecube.lib;

import org.jaudiotagger.audio.AudioFileIO;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Set;

/**
 * 音频时长探测，对标 Python 版 {@code core/music.py::_probe_audio}：
 * 下载音频到临时文件，读一次时长（秒），失败返回 null。
 *
 * <p>只允许白名单域名、上限 64MB、超时 60 秒——自制谱的 {@code AudioUrl}
 * 都指向 {@code dance-user-file.oss-cn-shanghai.aliyuncs.com}。</p>
 */
public final class AudioProbe {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "dance-user-file.oss-cn-shanghai.aliyuncs.com");

    private static final long MAX_BYTES = 64L << 20;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AudioProbe() {
    }

    /** 探测音频时长（秒，保留 1 位小数）；失败返回 null，不抛异常。 */
    public static Double probe(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!ALLOWED_HOSTS.contains(host)) {
                return null;
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            String suffix = ".mp3";
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && path.length() - dot <= 6) {
                suffix = path.substring(dot);
            }
            Path tmp = Files.createTempFile("probe-", suffix);
            try {
                HttpResponse<InputStream> response = CLIENT.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    return null;
                }
                try (InputStream in = response.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.size(tmp) > MAX_BYTES) {
                    return null;
                }
                double seconds = AudioFileIO.read(tmp.toFile()).getAudioHeader().getTrackLength();
                return seconds > 0 ? Math.round(seconds * 10) / 10.0 : null;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
