package com.chicluo.dancecube.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 舞立方开放接口的 HTTP 封装，对标 Python 版 {@code core/client.py} 的 {@code Api}。
 *
 * <p>Base 默认 {@code https://dancedemo.shenghuayule.com}，可用环境变量 {@code DC_BASE} 覆盖。</p>
 */
public class DanceApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final String token;
    private final String uid;
    private final String base;
    private final HttpClient http;

    public DanceApi(String accessToken, String uid) {
        this.token = accessToken;
        this.uid = String.valueOf(uid);
        this.base = System.getenv().getOrDefault("DC_BASE", "https://dancedemo.shenghuayule.com");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String uid() {
        return uid;
    }

    /** 一次 GET 的结果。body 解析失败时是 {@code {"_raw": ...}}，网络异常时是 {@code {"_error": ...}}。 */
    public record ApiResult(int status, JsonNode body) {
        public boolean ok() {
            return status >= 200 && status < 300 && body != null && !body.has("_error");
        }

        public boolean hasError() {
            return body != null && body.has("_error");
        }

        public String error() {
            return body == null ? "" : body.path("_error").asText("");
        }
    }

    public ApiResult get(String path, Map<String, String> params) {
        String url = base + path;
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(url).append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                first = false;
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
            url = sb.toString();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", UA)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body() == null ? "" : resp.body();
            try {
                return new ApiResult(resp.statusCode(), MAPPER.readTree(body));
            } catch (Exception parseError) {
                return new ApiResult(resp.statusCode(),
                        MAPPER.createObjectNode().put("_raw", body.substring(0, Math.min(300, body.length()))));
            }
        } catch (Exception e) {
            var node = MAPPER.createObjectNode();
            node.put("_error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return new ApiResult(0, node);
        }
    }
}
