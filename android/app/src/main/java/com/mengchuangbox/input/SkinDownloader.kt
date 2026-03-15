package com.mengchuangbox.input

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 将远程皮肤下载到本地。仅当用户点击「下载」时调用，列表不自动拉取。
 */
object SkinDownloader {

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @param baseUrl 站点根 URL（与 list.json 一致）
     * @param skin 当前皮肤（含 skinManifestUrl 或至少 previewUrl/backgroundUrl）
     * @param callback 主线程：success, message
     */
    @JvmStatic
    fun download(
        context: Context,
        baseUrl: String,
        skin: Skin,
        callback: ((success: Boolean, message: String) -> Unit)?
    ) {
        val id = skin.id
        val dir = File(SkinLoader.getSkinsDir(context), id)
        executor.execute {
            var success = false
            var message = "下载失败"
            try {
                dir.mkdirs()
                val baseNorm = baseUrl.trimEnd('/')
                val skinBase = baseNorm + "/skins/" + id

                if (!skin.skinManifestUrl.isNullOrBlank()) {
                    // 有 skin.json：拉取清单再按清单下载背景与按键图，并复制一份 background 为 dir/background.png 供 IME 使用
                    val conn = URL(skin.skinManifestUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.requestMethod = "GET"
                    conn.connect()
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        mainHandler.post { callback?.invoke(false, "无法获取皮肤清单") }
                        return@execute
                    }
                    val manifest = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    val bgRel = manifest.optString("backgroundUrl", "").trim()
                    if (bgRel.isNotEmpty()) {
                        val bgUrl = skinBase + "/" + bgRel.replace(Regex("^/"), "")
                        downloadToFile(bgUrl, File(dir, "background.png"))
                    }
                    val keyAssets = manifest.optJSONObject("keyAssets") ?: JSONObject()
                    for (keyType in keyAssets.keys()) {
                        val keyObj = keyAssets.optJSONObject(keyType)
                        if (keyObj != null) {
                            val subDir = File(dir, keyType)
                            subDir.mkdirs()
                            for (keyId in keyObj.keys()) {
                                val fname = keyObj.optString(keyId, "").trim()
                                if (fname.isEmpty()) continue
                                val relPath = keyType + "/" + fname
                                val dest = File(dir, relPath)
                                dest.parentFile?.mkdirs()
                                downloadToFile(skinBase + "/" + relPath, dest)
                            }
                        }
                    }
                    success = true
                    message = "已下载到本地"
                } else {
                    // 无 skin.json（如纯白等）：只下 preview + background
                    if (!skin.backgroundUrl.isNullOrBlank()) {
                        downloadToFile(skin.backgroundUrl!!, File(dir, "background.png"))
                    }
                    if (!skin.previewUrl.isNullOrBlank()) {
                        downloadToFile(skin.previewUrl!!, File(dir, "preview.png"))
                    }
                    success = true
                    message = "已下载到本地"
                }
            } catch (e: Exception) {
                message = e.message ?: "下载失败"
            }
            mainHandler.post { callback?.invoke(success, message) }
        }
    }

    private fun downloadToFile(url: String, dest: File): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            FileOutputStream(dest).use { out ->
                conn.inputStream.use { it.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
