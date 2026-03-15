# RIME（中州韵）引擎集成方案

## 为什么选 RIME

- **体积小**：引擎 + 词库整体可控制在约 3MB 以内，打包进 APK 可接受。
- **全本地**：无需联网，算法与词库都在本地，隐私与体验都好。
- **算法成熟**：RIME 在桌面/移动端广泛使用，拼音、简繁、多种方案都支持。
- **开源**：BSD 协议，可商用、可改、可打包进应用。

## 现状与可选做法

- **没有现成 AAR**：RIME 核心是 **librime**（C++），Android 上同文（Trime）是「完整 IME 应用」，没有单独提供「仅引擎」的 Maven 依赖。
- **同文 Trime**：GitHub [osfans/trime](https://github.com/osfans/trime) 通过 JNI 调用 librime，引擎和词库都打在同一个 app 里。
- **可行方向**：在我们工程里**嵌入 librime**（或复用 Trime 的 native + JNI 层），把当前 26 键的拼音候选改为由 RIME 计算，词库和方案文件随 APK 一起下发，本地计算。

## 集成思路（概要）

1. **Native 层**
   - 引入 **librime**（或从 Trime 的 submodule 构建）：用 NDK 编译出 `librime.so`（可按 ABI 裁剪）。
   - 在 Trime 的 `app/src/main/jni` 里已有对 librime 的封装，可参考或抽离成我们自己的 JNI 模块。

2. **数据与资源**
   - 将 RIME 的**方案与词库**（如 拼音方案 + 对应 dict）放到我们 `app/src/main/assets`（或下载到应用目录后由引擎加载）。
   - 体积可控制在约 2–3MB（仅拼音+常用词库时），整体算法+库+资源可控制在约 3MB 量级。

3. **应用层**
   - 保留现有 **MengBoxInputMethodService** 与 26 键 UI，仅把「拼音串 → 候选列表」改为调用 RIME 引擎（通过 JNI）。
   - 引擎在 IME 启动或首次使用拼音时初始化一次，之后本地计算，无需网络。

## 实施步骤（建议顺序）

| 步骤 | 内容 |
|------|------|
| 1 | 在工程中启用 NDK，添加 CMake/ndk-build，能编译 C++。 |
| 2 | 将 librime（及依赖：如 opencc、yaml-cpp 等）以 submodule 或拷贝方式加入，并编写/复用 Android 构建脚本。 |
| 3 | 复用或重写 Trime 的 JNI 封装：初始化 RIME、输入键/拼音、取候选、上屏。 |
| 4 | 在 Kotlin 层封装「RimeEngine」：init(context/assets)、getCandidates(pinyin)、commit(candidate) 等，与现有 `PinyinDict` 接口对齐或替换。 |
| 5 | 将 RIME 方案与词库放入 assets 或指定目录，应用启动时解压/拷贝到引擎可读路径并加载。 |
| 6 | 26 键拼音逻辑改为调用 RimeEngine，测试候选、上屏、退格等。 |

## 体积与依赖说明

- **librime 本体**：约 1–2MB（so，视 ABI 与 strip 情况）。
- **词库/方案**：仅拼音+常用词约 1–2MB；若加五笔、双拼等会再增。
- **整体**：算法+库+资源控制在约 3MB 是合理预期，APK 增加量可接受。
- **依赖**：Trime 使用 Boost、OpenCC、LevelDB、yaml-cpp 等，我们若只做拼音可考虑最小依赖（以 Trime 的 CMake/Android.mk 为参考裁剪）。

## 国内访问与构建

- **源码**：librime、Trime 均在 **GitHub**，国内一般可访问；若 clone 慢可用镜像或代理。
- **构建**：全部本地 NDK 编译，**不需要** Google 服务或外网 API；词库随 APK 打包，用户使用完全离线。

## 小结

- RIME 适合「打包进 APK、本地计算、体积约 3MB、算法好」的需求。
- 需要做的是：**把 librime + JNI + 词库接入当前工程**，而不是加一行 Gradle 依赖。

## 已完成的集成（本工程）

- **NDK + CMake**：已启用，编译 `rime_jni`（JNI 桥接，dlopen 加载 librime.so）。
- **JNI 桥接**：`app/src/main/jni/RimeBridge.cpp`，提供 init、createSession、setInput、getCandidates、selectCandidate、getCommit、getPreedit。
- **Kotlin 层**：`RimeEngine` 在后台 init，解压 `assets/native/abi/librime.so` 与 `assets/rime_shared` 到 filesDir，优先供 `PinyinDict.getCandidates` 使用。
- **克隆与资源**：详见 [CLONE_RIME.md](CLONE_RIME.md)。本机执行 `git clone` 拉取 librime 源码；librime.so 与方案词库按该文档放入 assets 后即可运行。
