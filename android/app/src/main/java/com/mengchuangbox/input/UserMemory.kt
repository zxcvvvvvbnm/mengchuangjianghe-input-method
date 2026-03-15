package com.mengchuangbox.input

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户记忆：记录用户选过的候选（拼音/首字母 key → 候选词与次数），下次同 key 时优先展示。
 * 持久化到 SharedPreferences，实现类似 PinyinIME 的“越用越准”。
 */
object UserMemory {

    private const val PREFS_NAME = "pinyin_user_memory"
    private const val MAX_ENTRIES_PER_KEY = 20
    private const val MAX_KEYS = 500

    @Volatile
    private var cache: MutableMap<String, MutableMap<String, Int>> = ConcurrentHashMap()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun load(context: Context) {
        val p = prefs(context)
        val all = p.all
        val m = mutableMapOf<String, MutableMap<String, Int>>()
        for ((k, v) in all) {
            if (k.startsWith("k_") && v is String) {
                val key = k.removePrefix("k_")
                val parts = v.split(';').mapNotNull { s ->
                    val idx = s.lastIndexOf(',')
                    if (idx > 0) {
                        val word = s.substring(0, idx)
                        val count = s.substring(idx + 1).toIntOrNull() ?: 1
                        word to count
                    } else null
                }
                if (parts.isNotEmpty()) {
                    m[key] = parts.toMap().toMutableMap()
                }
            }
        }
        cache = m
    }

    /** 记录用户选择了某 key 下的候选（如 key=nihao, candidate=你好），用于下次排序 */
    @JvmStatic
    fun record(context: Context, key: String, candidate: String) {
        val k = key.trim().lowercase()
        if (k.isEmpty() || candidate.isEmpty()) return
        synchronized(cache) {
            if (cache.isEmpty()) load(context)
            val keyMap = cache.getOrPut(k) { mutableMapOf() }
            keyMap[candidate] = (keyMap[candidate] ?: 0) + 1
            // 限制单 key 候选数量，只保留前 MAX_ENTRIES_PER_KEY 个（按次数）
            if (keyMap.size > MAX_ENTRIES_PER_KEY) {
                val sorted = keyMap.entries.sortedByDescending { it.value }.take(MAX_ENTRIES_PER_KEY)
                keyMap.clear()
                sorted.forEach { keyMap[it.key] = it.value }
            }
            if (cache.size > MAX_KEYS) {
                val keysToRemove = cache.keys.take(cache.size - MAX_KEYS)
                keysToRemove.forEach { cache.remove(it) }
            }
            save(context, k, keyMap)
        }
    }

    private fun save(context: Context, key: String, keyMap: Map<String, Int>) {
        val value = keyMap.entries.joinToString(";") { "${it.key},${it.value}" }
        prefs(context).edit().putString("k_$key", value).apply()
    }

    /** 同 key 下用户常选的候选，按次数从高到低，用于排在候选栏前面 */
    @JvmStatic
    fun getPreferred(context: Context, key: String): List<String> {
        val k = key.trim().lowercase()
        if (k.isEmpty()) return emptyList()
        synchronized(cache) {
            if (cache.isEmpty()) load(context)
            val keyMap = cache[k] ?: return emptyList()
            return keyMap.entries.sortedByDescending { it.value }.map { it.key }
        }
    }

    /** 在已有候选列表前插入用户记忆的候选（去重后合并） */
    @JvmStatic
    fun mergePreferred(context: Context, key: String, candidates: List<String>): List<String> {
        val preferred = getPreferred(context, key)
        if (preferred.isEmpty()) return candidates
        val preferredSet = preferred.toSet()
        val rest = candidates.filter { it !in preferredSet }
        return preferred.filter { it in candidates.toSet() } + rest
    }
}
