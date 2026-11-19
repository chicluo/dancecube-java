package com.chicluo.dancecube.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 推送目标与待推送变更的持久化，对标 Python 版 {@code core/push.py} 的 {@code PushStore}。
 * 每个会话（UMO）独立开关；pending 取出后清空。
 */
public class PushStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> KINDS =
            List.of("official_new", "official_gone", "user_new", "user_gone");

    private final Path path;

    public PushStore(Path path) {
        this.path = path;
    }

    private Map<String, Object> load() {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(path.toFile());
            if (node != null && node.isObject()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = MAPPER.convertValue(node, Map.class);
                return map;
            }
        } catch (IOException ignored) {
            // 坏文件按空处理
        }
        return new LinkedHashMap<>();
    }

    private void save(Map<String, Object> data) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存推送状态失败：" + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> targets() {
        Object targets = load().get("targets");
        return targets instanceof List ? (List<Map<String, Object>>) targets : new ArrayList<>();
    }

    public List<Map<String, Object>> enabledTargets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> target : targets()) {
            if (Boolean.TRUE.equals(target.get("enabled"))) {
                out.add(target);
            }
        }
        return out;
    }

    public boolean isEnabled(String umo) {
        for (Map<String, Object> target : targets()) {
            if (umo.equals(String.valueOf(target.get("umo")))
                    && Boolean.TRUE.equals(target.get("enabled"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 开启/关闭某个会话；返回目标是否存在（关闭不存在的目标返回 false）。
     */
    @SuppressWarnings("unchecked")
    public boolean setEnabled(String umo, boolean enabled) {
        String key = umo == null ? "" : umo.strip();
        if (key.isEmpty()) {
            return false;
        }
        Map<String, Object> data = load();
        List<Map<String, Object>> targets = (List<Map<String, Object>>) data.computeIfAbsent(
                "targets", k -> new ArrayList<Map<String, Object>>());
        for (Map<String, Object> target : targets) {
            if (key.equals(String.valueOf(target.get("umo")))) {
                target.put("enabled", enabled);
                target.put("updated_at", now());
                save(data);
                return true;
            }
        }
        if (!enabled) {
            return false;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("umo", key);
        created.put("enabled", true);
        created.put("added_at", now());
        targets.add(created);
        save(data);
        return true;
    }

    /** 把一次检测到的变化并入 pending。 */
    @SuppressWarnings("unchecked")
    public void savePending(Map<String, Object> changes) {
        Map<String, Object> data = load();
        Map<String, Object> pending = (Map<String, Object>) data.computeIfAbsent(
                "pending", k -> new LinkedHashMap<String, Object>());
        for (String kind : KINDS) {
            LinkedHashSet<Object> merged = new LinkedHashSet<>();
            Object old = pending.get(kind);
            if (old instanceof List<?> list) {
                merged.addAll(list);
            }
            Object add = changes.get(kind);
            if (add instanceof List<?> list) {
                merged.addAll(list);
            }
            pending.put(kind, new ArrayList<>(merged));
        }
        pending.put("date", changes.getOrDefault("date", LocalDate.now().toString()));
        data.put("pending", pending);
        save(data);
    }

    /** 取出并清空 pending；没有变化返回空 Map。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> takePending() {
        Map<String, Object> data = load();
        Map<String, Object> pending = (Map<String, Object>) data.get("pending");
        boolean has = false;
        if (pending != null) {
            for (String kind : KINDS) {
                Object value = pending.get(kind);
                if (value instanceof List<?> list && !list.isEmpty()) {
                    has = true;
                    break;
                }
            }
        }
        if (!has) {
            return new LinkedHashMap<>();
        }
        data.put("pending", new LinkedHashMap<>());
        save(data);
        return pending;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }
}
