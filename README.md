# 萌创匠盒输入法（孟创江河）· 开源安卓输入法

**仓库**：[github.com/Mencaje/mengchuangjianghe-input-method](https://github.com/Mencaje/mengchuangjianghe-input-method)  
**协议**：Apache License 2.0（见根目录 [`LICENSE`](LICENSE)）

面向 **社区共建**：欢迎改词库、修 Bug、加功能、提 PR。单靠个人维护很难做成「大家都好用」的输入法，开源就是为了让大家一起把它养大。

---

## 如何把说明同步到 GitHub（很重要）

文档和代码都在本仓库里；**只有你执行推送后**，GitHub 网页上才会出现最新的 `README.md`、最新代码。

在项目根目录（含 `.git` 的那一层）打开终端，执行：

```bash
git pull origin main
git push origin main
```

若提示登录，用 GitHub 账号或 **Personal Access Token**（Settings → Developer settings → Tokens）作为密码。  
如果你从未推送过含 `README.md` 的那次提交，本地可先执行 `git status` 确认有未推送提交，再 `git push`。

---

## 克隆后怎么用 Android Studio 打开

```bash
git clone https://github.com/Mencaje/mengchuangjianghe-input-method.git
cd mengchuangjianghe-input-method
```

用 **Android Studio** 打开 **`mengbox_input_android`** 文件夹（里面有 `settings.gradle.kts`、`app/`）。  
不要只打开仓库根目录下的某个子 `app`，否则 Gradle 找不到与 **`mengchuangjianghe-asr`** 的 `includeBuild` 联动。

要求：JDK 17、Android SDK；首次需能访问 Google Maven（或自行配置镜像）。

---

## 仓库里主要有什么

| 路径 | 内容 |
|------|------|
| **`mengbox_input_android/`** | 输入法主工程：UI、词库、JNI、Rime 接入等 |
| **`mengchuangjianghe-asr/`** | 语音相关（长按空格等），与主工程并列，由 Gradle 关联 |
| **`wordlist-public/`** | 公共词库源文件与构建脚本 |
| 其他如 `repo_deploy/`、`萌创匠盒输入法/`、`asr_deploy/` | 历史或部署参考，可按需浏览 |

---

## 目前已实现的能力（仓库现状 · 依据 `mengbox_input_android` 源码与 `assets` 实测）

以下为 **当前主线工程真实行为**：实现方式、数据规模、协作时易踩坑一并写明，便于贡献者对齐预期。

---

### 1. 应用壳与输入法服务

| 组件 | 路径 / 类 | 作用 |
|------|-----------|------|
| 桌面引导 | `MainActivity.kt` | 引导用户「启用输入法 → 切换为本输入法 → 选择默认键盘类型（9/26/手写/笔画）」；未完成或系统切走输入法时会回到引导。 |
| IME 服务 | `MengBoxInputMethodService.kt` | `InputMethodService`：按偏好加载对应键盘布局，`onCreate` 中初始化 `PinyinDict.init`、`RimeEngine.init`。 |
| 权限 | `AndroidManifest.xml` | `INTERNET`（远程词库/皮肤）、`RECORD_AUDIO`（语音）、`VIBRATE`。 |
| 语音权限页 | `VoicePermissionRequestActivity` | 透明 Activity，用于从输入法内拉起麦克风授权。 |

逻辑要点：**键盘类型**存在 `SharedPreferences`（`MainActivity` 定义的 `KEY_DEFAULT_KEYBOARD_TYPE` 等）；IME 侧读取同一偏好决定 inflate 哪套 layout（`input_keyboard_9key` / `26key` / `handwrite` / `stroke`）。

---

### 2. 四种键盘：已实现到什么程度

#### 26 键

- **中文**：拼音缓冲 `StringBuilder`，`setComposingText` 显示组字；候选来自 `PinyinDict.getPinyinCandidateBar` → 合并 **Rime（若就绪）**、本地音节切分、首字母联想、`UserMemory`。
- **英文**：切换 `isEnglishMode26`，字母直接 `commitText`；Shift 控制大小写。
- **候选栏**：横向 Chip；右侧展开键打开「单字扩展网格」；**Rime 就绪时**另有上一页/下一页（调用 `RimeEngine.flipCandidatePage`），选词可走 `pickCandidateOnCurrentPage` 或按候选文本 **legacy 上屏**（单音节 + 本地 bigram 重排时会关闭与 Rime 菜单下标对齐，避免错字）。
- **空格**：短按在无英文模式下优先尝试 **Rime 首选**（与菜单一致）；否则合并列表首词。
- **品牌与特殊 App**：如对「萌创匠盒」图标/文字上屏在部分包名下有专门分支（避免 ImageSpan 显示为 OBJ）。

#### 9 键（T9）

- **逻辑**：`T9Helper` — 数字串 ↔ 拼音首字母规则与常见 T9 一致；候选词来自 **`PinyinDict.getAllWords()`** 建索引 + 前缀匹配。
- **展示**：候选栏上方展示「当前数字对应的默认字母串」逻辑（代码注释：与 YiiGuxing/T9Search 思路一致）。

#### 手写 / 笔画

- **布局**：各自 `input_keyboard_handwrite`、`input_keyboard_stroke`；与 **统一候选条**（`candidate_bar_unified` / 内嵌区域）共用剪贴板、换键盘浮层等。
- **数据**：同样走 **同一套 `PinyinDict` 词库**（类注释写明九宫格与 26 键等共用）。

---

### 3. `PinyinDict`：拼音解码的核心逻辑（必读）

运行时在 **`PinyinDict.init`** 后台线程：

1. **字表**：遍历 CJK 平面一段 Unicode，用 **Pinyin4j** 生成「拼音 → 汉字列表」，得到 **`validSyllables`**（用于音节切分）和 **`pinyinToChars`**。
2. **词表**：读 `assets` 多个 **「全小写拼音 + 空格 + 词语」** 文本，合并进 `pinyinToWords`；同一拼音下多词 **保留列表顺序后再 distinct**。
3. **音节切分**：前向最长匹配 **`segmentPinyin`**，供整串拼音组词。
4. **组词**：`getCandidatesFromSegments` — 多音节优先词库精确词；否则汉字笛卡尔组合（有上限）；末尾不完整音节时用语库 **前缀拼音** 补候选。
5. **首字母联想**：`abbrToWords` / `abbr_words.txt`、`abbr_weighted.txt`（带权重排序）。
6. **联想**：`buildAssociationMap` 从全部词表里扫 **相邻字 bigram**、**同首字词条** → `charToNextChars`、`charToPhrases`。
7. **接续优先**：`boostCandidatesWithAssociation` — 上一字已知时把 bigram 里下一字前置（**Rime 就绪且单音节缓冲**时也会叠在合并列表上并重排，见源码）。
8. **字频**：`char_frequency_weights.txt`（字 → 整数权重）参与单字排序、整词粗权重。
9. **远程**：`RemoteWordListFetcher` HTTP GET 静态 `wordlist.txt`，解析后 **`PinyinDict.setRemoteWordList`** 合并（IME `onCreate` 里若配置了 URL 会触发拉取）。
10. **可选 Rime**：`getPinyinCandidateBar` 在 **`RimeEngine.isReady()`** 时先 `ensurePinyinSynced`，再 **`mergeCandidateListsPreferPrimary(rime, legacy)`**。

---

### 4. 词库与统计（当前仓库内实测）

下列统计为 **`app/src/main/assets` 下文本**：去掉空行与 `#` 注释后的 **有效行数**（格式均为「左侧拼音 / 键 + 空格 + 右侧内容」，便于人工核对）。

| 文件 | 有效行数（约） | 在 `loadWordList` 中的角色 |
|------|----------------|---------------------------|
| `pinyin_words.txt` | **8759** | 主词库，最先加载 |
| `wordlist_daily.txt` | **17089** | 日常用语合并 |
| `pinyin_words_food.txt` | **6541** | 饮食类 |
| `pinyin_append_temp.txt` | **2815** | 增补缓冲 |
| `abbr_weighted.txt` | **2842** | 两字母键 + 词 + **权重** |
| `char_frequency_weights.txt` | **5538** | 单字权重（制表符分隔） |
| 除主库外的合并词库（car、诗词、国家、modern、成语、天文、daily、增补、extended 等） | 分项见下表 | `mergeWordListFromAsset` **按文件名顺序**依次并入同一 `MutableMap` |

**合并词库分项有效行（约）**：`car_brands.txt` **320**，`pinyin_words_poetry.txt` **692**，`pinyin_words_countries.txt` **195**，`pinyin_words_modern.txt` **244**，`pinyin_append_temp.txt` **2815**，`wordlist_idioms.txt` **216**，`wordlist_astronomy.txt` **2383**，`wordlist_daily.txt` **17089**，`pinyin_words_extended_batch_a.txt` **1428**。  
与主库 **`pinyin_words.txt` 8759 行**相加，「`拼音␣词`」格式行合计 **约 40682 行**（同一拼音出现在多文件时会 **追加到同一 key**，故内存中 **词条数 ≠ 行数简单相加**）。

**首字母与字频（格式不同，单独计数）**：`abbr_words.txt` **676**，`abbr_weighted.txt` **2842**，`char_frequency_weights.txt` **5538**。

**未参与运行时加载、仅作脚本/素材的示例**：`wordlist_poetry_idioms_src.txt`、`gen_countries_wordlist.py`、`strip_food_brands.py` 等——**不在 `PinyinDict.loadWordList` 的白名单里**，除非你再改 Kotlin 或先生成进某个已加载文件。

**Rime 侧语言数据（与本地 TXT 并列部署）**：

- `rime_shared/luna_pinyin.dict.yaml`：**70839** 行（朙月拼音词典源）。
- `rime_shared/essay.txt`：**约 44 万行**（八股文语言模型文本；文件体积约 **5.6MB** 量级）。
- 另有 `stroke.dict.yaml`、`symbols.yaml`、`default.yaml` 等 plum 体系文件，首启复制到应用私有目录由 librime 维护/编译。

---

### 5. Rime（librime）接入 · 已实现行为

| 能力 | 说明 |
|------|------|
| JNI | `app/src/main/cpp/rime_jni.cpp`：`HAVE_LIBRIME` 链接预编译库；否则 **STUB** 返回空候选。 |
| Kotlin | `RimeJni.kt` / `RimeEngine.kt`：部署 `assets/rime_shared` → `filesDir`，`nativeInit`、`nativeUpdateAndGetCandidates`、`nativeGetCurrentCandidates`、`flipCandidatePage`、`pickCandidateOnCurrentPage`、`getPreedit`、`getInput`、`setOption`、`clearComposition`。 |
| 默认方案 | JNI 内 `select_schema(..., "luna_pinyin")`；`default.yaml` 已裁剪为仅列出 `luna_pinyin`；**schema 已去掉 `grammar:/hant`**，避免缺省 grammar 文件报错。 |
| 简体开关 | 初始化后 `set_option("simplification", true)`（Kotlin + C++ 侧均有）。 |

**若未放置 `jniLibs/<abi>/librime.so`**：编译仍通过，但 **`RimeEngine.isReady()==false`**，整句智能完全依赖上文 **本地 `PinyinDict`**。

---

### 6. 语音（ASR）

- **Gradle**：`settings.gradle.kts` 使用 **`includeBuild("../mengchuangjianghe-asr")`**，故克隆仓库后目录结构必须保留 **`mengbox_input_android` 与 `mengchuangjianghe-asr` 同级**。
- **长按空格**：`MengBoxInputMethodService` 内优先尝试 **打包在本地的 native Whisper 路径**（若 `.so` + `assets` 模型齐备）；否则走 **系统 `SpeechRecognizer`**（具体分支以源码为准）。
- **模型**：`ggml*.bin` 默认 **`.gitignore` 忽略**（体积大）；按 `scripts/README_voice.md` 自备后放入 `assets`。

---

### 7. 皮肤与远程配置

- **`RemoteSkinFetcher` / `SkinDownloader` / `SkinLoader`**：支持从 URL 拉皮肤列表与资源并在应用内展示（详见 `Skin*.kt`）。
- **远程词库 URL**：可在应用侧配置写入 `RemoteWordListFetcher.remoteWordListUrl`，默认意图可指向 **`wordlist-public/wordlist.txt`**（见 `RemoteWordListFetcher` 注释）。

---

### 8. 用户记忆 `UserMemory`

- **持久化**：`SharedPreferences`，键为拼音或上下文 key → 候选词及选用次数。
- **排序**：`mergePreferred` 把用户常选词提前；与 Rime 菜单错位时逻辑在服务层已做区分。

---

### 9. 使用与共建时「常见问题」（在已实现代码前提下）

| 现象 | 常见原因 |
|------|-----------|
| 克隆后 Gradle 失败 | 未连上 Google Maven / JitPack；或 **只打开了子目录** 导致找不到 `mengchuangjianghe-asr`。 |
| Rime 无效果 | **未放 `librime.so`** → 永远 STUB；或 **`rime_shared` 未被打进 APK**（正常应在 `assets`）。 |
| 整句不如商业输入法 | **未启用 librime** 或 **仅用本地 4 万行级合并词**；云端大模型本产品未内置。 |
| 远程词库不更新 | **未配置 URL** 或网络失败；拉取成功后需走 `setRemoteWordList` 合并逻辑。 |
| 语音无结果 | **无麦克风权限**、无 **ggml 模型**、设备无 Google 语音且本地引擎未加载。 |
| 修改 `wordlist_poetry_idioms_src.txt` 无效 | **运行时未加载该文件名**——需合并进 `pinyin_words.txt` 或已加载的某一文件或通过脚本生成。 |
| APK 体积大 | **essay + stroke + luna dict + 本地词库** 均为刻意打包的开源数据；可按需裁剪 Rime 表（自行承担兼容性）。 |

---


## 尚未实现或明显不完整的地方（诚实清单）

以下内容 **不代表永远不做**，而是当前版本读者应心里有数：

| 类别 | 说明 |
|------|------|
| **云端大模型联想** | 未内置类似商业输入法的大规模云联想 / 热搜词云；侧重本地 + 可配置远程词库。 |
| **商业级运营能力** | 无完整「皮肤商店 / 账号体系 / 付费增值」闭环；皮肤与词库偏社区文件与 URL 分发。 |
| **librime 二进制** | **仓库不强制包含** `librime.so`（避免体积与许可证分发责任）；需要贡献者自备或从合规来源放入 `jniLibs` 后构建。 |
| **极致机型适配** | 折叠屏、平板分栏、横屏游戏键盘等未宣称「全机型满分体验」。 |
| **细节功能** | 例如部分键盘上的「数字键盘」等入口可能仍为占位或简化实现，以仓库内代码为准。 |

---

## 和主流输入法相比：优点与短板

### 优点

- **源码与词库构建流程开放**：可审计、可 Fork、可定制词库与行为，适合学习与二次发行（遵守各依赖许可证）。
- **架构上同时支持「传统本地词库」与「Rime 引擎」**：长句场景可交给 Rime + essay，小众词可用本地 TXT 合并补齐。
- **隐私取向**：可不依赖云端输入（取决于你是否启用远程 URL、是否使用系统语音）；本地 Whisper 路径可减少对系统语音的依赖（需自备模型）。
- **社区扩展**：词库、皮肤、脚本均可 PR；符合「大家一起维护」的目标。

### 短板（相对搜狗 / 讯飞 / Gboard 等产品）

- **投入与数据规模**：大厂有专职团队与海量用户反馈数据；本项目依赖社区贡献，默认体验会因词库与是否启用 Rime **差异很大**。
- **智能上限**：没有默认绑定的「无限云联想」；完整拼音句子上屏体验强依赖 **是否部署 Rime `.so` + 共享数据** 与词库维护。
- **语音体验**：依赖模型大小、设备算力、麦克风权限；与深度定制云语音的产品相比，「开箱即顶尖识别率」不现实。

---

## 我们希望把它发展成什么样的输入法

短期与中期方向（欢迎认领任务）：

1. **词库共建**：持续合并 `wordlist-public` 与 `assets` 词表，建立清晰的贡献规范（格式、许可、去重规则）。
2. **Rime 一体化体验**：文档化「一键放入 jniLibs + 方案」；可选提供 CI 或脚本生成最小可运行包（在许可证允许范围内）。
3. **稳定性与无障碍**：候选栏、焦点、深色模式、TalkBack 等逐步打磨。
4. **语音**：优化本地模型下载与首次引导；可选多种引擎插件化。
5. **皮肤与主题**：完善远程皮肤协议与缓存，鼓励社区上传合规皮肤。

长期愿景：**成为「可自主掌控数据流、可深度定制」的开源中文输入法基座**——学校、企业、极客群体可以 fork 后做自己的词库与策略，而不必完全依赖闭源云端。

---

## 参与贡献

- **词库**：改 `wordlist-public/` 或 `mengbox_input_android/app/src/main/assets/` 下源文件，跑通构建脚本后提 PR。
- **功能 / Bug**：改 `mengbox_input_android` 或 `mengchuangjianghe-asr`，PR 里写清复现步骤、截图或日志。
- **大文件**：`librime.so`、Whisper 模型等若需入库，请确认许可证与体积；必要时用 Git LFS 或 Release 附件分发。

---

## 许可证与第三方

- 本仓库默认 **Apache 2.0**。
- **librime**、**Rime 方案数据**、**Whisper 模型** 等第三方各有许可证，复制与再分发前请阅读对应目录或上游说明（如 `mengbox_input_android/third_party/librime/LICENSE`、`NOTICE`）。
