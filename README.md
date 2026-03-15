# 萌创匠盒输入法 (mengchuangjianghe-input-method)

开源安卓输入法，专注高效输入体验与社区共建，由个人开发者「萌创匠盒」维护。

## 仓库说明

本仓库包含**公共词库**与**皮肤资源**，均供应用通过 URL 拉取使用。欢迎所有人参与词库与皮肤的制作、维护与贡献。

### 词库目录：`wordlist-public/`

- **词库源文件**：`wordlist_src*.txt`（按分类分文件，便于维护）
- **构建脚本**：在 `wordlist-public/` 目录下运行 `python build.py`，会生成 `wordlist.txt`、`wordlist.js`
- **贡献方式**：编辑 `wordlist-public/` 下的任意 `wordlist_src*.txt`，按「拼音 词」格式添加词条，运行 `build.py` 后提交 PR

详细格式说明、文件列表与注意事项见 **[wordlist-public/README.md](wordlist-public/README.md)**。

### 皮肤目录：`skins/`

- **一个皮肤一个子文件夹**，文件夹名即皮肤名；内置 `white` 默认白皮。
- **构建脚本**：在仓库根目录运行 `python build_skins.py`，会生成 `skins/list.json` 与 `skins/white/` 的预览、背景图。
- **贡献方式**：在 `skins/` 下新建皮肤文件夹，按 `skins/皮肤制作教程.txt` 准备图片与介绍，在皮肤文件夹内运行 `build_skin.py`，再在根目录运行 `build_skins.py` 后提交 PR。

详细制作步骤、按键命名与尺寸说明见 **[skins/README.md](skins/README.md)** 与 **skins/皮肤制作教程.txt**。

## License

Apache-2.0
