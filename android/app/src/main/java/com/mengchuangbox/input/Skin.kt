package com.mengchuangbox.input

import java.io.File

/**
 * 皮肤项。列表仅展示，不强制拉取；用户点开查看后点「下载」才下到本地，点「应用」才应用到键盘。
 */
data class Skin(
    val id: String,
    val name: String,
    val previewPath: String? = null,
    val previewUrl: String? = null,
    val backgroundUrl: String? = null,
    /** 皮肤清单 URL（skin.json），下载时拉取并据此下载全部资源 */
    val skinManifestUrl: String? = null,
    /** 介绍/使用教程 URL（intro.json），点开皮肤时拉取展示 */
    val introUrl: String? = null,
    /** 已下载到本地时的文件夹；为 null 表示未下载 */
    val folder: File? = null
)
