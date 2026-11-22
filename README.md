<div align="center">

<img src="https://cdn.modrinth.com/data/cached_images/8d80b27eba8abc8de863605bab1b35b2f957f462.jpg" alt="Kuva Lich" width="800">

# Kuva Lich | 赤毒玄骸

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_kuvalich_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/kuvalich)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/kuvalich?style=for-the-badge&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/kuvalich)
[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey?style=for-the-badge)](LICENSE)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

</div>

<details open>
<summary><h2>English</h2></summary>

**Kuva Lich** is a combat and progression mod for Minecraft 1.20.1 and 1.12.2 Forge, modeled on the Kuva Lich system of *Warframe*. Every player raises a personal Lich and can only finish it by placing three Requiem cards in the right order. Mods fit any weapon: vanilla swords and bows, TACZ guns and weapons from other mods.

| Minecraft | Loader / Java | Install on | Dependencies |
|---|---|---|---|
| 1.20.1 | Forge 47.x, Java 17 | Client and server | None (GeckoLib and Cloth Config API are bundled) |
| 1.12.2 | Forge 14.23.x, Java 8 | Client and server | None |

The existing 1.12.2 releases remain available for download, and the new content described here will be brought to that version in later updates. All cards and screenshots on this page are from the 1.20.1 version.

On 1.20.1, TACZ and Curios are optional. Without them, the gun stats and the Curios slot bonuses simply do not appear.

### Mod cards

Hovering a mod in an inventory unfolds it into a full card. Frames come in six tiers, and each of the 498 cards has its own cover image staged in game.

![Six card tiers](https://cdn.modrinth.com/data/cached_images/e792c9264b1eee06c306257925f1a387a63692f5.jpg)
*Bronze, Silver, Gold, Prime, Riven and Executioner*

### Veiled mods

Dropped mods start veiled and are revealed with a right-click in the main hand. Riven stats are fully random and can be rerolled with Kuva at the Requiem Forge.

![Veiled mod cards](https://cdn.modrinth.com/data/cached_images/63f8ed5781641f2ecbdfff92874b0a9815bad754.jpg)

### Warframe mods

Warframe mods go on the player through the Requiem Warframe Arsenal. No armor is needed.

![Warframe mod cards](https://cdn.modrinth.com/data/cached_images/82b47ef6ee41f76cbfc06de20c1b92f5692c6f5c.jpg)

### Weapon panel

Any item that receives a base panel at the Requiem Forge accepts weapon mods. The tooltip panel lists the final stats, kill stacks, element combinations and installed mods.

![Weapon tooltip panels](https://cdn.modrinth.com/data/cached_images/b694207f55c7d5c31e92aeda560bba0973cedaa4.png)
*Left: Kuva Shildeg with eight mods. Right: a diamond sword with three.*

### A personal Lich

While a player has no Lich, a Kuva Larvling appears from time to time. Executing it gives birth to a Lich with its own name, weapon, element, personality and rank. The Lich Profile (default `K`) shows everything about it.

![Lich Profile](https://cdn.modrinth.com/data/cached_images/f2e61d3bcbe847419ed2ff6eb0001499c1d4f7cb.jpg)

### Mercy

A Lich has three health segments. Each time one empties it goes down, and the Mercy key (default `X`) checks the next Requiem card. Eight finishers are drawn at random, never the same one twice in a row for the same player.

![A Mercy finisher](https://cdn.modrinth.com/data/cached_images/7fb42b8ce6bd5c1170521fe8690c571a31e1eb60.jpg)

### Convert or Vanquish

When all three cards are right, the final execution pauses: vanquish the Lich for its rewards, or convert it into an ally.

### Ephemera

A Lich has a 20% chance to carry an Ephemera, granted when the Lich is finished. There are 18 styles, each with a different form under each of the seven elements. Ephemera are cosmetic only.

![Ephemera](https://cdn.modrinth.com/data/cached_images/c06f51b5156e8f34663cd8c30f26c7d5eb412b74.jpg)

### For server owners and modpack makers

- **Config files:** `config/kuvalich-common.toml` holds the gameplay values (spawning, decryption, Lich stats, Mercy, allies, Ephemera); `config/kuvalich-modules.toml` adjusts the values of every built-in mod card and defines custom ones; `kuvalich-tooltip.toml` holds client display options.
- **Config screen:** select Kuva Lich in the Mods list and click *Config*.
- **Spawning:** spawn weight, nearby limit, count radius and minimum spacing are set separately for Liches and Thralls.
- **Toggles:** Mercy kills, conversion, allies and Lich personalities can each be turned off; the guide follows those settings.
- **Commands:** operators can use `/kuvalich` to inspect and adjust each player's Lich, roster and pending items.

### FAQ

**Do I need to have played Warframe?**
No. The Field Guide explains every system from the start; players who know Warframe will simply recognize more of it.

**Can weapons from other mods take mods?**
Yes. Any item can be consecrated at the Requiem Forge, including weapons from SlashBlade, gun mods and magic mods.

**Does the server need it?**
Yes, install it on both the client and the server. It also works in single player.

**Can I put it in a modpack?**
Yes, no need to ask. Please credit the author; commercial use is not allowed (see the license below).

**Anything to know before updating?**
Back up your config folder first, and update the client and the server together. The changelog of each version lists what changed.

### Building from source

<details>
<summary><b>Prerequisite: the TACZ jar</b></summary>

**TACZ** (*Timeless and Classics Zero*) is a compile-time dependency: 18 source files reference `com.tacz`, so its jar is needed to compile. At runtime TACZ is optional (`mandatory=false` in `mods.toml`). It is not pulled from Maven; `forge/build.gradle` scans a local folder instead:

```groovy
fileTree(dir: 'dependencies', include: '*.jar').each { File file ->
    modApi(files(file))
    jijSource(files(file))
}
```

If that folder is empty, the build fails with `package com.tacz does not exist`.

1. Get the TACZ Forge jar for 1.20.1 from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tacz) or [Modrinth](https://modrinth.com/mod/tacz).
2. Put it into `forge/dependencies/`. This repository is built against `tacz-1.20.1-1.1.7-hotfix.jar`.
3. Build with a local Gradle **8.12.1** (the version in `gradle/wrapper/gradle-wrapper.properties`; the repository ships no `gradlew` script):
   ```bash
   gradle build
   ```
   The jar is written to `forge/build/libs/`.

Everything else (Forge, Architectury, GeckoLib, Curios, MixinExtras and so on) is resolved from Maven. Gradle must run on **JDK 21**, which Architectury Loom requires; the mod itself is still compiled for Java 17.

</details>

[Source code](https://github.com/Roinflam/KuvaLich) · [Issues](https://github.com/Roinflam/KuvaLich/issues) · [MC百科](https://www.mcmod.cn/class/14140.html)

### License and credits

- Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) (full text: [LICENSE](LICENSE)): free to use, modify and include in modpacks; no commercial use; redistributed or modified versions must credit the original author (Roinflam), state what was changed, and use the same license.
- Warframe is a trademark of Digital Extremes Ltd. Kuva Lich and the other names and concepts from Warframe referenced in this mod belong to their respective owners. This is an unofficial fan-made mod and is not affiliated with or endorsed by Digital Extremes.
- [GeckoLib](https://github.com/bernie-g/geckolib) (MIT) and [Cloth Config API](https://github.com/shedaniel/cloth-config) by shedaniel (GNU LGPLv3) are bundled unmodified as separate jar-in-jar files (under `META-INF/jars/`). They keep their own licenses and can be replaced with other compatible builds.
- Sound sources are listed in [SOUND-SOURCES.md](SOUND-SOURCES.md); the card font license is in [`ofl.txt`](forge/src/main/resources/assets/kuvalich/font/hover_card/ofl.txt).
- Special thanks to 白狐大小姐.

</details>

<details>
<summary><h2>中文</h2></summary>

**赤毒玄骸**是 Minecraft 1.20.1 与 1.12.2 Forge 的战斗与成长模组，按《星际战甲》的赤毒玄骸系统制作。每名玩家会培养出一只专属玄骸，只有按正确顺序摆好三张安魂卡并依次处决，才能将其终结。模组卡可以装在任何武器上，包括原版武器、TACZ 枪械和其他模组的武器。

| Minecraft | 加载器 / Java | 安装位置 | 前置 |
|---|---|---|---|
| 1.20.1 | Forge 47.x，Java 17 | 客户端和服务端 | 无（已内置 GeckoLib 与 Cloth Config API） |
| 1.12.2 | Forge 14.23.x，Java 8 | 客户端和服务端 | 无 |

1.12.2 版仍可下载，本页介绍的新内容将在后续更新中同步到该版本；页面中的卡片和截图均来自 1.20.1 版。

1.20.1 版中，TACZ 和 Curios 为可选联动，未安装时不显示枪械词条和饰品栏加成。

### 模组卡

鼠标停在模组卡上，图标原地展开成完整的卡。边框分青铜、白银、黄金、Prime、裂罅、执刑官六档，498 张卡各有一张游戏内摆拍的卡面。

![六档模组卡](https://cdn.modrinth.com/data/cached_images/6b7ef38f59eb63a618337ce2d0555d16b059b088.jpg)
*青铜、白银、黄金、Prime、裂罅、执刑官*

### 未揭示的模组

掉落的模组卡起初没有揭示，主手持握时按右键即可揭示。裂罅模组揭示后词条完全随机，可在安魂之融中用赤毒重洗。

![未揭示的模组卡](https://cdn.modrinth.com/data/cached_images/9082aac334f01c59e510363767d4749671519622.jpg)

### 战甲模组

战甲模组装在玩家身上，放进安魂之战甲军械库即可生效，不需要穿盔甲。

![战甲模组卡](https://cdn.modrinth.com/data/cached_images/ff8d4f510acb2399230d14f2dc2f469b8c897f17.jpg)

### 武器面板

任何物品在安魂之融中获得基础面板后，都可以安装武器模组。悬浮面板列出最终数值、击杀叠层、元素组合和已装模组。

![武器悬浮面板](https://cdn.modrinth.com/data/cached_images/4f587ce050a8859f4e6b860e2392f04a811441ae.png)
*左：赤毒希尔德，装满八张模组；右：装了三张模组的钻石剑*

### 专属玄骸

玩家没有玄骸时，赤毒幼体会不时出现。处决幼体后诞生一只专属玄骸，带有名字、武器、元素、性格和等级。玄骸档案（默认 `K`）显示它的全部情报。

![玄骸档案](https://cdn.modrinth.com/data/cached_images/7bcecb77d890467a560823328c1510a16159fbde.jpg)

### 怜悯之击

玄骸血条分三段，每段打空时倒地，按怜悯之击（默认 `X`）依次验证安魂卡。处决共有八套动作，同一名玩家不会连续抽到同一套。

![怜悯之击](https://cdn.modrinth.com/data/cached_images/7fb42b8ce6bd5c1170521fe8690c571a31e1eb60.jpg)

### 感化或消灭

三张安魂卡全部正确时，最后一次处决会停下来，由玩家选择消灭玄骸领取奖励，或将其感化为盟友。

![感化或消灭](https://cdn.modrinth.com/data/cached_images/59626026649b29ef2ba15bdfebd1c39fa343c3de.jpg)

### 幻纹

每只玄骸有 20% 的几率带有幻纹，终结它即可获得。幻纹共 18 款，在七种元素下各有不同形态，只改变外观。

![幻纹](https://cdn.modrinth.com/data/cached_images/c06f51b5156e8f34663cd8c30f26c7d5eb412b74.jpg)

### 服主与整合包作者

- **配置文件**：`config/kuvalich-common.toml` 为玩法数值（生成、解密、玄骸面板、处决、盟友、幻纹等）；`config/kuvalich-modules.toml` 可统一调整全部内置模组卡的数值，也可新建自定义模组卡；`kuvalich-tooltip.toml` 为客户端显示设置；
- **配置界面**：在「模组」列表中选中 KuvaLich，点击「配置」打开；
- **生成**：赤毒玄骸和赤毒奴仆的生成权重、附近数量上限、统计半径和最小间距分别设置；
- **开关**：怜悯之击、感化、盟友和玄骸性格均可单独关闭，冒险指南的内容随之变化；
- **指令**：`/kuvalich` 系列管理指令可查看和调整每名玩家的玄骸、盟友名册和待领取物品。

### 常见问题

**没有玩过《星际战甲》能否上手？**
可以。冒险指南从头讲解全部玩法，玩过《星际战甲》的玩家会更熟悉其中的设定。

**其他模组的武器能否安装模组？**
可以。任何物品都可以在安魂之融中开光，包括拔刀剑、枪械和魔法类模组的武器。

**服务器能否使用？**
可以，客户端和服务端都需要安装。

**可以加入整合包吗？**
可以，无需另行询问；请注明原作者，不得用于商业用途（见下方许可与声明）。

**更新前需要注意什么？**
建议先备份 config 文件夹，客户端和服务端一同更新。各版本的改动见更新日志。

### 从源码构建

<details>
<summary><b>前置：TACZ 的 jar</b></summary>

**TACZ**（*Timeless and Classics Zero*）是编译期依赖：仓库里有 18 个源文件引用 `com.tacz`，编译时需要它的 jar；运行时 TACZ 是可选联动（`mods.toml` 里 `mandatory=false`）。它不从 Maven 拉取，`forge/build.gradle` 扫描本地目录：

```groovy
fileTree(dir: 'dependencies', include: '*.jar').each { File file ->
    modApi(files(file))
    jijSource(files(file))
}
```

目录为空时构建会报 `package com.tacz does not exist`。

1. 从 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tacz) 或 [Modrinth](https://modrinth.com/mod/tacz) 获取 1.20.1 的 TACZ Forge 版 jar。
2. 放进 `forge/dependencies/` 目录。本仓库对应的版本是 `tacz-1.20.1-1.1.7-hotfix.jar`。
3. 用本机 Gradle **8.12.1**（`gradle/wrapper/gradle-wrapper.properties` 里写的版本；仓库内没有 `gradlew` 脚本）构建：
   ```bash
   gradle build
   ```
   产物位于 `forge/build/libs/`。

其余依赖（Forge、Architectury、GeckoLib、Curios、MixinExtras 等）都由 Gradle 从 Maven 解析。Gradle 需要运行在 **JDK 21** 上（Architectury Loom 的要求），模组本身仍按 Java 17 编译。

</details>

[源代码](https://github.com/Roinflam/KuvaLich) · [问题反馈](https://github.com/Roinflam/KuvaLich/issues) · [MC百科](https://www.mcmod.cn/class/14140.html)

### 许可与声明

- 本模组采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)（知识共享 署名-非商业性使用-相同方式共享 4.0 国际）协议，全文见 [LICENSE](LICENSE)：可以免费使用、修改、加入整合包；不得用于商业用途；转载或发布修改版需署名原作者 Roinflam、注明改动，并沿用同一协议；
- 《Warframe》（星际战甲）是 Digital Extremes Ltd. 的商标。本模组中引用的赤毒玄骸等游戏名称和设定，版权归各自权利人所有。本模组为非官方同人作品，与 Digital Extremes 无关联，也未获其认可；
- [Cloth Config API](https://github.com/shedaniel/cloth-config)（作者 shedaniel，GNU LGPLv3 许可）和 [GeckoLib](https://github.com/bernie-g/geckolib)（MIT 许可）均未作修改，以独立 jar-in-jar 的形式内嵌（位于 `META-INF/jars/`）；它们沿用各自的许可证，可自行替换为其它兼容版本；
- 音效来源见 [SOUND-SOURCES.md](SOUND-SOURCES.md)，卡面字体许可见 [`ofl.txt`](forge/src/main/resources/assets/kuvalich/font/hover_card/ofl.txt)；
- 特别鸣谢白狐大小姐。

</details>
