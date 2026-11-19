package com.chicluo.dancecube.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扫码登录，对标 Python 版 {@code core/login.py}。
 *
 * <pre>
 * 1. GET  /Dance/api/Common/GetQrCode?id=   → {ID, QrcodeUrl, ExpireTime}
 * 2. 玩家用微信扫码 QrcodeUrl
 * 3. POST /Dance/token (form)               → {access_token, userId, ...}
 * </pre>
 */
public class QrLogin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE =
            System.getenv().getOrDefault("DC_BASE", "https://dancedemo.shenghuayule.com");
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 二维码结果：PNG 字节 + 元信息。 */
    public record QrCode(byte[] png, String id, String url, long createdAt) {
    }

    /** 申请一个登录二维码。 */
    public QrCode requestQr() throws IOException, InterruptedException {
        var resp = get(BASE + "/Dance/api/Common/GetQrCode?id=");
        if (resp.statusCode() != 200) {
            throw new IOException("获取二维码失败：HTTP " + resp.statusCode());
        }
        JsonNode data = MAPPER.readTree(resp.body());
        String id = data.path("ID").asText("");
        String url = data.path("QrcodeUrl").asText("");
        if (id.isEmpty() || url.isEmpty()) {
            throw new IOException("二维码接口返回缺少 ID/QrcodeUrl");
        }
        byte[] png = download(url);
        if (png.length < 500) {
            throw new IOException("二维码图片下载失败：内容过短（" + png.length + " 字节）");
        }
        return new QrCode(png, id, url, System.currentTimeMillis() / 1000);
    }

    /** 轮询换取 token；超时返回 {@code null}。 */
    public Map<String, String> pollToken(String qid, int timeoutSeconds, double intervalSeconds)
            throws InterruptedException, IOException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_type", "qrcode");
            form.put("grant_type", "client_credentials");
            form.put("client_id", qid);
            var resp = postForm(BASE + "/Dance/token", form);
            if (resp.statusCode() == 200) {
                JsonNode node = MAPPER.readTree(resp.body());
                String access = node.path("access_token").asText("");
                if (!access.isEmpty()) {
                    Map<String, String> token = new LinkedHashMap<>();
                    token.put("access_token", access);
                    token.put("userId", node.path("userId").asText(""));
                    token.put("_obtained_at", String.valueOf(System.currentTimeMillis() / 1000));
                    token.put("_client_id", qid);
                    return token;
                }
            }
            Thread.sleep((long) (intervalSeconds * 1000));
        }
        return null;
    }

    // ------------------------------------------------------------------ 内部

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postForm(String url, Map<String, String> form)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .GET()
                .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IOException("二维码图片下载失败：HTTP " + resp.statusCode());
        }
        return resp.body();
    }
}
