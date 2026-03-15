# 输入法皮肤 (Skins)

本目录为**开源皮肤库**，供萌创匠盒输入法 APK 通过 URL 拉取。欢迎所有人参与制作与贡献，一起完善皮肤资源。

## 结构说明

- **一个皮肤 = 一个子文件夹**，文件夹名即皮肤 ID（如 `white` 为内置白色皮肤）。
- 每个皮肤文件夹内需包含：**键盘图片**（assets：background、k26、k9、handwrite、stroke）与**介绍**（intro：视频、图片、文字）。
- 根目录的 **build_skins.py**（在仓库根目录运行）会扫描本目录下所有皮肤子文件夹，生成 **list.json** 与默认 **white** 的预览/背景图，APK 拉取 list.json 后即可展示并下载皮肤。

## 如何贡献新皮肤

1. 在 `skins/` 下新建一个文件夹，文件夹名即皮肤名（如 `我的主题`）。
2. 按 **皮肤制作教程.txt** 准备 `intro/`、`assets/`（含 background、k26、k9、handwrite、stroke 等子目录及对应图片）。
3. 将 **build_skin.py** 复制到该皮肤文件夹内，运行 `python build_skin.py`，生成该皮肤的 `skin.json`、`intro.json`。
4. 回到**仓库根目录**运行 `python build_skins.py`，更新 `skins/list.json`。
5. 提交并推送，发起 Pull Request；合并后所有人即可在应用内拉取到新皮肤。

## 文件说明

| 文件/目录 | 说明 |
|-----------|------|
| **list.json** | 皮肤列表（由 build_skins.py 生成），APK 拉取此文件获取所有皮肤信息 |
| **white/** | 默认白色皮肤（preview.png、background.png，由 build_skins.py 生成） |
| **build_skin.py** | 单皮肤构建脚本，复制到各皮肤文件夹内运行 |
| **皮肤制作教程.txt** | 详细制作步骤、按键命名表、目录结构 |
| **README.txt** | 图片尺寸与适配说明（preview 160×80，background 720×320） |

## 注意事项

- 图片尺寸见 **README.txt**；按键命名必须与教程中的 ID 一致（如 k26_q.png、k9_1.png）。
- 新增或修改皮肤后，务必在仓库根目录运行 **build_skins.py** 以更新 list.json，再提交。
