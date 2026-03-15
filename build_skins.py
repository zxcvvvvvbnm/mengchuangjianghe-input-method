# -*- coding: utf-8 -*-
"""
本仓库：生成皮肤列表与白色壁纸，供 APK 拉取。社区可新增皮肤文件夹后运行本脚本汇总。
运行：python build_skins.py（在仓库根目录执行）
"""
import base64
import os
import json

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SKINS_DIR = os.path.join(SCRIPT_DIR, "skins")
PREVIEW_W, PREVIEW_H = 160, 80
BACKGROUND_W, BACKGROUND_H = 720, 320

def ensure_skins_white():
    white_dir = os.path.join(SKINS_DIR, "white")
    os.makedirs(white_dir, exist_ok=True)
    try:
        from PIL import Image
        Image.new("RGB", (PREVIEW_W, PREVIEW_H), (255, 255, 255)).save(os.path.join(white_dir, "preview.png"))
        Image.new("RGB", (BACKGROUND_W, BACKGROUND_H), (255, 255, 255)).save(os.path.join(white_dir, "background.png"))
        print("已生成 skins/white/preview.png、background.png")
    except ImportError:
        WHITE_1X1_B64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        data = base64.b64decode(WHITE_1X1_B64)
        for name in ("preview.png", "background.png"):
            with open(os.path.join(white_dir, name), "wb") as f:
                f.write(data)
        print("已生成 skins/white 白图（1×1）；建议安装 Pillow 后重新运行")

def collect_all_skins():
    list_data = []
    if not os.path.isdir(SKINS_DIR):
        return list_data
    for name in sorted(os.listdir(SKINS_DIR)):
        if name.startswith("."):
            continue
        skin_dir = os.path.join(SKINS_DIR, name)
        if not os.path.isdir(skin_dir):
            continue
        skin_id = name
        entry = {"id": skin_id, "name": skin_id, "previewUrl": "", "backgroundUrl": ""}
        skin_json_path = os.path.join(skin_dir, "skin.json")
        if os.path.isfile(skin_json_path):
            try:
                with open(skin_json_path, "r", encoding="utf-8") as f:
                    manifest = json.load(f)
                prefix = "skins/" + skin_id + "/"
                entry["name"] = manifest.get("name", skin_id)
                entry["previewUrl"] = (prefix + manifest["previewUrl"]) if manifest.get("previewUrl") else ""
                entry["backgroundUrl"] = (prefix + manifest["backgroundUrl"]) if manifest.get("backgroundUrl") else ""
                entry["skinManifestUrl"] = prefix + "skin.json"
                if os.path.isfile(os.path.join(skin_dir, "intro.json")):
                    entry["introUrl"] = prefix + "intro.json"
                if manifest.get("intro"):
                    entry["intro"] = manifest["intro"]
                if manifest.get("keyAssets"):
                    entry["keyAssets"] = manifest["keyAssets"]
            except Exception:
                pass
        if not entry["previewUrl"] and os.path.isfile(os.path.join(skin_dir, "preview.png")):
            entry["previewUrl"] = "skins/" + skin_id + "/preview.png"
        if not entry["backgroundUrl"] and os.path.isfile(os.path.join(skin_dir, "background.png")):
            entry["backgroundUrl"] = "skins/" + skin_id + "/background.png"
        list_data.append(entry)
    return list_data

def main():
    ensure_skins_white()
    list_data = collect_all_skins()
    list_path = os.path.join(SKINS_DIR, "list.json")
    with open(list_path, "w", encoding="utf-8") as f:
        json.dump(list_data, f, ensure_ascii=False, indent=2)
    print("已生成 skins/list.json（共 %d 个皮肤）" % len(list_data))
    return 0

if __name__ == "__main__":
    exit(main())
