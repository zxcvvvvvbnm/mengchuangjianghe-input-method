# -*- coding: utf-8 -*-
"""归一化 pinyin_words.txt：带声调数字/声调符号的拼音转为无调格式，并去重。"""
import re
import sys
import unicodedata

def strip_accents(s):
    """去掉拉丁字母上的声调符号，如 yìng -> ying。"""
    n = unicodedata.normalize('NFD', s)
    return ''.join(c for c in n if not unicodedata.combining(c))

def normalize_pinyin(py_part):
    """把 'zhe4 me' 或 'de5' 或 'yìng fu' 转为 'zheme' / 'de' / 'yingfu'。"""
    if not py_part or not py_part.strip():
        return py_part
    parts = py_part.strip().split()
    out = []
    for p in parts:
        # 去掉末尾数字 1-5，再去掉声调符号
        p = re.sub(r'[1-5]$', '', p)
        p = strip_accents(p).lower()
        if p:
            out.append(p)
    return ''.join(out)

def process_line(line):
    """返回 (归一化后的整行, 该行的 key 用于去重) 或 (原行, None) 表示保留不参与去重。"""
    t = line.rstrip('\n\r')
    raw = t.strip()
    if not raw or raw.startswith('#'):
        return (t, None)
    parts = raw.split()
    if len(parts) < 2:
        return (t, None)
    # 最后一段是词，前面所有段是拼音（可能 "zhe4 me" 或 "zheme"）
    word_part = parts[-1]
    py_parts = parts[:-1]
    py_part = ' '.join(py_parts)
    if not word_part or not py_part:
        return (t, None)
    # 若拼音部分含数字 1-5 或含非 ASCII（声调符号），则归一化
    if re.search(r'[1-5](?:\s|$)', py_part) or any(ord(c) > 127 for c in py_part):
        new_py = normalize_pinyin(py_part)
        if new_py:
            new_line = new_py + ' ' + word_part
            return (new_line, (new_py, word_part))
    # 无声调：多段拼音（如 "huo lu 活路"）合并为 "huolu 活路"
    py_key = ''.join(p.lower() for p in py_parts)
    if not py_key:
        return (t, None)
    if len(py_parts) > 1:
        new_line = py_key + ' ' + word_part
        return (new_line, (py_key, word_part))
    return (t, (py_key, word_part))

def main():
    path = 'app/src/main/assets/pinyin_words.txt'
    if len(sys.argv) > 1:
        path = sys.argv[1]
    seen = set()
    out_lines = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            new_line, key = process_line(line)
            if key is not None:
                if key in seen:
                    continue  # 去重：同一 (拼音, 词) 只保留第一次
                seen.add(key)
            out_lines.append(new_line if new_line else line.rstrip('\n\r'))
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out_lines))
        if out_lines and not out_lines[-1].endswith('\n'):
            f.write('\n')
    print('Done: normalized and deduped,', len(out_lines), 'lines')

if __name__ == '__main__':
    main()
