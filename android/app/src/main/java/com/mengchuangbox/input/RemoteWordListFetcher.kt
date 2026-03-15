package com.mengchuangbox.input

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 远程词库拉取：从静态托管 URL 拉取 wordlist.txt，解析后合并到 PinyinDict。
 * 留口子：本地词库不足时，可配置此 URL，拉取后与本地合并使用。
 * 格式：每行 "pinyin 词"（与 pinyin_words.txt 一致）。
 */
object RemoteWordListFetcher {

    /** 远程词库 URL（默认指向 GitHub 仓库 wordlist-public/wordlist.txt，社区更新后 APK 拉取即得最新） */
    @Volatile
    var remoteWordListUrl: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 拉取远程词库并合并到 PinyinDict。回调在主线程执行。
     * @param url 若为 null 则使用 remoteWordListUrl
     */
    @JvmStatic
    fun fetch(url: String? = remoteWordListUrl, callback: ((success: Boolean, count: Int) -> Unit)? = null) {
        val u = url ?: remoteWordListUrl
        if (u.isNullOrBlank()) {
            mainHandler.post { callback?.invoke(false, 0) }
            return
        }
        executor.execute {
            var success = false
            var count = 0
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.connect()
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val map = mutableMapOf<String, MutableList<String>>()
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { line ->
                            val t = line.trim()
                            if (t.isEmpty() || t.startsWith("#")) return@forEachLine
                            val space = t.indexOf(' ')
                            if (space <= 0) return@forEachLine
                            val py = t.substring(0, space).lowercase()
                            val word = t.substring(space).trim()
                            if (py.isNotEmpty() && word.isNotEmpty()) {
                                map.getOrPut(py) { mutableListOf() }.add(word)
                                count++
                            }
                        }
                    }
                    PinyinDict.setRemoteWordList(map.mapValues { it.value.distinct() })
                    success = true
                }
            } catch (_: Exception) { }
            mainHandler.post { callback?.invoke(success, count) }
        }
    }
}
