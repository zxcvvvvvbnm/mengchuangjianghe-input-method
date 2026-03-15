# -*- coding: utf-8 -*-
"""首字母联想带权重词库：去重（同键同词保留权重最大）、按键与权重排序后写回。

去重规则（与豆包建议一致）：
- 唯一键 = 首字母缩写 + 词语（即 (key, word)）。
- 同一缩写+同一词出现多次时，只保留权重最高的那一条（如 NH 你好 100 与 NH 你好 90 只保留 100）。
- 不同缩写下的同一词会分别保留（如 NH 你好 100 与 WW 你好 80 两条都保留），不会误删。
"""
import sys
import os

DEFAULT_PATH = "app/src/main/assets/abbr_weighted.txt"

def main():
    path = DEFAULT_PATH
    if len(sys.argv) > 1:
        path = sys.argv[1]
    if not os.path.isfile(path):
        print("File not found:", path)
        return
    # 唯一键 = (首字母缩写, 词语)；同键只保留权重最大的一条
    seen = {}  # (key, word) -> max_weight
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
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
            w = parts[2]
            if not w.isdigit():
                continue
            weight = int(w)
            k = (key, word)
            if k not in seen or seen[k] < weight:
                seen[k] = weight
    # 先按键，同一键内按权重降序（权重高的在前），同权重再按词排序
    lines = []
    for (key, word), weight in sorted(seen.items(), key=lambda x: (x[0][0], -x[1], x[0][1])):
        lines.append(f"{key} {word} {weight}")
    header = """# 首字母联想（带权重）：每行「两字母键 词 权重」，权重越大候选越靠前
# 示例：nh 你好 100
# 粘贴后运行：python normalize_abbr_weighted.py

"""
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(header)
        f.write("\n".join(lines))
        f.write("\n")
    print("Done: deduped and sorted,", len(lines), "entries")

if __name__ == "__main__":
    main()
