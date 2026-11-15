# 处决叠层音效的素材来源

本文件列出 `forge/src/main/resources/assets/kuvalich/sounds/mercy/layer/` 下各音效所用的素材。所有素材均为 CC0（公有领域贡献），下载时核对过来源页面的许可证一栏。成品经过裁切、变调、滤波、混合和响度统一，转为单声道 Ogg Vorbis；原始文件不随模组分发。

## 素材包

| 包 | 作者 | 来源页面 | 许可证 | 下载大小 |
|---|---|---|---|---|
| RPG Audio | Kenney | https://kenney.nl/assets/rpg-audio | CC0 | 964,837 字节（`kenney_rpg-audio.zip`） |
| Impact Sounds | Kenney | https://kenney.nl/assets/impact-sounds | CC0 | 800,850 字节（`kenney_impact-sounds.zip`） |
| 20 Sword Sound Effects (Attacks and Clashes) | StarNinjas | https://opengameart.org/content/20-sword-sound-effects-attacks-and-clashes | CC0 | 149,512 字节（`sword_-_starninjas_1.zip`） |
| 75 CC0 breaking / falling / hit sfx | rubberduck | https://opengameart.org/content/75-cc0-breaking-falling-hit-sfx | CC0 | 1,624,406 字节（`sfx_breaking_and_falling.zip`） |
| Electricity Game Sound Pack | faxcorp | https://opengameart.org/content/electricity-game-sound-pack | CC0 | 1,221,468 字节（单个文件 `deathboom.wav`） |

## 各音效所用的原文件

| 模组内文件 | 原文件 | 所在的包 |
|---|---|---|
| `layer/swing/1.ogg` | `sword.3.ogg`；`cloth1.ogg`、`clothBelt.ogg` | 20 Sword Sound Effects；RPG Audio |
| `layer/swing/2.ogg` | `sword.4.ogg`；`cloth3.ogg`、`clothBelt.ogg` | 20 Sword Sound Effects；RPG Audio |
| `layer/swing/3.ogg` | `sword.1.ogg`；`cloth2.ogg`、`clothBelt.ogg` | 20 Sword Sound Effects；RPG Audio |
| `layer/pierce/1.ogg` | `impactPunch_heavy_000.ogg`；`knifeSlice2.ogg`、`chop.ogg`；`bfh1_hit_04.ogg` | Impact Sounds；RPG Audio；75 CC0 breaking / falling / hit sfx |
| `layer/pierce/2.ogg` | `impactPunch_heavy_001.ogg`；`knifeSlice.ogg`、`chop.ogg`；`bfh1_hit_05.ogg` | Impact Sounds；RPG Audio；75 CC0 breaking / falling / hit sfx |
| `layer/pierce/3.ogg` | `impactPunch_heavy_002.ogg`；`knifeSlice2.ogg`、`chop.ogg`；`bfh1_hit_07.ogg` | Impact Sounds；RPG Audio；75 CC0 breaking / falling / hit sfx |
| `layer/thud/1.ogg` | `impactPunch_heavy_003.ogg`、`impactSoft_heavy_001.ogg`，另叠合成的低频正弦 | Impact Sounds |
| `layer/thud/2.ogg` | `impactPunch_heavy_004.ogg`、`impactSoft_heavy_002.ogg`，另叠合成的低频正弦 | Impact Sounds |
| `layer/tail/1.ogg` | `impactBell_heavy_002.ogg`、`impactMetal_heavy_001.ogg`；`deathboom.wav` | Impact Sounds；Electricity Game Sound Pack |
| `layer/tail/2.ogg` | `impactBell_heavy_003.ogg`、`impactMetal_heavy_002.ogg`；`deathboom.wav` | Impact Sounds；Electricity Game Sound Pack |
| `layer/wrong/1.ogg` | `impactMetal_light_001.ogg`、`impactPlate_heavy_000.ogg`；`drawKnife3.ogg` | Impact Sounds；RPG Audio |
| `layer/wrong/2.ogg` | `impactMetal_light_002.ogg`、`impactPlate_heavy_001.ogg`；`drawKnife1.ogg` | Impact Sounds；RPG Audio |
| `layer/breakfree/1.ogg` | `impactPunch_medium_000.ogg`；`drawKnife2.ogg`、`cloth1.ogg` | Impact Sounds；RPG Audio |
| `layer/breakfree/2.ogg` | `impactPunch_medium_001.ogg`；`drawKnife2.ogg`、`cloth2.ogg` | Impact Sounds；RPG Audio |
| `layer/grab/1.ogg` | `impactSoft_medium_000.ogg`；`cloth4.ogg`、`clothBelt.ogg` | Impact Sounds；RPG Audio |
| `layer/grab/2.ogg` | `impactSoft_medium_001.ogg`；`handleSmallLeather.ogg`、`clothBelt.ogg` | Impact Sounds；RPG Audio |
| `layer/grab/3.ogg` | `impactSoft_medium_002.ogg`；`cloth2.ogg`、`clothBelt.ogg` | Impact Sounds；RPG Audio |
| `layer/leap/1.ogg` | `sword.7.ogg`；`cloth1.ogg` | 20 Sword Sound Effects；RPG Audio |
| `layer/leap/2.ogg` | `sword.10.ogg`；`cloth3.ogg` | 20 Sword Sound Effects；RPG Audio |
| `layer/land/1.ogg` | `footstep_concrete_001.ogg`、`impactSoft_heavy_003.ogg`；`cloth4.ogg` | Impact Sounds；RPG Audio |
| `layer/land/2.ogg` | `footstep_concrete_002.ogg`、`impactSoft_heavy_004.ogg`；`cloth4.ogg` | Impact Sounds；RPG Audio |
