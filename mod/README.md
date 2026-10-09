# 朋友的酒 · 模组工程

本目录为 NeoForge 1.21.1 工程；玩法、素材来源、六版本构建和测试入口见[项目 README](../README.md)。五个移植工程直接引用本目录的公共动画代码与生产资源，修改目录布局时必须同步调整其相对路径。

模板文件的 MIT 许可保留于 [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt)。以下上游资料原文留存供溯源，当前工程的启动方式以父目录项目说明为准。

## 上游模板资料

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
