# -*- coding: utf-8 -*-
"""校验 abbr_weighted.txt：每条的首字母键是否与词语真实拼音首字母一致。

用法：python validate_abbr_weighted.py [路径]
依赖：pip install pypinyin

例如 jx 学习 -> 学习=xué xí -> xx，与 jx 不符，会报错；
     gx 历史 -> 历史=lì shǐ -> ls，与 gx 不符，会报错。
"""
import sys
import os

try:
    from pypinyin import lazy_pinyin, Style
except ImportError:
    print("请先安装: pip install pypinyin")
    sys.exit(1)

DEFAULT_PATH = "app/src/main/assets/abbr_weighted.txt"


def word_to_abbr(word: str) -> str:
    """词语转首字母键（两字取两字母，多字取每字拼音首字母前两位等，与输入法一致取「每音节首字母」）。"""
    if not word:
        return ""
    # 每字取拼音首字母（如 继续 -> j,x -> jx）
    letters = lazy_pinyin(word, style=Style.FIRST_LETTER)
    return "".join(l for l in letters if l).lower()[:2]


def main():
    path = DEFAULT_PATH
    if len(sys.argv) > 1:
        path = sys.argv[1]
    if not os.path.isfile(path):
        print("文件不存在:", path)
        return
    errors = []
    with open(path, "r", encoding="utf-8") as f:
        for num, line in enumerate(f, 1):
            t = line.strip()
            if not t or t.startswith("#"):
                continue
            parts = t.split()
            if len(parts) < 3:
                continue
            key = parts[0].lower()
            if len(key) != 2:
                continue
            word = parts[1]
            if not word or any(ord(c) < 0x4E00 or ord(c) > 0x9FFF for c in word):
                continue
            expected = word_to_abbr(word)
            if expected != key:
                errors.append((num, key, word, expected, " ".join(parts[2:])))
    if not errors:
        print("校验通过，未发现首字母与词语不匹配的条目。")
        return
    out_msg = "发现 %d 条首字母与词语不匹配（键应为该词真实拼音首字母）：\n\n" % len(errors)
    for num, key, word, expected, rest in errors:
        out_msg += "  行 %d: %s %s ...  -> 应为 %s（当前键 %s）\n" % (num, key, word, expected, key)
    out_msg += "\n可据此逐条修正或从词库中删除错误条目。"
    print(out_msg)
    # 同时写入文件，避免控制台编码乱码，便于逐条修正
    report_path = path.replace(".txt", "_validate_errors.txt") if path.endswith(".txt") else path + "_validate_errors.txt"
    with open(report_path, "w", encoding="utf-8") as rf:
        rf.write(out_msg)
    print("\n已写入: %s" % report_path)
    sys.exit(1)


if __name__ == "__main__":
    main()
