package com.chicluo.dancecube.lib;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多用户 token 存储，对标 Python 版 {@code plugin_data/astrbot_plugin_dancecube/tokens/*.json}。
 * 一个 uid 一个文件，写入用临时文件 + 原子替换。
 */
public class TokenStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;

    public TokenStore(Path dataDir) {
        this.dir = dataDir.resolve("tokens");
    }

    public Path dir() {
        return dir;
    }

    public Map<String, String> get(String uid) {
        Path path = fileOf(uid);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return MAPPER.readValue(path.toFile(), MAPPER.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (IOException e) {
            return null;
        }
    }

    public void save(String uid, Map<String, String> token) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(uid + ".json.tmp");
            MAPPER.writeValue(tmp.toFile(), token);
            Files.move(tmp, fileOf(uid), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存 token 失败：" + e.getMessage(), e);
        }
    }

    public void remove(String uid) {
        try {
            Files.deleteIfExists(fileOf(uid));
        } catch (IOException ignored) {
            // 删不掉就算了，下次覆盖写
        }
    }

    /** 所有可用 token（uid -> token），按文件名排序。 */
    public Map<String, Map<String, String>> all() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            Map<String, String> token = MAPPER.readValue(path.toFile(),
                                    MAPPER.getTypeFactory().constructMapType(
                                            LinkedHashMap.class, String.class, String.class));
                            if (token != null && token.get("access_token") != null) {
                                String uid = token.get("userId");
                                if (uid != null) {
                                    out.put(uid, token);
                                }
                            }
                        } catch (IOException ignored) {
                            // 坏文件跳过
                        }
                    });
        } catch (IOException ignored) {
            // 目录读不了返回空
        }
        return out;
    }

    private Path fileOf(String uid) {
        return dir.resolve(uid.replaceAll("[^0-9A-Za-z_-]", "_") + ".json");
    }

    public static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
