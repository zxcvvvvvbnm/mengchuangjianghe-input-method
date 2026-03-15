# 语音转文字 1.0（长按空格说话出字）

## 一步：下载模型

在项目根目录（mengbox_input_android）执行：

```powershell
.\scripts\download_whisper_model.ps1 -Model tiny
```

会将 `ggml-tiny.bin` 下载到 `app/src/main/assets/`。若网络较慢可手动从 [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin) 下载后放入该目录。

## 二步：构建并运行

依赖的 ASR 库（mengchuangjianghe-asr）已默认开启 `useWhisper=true`，构建时会自动编入 whisper。直接：

- 用 Android Studio 打开 **mengbox_input_android**，运行到真机/模拟器；或
- 命令行：`.\gradlew :app:assembleDebug`，安装生成的 APK。

## 使用

在任意输入框调出本输入法，**长按空格键**开始录音，松手后即可看到识别文字上屏。
