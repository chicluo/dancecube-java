package com.chicluo.dancecube.lib;

import com.chicluo.dancecube.client.DanceApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 曲库联网刷新，对标 Python 版 {@code MusicLib.refresh_official/refresh_user}。
 * 依次尝试所有已登录 token，任一成功即用；官谱少于 500 首视为残缺、不覆盖缓存。
 */
public class RefreshService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MusicLibrary library;
    private final TokenStore tokens;

    public RefreshService(MusicLibrary library, TokenStore tokens) {
        this.library = library;
        this.tokens = tokens;
    }

    /** 官方接口请求：轮换 token。 */
    private JsonNode fetch(String path, Map<String, String> params) throws IOException {
        Map<String, Map<String, String>> all = tokens.all();
        if (all.isEmpty()) {
            throw new IOException("曲库尚未初始化，请先让任意用户执行 /舞立方登录");
        }
        IOException last = null;
        for (Map<String, String> token : all.values()) {
            String access = token.get("access_token");
            String uid = token.get("userId");
            if (access == null || uid == null) {
                continue;
            }
            DanceApi api = new DanceApi(access, uid);
            DanceApi.ApiResult result = api.get(path, params);
            if (result.hasError() || !result.ok()) {
                last = new IOException(result.error().isEmpty()
                        ? "HTTP " + result.status() : result.error());
                continue;
            }
            return result.body();
        }
        throw last == null ? new IOException("曲库请求失败") : last;
    }

    /** 强制重拉官谱并写盘；返回是否成功。 */
    public synchronized boolean refreshOfficial() {
        JsonNode body;
        try {
            body = fetch("/Dance/MusicData/GetValidMusicList", null);
        } catch (IOException e) {
            return false;
        }
        if (body == null || !body.isArray() || body.size() < 500) {
            return false; // 残缺数据不覆盖好缓存
        }
        try {
            writeAtomic(library.officialPath(), body);
        } catch (IOException e) {
            return false;
        }
        library.invalidate();
        return true;
    }

    /** 强制重拉自制谱（翻页）并写盘；返回是否成功。 */
    public synchronized boolean refreshUser() {
        ArrayNode all = MAPPER.createArrayNode();
        try {
            for (int page = 1; page <= 30; page++) {
                JsonNode data = fetch("/Dance/api/Goods/GetGoodsMusic",
                        Map.of("page", String.valueOf(page), "pagesize", "1000",
                                "orderby", "3", "ordertype", "2"));
                JsonNode list = data == null ? null : data.get("List");
                if (list == null || !list.isArray() || list.isEmpty()) {
                    break;
                }
                all.addAll((ArrayNode) list);
                int total = data.path("RecordCount").asInt(0);
                if (total > 0 && all.size() >= total) {
                    break;
                }
            }
        } catch (IOException e) {
            return false;
        }
        if (all.isEmpty()) {
            return false;
        }
        try {
            writeAtomic(library.userPath(), all);
        } catch (IOException e) {
            return false;
        }
        library.invalidate();
        return true;
    }

    private static void writeAtomic(java.nio.file.Path path, JsonNode data) throws IOException {
        Files.createDirectories(path.getParent());
        java.nio.file.Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }
}
