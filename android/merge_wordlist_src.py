# -*- coding: utf-8 -*-
"""
将「拼音带声调数字 + 词」格式转为 pinyin_words.txt 的 key 词 格式。
用法: python merge_wordlist_src.py < wordlist_poetry_idioms_src.txt >> 追加目标
或: python merge_wordlist_src.py wordlist_poetry_idioms_src.txt
"""
import re
import sys

def to_key(parts):
    key = "".join(re.sub(r"[1-5]$", "", p.strip()) for p in parts).lower()
    key = key.replace("ü", "v").replace("u:", "v")
    for a, b in [("á","a"),("à","a"),("ā","a"),("ǎ","a"),("é","e"),("è","e"),("ē","e"),("ě","e"),
                 ("í","i"),("ì","i"),("ī","i"),("ǐ","i"),("ó","o"),("ò","o"),("ō","o"),("ǒ","o"),
                 ("ú","u"),("ù","u"),("ū","u"),("ǔ","u")]:
        key = key.replace(a, b)
    return key

def has_chinese(s):
    return bool(re.search(r"[\u4e00-\u9fff]", s))

def main():
    seen = set()
    if len(sys.argv) > 1:
        f = open(sys.argv[1], "r", encoding="utf-8")
    else:
        f = sys.stdin
    with f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("===") or line == "text":
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            # 最后一个含中文的为词，其前均为拼音
            word = None
            py_parts = []
            for i, p in enumerate(parts):
                if has_chinese(p):
                    word = "".join(parts[i:]) if i < len(parts) else p
                    py_parts = parts[:i]
                    break
            if word is None:
                # 整行无中文则最后一段当词（如 de5 的）
                word = parts[-1]
                py_parts = parts[:-1]
            if not py_parts:
                continue
            key = to_key(py_parts)
            if not key:
                continue
            entry = (key, word)
            if entry in seen:
                continue
            seen.add(entry)
            print(key, word)
    return 0

if __name__ == "__main__":
    exit(main())
