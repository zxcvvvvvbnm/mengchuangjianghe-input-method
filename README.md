# 萌创匠盒输入法 (mengchuangjianghe-input-method)

开源安卓输入法，专注高效输入体验与社区共建，由个人开发者「萌创匠盒」维护。

## 仓库说明

本仓库包含**输入法公共词库**，供应用通过 URL 拉取使用。欢迎所有人参与词库维护与贡献。

### 词库目录：`wordlist-public/`

- **词库源文件**：`wordlist_src*.txt`（按分类分文件，便于维护）
- **构建脚本**：在 `wordlist-public/` 目录下运行 `python build.py`，会生成 `wordlist.txt`、`wordlist.js`
- **贡献方式**：编辑 `wordlist-public/` 下的任意 `wordlist_src*.txt`，按「拼音 词」格式添加词条，运行 `build.py` 后提交 PR

详细格式说明、文件列表与注意事项见 **[wordlist-public/README.md](wordlist-public/README.md)**。

## License

Apache-2.0
