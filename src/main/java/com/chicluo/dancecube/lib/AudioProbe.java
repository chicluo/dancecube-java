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
            "dance-user-file.oss-cn-shanghai.aliyuncs.com",
            // 少数自制谱的 AudioUrl 指向官方资源站（实测 23812 断罪）
            "asset-music.oss-cn-shanghai.aliyuncs.com");

    private static final long MAX_BYTES = 64L << 20;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AudioProbe() {
    }

    /**
     * 解析 URL：部分曲目的 AudioUrl 带空格或中文（如 {@code .../502_adjudicatorz 断罪_convert.mp3}），
     * {@code URI.create} 会直接抛 {@code IllegalArgumentException}，所以解析失败时按 UTF-8 做百分号编码重试。
     */
    static URI toUri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            return URI.create(percentEncode(url));
        }
    }

    /** 只编码非 ASCII 与空白字符，保留 {@code :/?#[]@!$&'()*+,;=} 等 URL 结构字符和已有的 {@code %}。 */
    private static String percentEncode(String url) {
        StringBuilder sb = new StringBuilder(url.length() + 32);
        for (byte b : url.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c > 0x20 && c < 0x7F) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    /**
     * 一次探测的结果：时长（秒，1 位小数）与音频体积（字节）。
     * 两者都拿到才能算码率，所以全量拉取时用 {@link #probeFull(String)} 而不是 {@link #probe(String)}。
     */
    public record Result(double seconds, long bytes) {
    }

    /** 探测音频时长（秒，保留 1 位小数）；失败返回 null，不抛异常。 */
    public static Double probe(String url) {
        Result result = probeFull(url);
        return result == null ? null : result.seconds();
    }

    /** 探测音频时长与体积；失败返回 null，不抛异常。 */
    public static Result probeFull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = toUri(url);
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
                long bytes = Files.size(tmp);
                if (bytes > MAX_BYTES) {
                    return null;
                }
                double seconds = AudioFileIO.read(tmp.toFile()).getAudioHeader().getTrackLength();
                if (seconds <= 0) {
                    return null;
                }
                return new Result(Math.round(seconds * 10) / 10.0, bytes);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception | LinkageError e) {
            // LinkageError 兜住 NoClassDefFoundError 一类：缺依赖时只当探测失败，别让整个查询命令崩掉
            return null;
        }
    }
}
