package com.mengchuangbox.input

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 从指定文件夹加载皮肤列表，支持热更新：文件夹内每个子文件夹为一种皮肤，
 * 可含 manifest.json（name 等）、预览图、JS 等。
 */
object SkinLoader {

    private const val SKINS_DIR = "skins"
    private const val MANIFEST_FILE = "manifest.json"
    private const val KEY_NAME = "name"

    /**
     * 获取皮肤根目录（可热更新写入）。
     * 优先使用 getExternalFilesDir("skins")，便于后续下载/更新。
     */
    fun getSkinsDir(context: Context): File {
        val external = context.getExternalFilesDir(null)?.let { File(it, SKINS_DIR) }
        if (external != null) {
            if (!external.exists()) external.mkdirs()
            return external
        }
        val internal = File(context.filesDir, SKINS_DIR)
        if (!internal.exists()) internal.mkdirs()
        return internal
    }

    /**
     * 扫描皮肤目录，每个子文件夹为一个皮肤；可含 manifest.json 和预览图。
     */
    fun loadSkins(context: Context): List<Skin> {
        val root = getSkinsDir(context)
        val list = mutableListOf<Skin>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val manifest = File(dir, MANIFEST_FILE)
            var name = dir.name
            var previewPath: String? = null
            if (manifest.exists()) {
                try {
                    val json = JSONObject(manifest.readText())
                    name = json.optString(KEY_NAME, name)
                    previewPath = json.optString("preview")?.takeIf { it.isNotEmpty() }
                } catch (_: Exception) { }
            }
            if (previewPath == null) {
                val png = File(dir, "preview.png")
                if (png.exists()) previewPath = png.absolutePath
            }
            list.add(Skin(id = dir.name, name = name, previewPath = previewPath, folder = dir))
        }
        return list.sortedBy { it.name }
    }
}
