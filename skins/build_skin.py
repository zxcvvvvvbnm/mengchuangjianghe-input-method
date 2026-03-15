# -*- coding: utf-8 -*-
"""
单皮肤构建脚本：请复制到「某个皮肤文件夹」内，与 intro、assets 同级放置后运行。
会检测本文件夹内的「介绍」与「键盘图片」内容，生成：
  - skin.json：本皮肤清单（背景、按键图、介绍列表），供 APK 拉取并安装到键盘；
  - intro.json：使用教程/介绍列表（视频、图片、文字），供点开皮肤时查看。
运行完成后，再回到仓库根目录运行 build_skins.py，即可被远程检测到。
"""
import os
import json

# 本脚本所在目录 = 当前皮肤根目录
SKIN_ROOT = os.path.dirname(os.path.abspath(__file__))
SKIN_ID = os.path.basename(SKIN_ROOT)

# 支持的介绍/键盘图片文件夹名（中英文均可）
INTRO_NAMES = ("intro", "\u4ecb\u7ecd")  # intro, 介绍
ASSETS_NAMES = ("assets", "\u952e\u76d8\u56fe\u7247")  # assets, 键盘图片

# 按键 ID 列表（与皮肤制作教程.txt 一致，用于检测并生成 skin.json）
K26_IDS = [
    "k26_q", "k26_w", "k26_e", "k26_r", "k26_t", "k26_y", "k26_u", "k26_i", "k26_o", "k26_p",
    "k26_a", "k26_s", "k26_d", "k26_f", "k26_g", "k26_h", "k26_j", "k26_k", "k26_l",
    "k26_shift", "k26_z", "k26_x", "k26_c", "k26_v", "k26_b", "k26_n", "k26_m", "k26_back",
    "k26_123", "k26_comma", "k26_space", "k26_period", "k26_zh_en", "k26_enter"
]
K9_IDS = [
    "k9_sym_comma", "k9_1", "k9_2", "k9_3", "k9_backspace",
    "k9_sym_period_cn", "k9_4", "k9_5", "k9_6", "k9_retype",
    "k9_sym_question", "k9_7", "k9_8", "k9_9", "k9_enter",
    "k9_bottom_sym", "k9_123", "k9_space", "k9_zh_en"
]
HANDWRITE_IDS = [
    "handwrite_123", "handwrite_comma", "handwrite_space", "handwrite_period",
    "handwrite_clear", "handwrite_enter"
]
STROKE_IDS = [
    "stroke_1", "stroke_2", "stroke_3", "stroke_4", "stroke_5",
    "stroke_6", "stroke_7", "stroke_8", "stroke_9",
    "stroke_123", "stroke_comma", "stroke_space", "stroke_period", "stroke_zh_en", "stroke_enter"
]

IMAGE_EXT = (".png", ".jpg", ".jpeg", ".webp")
VIDEO_EXT = (".mp4", ".webm", ".mov")
TEXT_EXT = (".txt", ".md")


def find_subdir(root, possible_names):
    for name in possible_names:
        path = os.path.join(root, name)
        if os.path.isdir(path):
            return path
    return None


def collect_intro(intro_dir):
    """收集介绍文件夹内的视频、图片、文字，返回列表 [{type, url, name}, ...]"""
    items = []
    if not intro_dir or not os.path.isdir(intro_dir):
        return items
    for fname in sorted(os.listdir(intro_dir)):
        path = os.path.join(intro_dir, fname)
        if os.path.isdir(path):
            continue
        low = fname.lower()
        if any(low.endswith(e) for e in VIDEO_EXT):
            items.append({"type": "video", "url": fname, "name": fname})
        elif any(low.endswith(e) for e in IMAGE_EXT):
            items.append({"type": "image", "url": fname, "name": fname})
        elif any(low.endswith(e) for e in TEXT_EXT):
            items.append({"type": "text", "url": fname, "name": fname})
    return items


def collect_key_images(keys_dir, id_list):
    """返回 { "k26_q": "k26_q.png", ... }，仅包含存在的文件"""
    out = {}
    if not keys_dir or not os.path.isdir(keys_dir):
        return out
    for kid in id_list:
        for ext in (".png", ".jpg", ".jpeg", ".webp"):
            fname = kid + ext
            if os.path.isfile(os.path.join(keys_dir, fname)):
                out[kid] = fname
                break
    return out


def main():
    intro_dir = find_subdir(SKIN_ROOT, INTRO_NAMES)
    assets_dir = find_subdir(SKIN_ROOT, ASSETS_NAMES)

    # 介绍列表（使用教程）
    intro_list = collect_intro(intro_dir)
    intro_path = os.path.join(SKIN_ROOT, "intro.json")
    with open(intro_path, "w", encoding="utf-8") as f:
        json.dump({"skinId": SKIN_ID, "items": intro_list}, f, ensure_ascii=False, indent=2)
    print("已生成 intro.json（介绍/使用教程，共 %d 项）" % len(intro_list))

    # 背景、预览、按键图（相对路径，相对于本皮肤目录）
    background_url = None
    preview_url = None
    key_assets = {}

    if assets_dir:
        bg_dir = os.path.join(assets_dir, "background")
        if os.path.isdir(bg_dir):
            for name in ("background.png", "background.jpg", "background.jpeg"):
                if os.path.isfile(os.path.join(bg_dir, name)):
                    background_url = "assets/background/" + name
                    break
        key_assets["k26"] = collect_key_images(os.path.join(assets_dir, "k26"), K26_IDS)
        key_assets["k9"] = collect_key_images(os.path.join(assets_dir, "k9"), K9_IDS)
        key_assets["handwrite"] = collect_key_images(os.path.join(assets_dir, "handwrite"), HANDWRITE_IDS)
        key_assets["stroke"] = collect_key_images(os.path.join(assets_dir, "stroke"), STROKE_IDS)
        # 预览图：优先 intro 里第一张图，否则用 background
        if intro_list:
            for it in intro_list:
                if it.get("type") == "image":
                    preview_url = "intro/" + it["url"]
                    break
        if not preview_url and background_url:
            preview_url = background_url

    skin_manifest = {
        "id": SKIN_ID,
        "name": SKIN_ID,
        "previewUrl": preview_url or "",
        "backgroundUrl": background_url or "",
        "keyAssets": key_assets,
        "intro": intro_list
    }
    skin_path = os.path.join(SKIN_ROOT, "skin.json")
    with open(skin_path, "w", encoding="utf-8") as f:
        json.dump(skin_manifest, f, ensure_ascii=False, indent=2)
    print("已生成 skin.json（键盘资源清单，供 APK 拉取）")

    print("本皮肤「%s」构建完成。请回到仓库根目录运行 build_skins.py 以被远程检测。" % SKIN_ID)
    return 0


if __name__ == "__main__":
    exit(main())
