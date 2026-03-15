# -*- coding: utf-8 -*-
"""
公共词库构建脚本：合并所有 wordlist_src*.txt，生成 wordlist.txt、wordlist.js。
供输入法应用通过 URL 拉取。运行：python build.py
"""
import os
import re
import json
import glob

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_GLOB = "wordlist_src*.txt"

def to_key(parts):
    key = "".join(re.sub(r"[1-5]$", "", p.strip()) for p in parts).lower()
    key = key.replace("ü", "v").replace("u:", "v")
    for a, b in [("á", "a"), ("à", "a"), ("ā", "a"), ("ǎ", "a"), ("é", "e"), ("è", "e"), ("ē", "e"), ("ě", "e"),
                 ("í", "i"), ("ì", "i"), ("ī", "i"), ("ǐ", "i"), ("ó", "o"), ("ò", "o"), ("ō", "o"), ("ǒ", "o"),
                 ("ú", "u"), ("ù", "u"), ("ū", "u"), ("ǔ", "u")]:
        key = key.replace(a, b)
    return key

CATEGORY_RE = re.compile(r"^\s*#\s*===\s*(.+?)\s*===\s*$")

def parse_src(path):
    """解析词库源文件，返回 (扁平条目列表, 分类列表)。"""
    entries = []
    categories = []
    current_cat = None
    seen = set()
    if not os.path.isfile(path):
        return entries, categories
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line_stripped = line.strip()
            m = CATEGORY_RE.match(line_stripped)
            if m:
                cat_name = m.group(1).strip()
                current_cat = {"id": cat_name, "name": cat_name, "lines": []}
                categories.append(current_cat)
                continue
            if not line_stripped or line_stripped.startswith("#") or line_stripped == "```":
                continue
            parts = line_stripped.split()
            if len(parts) < 2:
                continue
            word = parts[-1]
            py_parts = [p for p in parts[:-1] if not re.search(r"[\u4e00-\u9fff]", p)]
            if not py_parts:
                continue
            key = to_key(py_parts)
            if not key:
                continue
            if (key, word) in seen:
                continue
            seen.add((key, word))
            entry_line = f"{key} {word}"
            entries.append((key, word))
            if current_cat is not None:
                current_cat["lines"].append(entry_line)
    return entries, categories

def merge_all_src(script_dir):
    """合并所有 wordlist_src*.txt，去重后返回 (扁平条目列表, 分类列表)。"""
    pattern = os.path.join(script_dir, SRC_GLOB)
    paths = sorted(glob.glob(pattern))
    all_entries = []
    all_categories = []
    seen = set()
    for path in paths:
        entries, categories = parse_src(path)
        for k, w in entries:
            if (k, w) not in seen:
                seen.add((k, w))
                all_entries.append((k, w))
        all_categories.extend(categories)
    return all_entries, all_categories

def main():
    entries, categories = merge_all_src(SCRIPT_DIR)
    txt_path = os.path.join(SCRIPT_DIR, "wordlist.txt")
    with open(txt_path, "w", encoding="utf-8") as f:
        for key, word in entries:
            f.write(f"{key} {word}\n")
    js_path = os.path.join(SCRIPT_DIR, "wordlist.js")
    with open(js_path, "w", encoding="utf-8") as f:
        f.write("// 公共词库（由 build.py 生成，含分类供输入法按类推荐）\n")
        data = {
            "lines": [f"{k} {v}" for k, v in entries],
            "categories": categories,
        }
        f.write("var REMOTE_WORDLIST = " + json.dumps(data, ensure_ascii=False, indent=2) + ";\n")
    print("已生成 %d 条词库（%d 个分类）-> wordlist.txt、wordlist.js" % (len(entries), len(categories)))
    return 0

if __name__ == "__main__":
    exit(main())
