package com.mengchuangbox.input

/**
 * T9 九宫格匹配，与 YiiGuxing/T9Search 逻辑一致：数字串→拼音→候选词。
 * 使用同一词库 PinyinDict.getAllWords()，支持多音字（取首拼音建键，匹配时前缀匹配）。
 */
object T9Helper {

    /** 数字→该键第一个字母（无候选时候选栏上方仍显示字母不显示数字）：2→a,3→d,4→g,5→j,6→m,7→p,8→t,9→w */
    private val DIGIT_TO_FIRST_LETTER = mapOf(
        '2' to 'a', '3' to 'd', '4' to 'g', '5' to 'j', '6' to 'm', '7' to 'p', '8' to 't', '9' to 'w'
    )

    /** 字母→数字，与标准 T9 一致：2=ABC, 3=DEF, 4=GHI, 5=JKL, 6=MNO, 7=PQRS, 8=TUV, 9=WXYZ */
    private val LETTER_TO_DIGIT = run {
        val map = mutableMapOf<Char, Char>()
        "abc".forEach { map[it] = '2' }
        "def".forEach { map[it] = '3' }
        "ghi".forEach { map[it] = '4' }
        "jkl".forEach { map[it] = '5' }
        "mno".forEach { map[it] = '6' }
        "pqrs".forEach { map[it] = '7' }
        "tuv".forEach { map[it] = '8' }
        "wxyz".forEach { map[it] = '9' }
        map
    }

    /** 为单个汉字/词生成 T9 数字串（多音字取第一个拼音）。 */
    @JvmStatic
    fun buildT9Key(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            if (c.code < 128) {
                val lower = c.lowercaseChar()
                if (lower in LETTER_TO_DIGIT) sb.append(LETTER_TO_DIGIT[lower])
                continue
            }
            val pinyins = PinyinDict.getPinyinForChar(c)
            if (pinyins.isEmpty()) continue
            val py = pinyins.first().lowercase()
            for (letter in py) {
                if (letter in LETTER_TO_DIGIT) sb.append(LETTER_TO_DIGIT[letter])
            }
        }
        return sb.toString()
    }

    /** 数字串转成「每键取第一个字母」的字母串，无候选时候选栏上方显示这个而不是数字。 */
    @JvmStatic
    fun digitsToDefaultLetters(digits: String): String = digits.map { DIGIT_TO_FIRST_LETTER[it] ?: it }.joinToString("")

    /** 判断 T9 键是否与用户输入的数字串匹配（前缀匹配或完全匹配）。 */
    @JvmStatic
    fun matches(t9Key: String, constraint: String): Boolean {
        if (constraint.isEmpty()) return true
        if (t9Key.length < constraint.length) return false
        return t9Key.startsWith(constraint)
    }

    /** 词→T9 键缓存，避免重复计算。 */
    private var wordToT9Key: Map<String, String> = emptyMap()
    private var indexBuilt = false

    @Synchronized
    private fun ensureIndex() {
        if (indexBuilt) return
        indexBuilt = true
        wordToT9Key = PinyinDict.getAllWords().associateWith { buildT9Key(it) }
    }

    /**
     * 根据用户输入的数字串返回候选词（与 YiiGuxing/T9Search 能力一致：数字→拼音→词库候选）。
     * 使用同一联想词库 pinyin_words.txt。
     */
    @JvmStatic
    fun search(digits: String, maxResults: Int = 30): List<String> {
        val constraint = digits.trim().filter { it in '2'..'9' }
        if (constraint.isEmpty()) return emptyList()
        ensureIndex()
        return wordToT9Key
            .filter { (_, key) -> matches(key, constraint) }
            .keys
            .toList()
            .sortedBy { wordToT9Key[it]!!.length }
            .take(maxResults)
    }

    /** 清空缓存（词库更新后可调用）。 */
    @JvmStatic
    fun clearIndex() {
        indexBuilt = false
        wordToT9Key = emptyMap()
    }
}
