# 萌创匠盒输入法 (mengchuangjianghe-input-method)

开源安卓输入法，专注高效输入体验与社区共建。  
**词库、皮肤、Android 应用源码** 全部开源在本仓库；支持 **语音转文字**（可对接本组织 [mengchuangjianghe-asr](https://github.com/Mencaje/mengchuangjianghe-asr) 或其它引擎）。欢迎一起维护词库、修 bug、做功能。

**仓库地址**：[https://github.com/Mencaje/mengchuangjianghe-input-method](https://github.com/Mencaje/mengchuangjianghe-input-method)

---

## 要做成什么样的输入法

- **高效输入**：26 键 / 9 键 / 手写 / 笔画，统一词库与联想；默认从本仓库拉取词库与皮肤，社区更新即生效。
- **语音输入**：长按空格键触发语音，可对接本组织纯本地 [mengchuangjianghe-asr](https://github.com/Mencaje/mengchuangjianghe-asr)（推荐，离线、不依赖系统语音服务），也可对接其它引擎。
- **社区共建**：词库、皮肤、应用代码均在本仓库，Fork 后改词库/皮肤/代码，PR 合并即可让所有人用上。

---

## 语音转文字怎么对接

### 推荐：对接本组织 [mengchuangjianghe-asr](https://github.com/Mencaje/mengchuangjianghe-asr)

应用已依赖 `com.github.Mencaje:mengchuangjianghe-asr:android:1.3.0`。跑通「说话出字」两步：  
1）在 `android/app/src/main/assets/` 放入 `ggml-tiny.bin`（可运行 `android/scripts/download_whisper_model.ps1 -Model tiny` 或从 [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin) 下载）；  
2）构建时 ASR 库会拉取 whisper，首次较慢，之后长按空格说话、松手即可出字。

**常见问题**（仍走系统识别、识别慢、结果为空、模型加载 ANR 等）及解决办法见 ASR 仓库：[常见问题与解决方案](https://github.com/Mencaje/mengchuangjianghe-asr#%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98%E4%B8%8E%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88)。

### 对接其它语音引擎

若使用系统 `SpeechRecognizer` 或其它 SDK，只需在长按空格后的识别回调里将得到的文本交给当前上屏接口即可。

---

## 仓库说明

### Android 应用源码：`android/`

- 用 Android Studio 打开 `android/` 即可编译、运行；默认从本仓库拉取词库与皮肤。
- 详见 **[android/README.md](android/README.md)**。

### 词库目录：`wordlist-public/`

- **词库源文件**：`wordlist_src*.txt`；在 `wordlist-public/` 下运行 `python build.py` 生成 `wordlist.txt`、`wordlist.js`。
- **贡献**：编辑 `wordlist_src*.txt`，按「拼音 词」格式添加，运行 `build.py` 后 PR。详见 **[wordlist-public/README.md](wordlist-public/README.md)**。

### 皮肤目录：`skins/`

- **一个皮肤一个子文件夹**，文件夹名即皮肤名；内置 `white` 默认白皮。
- **构建脚本**：在仓库根目录运行 `python build_skins.py`，会生成 `skins/list.json` 与 `skins/white/` 的预览、背景图。
- **贡献方式**：在 `skins/` 下新建皮肤文件夹，按 `skins/皮肤制作教程.txt` 准备图片与介绍，在皮肤文件夹内运行 `build_skin.py`，再在根目录运行 `build_skins.py` 后提交 PR。

详细制作步骤、按键命名与尺寸说明见 **[skins/README.md](skins/README.md)** 与 **skins/皮肤制作教程.txt**。

---

### 词库：一起优化、让词库更丰富

应用默认拉取本仓库 `wordlist-public/wordlist.txt`。欢迎大家编辑 `wordlist_src*.txt` 加词条、补分类，运行 `build.py` 后提交 PR，合并后所有人即可获得更丰富的候选。

---

## 总结

| 目录 | 内容 | 谁可以贡献 |
|------|------|------------|
| **android/** | 输入法 APK 源码（含语音对接） | 修 bug、做功能、提 PR |
| **wordlist-public/** | 词库源文件与构建 | 加词条、补分类、运行 `build.py` 后 PR |
| **skins/** | 皮肤资源与列表 | 做新皮肤、按教程运行脚本后 PR |

只要有人贡献词库、皮肤或代码，合并后大家拉取/更新即可享受到——项目会越做越好。

## License

Apache-2.0
