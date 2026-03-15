# 萌创匠盒输入法 - Android 应用

本目录为萌创匠盒输入法的 **Android 应用源码**，与仓库中的 `wordlist-public/`（词库）、`skins/`（皮肤）一起构成完整开源项目。欢迎修 bug、做功能、提 PR，大家一起把输入法做得更好。

## 环境要求

- Android Studio 或兼容的 IDE（推荐最新稳定版）
- JDK 17+
- Android SDK（项目配置见 `build.gradle.kts` / `app/build.gradle.kts`）

## 构建与运行

1. 用 Android Studio 打开本目录（`android/`），等待 Gradle 同步完成。
2. 若缺少 `local.properties`，在项目根目录创建该文件，内容为：
   ```properties
   sdk.dir=你的Android SDK路径
   ```
3. 连接设备或启动模拟器，点击 Run 编译并安装 Debug 包。

命令行构建（可选）：
```bash
./gradlew assembleDebug
```
产出在 `app/build/outputs/apk/debug/`。

## 与仓库其他部分的关系

- **词库**：应用默认从本仓库的 `wordlist-public/wordlist.txt`（GitHub raw）拉取远程词库，无需改代码即可享受社区更新的词库。
- **皮肤**：应用默认从本仓库的 `skins/list.json` 及对应资源拉取皮肤列表与皮肤包，社区在仓库中更新 `skins/` 后，用户即可在应用内看到并下载新皮肤。

词库与皮肤的源文件与构建脚本均在仓库根目录的 `wordlist-public/`、`skins/` 及根目录的 `build_skins.py`，修改后提交 PR 即可参与维护。

## 参与开发

- 修 bug、提需求：欢迎开 Issue。
- 改代码、做功能：Fork 本仓库，在 `android/` 下修改后发起 Pull Request 到主仓库。
- 代码风格：遵循项目现有 Kotlin/Android 风格，保持可读性即可。

感谢每一位贡献者。
