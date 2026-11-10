# 《朋友的酒》

> 放下玩偶播歌，附近生物会靠过来，随着音乐回弹、旋转或绕圈。**v0.2.0** 更新游戏内「模组」详情页的介绍，补齐玩法和问题反馈入口。

## 下载与安装

从 [v0.2.0 Release](https://github.com/jiaotan798/friendswine/releases/tag/v0.2.0) 下载与下表匹配的 `.jar`。

| Minecraft | 加载器 | 安装位置 | 前置模组 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.0.0 或更高 | 客户端及服务端 | 无 |
| 1.20.1 | Fabric Loader 0.16.10 或更高 | 客户端及服务端 | Fabric API 0.92.9+1.20.1 或更高 |
| 1.21.1 | NeoForge 21.1.1 或更高 | 客户端及服务端 | 无 |
| 1.21.1 | Fabric Loader 0.15.11 或更高 | 客户端及服务端 | Fabric API 0.116.7+1.21.1 或更高 |
| 26.1.2 | NeoForge 26.1.2.71 或更高 | 客户端及服务端 | 无 |
| 26.1.2 | Fabric Loader 0.18.4 或更高 | 客户端及服务端 | Fabric API 0.149.0+26.1.2 或更高 |

把匹配的 JAR 和所需前置模组放进对应实例的 `mods` 目录。搭配《烧心琵琶曲》时请使用 [0.3.2](https://github.com/jiaotan798/shaoxinpipaqu/releases/tag/v0.3.2)。Java 版本、联机和升级要求见[安装指南](https://github.com/jiaotan798/friendswine/releases/download/v0.2.0/INSTALL.txt)。

## 玩法

- **播放音乐**。放下玩偶后右键播放，再次右键停止。附近生物会向播放中的玩偶聚集。
- **切换档位**。用遥控器右键绑定玩偶，再对空气使用，依次切换回弹、加入自转、加入生物绕圈和停止。
- **饮用朋友的酒**。饮用后获得约 169 秒酒劲，人物和镜头会随之回弹。牛奶可以解除酒劲。
- **遇见三位角色**。户山香澄、艾玛和阿米娅会自然生成，也可能在玩偶进入第三档时出现。带着玩偶时，它们可能跟上来。

触发条件和道具效果见[完整玩法说明](docs/完整玩法说明.md)，配置方法见[模组设置](docs/模组设置.md)。各版本变更见[更新记录](CHANGELOG.md)。

## 构建源码

```powershell
.\mod\gradlew.bat -p mod build
```

1.21.1 NeoForge 版的产物在 `mod/build/libs/`。构建环境及其他版本和加载器的步骤见[构建说明](docs/构建说明.md)。

## 反馈

在 QQ 搜索群号 **873511838**，或扫描下方二维码加入「mc模组问题反馈群」。也可以在本仓库的 **[Issues](https://github.com/jiaotan798/friendswine/issues)** 提交问题。反馈时请附上模组版本、Minecraft 版本、加载器和复现步骤。

<img src=".github/feedback-qq.png" alt="mc模组问题反馈群二维码，群号 873511838" width="220">

## 作者、许可与素材

共创作者：jiaotan_、你个人机cc。

- jiaotan_：[bilibili](https://space.bilibili.com/409729840?) · [抖音](https://v.douyin.com/KDs5ZxJh5V8/)
- 你个人机cc：[bilibili](https://space.bilibili.com/400763031?) · [抖音](https://v.douyin.com/iyCu2FJYTGc/)

本模组的原创内容保留所有权利（**All Rights Reserved**）。工程基于 [NeoForge MDK 1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/0d385327b15a9497991c0cc032eb63bc18aa3cf7)，模板适用 [MIT 许可](mod/TEMPLATE_LICENSE.txt)，映射说明见[工程上游资料](mod/README.md)。原始素材和资源工具见[构建说明](docs/构建说明.md#原始素材与资源工具)。
