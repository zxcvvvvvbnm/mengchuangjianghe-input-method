package com.mengchuangbox.input

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 从静态托管拉取皮肤列表（skins/list.json），返回仅含远程 URL 的 Skin 列表。
 */
object RemoteSkinFetcher {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @param baseUrl 站点根 URL，如 https://mengboxstatichost.pages.dev（无末尾斜杠）
     * @param callback 主线程回调，success=false 或 list 为空时传空列表
     */
    @JvmStatic
    fun fetchSkinList(baseUrl: String?, callback: ((success: Boolean, list: List<Skin>) -> Unit)?) {
        val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            mainHandler.post { callback?.invoke(false, emptyList()) }
            return
        }
        val listUrl = (base.trimEnd('/') + "/skins/list.json")
        executor.execute {
            var success = false
            val result = mutableListOf<Skin>()
            try {
                val conn = URL(listUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.connect()
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val arr = JSONArray(json)
                    val baseNorm = base.trimEnd('/')
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id", "").ifBlank { "skin_$i" }
                        val name = obj.optString("name", id)
                        val previewRel = obj.optString("previewUrl", "").trim()
                        val bgRel = obj.optString("backgroundUrl", "").trim()
                        val manifestRel = obj.optString("skinManifestUrl", "").trim()
                        val introRel = obj.optString("introUrl", "").trim()
                        val previewUrl = if (previewRel.isEmpty()) null else baseNorm + "/" + previewRel.replace(Regex("^/"), "")
                        val backgroundUrl = if (bgRel.isEmpty()) null else baseNorm + "/" + bgRel.replace(Regex("^/"), "")
                        val skinManifestUrl = if (manifestRel.isEmpty()) null else baseNorm + "/" + manifestRel.replace(Regex("^/"), "")
                        val introUrl = if (introRel.isEmpty()) null else baseNorm + "/" + introRel.replace(Regex("^/"), "")
                        result.add(Skin(id = id, name = name, previewUrl = previewUrl, backgroundUrl = backgroundUrl, skinManifestUrl = skinManifestUrl, introUrl = introUrl))
                    }
                    success = true
                }
            } catch (_: Exception) { }
            mainHandler.post { callback?.invoke(success, result) }
        }
    }
}
