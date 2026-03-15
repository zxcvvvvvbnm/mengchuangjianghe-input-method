# 萌创匠盒输入法 (mengchuangjianghe-input-method)

开源安卓输入法，专注高效输入体验与社区共建，由个人开发者「萌创匠盒」维护。  
**词库、皮肤、Android 应用源码** 全部开源在本仓库，大家既可以维护词库与皮肤，也可以直接参与改应用、修 bug、做功能——一起把项目做得更完善。

## 仓库说明

本仓库包含三部分，均可独立贡献：

### Android 应用源码：`android/`

- 萌创匠盒输入法的 **Android 工程**，用 Android Studio 打开 `android/` 即可编译、运行、调试。
- 应用默认从本仓库拉取**词库**（`wordlist-public/wordlist.txt`）与**皮肤**（`skins/list.json` 等），无需改配置即可使用社区最新词库与皮肤。
- 欢迎修 bug、提需求、做功能，Fork 后改代码发起 Pull Request 即可。

详见 **[android/README.md](android/README.md)**。

---

### 词库目录：`wordlist-public/`

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

---

## 总结

| 目录 | 内容 | 谁可以贡献 |
|------|------|------------|
| **android/** | 输入法 APK 源码 | 修 bug、做功能、提 PR |
| **wordlist-public/** | 词库源文件与构建 | 加词条、补分类、运行 `build.py` 后 PR |
| **skins/** | 皮肤资源与列表 | 做新皮肤、按教程运行 `build_skin.py` 与 `build_skins.py` 后 PR |

只要有人贡献词库、皮肤或代码，合并后大家拉取/更新即可享受到——项目会越做越好。

## License

Apache-2.0
