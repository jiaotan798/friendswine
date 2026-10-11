# 朋友的酒

Minecraft Java 模组，模组 ID 为 `friendswine`，当前源码版本 **0.1.16**。本仓库包含六个版本工程、测试和原始素材，可独立克隆构建。

## 下载与安装

在 [0.1.16 发布页](https://github.com/jiaotan798/friendswine/releases/tag/v0.1.16)下载成品。按 Minecraft 版本和加载器选择一个 JAR，放入游戏的 `mods` 文件夹并替换旧版；多人游戏客户端与服务端均需更新。加载器、Java 与 Fabric API 要求见[六版本工程](#六版本工程)。

0.1.16 每 10 tick 优先改选 16 格内更近的玩偶持有者，等距保持当前目标；没有更近者时仍继续跟随有效的原玩家。已有验证结果见 [0.1.16 验证](validation/0.1.16.md)，此前各次修改的记录保存在 [validation](validation/)。旧版成品保留在 [0.1.12 发布页](https://github.com/jiaotan798/friendswine/releases/tag/v0.1.12)。

## 玩法

- 玩偶：右键开始循环音乐、果冻回弹与自转，再次右键停止。音乐每约 56.294 秒循环，播放状态随玩偶保存。
- 遥控器：右键玩偶绑定，对空气使用依次切换「回弹 → 加入自转 → 加入生物绕圈 → 停止」。切档保留音乐进度，不强制加载目标区块。
- 朋友的酒：饮用后获得约 168.9 秒酒劲，人物果冻回弹，有酒劲时三个视角的镜头同步回弹；牛奶可解除。
- 活动玩偶使 50 格内生物寻路靠近，10 格内平滑吸引，并在水平 5 格范围内分散站位或绕圈。音乐范围 20 格、视觉效果范围 10 格；玩家保留移动控制，形变不改变碰撞箱。
- 播放区域会抑制生物对玩家或其他生物的攻击：攻击者或受击者在玩偶 50 格内即可生效。苦力怕爆炸、玩家主动攻击与环境伤害保持原版规则。
- 户山香澄、艾玛与阿米娅：稀有自然生成，可使用生成蛋。遥控器进入第三档时尝试追加生成，随机目标为 2～4 只，并按 16 格内三种生物合计 4 只的上限裁减。三种角色等概率选择。自然生成和生成蛋产生的已有个体也计入数量；此上限只限制玩偶追加生成。普通播放、NPC 放置及存档恢复不触发生成。
- 三种生物在 50 格内没有播放中的玩偶且自身未携带玩偶时，玩家直接背包、快捷栏或双手有玩偶即可触发 2 倍速度跟随。每 10 tick 优先选择 16 格内更近的存活、非旁观持有者，创造玩家也能跟随；另一位更近就换人，等距保持当前目标。附近没有更近者时继续跟随范围外有效原玩家；玩家失去玩偶、死亡、离线、跨维度或进入旁观模式时失效。跟随期间免于远距离自然消失；仅普通寻路，不传送、不强制加载区块、不读取嵌套容器。
- 偷取仍只针对生存／冒险玩家手持的玩偶：取走一件并尝试在附近安全位置放置、开启，受生物破坏游戏规则控制。成功偷取播放两秒短音效；携带中的立体玩偶显示在角色旁，成功放置后消失；存档使用原版主手装备保存。播放中的玩偶与携带后的放置优先，普通寻路和蹦迪速度保持原值。
- 三种角色免疫常规攻击、环境伤害、近战／横扫／爆炸击退，保留 `/kill` 和虚空清理，并返还仍携带的玩偶。固定挂点随立板朝向和缩放变化，不逐帧追踪 GIF 中的手部。
- 成年傻子村民通过原版交易窗口出售，基础价格为：玩偶 8 个绿宝石，遥控器 4 个绿宝石，酒 16 个小麦和 1 个玻璃瓶。每项每日 16 次库存；同一村民的库存由玩家共享，按游戏日期补货。

创造页「朋友的酒」中可取得全部物品，也可通过 `/give @s friendswine:<物品ID>` 获取；物品 ID 为 `doll`、`remote`、`wine`、`kasumi_spawn_egg`、`emma_spawn_egg`、`amiya_spawn_egg`。没有合成或酿造配方。多人游戏的客户端与服务器需安装相同目标版本。

## 模组设置

可在模组设置中调整显示效果和生物绕圈速度：

- Forge／NeoForge：打开「模组」列表，选中「朋友的酒」，进入配置界面。
- Fabric：安装对应 Minecraft 版本的可选客户端模组 **Mod Menu**，再从模组列表进入「朋友的酒」设置。

六个版本的参数默认值和范围相同：

| 参数 | 默认值 | 可调范围 | 效果 |
| --- | --- | --- | --- |
| 上下压缩幅度 | 50% | 0～90% | 调整果冻压扁程度及酒劲镜头回弹幅度；0 关闭压缩 |
| 压缩时变宽幅度 | 50% | 0～200% | 最压扁时额外增加的横向宽度，深度不变；0 关闭变宽 |
| 旋转速度倍率 | 1 | 0～4 | 调整自转速度；0 关闭自转 |
| 生物绕圈速度倍率 | 1 | 0～4 | 1 约 20 秒一圈，2 约 10 秒一圈；0 暂停绕圈 |
| 香澄最小／最大显示倍率 | 0.75／1.25 | 各为 0.5～20 | 调整户山香澄随玩偶音乐变大变小的显示范围 |
| 艾玛最小／最大显示倍率 | 0.75／1.25 | 各为 0.5～20 | 调整艾玛随玩偶音乐变大变小的显示范围 |
| 阿米娅最小／最大显示倍率 | 0.75／1.25 | 各为 0.5～20 | 独立调整阿米娅的显示范围，旧配置的其他值保留 |

压缩与变宽幅度填写整数，速度与显示倍率可填写小数；每种生物的最小显示倍率不得大于最大值。修改后点击「完成」保存。

压缩、变宽、自转和生物大小仅影响本机画面，不改变碰撞箱、音乐速度或服务端玩法。**生物绕圈速度由服务端统一控制，影响所有玩家**：Forge／Fabric 需进入世界后，由单人世界所有者或具有游戏管理权限的玩家修改，无权限时该项不可编辑；NeoForge 通过世界／服务器的服务端配置管理该项。

## 六版本工程

| Minecraft | 加载器 | 最低加载器版本 | 工程目录 | 构建 JDK | 游戏／成品 Java |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | Forge | 47.0.0 | `ports/forge-1.20.1` | 17 | 17 |
| 1.20.1 | Fabric | 0.16.10 | `ports/fabric-1.20.1` | 21，同时安装 17 | 17 |
| 1.21.1 | NeoForge | 21.1.1 | `mod` | 21 | 21 |
| 1.21.1 | Fabric | 0.15.11 | `ports/fabric-1.21.1` | 21 | 21 |
| 26.1.2 | NeoForge | 26.1.2.71 | `ports/neoforge-26.1.2` | 25 | 25 |
| 26.1.2 | Fabric | 0.18.4 | `ports/fabric-26.1.2` | 25 | 25 |

Fabric 版本还需安装对应 Minecraft 版本的 Fabric API，最低要求为：

- 1.20.1：`0.92.9+1.20.1`
- 1.21.1：`0.116.7+1.21.1`
- 26.1.2：`0.149.0+26.1.2`

Mod Menu 为 Fabric 客户端的可选依赖，入口与可调参数见[模组设置](#模组设置)。

五个移植工程直接引用 `mod` 中的 `JellyAnimation.java`、`GuestAnimation.java`、`GuestVariant.java` 和生产资源。请克隆完整仓库并保留目录布局，不要单独移动某个移植工程。加载器、API、插件和 Gradle 版本固定在各工程配置中。

### 构建与测试

安装所需 JDK，将 `JAVA_HOME` 指向表中的构建 JDK，并将其 `bin` 加入 `PATH`。首次构建需要联网下载 Gradle 与依赖。

Fabric 1.20.1 的 Gradle 使用 JDK 21，游戏测试使用 JDK 17；可用 `FRIENDSWINE_JAVA17`、`FRIENDSWINE_JAVA21` 指定两个 JDK 的安装目录。其他工程只需对应版本的 JDK。

Forge／NeoForge 的编译版本与运行时下限分别配置。GameTest 使用工程默认的开发加载器版本；构建兼容旧加载器的成品时，使用后面的最低版本参数。

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

Fabric 的 `build` 已包含 GameTest；Forge／NeoForge 显式执行 `runGameTestServer`。GameTest 服务端使用测试运行目录，请勿放入实际游玩存档。

Linux／macOS 使用 `gradlew`，保留 `-p` 指定工程目录，并按上表设置 JDK。例如从仓库根目录构建和测试 Forge 1.20.1：

```bash
./ports/forge-1.20.1/gradlew -p ./ports/forge-1.20.1 build runGameTestServer
```

完成回归测试后，按对应 JDK 构建三个原生加载器的兼容成品：

```powershell
# Forge 1.20.1：JAVA_HOME 指向 JDK 17
& .\ports\forge-1.20.1\gradlew.bat -p .\ports\forge-1.20.1 -Pforge_version=47.0.0 build

# NeoForge 1.21.1：JAVA_HOME 指向 JDK 21
& .\mod\gradlew.bat -p .\mod -Pneo_version=21.1.1 build

# NeoForge 26.1.2：JAVA_HOME 指向 JDK 25
& .\ports\neoforge-26.1.2\gradlew.bat -p .\ports\neoforge-26.1.2 -Pneo_version=26.1.2.71 build
```

成品位于各工程的 `build/libs/`，安装使用 `friendswine-0.1.16-加载器-Minecraft版本.jar`，不要安装带 `dev` 或 `sources` 的文件。客户端开发入口为同一工程的 `runClient`，服务器入口为 `runServer`。

## 原始素材与资源工具

- [原始玩偶模型](assets/source/doll.bbmodel)、[原始音乐](assets/source/doll_music.mp3)及[动画采样](assets/source/animation-samples.json)。
- [户山香澄 PNG／GIF](assets/guests/kasumi/)、[艾玛 PNG／GIF](assets/guests/emma/)与[阿米娅原 GIF／首帧 PNG](assets/guests/amiya/)。
- 生产资源位于 `mod/src/main/resources/`，构建直接使用已提交资源，不要求安装素材处理工具。

仓库中的模型保留原网格、嵌入贴图和动画，已去除对外部文件的引用。

使用 Python 3 和 Pillow 重建三种生物图集：

```powershell
python .\scripts\prepare-guest-textures.py
```

脚本只写生产图集、静态贴图和帧描述，不修改原始素材；逐帧检查 RGBA 像素和原始帧时长，不裁帧、不重新着色。阿米娅为 27 帧、每帧 100ms、2.7 秒一轮，图集 6 列 × 5 行、6000 × 5000 像素；原尺寸纹理在较低显存设备上的表现需要分别检查。

可用 `--output-root <目录>` 指定另一处输出目录，例如用于临时校验。

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

0.1.16 的已有记录覆盖六版本构建、GameTest 与本机客户端验收，详见 [0.1.16 验证](validation/0.1.16.md)；独立多人联机、最低加载器客户端和低显存设备未单独验收。修改公共代码或资源后，应重新构建全部六个版本。
