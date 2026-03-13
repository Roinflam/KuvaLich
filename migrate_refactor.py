#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KuvaLich 项目重构迁移脚本
=============================
用法：
  1. 将此脚本放到项目根目录（与 forge/ 同级）
  2. 运行: python3 migrate_refactor.py
  3. 脚本会在项目根目录生成 refactored/ 目录，包含所有修改后的 .java 文件
  4. 确认无误后，用 refactored/ 的内容替换原 forge/src/main/java/pers/roinflam/kuvalich/ 目录

变更清单：
  [类名重命名] base 包下的 XxxBase → AbstractXxx
  [包迁移+类名] itemstack/ 拆分为 module/weapon/, module/warframe/, module/, weapon/
  [全局引用更新] 所有 .java 文件中的 import 和代码引用同步更新

注意：此脚本不会修改原文件，只读取原文件并在 refactored/ 目录输出结果。
"""
import os
import re
import shutil
import sys

# ============================================================
# 配置：原始源码根目录（相对于脚本位置）
# ============================================================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_ROOT = os.path.join(SCRIPT_DIR, "forge", "src", "main", "java", "pers", "roinflam", "kuvalich")
OUT_ROOT = os.path.join(SCRIPT_DIR, "refactored")

# ============================================================
# 1. 全限定名替换规则（用于 import / package 声明）
#    顺序：长匹配在前，避免短路径被先替换
# ============================================================
FQN_REPLACEMENTS = [
    # --- itemstack 包迁移（含类名变更） ---
    ("pers.roinflam.kuvalich.itemstack.ItemModule",
     "pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler"),

    ("pers.roinflam.kuvalich.itemstack.WarframeModule",
     "pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler"),

    ("pers.roinflam.kuvalich.itemstack.KillStackManager",
     "pers.roinflam.kuvalich.module.KillStackManager"),

    ("pers.roinflam.kuvalich.itemstack.KuvaWeapon",
     "pers.roinflam.kuvalich.weapon.KuvaWeaponUtil"),

    # --- base 类重命名（包路径不变，只改类名） ---
    ("pers.roinflam.kuvalich.base.item.ItemModuleBase",
     "pers.roinflam.kuvalich.base.item.AbstractItemModule"),

    ("pers.roinflam.kuvalich.base.item.WarframeModuleBase",
     "pers.roinflam.kuvalich.base.item.AbstractWarframeModule"),

    ("pers.roinflam.kuvalich.base.item.KuvaWeaponBase",
     "pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon"),

    ("pers.roinflam.kuvalich.base.item.RequiemCardBase",
     "pers.roinflam.kuvalich.base.item.AbstractRequiemCard"),

    # ModuleBase 放最后（是 ItemModuleBase 的子串）
    ("pers.roinflam.kuvalich.base.item.ModuleBase",
     "pers.roinflam.kuvalich.base.item.AbstractModule"),

    ("pers.roinflam.kuvalich.base.enchantment.EnchantmentBase",
     "pers.roinflam.kuvalich.base.enchantment.AbstractEnchantment"),

    ("pers.roinflam.kuvalich.base.entity.KuvaBase",
     "pers.roinflam.kuvalich.base.entity.AbstractKuva"),
]

# ============================================================
# 2. 简单类名替换规则（代码中的 extends / instanceof / 方法调用等）
#    顺序关键：长名在前！\b 保证不会匹配子串
#    例如 \bItemModuleBase\b 不会匹配 AbstractItemModule 中的任何部分
# ============================================================
CLASS_RENAMES = [
    # 先处理长名（Base后缀的）
    ("ItemModuleBase",      "AbstractItemModule"),
    ("WarframeModuleBase",  "AbstractWarframeModule"),
    ("KuvaWeaponBase",      "AbstractKuvaWeapon"),
    ("RequiemCardBase",     "AbstractRequiemCard"),
    ("EnchantmentBase",     "AbstractEnchantment"),
    ("KuvaBase",            "AbstractKuva"),
    ("ModuleBase",          "AbstractModule"),
    # 再处理迁移类（这些也改了类名）
    # 注意 \bItemModule\b 不会匹配 ItemModuleBase（已在上面替换完毕）
    #       也不会匹配 ItemCommonModule / ItemRareModule（因为 \b 在 e 和 B/C/R 之间无边界）
    ("ItemModule",          "WeaponModuleHandler"),
    ("WarframeModule",      "WarframeModuleHandler"),
    ("KuvaWeapon",          "KuvaWeaponUtil"),
    # KillStackManager 类名不变，只改了包（已由 FQN 规则处理）
]

# ============================================================
# 3. 被迁移文件的 package 声明更新
#    key = 原文件名, value = 新的 package 声明行
# ============================================================
PACKAGE_UPDATES = {
    "WeaponModuleHandler.java":   "package pers.roinflam.kuvalich.module.weapon;",
    "WarframeModuleHandler.java": "package pers.roinflam.kuvalich.module.warframe;",
    "KillStackManager.java":     "package pers.roinflam.kuvalich.module;",
    "KuvaWeaponUtil.java":       "package pers.roinflam.kuvalich.weapon;",
}

# 原 package 声明（用于匹配替换）
OLD_ITEMSTACK_PACKAGE = "package pers.roinflam.kuvalich.itemstack;"

# ============================================================
# 4. 文件移动映射（相对于 kuvalich/ 目录）
#    key = 原路径, value = 新路径
# ============================================================
FILE_MOVES = {
    # base 类重命名（包目录不变，只改文件名）
    os.path.join("base", "item", "ModuleBase.java"):
        os.path.join("base", "item", "AbstractModule.java"),
    os.path.join("base", "item", "ItemModuleBase.java"):
        os.path.join("base", "item", "AbstractItemModule.java"),
    os.path.join("base", "item", "WarframeModuleBase.java"):
        os.path.join("base", "item", "AbstractWarframeModule.java"),
    os.path.join("base", "item", "KuvaWeaponBase.java"):
        os.path.join("base", "item", "AbstractKuvaWeapon.java"),
    os.path.join("base", "item", "RequiemCardBase.java"):
        os.path.join("base", "item", "AbstractRequiemCard.java"),
    os.path.join("base", "enchantment", "EnchantmentBase.java"):
        os.path.join("base", "enchantment", "AbstractEnchantment.java"),
    os.path.join("base", "entity", "KuvaBase.java"):
        os.path.join("base", "entity", "AbstractKuva.java"),

    # itemstack 包拆分
    os.path.join("itemstack", "ItemModule.java"):
        os.path.join("module", "weapon", "WeaponModuleHandler.java"),
    os.path.join("itemstack", "WarframeModule.java"):
        os.path.join("module", "warframe", "WarframeModuleHandler.java"),
    os.path.join("itemstack", "KillStackManager.java"):
        os.path.join("module", "KillStackManager.java"),
    os.path.join("itemstack", "KuvaWeapon.java"):
        os.path.join("weapon", "KuvaWeaponUtil.java"),
}


def transform_content(content: str, target_filename: str) -> str:
    """
    对单个 Java 文件的内容应用所有替换规则

    @param content 原始文件内容
    @param target_filename 目标文件名（用于判断是否需要 package 声明替换）
    @return 替换后的文件内容
    """
    # 步骤1: 全限定名替换（import 路径、Javadoc 中的全路径引用等）
    for old_fqn, new_fqn in FQN_REPLACEMENTS:
        content = content.replace(old_fqn, new_fqn)

    # 步骤2: 被迁移文件自身的 package 声明替换
    if target_filename in PACKAGE_UPDATES and OLD_ITEMSTACK_PACKAGE in content:
        content = content.replace(OLD_ITEMSTACK_PACKAGE, PACKAGE_UPDATES[target_filename])

    # 步骤3: 简单类名替换（使用 \b 词边界保证安全）
    # \bKuvaWeapon\b 不会匹配 KuvaWeaponUtil 或 AbstractKuvaWeapon
    # 因为 \b 只在 word/non-word 字符边界触发
    for old_name, new_name in CLASS_RENAMES:
        content = re.sub(r'\b' + re.escape(old_name) + r'\b', new_name, content)

    return content


def main():
    # 检查源码目录是否存在
    if not os.path.isdir(SRC_ROOT):
        print(f"错误: 找不到源码目录 {SRC_ROOT}")
        print("请将此脚本放到项目根目录（与 forge/ 同级）再运行")
        sys.exit(1)

    # 清空输出目录
    if os.path.exists(OUT_ROOT):
        shutil.rmtree(OUT_ROOT)

    processed = 0
    moved = 0

    for dirpath, dirnames, filenames in os.walk(SRC_ROOT):
        for fname in filenames:
            if not fname.endswith('.java'):
                continue

            src_path = os.path.join(dirpath, fname)
            rel_path = os.path.relpath(src_path, SRC_ROOT)  # e.g. "itemstack/ItemModule.java"

            # 确定目标相对路径
            # 使用 os.path.normpath 统一路径分隔符
            normalized_rel = os.path.normpath(rel_path)
            if normalized_rel in FILE_MOVES:
                target_rel = FILE_MOVES[normalized_rel]
                moved += 1
            else:
                target_rel = rel_path

            target_filename = os.path.basename(target_rel)
            target_path = os.path.join(OUT_ROOT, target_rel)

            os.makedirs(os.path.dirname(target_path), exist_ok=True)

            # 读取 → 转换 → 写入
            with open(src_path, 'r', encoding='utf-8') as f:
                content = f.read()

            content = transform_content(content, target_filename)

            with open(target_path, 'w', encoding='utf-8') as f:
                f.write(content)

            status = "  ↪ 移动" if normalized_rel in FILE_MOVES else "  ✓"
            print(f"{status} {rel_path} → {target_rel}")
            processed += 1

    print(f"\n{'='*60}")
    print(f"完成！共处理 {processed} 个文件，其中 {moved} 个被移动/重命名")
    print(f"输出目录: {OUT_ROOT}")
    print(f"\n后续步骤:")
    print(f"  1. 对比 refactored/ 与原 kuvalich/ 目录，确认替换正确")
    print(f"  2. 用 refactored/ 的内容整体替换原 kuvalich/ 目录")
    print(f"  3. 删除残留的空目录 itemstack/（如果存在）")
    print(f"  4. 在 IDE 中全局搜索 'itemstack.' 和旧类名，确认没有遗漏")
    print(f"  5. 编译测试")


if __name__ == '__main__':
    main()
