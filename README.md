# 舞小鲸 · 舞立方查询（Java 版）

[![License: MIT](https://img.shields.io/badge/License-MIT-2ea043.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)

舞立方（DanceCube）查询 **Java 库**：曲库、扫码登录、个人信息、战力分析、成绩导出、谱面更新推送。

它是 [astrbot_plugin_dancecube](https://github.com/chicluo/astrbot_plugin_dancecube)（Python / AstrBot 插件）的 Java 对应实现，业务逻辑独立于任何 bot 框架——调用方把消息整理成文本，拿回文本/图片/文件自己发送。

## 功能

| 功能 | Java | Python 插件 |
|---|---|---|
| 曲库双库加载（官谱 + 自制谱） | ✅ | ✅ |
| 搜索归一化（简繁 / 日文异体 / 西里尔） | ✅ | ✅ |
| 扫码登录（二维码 + token 轮询） | ✅ | ✅ |
| 个人信息卡片 | ✅ | ✅ |
| 战力分析（B15+R15 / B30 / AP30） | ✅ | ✅ |
| 成绩导出 xlsx | ✅ | ✅ |
| 谱面更新推送 | ✅（数据层） | ✅ |
| 联网刷新曲库 | 🚧 进行中 | ✅ |
| Bot 收发适配 | 由调用方实现 | AstrBot 壳 |

## 快速开始

```kotlin
dependencies {
    implementation("com.chicluo:dancecube-java:1.0.0")
}
```

```java
Path dataDir = Path.of("path/to/plugin_data/astrbot_plugin_dancecube");
DanceCube cube = new DanceCube(dataDir);

// 曲库搜索
List<Song> hits = cube.search("calavera", 10);
for (Song song : hits) {
    System.out.println(song.name() + "（" + song.sourceLabel() + " · ID " + song.musicId() + "）");
}
System.out.println(cube.formatSong(hits.get(0), null, null));

// 玩家数据（需先扫码登录把 token 存进 dataDir/tokens/<uid>.json）
DancePlayer player = cube.player("1241510838");
PlayerSnapshot snapshot = player.snapshot();

// 战力分析
RatioResult result = cube.analyze("1241510838", "b30");
System.out.println("总分 " + result.total() + " 平均 " + result.average());

// 个人信息卡片
Path png = cube.renderProfile("1241510838", Path.of("out"));

// 导出
Path xlsx = cube.exportScores("1241510838", Path.of("out"));
```

## 结构

```
src/main/java/com/chicluo/dancecube/
├── DanceCube.java          门面
├── analysis/               战力计算与 B15/B30/AP30 分析
├── client/                 HTTP 客户端、玩家数据、扫码登录
├── export/                 xlsx 导出（Apache POI）
├── lib/                    曲库、token 存储、曲目格式化
├── model/                  Song / DanceLevel / Score / PlayerSnapshot
├── render/                 图片渲染（Java AWT）
└── util/                   TextNormalizer（搜索归一化）
```

## 数据

运行数据目录与 Python 版共用：

- `music_library.json` / `music_library_user.json` —— 曲库缓存
- `tokens/<uid>.json` —— 登录 token

## 依赖

- Java 25
- Jackson 2.18（JSON）
- opencc4j 1.8（繁简）
- Apache POI 5.3（xlsx）

## 构建

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew test
```

## License

MIT —— 与 Python 插件的 PolyForm Noncommercial 不同：这是可复用的**库**，MIT 方便第三方框架（含朋友的框架）直接集成。详见 [LICENSE](LICENSE)。

## 对接

见 [docs/INTEGRATION.md](docs/INTEGRATION.md)：怎么把本库接进任意 bot 框架，含最小示例。
## 关于这份 Java 版

> 坦白说：这套库是 DeepSeek 模型从 Python 版转写过来的。功能能对拍（数值、像素都验证过），但它在 Java 的「边角」上翻车明显更多——Spring 生命周期、Reactor 的异常传播、AWT 字体渲染、Windows 的 GBK 编码，都踩过坑、修过一轮。
>
> Python 版有几个月真实使用的打磨，Java 版只有两天的集中转写。质量差距主要在「磨了多少遍」，不在「能不能写」——有问题欢迎提 issue，一起补。