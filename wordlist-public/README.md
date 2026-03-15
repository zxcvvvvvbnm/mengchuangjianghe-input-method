# 输入法公共词库 (Wordlist Public)

本仓库为**开源公共词库**，供输入法应用通过 URL 拉取使用。欢迎所有人参与维护与贡献，一起把词库做得更好。

## 内容说明

- **词库源文件**：`wordlist_src.txt`、`wordlist_src_2_net.txt` 等（`wordlist_src*.txt`）
- **构建脚本**：`build.py` 合并所有源文件，生成 `wordlist.txt`、`wordlist.js`
- **生成文件**：运行脚本后得到 `wordlist.txt`、`wordlist.js`，可部署到静态托管（如 GitHub Pages）供应用拉取

## 如何贡献

1. **编辑词条**  
   在任意 `wordlist_src*.txt` 中按行添加，格式为：
   ```text
   拼音 词
   ```
   例如：`zhongguo 中国`、`beijing 北京`。可用 `# === 分类名 ===` 做分类，便于维护。

2. **运行构建**  
   本地安装 Python 3，在仓库根目录执行：
   ```bash
   python build.py
   ```
   会生成/更新 `wordlist.txt` 和 `wordlist.js`。

3. **提交与拉取**  
   - 提交（Commit）你修改的 `wordlist_src*.txt`，以及如需更新的 `wordlist.txt`、`wordlist.js`
   - 推送到你的 Fork，再发起 Pull Request 到上游仓库
   - 维护者合并后，所有人即可拉取到最新词库

## 文件说明

| 文件 | 说明 |
|------|------|
| `wordlist_src.txt` | 主词库源（五金、数学、师生、百家姓等） |
| `wordlist_src_2_net.txt` | 网络热梗 |
| `wordlist_src_3_work.txt` | 职场/领导员工用语 |
| `wordlist_src_4_scene.txt` | 各场景各职业 |
| `wordlist_src_5_brand.txt` | 品牌/产品名 |
| `wordlist_src_6_life.txt` | 单位时间、短句、称谓、生活高频 |
| `wordlist_src_7_profession.txt` | 各职业高频词 |
| `wordlist_src_8_couple.txt` | 情侣/日常聊天用语 |
| `wordlist_src_9_places.txt` | 省、市、区县、村地名 |
| `build.py` | 合并上述源文件，生成 wordlist.txt / wordlist.js |
| `build_places_wordlist.py` | 可选：生成/追加地名词库（需 pypinyin） |

## 注意事项

- 每行格式必须为：**拼音**（空格）**词**，拼音与词之间仅一个空格。
- 支持多文件：所有 `wordlist_src*.txt` 会被合并、去重后输出。
- 单文件体积：若托管平台限制单文件大小（如 10MB），可适当拆分源文件或控制词条量。

## 开源协议

本词库内容供输入法及相关应用使用，请遵守仓库标注的开源协议。
