# 对接方案

本库不绑定 bot 框架：你负责把消息整理成文本，库负责业务计算，结果（文本 / 图片路径 / 文件路径）由你发出去。

## 最小接入

```java
Path dataDir = Path.of("path/to/plugin_data/astrbot_plugin_dancecube");
DanceCube cube = new DanceCube(dataDir);
```

- `cube.search(keyword, limit)` → `List<Song>`，再 `cube.formatSong(song, null, null)` 拿详情文本
- `cube.player(uid).snapshot()` → 玩家数据
- `cube.analyze(uid, "b15" | "b30" | "ap30")` → `RatioResult`
- `cube.renderProfile(uid, outDir)` → PNG 路径
- `cube.exportScores(uid, outDir)` / `cube.exportCatalog("official" | "user", outDir)` → xlsx 路径

## 消息分发示例（伪代码）

```java
public String onCommand(String text, String uid) {
    String cmd = text.strip().replaceFirst("^/", "");
    if (cmd.startsWith("曲目")) {
        var hits = cube.search(cmd.substring(2).strip(), 10);
        if (hits.isEmpty()) return "曲库里没有找到。";
        return cube.formatSong(hits.get(0), null, null);
    }
    if (cmd.equals("mydc")) {
        Path png = cube.renderProfile(uid, tmpDir);
        bot.sendImage(groupId, png);  // 你的框架负责发送
        return null;
    }
    if (cmd.startsWith("myrt")) {
        var mode = cmd.contains("b30") ? "b30" : cmd.contains("ap30") ? "ap30" : "b15";
        var r = cube.analyze(uid, mode);
        return "总分 " + r.total() + "，平均 " + r.average();
    }
    return "未知命令";
}
```

## 扫码登录

```java
var login = new QrLogin();
var qr = login.requestQr();          // qr.png() 是二维码字节，先发给用户
bot.sendImage(groupId, qr.png());

// 后台线程轮询
Map<String,String> token = login.pollToken(qr.id(), 120, 2.0);
if (token != null) {
    cube.tokens().save(uid, token);  // 存进 dataDir/tokens/<uid>.json
}
```

## 接进某个具体框架（如枫尘的框架）

把本库当普通依赖：

1. `build.gradle.kts` 加 `implementation("com.chicluo:dancecube-java:1.0.0")`
2. 框架启动时构造 `DanceCube`（数据目录指向插件数据）
3. 在命令注册处把 `/曲目`、`/mydc`、`/myrt` 等转给 `DanceCube` 的方法
4. 图片/文件由框架的发送能力处理（本库只给路径）

需要的话我可以针对具体框架写一版适配示例。
