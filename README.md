# 朋友的酒

Minecraft Java 模组，模组 ID 为 `friendswine`，当前源码版本 **0.1.10**。本仓库包含六个版本工程、测试和原始素材，可独立克隆构建。当前阶段先以私有仓库托管，维护者审核并明确确认后再公开。

## 玩法

- 玩偶：右键开始循环音乐、果冻回弹与自转，再次右键停止。音乐每约 56.294 秒循环，播放状态随玩偶保存。
- 遥控器：右键玩偶绑定，对空气使用依次切换「回弹 → 加入自转 → 加入生物绕圈 → 停止」。切档保留音乐进度，不强制加载目标区块。
- 朋友的酒：饮用后获得约 168.9 秒酒劲，人物果冻回弹，有酒劲时三个视角的镜头同步回弹；牛奶可解除。
- 活动玩偶吸引 50 格内生物，音乐范围 20 格、视觉效果范围 10 格。玩家保留移动控制，形变不改变碰撞箱。
- 户山香澄与艾玛：稀有自然生成，可使用生成蛋。玩偶从停止变为播放时尝试生成合计 6～10 只，16 格内两种生物合计最多 10 只；切档和存档恢复不重复生成。
- 成年傻子村民通过原版交易窗口提供玩偶、遥控器和酒，每项每日 16 次库存；玩家共享库存，按游戏日期补货。

创造页「朋友的酒」中可取得全部物品，也可使用 `/give @s friendswine:doll`、`friendswine:remote`、`friendswine:wine`、`friendswine:kasumi_spawn_egg`、`friendswine:emma_spawn_egg`。没有合成或酿造配方。多人游戏的客户端与服务器需安装相同目标版本。

## 六版本工程

| Minecraft | 加载器 | 工程目录 | 构建 JDK | 游戏／成品 Java |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge | `ports/forge-1.20.1` | 17 | 17 |
| 1.20.1 | Fabric | `ports/fabric-1.20.1` | 21，同时安装 17 | 17 |
| 1.21.1 | NeoForge | `mod` | 21 | 21 |
| 1.21.1 | Fabric | `ports/fabric-1.21.1` | 21 | 21 |
| 26.1.2 | NeoForge | `ports/neoforge-26.1.2` | 25 | 25 |
| 26.1.2 | Fabric | `ports/fabric-26.1.2` | 25 | 25 |

五个移植工程直接引用 `mod` 中的 `JellyAnimation.java`、`GuestAnimation.java` 和生产资源。请克隆完整仓库并保留目录布局，不要单独移动某个移植工程。加载器、API、插件和 Gradle 版本固定在各工程配置中。

### 构建与测试

安装所需 JDK，将 `JAVA_HOME` 指向表中的构建 JDK，并将其 `bin` 加入 `PATH`。首次构建需要联网下载 Gradle 与依赖。

Fabric 1.20.1 的 Gradle 使用 JDK 21，游戏测试使用 JDK 17；可用 `FRIENDSWINE_JAVA17`、`FRIENDSWINE_JAVA21` 指定两个 JDK 的安装目录。其他工程只需对应版本的 JDK。

以下 PowerShell 命令从仓库根目录执行，运行前按目标设置 `JAVA_HOME`：

```powershell
# Forge 1.20.1：JAVA_HOME 指向 JDK 17
& .\ports\forge-1.20.1\gradlew.bat -p .\ports\forge-1.20.1 build runGameTestServer

# Fabric 1.20.1：JAVA_HOME 指向 JDK 21，FRIENDSWINE_JAVA17 指向 JDK 17
& .\ports\fabric-1.20.1\gradlew.bat -p .\ports\fabric-1.20.1 build

# NeoForge 1.21.1：JAVA_HOME 指向 JDK 21
& .\mod\gradlew.bat -p .\mod build runGameTestServer

# Fabric 1.21.1：JAVA_HOME 指向 JDK 21
& .\ports\fabric-1.21.1\gradlew.bat -p .\ports\fabric-1.21.1 build

# NeoForge 26.1.2：JAVA_HOME 指向 JDK 25
& .\ports\neoforge-26.1.2\gradlew.bat -p .\ports\neoforge-26.1.2 build runGameTestServer

# Fabric 26.1.2：JAVA_HOME 指向 JDK 25
& .\ports\fabric-26.1.2\gradlew.bat -p .\ports\fabric-26.1.2 build
```

Fabric 的 `build` 已包含 GameTest；Forge／NeoForge 显式执行 `runGameTestServer`。GameTest 服务端使用测试运行目录，请勿放入实际游玩存档。Linux／macOS 将 Wrapper 改为 `./对应目录/gradlew`，路径使用 `/`。

成品位于各工程的 `build/libs/`，安装使用 `friendswine-0.1.10-加载器-Minecraft版本.jar`，不要安装带 `dev` 或 `sources` 的文件。客户端开发入口为同一工程的 `runClient`，服务器入口为 `runServer`。

## 原始素材与资源工具

- [原始玩偶模型](assets/source/doll.bbmodel)、[原始音乐](assets/source/doll_music.mp3)及[动画采样](assets/source/animation-samples.json)。
- [户山香澄 PNG／GIF](assets/guests/kasumi/)与[艾玛 PNG／GIF](assets/guests/emma/)。
- 生产资源位于 `mod/src/main/resources/`，构建直接使用已提交资源，不要求安装素材处理工具。

发布模型移除了旧电脑的贴图文件路径和外部参考图引用；模型网格、嵌入贴图和动画保持原内容。

使用 Python 3 和 Pillow 重建两种生物图集：

```powershell
python .\scripts\prepare-guest-textures.py
```

脚本只写生产图集、静态贴图和帧描述，不修改原始素材；逐帧检查 RGBA 像素和原始帧时长，不裁帧、不重新着色。

使用 Node.js 和 FFmpeg 校验模型、贴图、音频和原始素材：

```powershell
# FFmpeg 已在 PATH 中时无需设置；否则指定可执行文件。
$env:FFMPEG = 'C:\工具\ffmpeg\bin\ffmpeg.exe'
node .\assets\source\check-resources.mjs
```

`assets/source/export-rest-mesh.js` 在 Blockbench 的 `execute_script` 中作为函数体运行，读取停止状态的原模型并返回网格，不修改模型。

## 署名与许可

共创作者：jiaotan_、你个人机cc。

- jiaotan_：[bilibili](https://space.bilibili.com/409729840?) · [抖音](https://v.douyin.com/KDs5ZxJh5V8/)
- 你个人机cc：[bilibili](https://space.bilibili.com/400763031?) · [抖音](https://v.douyin.com/iyCu2FJYTGc/)

**素材来源**：模型、音乐、贴图及 PNG／GIF 来自项目提供的原始素材，原始副本随仓库保存。

**模组许可**：沿用 **All Rights Reserved** 声明，源码托管不修改既有许可。

**工程来源**：基于 [NeoForge MDK 1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/0d385327b15a9497991c0cc032eb63bc18aa3cf7)。模板文件的 MIT 许可见 [TEMPLATE_LICENSE.txt](mod/TEMPLATE_LICENSE.txt)，Mojang 映射说明见[工程上游资料](mod/README.md)。

## 维护与验证边界

六个版本已通过构建和 GameTest。修改公共代码或资源后，应重新构建全部六个版本。

当前版本的双客户端联机及 YSM／车万女仆组合兼容性尚待实测。
