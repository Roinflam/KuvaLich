<div align="center">

<img src="https://cdn.modrinth.com/data/cached_images/c284f82387151b2190257bbaa771060a756dd4f8.jpg" alt="Kuva Lich" width="800">

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

**Kuva Lich** is a combat and progression mod for Minecraft 1.20.1 Forge, modeled on the Kuva Lich system of *Warframe*. Every player raises a personal Lich, a named nemesis with its own weapon, personality and rank, and can only finish it by working out the right order of three Requiem cards. A successful hunt yields Kuva weapons, Prime and Riven mods, and the Lich itself can be converted into an ally. Mods are upgrade cards that fit any weapon: vanilla swords and bows, TACZ guns, and weapons from any other mod.

### Versions

| Minecraft | Loader / Java | Install on | Dependencies |
|---|---|---|---|
| 1.20.1 | Forge 47.x, Java 17 | Client and server | None (GeckoLib and Cloth Config API are bundled) |

TACZ and Curios are optional. Without them, the gun stats and the Curios slot bonuses simply do not appear.

### The hunt

A round of play runs from mining to the final execution:

- **Mine:** break Requiem Ore with a *Requiem Destroyed* pickaxe to collect Requiem cards, mods and materials.
- **Nemesis:** while a player has no Lich, a Kuva Larvling appears nearby from time to time. Once knocked down, it reveals the weapon, level and element its Lich will carry. Executing it gives birth to that Lich.
- **Farm:** Kuva Thralls arrive in squads. Each Thrall executed adds decryption progress in three stages, and every full stage reveals one card of the answer.
- **Set the cards:** place three of the eight Requiem cards in the Requiem Gate in the order believed to be correct.
- **Confront:** the Lich comes on its own when its rage is full and announces itself over the comm window first. Its health has three segments; each time one empties it goes down, and the *Mercy* key (default `X`) checks the first one, two and then three cards. A wrong card lets it escape and raises its rank, which makes it tougher.

Each Lich is either Jovial or Logical. The personality decides how it talks and how it fights: Jovial Liches hit hard and grow fiercer as the fight drags on, while Logical ones are tough and adapt quickly. Weaknesses, resistances and ability tiers differ from Lich to Lich. A failed execution can also yield an **Oull**, the wildcard Requiem card.

![Lich Profile: rank, personality, rage, weapon, weaknesses and the execution record](https://cdn.modrinth.com/data/cached_images/11a4975b769415f7e55999e07cae771132029efb_0.webp)
*Lich Profile: rank, personality, rage, weapon, weaknesses and the execution record*

The **Lich Profile** opens from the Requiem Gate (default `K`) and shows everything about the current Lich: rank, personality, rage meter, weapon and element, health and armor, weaknesses and resistances, the cards in the Gate and the last five executions.

### Mercy kills, Convert or Vanquish

Every Lich execution draws one of **eight finishers**, and the same player never gets the same one twice in a row. Larger finishers are only drawn where the terrain leaves enough room. Hit-stop, camera shake and camera motion can be reduced or turned off in the client settings. With Yes Steve Model or a similar player model mod installed, the executor is shown with the vanilla model for the duration of the finisher.

![A Mercy finisher on a Kuva Lich](https://cdn.modrinth.com/data/cached_images/8d41b0594819bf16f5e2be9155eb873c08663964.jpg)
*A Mercy finisher on a Kuva Lich*

When all three cards are right, the final execution pauses and offers two choices:

- **Vanquish:** claim the Kuva weapon, a Lich Reliquary, Kuva, Riven Slivers and a guaranteed Prime or Riven mod.
- **Convert:** give up the rewards and add the Lich to the ally roster (3 slots by default). When the player is low on health, the active ally may join the fight, attack the enemies around them and leave after a while.

Rewards never drop on the ground. They go to the **Pending** list first and move into the inventory as soon as there is room; `/kuvalich inbox` claims them at once.

### Ephemera

A Lich has a 20% chance to carry an **Ephemera**, a cosmetic effect worn around the body. Finishing that Lich, whether by converting or vanquishing it, grants the Ephemera. There are **18 styles** around the head, shoulders, back, body and feet, and each takes a different form under each of the seven elements: tongues of fire, ice prisms, electric arcs, dripping toxin, impact rings, magnetic arcs or radiation lines. Ephemera go into the Warframe Arsenal and have no effect on stats. The tuning panel changes the primary and secondary colors, gradient, size and height.

![Six of the eighteen Ephemera styles](https://cdn.modrinth.com/data/cached_images/d1e093e744b82193f8b0555c5791a569499d7127.jpg)
*Six of the eighteen Ephemera styles*

### Mods and weapons

- **Weapon and Warframe mods:** weapon mods go on weapons in the Requiem Weapon Arsenal; Warframe mods go on the player in the Requiem Warframe Arsenal, with no armor needed.
- **Five rarities:** Bronze, Silver and Gold drop from ore and Thralls; Prime and Riven come from successful hunts. The Prime tier includes the Galvanized and Executioner series, which grow with kill stacks.
- **Rivens:** fully random stats with a disposition of one to five dots, revealed with a right-click and rerolled with Kuva at the Requiem Forge.
- **Any weapon:** consecrate an item at the Requiem Forge to give it a random base panel; from then on it takes weapon mods. Forma rerolls the panel.
- **13 Kuva weapons:** melee weapons with their own passives, each with a random level and innate element.
- **Combat:** critical hits, 14 damage types with combined elements and status effects, kill stacks, bane and damage numbers.
- **Mod Codex:** press `Tab` in either Arsenal to browse every mod card, with search.

Hovering a mod card in an inventory screen unfolds it into a full card in the style of *Warframe*, with a frame for each rarity and a cover image. All 498 mod cards have their own cover, staged and captured in game. Size and tilt are set in the `[hover_card]` section of `kuvalich-tooltip.toml`.

![Some of the 498 mod card covers](https://cdn.modrinth.com/data/cached_images/6db7ad962e8e2a57c14e871b9ab1a262d1f7d56a_0.webp)
*Some of the 498 mod card covers*

### TACZ and Curios

- **TACZ:** consecrated guns take weapon mods, and gun-only stats such as multishot, fire rate, reload speed, magazine size and burst radius take real effect.
- **Curios:** a consecrated trinket worn in a Curios slot lends its weapon mods to the main-hand weapon at a reduced rate.

### Field Guide

Every player receives the **Kuva Lich Field Guide** on first join. It covers the whole mod in ten sections, from the first pickaxe to the endgame, and its numbers follow the server configuration. Holding `Ctrl` over an item looks it up in the guide. The guide is available in English, Simplified Chinese and Traditional Chinese.

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

**赤毒玄骸**是 Minecraft 1.20.1 Forge 的战斗养成模组，玩法仿照《星际战甲》的赤毒玄骸系统。每名玩家拥有一只专属的玄骸，玄骸有自己的名字、武器、性格和等级，只有按正确顺序摆好三张安魂卡并依次处决，才能将其终结。讨伐成功可获得赤毒武器、Prime 模组和裂罅模组，也可以选择感化玄骸，使其成为盟友。本模组中的「模组」指强化卡，可安装在任何武器上，包括原版的剑和弓、TACZ 的枪械以及其他模组的武器。

### 运行需求

| Minecraft | 加载器与 Java | 安装位置 | 前置模组 |
|---|---|---|---|
| 1.20.1 | Forge 47.x，Java 17 | 客户端和服务端 | 无（GeckoLib 和 Cloth Config API 已内置） |

TACZ 和 Curios 为可选联动，未安装时对应的枪械词条和饰品栏加成不会出现。

### 讨伐流程

- **挖矿**：使用附有「灭骸」的镐挖掘安魂矿石，获得安魂卡、模组和材料；
- **宿敌**：玩家没有玄骸时，赤毒幼体会不时出现在附近。幼体被打倒后显示将来那只玄骸的武器、等级和元素，处决幼体后玄骸随之诞生；
- **积攒进度**：赤毒奴仆成队出现，处决奴仆可积攒解密进度。进度分三段，每积满一段揭示一张答案卡；
- **摆卡**：从八种安魂卡中选出三张，按推测的顺序放入灭骸之扉；
- **讨伐**：玄骸怒气满后自行找上门，现身前先在通讯窗中发话。玄骸的生命分为三段，每打空一段倒地一次，按怜悯之击键（默认 `X`）处决，三次处决依次核对前一、二、三张卡。卡序有误时玄骸挣脱逃走，玄骸等级提升一级。

玄骸的性格分为活泼和理性两种，决定其说话方式和战斗方式：活泼的玄骸攻击高、生命低，战斗越久越凶；理性的玄骸生命和护甲高，适应得快。每只玄骸的弱点、抗性和各项能力档位也各不相同。处决失败时有几率获得 **Oull**，即可替代任意安魂卡的万能卡。

玄骸档案在灭骸之扉中打开（默认 `K`），显示当前玄骸的等级、性格、怒气、武器与元素、生命与护甲、弱点与抗性、灭骸之扉中的安魂卡和最近五次处决记录。

![玄骸档案](https://cdn.modrinth.com/data/cached_images/7448286b5f6f2223e8ed394a493acd149c2d9cbd_0.webp)

### 怜悯之击与感化

每次处决玄骸时，从**八套处决动作**中随机抽取一套，同一名玩家不会连续两次抽到同一套；其中五套需要周围留有足够空间。处决时的顿帧、镜头震动和镜头运动可在客户端设置中减弱或关闭。装有 Yes Steve Model 等替换玩家模型的模组时，处决期间处决者改用原版玩家模型显示。

第三次处决的三刀全部正确时，处决停在最后一刻，由玩家选择：

- **消灭**：取得赤毒武器、玄骸之遗、赤毒、裂罅碎块，以及必出一张的 Prime 或裂罅模组；
- **感化**：放弃上述奖励，玄骸加入盟友名册（默认容纳 3 只）。玩家生命较低时，设为出战的盟友有几率前来助战，攻击周围的敌对生物，一段时间后退去。

讨伐所得的物品不会掉在地上，而是先记入待领取，背包有空位时自动放入；输入 `/kuvalich inbox` 可立即领取。

### 幻纹

每只玄骸有 20% 的几率带有**幻纹**，无论感化还是消灭，终结该玄骸时都会获得。幻纹是环绕在身上的光效，只改变外观，不影响属性，装在安魂之战甲军械库的幻纹格中。共 **18 种款式**，分布在头后、肩臂、背后、环身和足下；同一款式在火焰、冰冻、电击、毒素、冲击、磁力、辐射七种元素下形态各不相同。调整外观面板可修改主色与副色、渐变方式、大小和高度。

![十八种幻纹中的六种](https://cdn.modrinth.com/data/cached_images/d1e093e744b82193f8b0555c5791a569499d7127.jpg)

### 模组与武器

- **武器模组与战甲模组**：武器模组在安魂之武器军械库中安装到武器上，战甲模组在安魂之战甲军械库中安装到玩家本人身上，无需穿戴护甲；
- **五档品质**：青铜、白银、黄金来自安魂矿石和赤毒奴仆，Prime 和裂罅来自讨伐成功的奖励；Prime 一档另含镀层和执刑官两个系列，随击杀叠层增强；
- **裂罅模组**：词条完全随机，倾向为一至五点，右键揭示后可在安魂之融中消耗赤毒循环；
- **任何武器**：任何物品在安魂之融中用裂罅碎块开光后获得随机基础面板，此后即可安装武器模组；塑形块可重洗面板；
- **赤毒武器**：共 13 把近战武器，各有被动效果，等级和元素随机；
- **战斗系统**：暴击、14 种伤害类型与复合元素、异常状态、击杀叠层、克制和伤害数字；
- **模组图鉴**：在两座军械库中按 `Tab` 打开，可搜索并查看全部模组卡面。

在物品栏界面中将鼠标悬停在模组卡上，模组卡会展开为《星际战甲》样式的完整卡面，边框按品质区分。全部 498 张模组卡均有专属封面，由游戏内实景摆拍制作。卡面大小和倾斜效果在 `kuvalich-tooltip.toml` 的 `[hover_card]` 一节中设置。

![部分模组卡封面](https://cdn.modrinth.com/data/cached_images/6db7ad962e8e2a57c14e871b9ab1a262d1f7d56a_0.webp)

### 联动

- **TACZ**：开光后的枪械可以安装武器模组，多重射击、射速、装填速度、弹匣容量和爆炸半径等枪械专属词条实际生效；
- **Curios**：开光后的饰品佩戴在饰品栏中时，将其上的武器模组按一定比例加给主手武器。

### 冒险指南

玩家首次进入游戏时获得一本「赤毒玄骸 冒险指南」。指南共十个分类，从第一把镐讲到后期玩法，其中的数值随服务端配置变化。物品提示框出现时按住 `Ctrl`，可直接翻到指南中对应的篇目。指南提供简体中文、繁体中文和英文。

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
