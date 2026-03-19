#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
赤毒玄骸指南书修正脚本 v1.1（第二轮）
==============================================
用法：放到 assets/kuvalich/ 目录下运行
  python patch_guidebook_v2.py

修正内容：
  1. weapon_panel.p2 — 暴击档位对齐实际颜色
  2. weapon_panel.p3 — 暴击颜色修正（白/黄/橙/红/青）
  3. item_attr_crit.p1 — 精简防超页
==============================================
"""

import json
import os
import shutil
import sys


PATCHES_ZH = {
    # ---- 武器面板：暴击档位 ----
    "kuvalich.guidebook.entry.weapon_panel.p2":
        "$(l)暴击档位$()$(br2)"
        "暴击率超过100%%后$(br)"
        "可进入更高暴击档位：$(br2)"
        "$(#FFFF55)黄色暴击$() — 伤害 × 暴击倍率$(br2)"
        "$(#FFAA00)橙色暴击$() — 伤害 × 暴击倍率 × 2$(br2)"
        "$(#FF5555)红色暴击$() — 伤害 × 暴击倍率 × 3",

    # ---- 武器面板：伤害颜色 ----
    "kuvalich.guidebook.entry.weapon_panel.p3":
        "$(l)伤害颜色含义$()$(br2)"
        "$(#FFFFFF)白色$() = 普通伤害$(br2)"
        "$(#FFFF55)黄色$() = 暴击$(br2)"
        "$(#FFAA00)橙色$() = 双重暴击$(br2)"
        "$(#FF5555)红色$() = 三重暴击$(br2)"
        "$(#55FFFF)青色$() = 目标有护盾",

    # ---- 暴击词条参考：精简p1 ----
    "kuvalich.guidebook.entry.item_attr_crit.p1":
        "$(l)近战暴击$()$(br2)"
        "暴击几率 meleeCritStrikeProbability$(br)"
        "暴击伤害 meleeCritStrikeMultiplier$(br2)"
        "$(l)远程暴击$()$(br2)"
        "暴击几率 remoteCritStrikeProbability$(br)"
        "暴击伤害 remoteCritStrikeMultiplier",
}

PATCHES_EN = {
    "kuvalich.guidebook.entry.weapon_panel.p2":
        "$(l)Crit Tiers$()$(br2)"
        "When crit chance exceeds 100%%,$(br)"
        "higher crit tiers are possible:$(br2)"
        "$(#FFFF55)Yellow Crit$() — Damage × Multiplier$(br2)"
        "$(#FFAA00)Orange Crit$() — Damage × Multiplier × 2$(br2)"
        "$(#FF5555)Red Crit$() — Damage × Multiplier × 3",

    "kuvalich.guidebook.entry.weapon_panel.p3":
        "$(l)Damage Number Colors$()$(br2)"
        "$(#FFFFFF)White$() = Normal damage$(br2)"
        "$(#FFFF55)Yellow$() = Critical hit$(br2)"
        "$(#FFAA00)Orange$() = Double crit$(br2)"
        "$(#FF5555)Red$() = Triple crit$(br2)"
        "$(#55FFFF)Cyan$() = Target has shield",

    "kuvalich.guidebook.entry.item_attr_crit.p1":
        "$(l)Melee Crit$()$(br2)"
        "Crit Chance meleeCritStrikeProbability$(br)"
        "Crit Damage meleeCritStrikeMultiplier$(br2)"
        "$(l)Ranged Crit$()$(br2)"
        "Crit Chance remoteCritStrikeProbability$(br)"
        "Crit Damage remoteCritStrikeMultiplier",
}


def patch_lang(filepath, patches):
    if not os.path.exists(filepath):
        print(f"  ✗ 未找到 {filepath}")
        return False

    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)

    changed = sum(1 for k in patches if k in data and data[k] != patches[k])
    added = sum(1 for k in patches if k not in data)

    bak = filepath + '.v2.bak'
    shutil.copy2(filepath, bak)

    data.update(patches)

    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')

    name = os.path.basename(filepath)
    print(f"  ✓ {name}: {changed} 修改 + {added} 新增 (备份 → {name}.v2.bak)")
    return True


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    lang_dir = os.path.join(script_dir, 'lang')

    if not os.path.isdir(lang_dir):
        print(f"错误：在 {script_dir} 下找不到 lang/ 目录")
        sys.exit(1)

    print("=" * 50)
    print("  赤毒玄骸指南书修正 v1.1")
    print("  暴击颜色 + 暴击词条超页修正")
    print("=" * 50)
    print()

    patch_lang(os.path.join(lang_dir, 'en_us.json'), PATCHES_EN)
    patch_lang(os.path.join(lang_dir, 'zh_cn.json'), PATCHES_ZH)

    print()
    print("完成！重启游戏查看效果。")


if __name__ == '__main__':
    main()
