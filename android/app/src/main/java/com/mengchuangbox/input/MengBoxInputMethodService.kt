package com.mengchuangbox.input

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 萌创匠盒输入法：按引导页选择展示 9键/26键/手写/笔画 真实键盘。
 * 26键：中/英（灰=拼音，蓝=英文）、Shift（蓝=大写），字母键随状态切换。
 * 圆角方案：系统容器直角不变，内层用「仅顶部圆角」drawable 做背景，边角料透明，外层根透明，窗口透明，视觉上即圆角键盘。
 */
class MengBoxInputMethodService : InputMethodService() {

    private val tag = "MengBoxIME"

    /** 这些应用的输入框会把 IME 提交的 ImageSpan 显示成 OBJ，萌创匠盒图标在这些 app 里改为上屏文字 */
    private companion object {
        val PACKAGES_OBJ_FOR_ICON = setOf(
            "com.ss.android.ugc.aweme",   // 抖音
            "com.smile.gifmaker",         // 快手
            "com.tencent.mm",             // 微信
            "com.tencent.mobileqq",       // QQ
            "com.sina.weibo"              // 微博
        )
    }

    override fun onCreate() {
        super.onCreate()
        PinyinDict.init(applicationContext)
        // 静默使用默认远程词库：本地没有的候选从静态托管补全，不在应用内暴露「更新词库」
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val url = prefs.getString(MainActivity.KEY_REMOTE_WORDLIST_URL, null)?.trim()?.ifBlank { null }
            ?: MainActivity.DEFAULT_REMOTE_WORDLIST_URL
        RemoteWordListFetcher.remoteWordListUrl = url
        RemoteWordListFetcher.fetch(null, null)
    }

    override fun onCreateInputView(): View {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val type = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY)
            ?: MainActivity.KEYBOARD_TYPE_26KEY
        val content = when (type) {
            MainActivity.KEYBOARD_TYPE_9KEY -> layoutInflater.inflate(R.layout.input_keyboard_9key, null)
            MainActivity.KEYBOARD_TYPE_26KEY -> layoutInflater.inflate(R.layout.input_keyboard_26key, null)
            MainActivity.KEYBOARD_TYPE_HANDWRITE -> layoutInflater.inflate(R.layout.input_keyboard_handwrite, null)
            MainActivity.KEYBOARD_TYPE_STROKE -> layoutInflater.inflate(R.layout.input_keyboard_stroke, null)
            else -> layoutInflater.inflate(R.layout.input_keyboard_26key, null)
        }
        bindKeyboard(content, type)
        keyboardRootView = content
        // 底部预留区与键盘背景同色，和别家输入法一样融为一体
        val keyboardBgColor = keyboardBackgroundColor(type)
        val fallbackNavBarPx = fallbackNavigationBarHeightPx()
        // 外层包一层容器：底部预留区用键盘背景色，键盘本体仍靠内层圆角 drawable
        val wrapper = FrameLayout(this).apply {
            setBackgroundColor(keyboardBgColor)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // 首次即预留底部，避免 OPPO 等机型第一次弹出时 insets 未下发导致底栏被导航栏挡住
            setPadding(0, 0, 0, fallbackNavBarPx)
            // 预留底部导航/手势区；若系统已下发 insets 用系统值，否则用回退高度（解决“一打开就图二、点一下才图一”）
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
                val nav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val system = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val bottom = maxOf(nav.bottom, system.bottom)
                val effectiveBottom = if (bottom > 0) bottom else fallbackNavBarPx
                v.setPadding(0, 0, 0, effectiveBottom)
                windowInsets
            }
        }
        keyboardWrapperView = wrapper
        // 已选皮肤：优先用已下载的本地皮肤（用户点开→下载→应用），否则用远程背景 URL。背景铺满 wrapper（含底部横条）。
        val skinId = prefs.getString(MainActivity.KEY_SELECTED_SKIN_ID, null)?.trim()
        val skinBgUrl = prefs.getString(MainActivity.KEY_SELECTED_SKIN_BACKGROUND_URL, null)?.trim()
        if (!skinId.isNullOrEmpty()) {
            val localBg = java.io.File(SkinLoader.getSkinsDir(applicationContext), "$skinId/background.png")
            if (localBg.exists()) {
                Thread {
                    val bm = BitmapFactory.decodeFile(localBg.absolutePath)
                    Handler(Looper.getMainLooper()).post {
                        if (bm != null && keyboardWrapperView != null) {
                            keyboardWrapperView?.background = BitmapDrawable(resources, bm)
                        }
                    }
                }.start()
            }
        } else if (!skinBgUrl.isNullOrEmpty()) {
            Thread {
                var bm: android.graphics.Bitmap? = null
                try {
                    java.net.URL(skinBgUrl).openStream().use { bm = BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { }
                val bitmap = bm
                Handler(Looper.getMainLooper()).post {
                    if (bitmap != null && keyboardWrapperView != null) {
                        keyboardWrapperView?.background = BitmapDrawable(resources, bitmap)
                    }
                }
            }.start()
        }
        return wrapper
    }

    /** 无导航栏 insets 时的回退高度（OPPO 等首次弹出时 insets 常为 0），单位 px */
    private fun fallbackNavigationBarHeightPx(): Int {
        return try {
            val res = resources
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id != 0) {
                val px = res.getDimensionPixelSize(id)
                if (px > 0) px else 48.dpToPx()
            } else {
                48.dpToPx()
            }
        } catch (_: Throwable) {
            48.dpToPx()
        }
    }

    private fun Int.dpToPx(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    /** 与 26 键统一：所有键盘同背景色、同高低样式 */
    private fun keyboardBackgroundColor(type: String): Int = Color.parseColor("#FF2D2D2D")

    private var keyboardRootView: View? = null
    private var keyboardWrapperView: View? = null

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val w = window
        if (w == null) {
            Log.w(tag, "onStartInputView: window is null, cannot set transparent")
        } else {
            val win = w.window
            if (win == null) {
                Log.w(tag, "onStartInputView: window.window is null")
            } else {
                val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
                val type = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
                val isDarkKeyboard = true  // 所有键盘统一深色
                val keyboardBgColor = keyboardBackgroundColor(type)

                // 参考 Google ThemedNavBarKeyboard：允许 IME 控制导航条颜色与图标
                win.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                win.setNavigationBarColor(keyboardBgColor)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                }

                // 不贴合系统窗口，才能收到底部导航/IME 横条 insets（Vivo 等机型的地球键、下箭头区域）
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                val decor = win.decorView
                if (decor != null) {
                    decor.setBackgroundColor(Color.TRANSPARENT)
                    decor.post {
                        decor.setBackgroundColor(Color.TRANSPARENT)
                        Log.d(tag, "onStartInputView: window+decor set TRANSPARENT (rounded corners rely on drawable transparent 边角料)")
                    }
                    // 底部横条图标：深色键盘用白色图标，浅色键盘用深色图标（API 30+ 与 旧 API 双路径，提高 OEM 兼容）
                    WindowCompat.getInsetsController(win, decor)?.setAppearanceLightNavigationBars(!isDarkKeyboard)
                }
            }
        }
        // 根视图的 SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR（部分 OEM 如 Vivo 仍会参考）
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val type = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
        val isDarkKeyboard = true
        keyboardWrapperView?.let { wrapper ->
            val lightFlag = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val vis = wrapper.systemUiVisibility
            wrapper.setSystemUiVisibility(if (isDarkKeyboard) (vis and lightFlag.inv()) else (vis or lightFlag))
            ViewCompat.requestApplyInsets(wrapper)
            wrapper.post { ViewCompat.requestApplyInsets(wrapper) }
            // OPPO 等机型 insets 常延迟下发，再请求一次以便用真实高度替换回退 padding
            wrapper.postDelayed({ ViewCompat.requestApplyInsets(wrapper) }, 150)
        }
    }

    private fun bindKeyboard(view: View, type: String) {
        when (type) {
            MainActivity.KEYBOARD_TYPE_9KEY -> bind9Key(view)
            MainActivity.KEYBOARD_TYPE_26KEY -> bind26Key(view)
            MainActivity.KEYBOARD_TYPE_HANDWRITE -> bindHandwrite(view)
            MainActivity.KEYBOARD_TYPE_STROKE -> bindStroke(view)
        }
    }

    /** 按键震动反馈，与系统键盘一致 */
    private fun keyHaptic(v: View?) {
        v?.isHapticFeedbackEnabled = true
        v?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 先删掉正在组字的拼音（输入框里的 ni），再上屏候选，避免出现 ni你 */
    private fun commitReplacingComposing(composingLength: Int, text: CharSequence) {
        if (composingLength > 0) {
            currentInputConnection?.deleteSurroundingText(composingLength, 0)
        }
        commitText(text)
    }

    /** 当前输入框所在应用会把 IME 提交的 ImageSpan 显示成 OBJ，这类应用改为上屏文字「萌创匠盒」 */
    private fun currentAppShowsObjForIcon(): Boolean {
        val pkg = currentInputEditorInfo?.packageName ?: return true
        return pkg in PACKAGES_OBJ_FOR_ICON
    }

    private fun createMengchuangjiangheIconSpannable(): CharSequence? {
        val d = ContextCompat.getDrawable(this, R.drawable.mengchuangjianghe) ?: return null
        val size = (24 * resources.displayMetrics.density).toInt().coerceIn(24, 72)
        d.setBounds(0, 0, size, size)
        val span = ImageSpan(d)
        val s = SpannableString("\uFFFC")
        s.setSpan(span, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** 9键/手写/笔画共用：候选区两图标 + 换键盘/剪贴板浮层；点剪贴板时键盘高低不变（主内容 INVISIBLE 占位） */
    private fun setupUnifiedCandidateAndOverlays(view: View, mainContentId: Int) {
        val mainContent = view.findViewById<View>(mainContentId)
        val switchOverlay = view.findViewById<View>(R.id.k26_keyboard_switch_overlay)
        val clipboardOverlay = view.findViewById<View>(R.id.k26_clipboard_overlay)
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        var selectedTypeInOverlay = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
        val imeCardIds = listOf(
            R.id.k26_ime_card_9key to MainActivity.KEYBOARD_TYPE_9KEY,
            R.id.k26_ime_card_26key to MainActivity.KEYBOARD_TYPE_26KEY,
            R.id.k26_ime_card_handwrite to MainActivity.KEYBOARD_TYPE_HANDWRITE,
            R.id.k26_ime_card_stroke to MainActivity.KEYBOARD_TYPE_STROKE
        )
        fun updateImeCardSelection() {
            for ((id, type) in imeCardIds) {
                view.findViewById<FrameLayout>(id)?.setBackgroundResource(
                    if (type == selectedTypeInOverlay) R.drawable.bg_card_rounded_selected else R.drawable.bg_card_rounded
                )
            }
        }
        for ((id, type) in imeCardIds) {
            view.findViewById<FrameLayout>(id)?.setOnClickListener {
                keyHaptic(it)
                selectedTypeInOverlay = type
                updateImeCardSelection()
            }
        }
        updateImeCardSelection()
        view.findViewById<Button>(R.id.k26_switch_confirm)?.setOnClickListener {
            keyHaptic(it)
            val current = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
            prefs.edit().putString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, selectedTypeInOverlay).apply()
            mainContent?.visibility = View.VISIBLE
            switchOverlay?.visibility = View.GONE
            if (selectedTypeInOverlay != current) setInputView(onCreateInputView())
        }
        val clipboardList = clipboardOverlay?.findViewById<LinearLayout>(R.id.k26_clipboard_list)
        val clipboardEmpty = clipboardOverlay?.findViewById<android.widget.TextView>(R.id.k26_clipboard_empty)
        clipboardOverlay?.findViewById<Button>(R.id.k26_clipboard_close)?.setOnClickListener {
            keyHaptic(it)
            clipboardOverlay.visibility = View.GONE
            mainContent?.visibility = View.VISIBLE
        }
        val emptyChipsContainer = view.findViewById<LinearLayout>(R.id.k26_candidate_empty_chips)
        emptyChipsContainer?.removeAllViews()
        val keyboardChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, emptyChipsContainer, false)
        keyboardChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)?.setImageResource(R.drawable.ic_candidate_keyboard)
        keyboardChip.setOnClickListener {
            keyHaptic(it)
            selectedTypeInOverlay = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
            updateImeCardSelection()
            mainContent?.visibility = View.INVISIBLE
            switchOverlay?.visibility = View.VISIBLE
        }
        emptyChipsContainer?.addView(keyboardChip)
        val scissorsChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, emptyChipsContainer, false)
        scissorsChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)?.setImageResource(R.drawable.ic_candidate_clipboard)
        scissorsChip.setOnClickListener {
            keyHaptic(it)
            mainContent?.visibility = View.INVISIBLE
            clipboardOverlay?.visibility = View.VISIBLE
            clipboardList?.removeAllViews()
            val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clipboardEmpty?.visibility = View.GONE
                clipboardList?.visibility = View.VISIBLE
                for (i in 0 until clip.itemCount) {
                    val item = clip.getItemAt(i)
                    val text = item?.text?.toString() ?: continue
                    val row = android.widget.TextView(this).apply {
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
                        this.text = text
                        maxLines = 4
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setOnClickListener {
                            keyHaptic(it)
                            commitText(text)
                            clipboardOverlay.visibility = View.GONE
                            mainContent?.visibility = View.VISIBLE
                        }
                    }
                    clipboardList?.addView(row)
                }
            } else {
                clipboardEmpty?.visibility = View.VISIBLE
                clipboardList?.visibility = View.GONE
            }
        }
        emptyChipsContainer?.addView(scissorsChip)
        emptyChipsContainer?.visibility = View.VISIBLE
    }

    private fun bindHandwrite(view: View) {
        setupUnifiedCandidateAndOverlays(view, R.id.handwrite_main_content)
        view.findViewById<Button>(R.id.handwrite_clear)?.setOnClickListener {
            keyHaptic(it)
            // 清除书写区，后续可接 Canvas 重绘
        }
        view.findViewById<Button>(R.id.handwrite_space)?.setOnClickListener { keyHaptic(it); commitText(" ") }
        view.findViewById<Button>(R.id.handwrite_enter)?.setOnClickListener { keyHaptic(it); commitText("\n") }
        view.findViewById<Button>(R.id.handwrite_comma)?.setOnClickListener { keyHaptic(it); commitText(",") }
        view.findViewById<Button>(R.id.handwrite_period)?.setOnClickListener { keyHaptic(it); commitText(".") }
        view.findViewById<Button>(R.id.handwrite_123)?.setOnClickListener { keyHaptic(it) }
    }

    private fun bindStroke(view: View) {
        setupUnifiedCandidateAndOverlays(view, R.id.stroke_main_content)
        val strokes = listOf("一", "丨", "丿", "丶", "フ", "乛", "亅", "𠃍", "𡿨")
        strokes.forEachIndexed { i, s ->
            val id = resources.getIdentifier("stroke_${i + 1}", "id", packageName)
            if (id != 0) view.findViewById<Button>(id)?.setOnClickListener { keyHaptic(it); commitText(s) }
        }
        view.findViewById<Button>(R.id.stroke_space)?.setOnClickListener { keyHaptic(it); commitText(" ") }
        view.findViewById<Button>(R.id.stroke_enter)?.setOnClickListener { keyHaptic(it); commitText("\n") }
        view.findViewById<Button>(R.id.stroke_comma)?.setOnClickListener { keyHaptic(it); commitText(",") }
        view.findViewById<Button>(R.id.stroke_period)?.setOnClickListener { keyHaptic(it); commitText(".") }
        view.findViewById<Button>(R.id.stroke_123)?.setOnClickListener { keyHaptic(it) }
        view.findViewById<Button>(R.id.stroke_zh_en)?.setOnClickListener { keyHaptic(it) }
    }

    private fun commitText(s: CharSequence) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun deleteBack() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun setComposingText(text: CharSequence) {
        if (text.isEmpty()) {
            finishComposing()
            return
        }
        currentInputConnection?.setComposingText(text, text.length)
    }

    private fun finishComposing() {
        currentInputConnection?.finishComposingText()
    }

    /** 九宫格点击 @# 或 # 时候选栏显示的常用符号：第一个 @，第二个 #，后面常用符号（与图一/图三参考一致） */
    private val k9SymbolCandidates = listOf(
        "@", "#", "*", "+", "…", "~", "(", ")", ",", "/", "\\", "-", "_", "=",
        "[", "]", ";", ":", "'", "\"", "!", "?", "、", "。", "·"
    )

    /** 九宫格数字缓冲，用于 T9 检索（与 YiiGuxing/T9Search 一致：数字→拼音→词库候选） */
    private val k9DigitBuffer = StringBuilder()

    private fun bind9Key(view: View) {
        setupUnifiedCandidateAndOverlays(view, R.id.k9_main_content)
        val candidateList = view.findViewById<LinearLayout>(R.id.k26_candidate_list)
        val emptyChipsContainer = view.findViewById<LinearLayout>(R.id.k26_candidate_empty_chips)
        val pinyinRow = view.findViewById<android.widget.TextView>(R.id.k26_pinyin_row)  // 候选栏上方小字，九宫格显示当前数字串
        var hasSelectedCandidateFromBar9 = false
        val expandBtn9 = view.findViewById<android.widget.ImageButton>(R.id.k26_candidate_expand_btn)
        val expandContainer9 = view.findViewById<View>(R.id.k26_expand_picker_container)
        val expandGrid9 = view.findViewById<LinearLayout>(R.id.k26_expand_picker_grid)
        val expandDelete9 = view.findViewById<Button>(R.id.k26_expand_picker_delete)
        val keysContainer9 = view.findViewById<View>(R.id.k9_keys_container)

        fun showSymbolCandidates() {
            k9DigitBuffer.setLength(0)
            pinyinRow?.visibility = View.INVISIBLE
            emptyChipsContainer?.visibility = View.GONE
            candidateList?.removeAllViews()
            k9SymbolCandidates.forEach { sym ->
                val chip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                chip?.text = sym
                chip?.setOnClickListener {
                    keyHaptic(it)
                    commitText(sym)
                    candidateList?.removeAllViews()
                    emptyChipsContainer?.visibility = View.VISIBLE
                }
                candidateList?.addView(chip)
            }
        }

        fun refreshK9Candidates() {
            if (k9DigitBuffer.isEmpty()) {
                pinyinRow?.text = ""
                pinyinRow?.visibility = View.INVISIBLE
                candidateList?.removeAllViews()
                emptyChipsContainer?.visibility = View.VISIBLE
                expandBtn9?.visibility = View.GONE
                expandContainer9?.visibility = View.GONE
                keysContainer9?.visibility = View.VISIBLE
                return
            }
            expandBtn9?.visibility = View.VISIBLE
            expandBtn9?.setImageResource(
                if (hasSelectedCandidateFromBar9) R.drawable.ic_candidate_expand_close else R.drawable.ic_candidate_expand_down
            )
            emptyChipsContainer?.visibility = View.GONE
            candidateList?.removeAllViews()
            val digits = k9DigitBuffer.toString()
            val words = T9Helper.search(digits, 25)

            // 拼音只认一套逻辑：能来自候选就来自候选，长度=按键数。有候选用首候选拼音前N位；无候选用「最长有候选前缀」的拼音+剩余键默认字母，不变成另一套规则。
            val displayPinyin = if (words.isNotEmpty()) {
                PinyinDict.getPinyinForWord(words.first()).take(digits.length)
            } else {
                var prefixPinyin = ""
                var suffixDigits = digits
                for (len in digits.length - 1 downTo 1) {
                    val prefixWords = T9Helper.search(digits.take(len), 1)
                    if (prefixWords.isNotEmpty()) {
                        prefixPinyin = PinyinDict.getPinyinForWord(prefixWords.first()).take(len)
                        suffixDigits = digits.drop(len)
                        break
                    }
                }
                prefixPinyin + T9Helper.digitsToDefaultLetters(suffixDigits)
            }
            pinyinRow?.text = displayPinyin
            pinyinRow?.visibility = View.VISIBLE

            words.forEach { word ->
                val chip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                chip?.text = word
                chip?.setOnClickListener {
                    keyHaptic(it)
                    hasSelectedCandidateFromBar9 = true
                    commitText(word)
                    k9DigitBuffer.setLength(0)
                    pinyinRow?.text = ""
                    pinyinRow?.visibility = View.INVISIBLE
                    candidateList?.removeAllViews()
                    emptyChipsContainer?.visibility = View.VISIBLE
                    refreshK9Candidates()
                }
                candidateList?.addView(chip)
            }
        }

        fun fillExpandGrid9() {
            expandGrid9?.removeAllViews()
            val digits = k9DigitBuffer.toString()
            if (digits.isEmpty()) return
            // 展开区只显示单字，不显示双字词
            val words = T9Helper.search(digits, 80).filter { it.length == 1 }
            val COLS = 6
            words.chunked(COLS).forEach { rowWords ->
                val row = android.widget.LinearLayout(this@MengBoxInputMethodService).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dpToPx() }
                }
                rowWords.forEach { word ->
                    val btn = layoutInflater.inflate(R.layout.item_candidate_chip, row, false) as? Button
                    btn?.text = word
                    btn?.layoutParams = android.widget.LinearLayout.LayoutParams(0, 44.dpToPx(), 1f)
                    btn?.setOnClickListener {
                        keyHaptic(it)
                        hasSelectedCandidateFromBar9 = true
                        commitText(word)
                        k9DigitBuffer.setLength(0)
                        pinyinRow?.text = ""
                        pinyinRow?.visibility = View.INVISIBLE
                        expandContainer9?.visibility = View.GONE
                        keysContainer9?.visibility = View.VISIBLE
                        refreshK9Candidates()
                    }
                    row.addView(btn)
                }
                expandGrid9?.addView(row)
            }
        }

        expandBtn9?.setOnClickListener {
            keyHaptic(it)
            // 已展开时再点向下箭头：关闭展开（toggle）
            if (expandContainer9?.visibility == View.VISIBLE) {
                expandContainer9?.visibility = View.GONE
                keysContainer9?.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (hasSelectedCandidateFromBar9) {
                hasSelectedCandidateFromBar9 = false
                k9DigitBuffer.setLength(0)
                pinyinRow?.text = ""
                pinyinRow?.visibility = View.INVISIBLE
                expandContainer9?.visibility = View.GONE
                keysContainer9?.visibility = View.VISIBLE
                refreshK9Candidates()
            } else {
                expandContainer9?.visibility = View.VISIBLE
                keysContainer9?.visibility = View.GONE
                fillExpandGrid9()
            }
        }

        expandDelete9?.setOnClickListener {
            keyHaptic(it)
            if (k9DigitBuffer.isNotEmpty()) {
                k9DigitBuffer.setLength(k9DigitBuffer.length - 1)
                if (k9DigitBuffer.isEmpty()) {
                    pinyinRow?.text = ""
                    pinyinRow?.visibility = View.INVISIBLE
                    expandContainer9?.visibility = View.GONE
                    keysContainer9?.visibility = View.VISIBLE
                    refreshK9Candidates()
                } else {
                    val digits = k9DigitBuffer.toString()
                    val words = T9Helper.search(digits, 1)
                    val displayPinyin = if (words.isNotEmpty()) PinyinDict.getPinyinForWord(words.first()).take(digits.length)
                    else {
                        var prefixPinyin = ""
                        var suffixDigits = digits
                        for (len in digits.length - 1 downTo 1) {
                            val prefixWords = T9Helper.search(digits.take(len), 1)
                            if (prefixWords.isNotEmpty()) {
                                prefixPinyin = PinyinDict.getPinyinForWord(prefixWords.first()).take(len)
                                suffixDigits = digits.drop(len)
                                break
                            }
                        }
                        prefixPinyin + T9Helper.digitsToDefaultLetters(suffixDigits)
                    }
                    pinyinRow?.text = displayPinyin
                    pinyinRow?.visibility = View.VISIBLE
                    fillExpandGrid9()
                }
            }
        }

        view.findViewById<Button>(R.id.k9_1)?.setOnClickListener {
            keyHaptic(it)
            showSymbolCandidates()
        }
        val k9IdToDigit = mapOf(
            R.id.k9_2 to '2', R.id.k9_3 to '3', R.id.k9_4 to '4', R.id.k9_5 to '5',
            R.id.k9_6 to '6', R.id.k9_7 to '7', R.id.k9_8 to '8', R.id.k9_9 to '9'
        )
        k9IdToDigit.forEach { (id, digit) ->
            view.findViewById<Button>(id)?.setOnClickListener {
                keyHaptic(it)
                hasSelectedCandidateFromBar9 = false
                k9DigitBuffer.append(digit)
                refreshK9Candidates()
            }
        }
        view.findViewById<Button>(R.id.k9_backspace)?.setOnClickListener {
            keyHaptic(it)
            if (k9DigitBuffer.isNotEmpty()) {
                k9DigitBuffer.setLength(k9DigitBuffer.length - 1)
                refreshK9Candidates()
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }
        view.findViewById<Button>(R.id.k9_retype)?.setOnClickListener {
            keyHaptic(it)
            k9DigitBuffer.setLength(0)
            refreshK9Candidates()
        }
        view.findViewById<Button>(R.id.k9_enter)?.setOnClickListener { keyHaptic(it); commitText("\n") }
        view.findViewById<Button>(R.id.k9_sym_comma)?.setOnClickListener { keyHaptic(it); commitText(",") }
        view.findViewById<Button>(R.id.k9_sym_period_cn)?.setOnClickListener { keyHaptic(it); commitText("。") }
        view.findViewById<Button>(R.id.k9_sym_question)?.setOnClickListener { keyHaptic(it); commitText("?") }
        view.findViewById<Button>(R.id.k9_bottom_sym)?.setOnClickListener {
            keyHaptic(it)
            showSymbolCandidates()
        }
        view.findViewById<Button>(R.id.k9_space)?.setOnClickListener { keyHaptic(it); commitText(" ") }
        view.findViewById<Button>(R.id.k9_123)?.setOnClickListener { keyHaptic(it) }
        view.findViewById<Button>(R.id.k9_zh_en)?.setOnClickListener { keyHaptic(it) }
    }

    private var k26View: View? = null
    private var isEnglishMode26 = false   // false=拼音(中) 中/英灰，true=英文 中/英蓝
    private var isShiftOn26 = false       // 英文下 true=大写 Shift蓝
    private val pinyinBuffer26 = StringBuilder() // 拼音模式下的拼音缓冲
    /** 上一拍上屏的最后一个字，用于联想：缓冲为空时展示「下一个可能打的字」 */
    private var lastCommittedChar: String? = null

    private fun bind26Key(view: View) {
        k26View = view
        val letterIds = "qwertyuiopasdfghjklzxcvbnm".map { c ->
            resources.getIdentifier("k26_$c", "id", packageName)
        }.filter { it != 0 }
        val candidateList = view.findViewById<LinearLayout>(R.id.k26_candidate_list)
        val emptyChipsContainer = view.findViewById<LinearLayout>(R.id.k26_candidate_empty_chips)  // 无候选时两图标放此层，整栏垂直居中不裁切
        val pinyinRow = view.findViewById<android.widget.TextView>(R.id.k26_pinyin_row)
        val mainContent = view.findViewById<View>(R.id.k26_main_content)  // 候选栏+按键整块，打开浮层时隐藏
        val switchOverlay = view.findViewById<View>(R.id.k26_keyboard_switch_overlay)
        val clipboardOverlay = view.findViewById<View>(R.id.k26_clipboard_overlay)
        var lastSuffixLen = 0  // 当前候选对应的拼音后缀长度，选中后只删这段，继续组词
        // 候选栏右侧：有候选时显示向下箭头，用户点过候选后显示叉号；点叉号恢复剪贴板/键盘两图标
        var hasSelectedCandidateFromBar = false
        val expandBtn = view.findViewById<android.widget.ImageButton>(R.id.k26_candidate_expand_btn)
        val expandContainer = view.findViewById<View>(R.id.k26_expand_picker_container)
        val expandGrid = view.findViewById<LinearLayout>(R.id.k26_expand_picker_grid)
        val expandDelete = view.findViewById<Button>(R.id.k26_expand_picker_delete)
        val keysContainer = view.findViewById<View>(R.id.k26_keys_container)

        // 换键盘浮层：4 张卡片 + 右上角对勾
        val imeCardIds = listOf(
            R.id.k26_ime_card_9key to MainActivity.KEYBOARD_TYPE_9KEY,
            R.id.k26_ime_card_26key to MainActivity.KEYBOARD_TYPE_26KEY,
            R.id.k26_ime_card_handwrite to MainActivity.KEYBOARD_TYPE_HANDWRITE,
            R.id.k26_ime_card_stroke to MainActivity.KEYBOARD_TYPE_STROKE
        )
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        var selectedTypeInOverlay = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
        fun updateImeCardSelection() {
            for ((id, type) in imeCardIds) {
                view.findViewById<FrameLayout>(id)?.setBackgroundResource(
                    if (type == selectedTypeInOverlay) R.drawable.bg_card_rounded_selected else R.drawable.bg_card_rounded
                )
            }
        }
        for ((id, type) in imeCardIds) {
            view.findViewById<FrameLayout>(id)?.setOnClickListener {
                keyHaptic(it)
                selectedTypeInOverlay = type
                updateImeCardSelection()
            }
        }
        updateImeCardSelection()
        view.findViewById<Button>(R.id.k26_switch_confirm)?.setOnClickListener {
            keyHaptic(it)
            val current = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
            prefs.edit().putString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, selectedTypeInOverlay).apply()
            mainContent?.visibility = View.VISIBLE
            switchOverlay?.visibility = View.GONE
            if (selectedTypeInOverlay != current) setInputView(onCreateInputView())
        }

        // 剪贴板浮层：关闭叉号
        val clipboardList = clipboardOverlay?.findViewById<LinearLayout>(R.id.k26_clipboard_list)
        val clipboardEmpty = clipboardOverlay?.findViewById<android.widget.TextView>(R.id.k26_clipboard_empty)
        clipboardOverlay?.findViewById<Button>(R.id.k26_clipboard_close)?.setOnClickListener {
            keyHaptic(it)
            clipboardOverlay.visibility = View.GONE
            mainContent?.visibility = View.VISIBLE
        }

        fun refresh26Keys() {
            // 拼音：字母键显示大写；英文且未按 Shift：显示小写；英文+Shift：显示大写
            val showUpper = !isEnglishMode26 || isShiftOn26
            letterIds.forEachIndexed { i, id ->
                view.findViewById<Button>(id)?.let { btn ->
                    val c = "qwertyuiopasdfghjklzxcvbnm"[i]
                    btn.text = if (showUpper) c.uppercaseChar().toString() else c.toString()
                }
            }
            view.findViewById<Button>(R.id.k26_zh_en)?.setBackgroundResource(
                if (isEnglishMode26) R.drawable.key_bg_26_active else R.drawable.key_bg_26
            )
            view.findViewById<Button>(R.id.k26_shift)?.setBackgroundResource(
                if (isShiftOn26) R.drawable.key_bg_26_active else R.drawable.key_bg_26
            )
        }

        fun refreshCandidates() {
            candidateList?.removeAllViews()
            emptyChipsContainer?.removeAllViews()
            emptyChipsContainer?.visibility = View.GONE
            lastSuffixLen = 0
            if (!isEnglishMode26 && pinyinBuffer26.isNotEmpty()) {
                val pinyin = pinyinBuffer26.toString()
                pinyinRow?.text = pinyin
                pinyinRow?.visibility = View.VISIBLE
                // 统一候选：切分 + 末段单字母即不完整音节（j/g/x 等一视同仁）+ 首字母联想，逻辑在 PinyinDict.getPinyinCandidates
                var baseCands = PinyinDict.getPinyinCandidates(pinyin)
                val dedup = PinyinDict.deduplicateRepeatedChars(pinyin)
                if (dedup != pinyin) {
                    baseCands = (baseCands + PinyinDict.getPinyinCandidates(dedup)).distinct()
                }
                var combined = UserMemory.mergePreferred(applicationContext, pinyin, baseCands).take(25)
                // 品牌名：打 meng 即优先联想 萌创匠盒
                if (pinyin.startsWith("meng") && "萌创匠盒" !in combined) {
                    combined = listOf("萌创匠盒") + combined.filter { it != "萌创匠盒" }.take(24)
                }
                val composingLen = pinyin.length
                combined.forEach { word ->
                    if (word == "萌创匠盒") {
                        // 候选1：图标，支持则上屏图标，否则上屏「萌创匠盒」
                        val iconChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, candidateList, false)
                        iconChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)
                            ?.setImageResource(R.drawable.mengchuangjianghe)
                        iconChip.setOnClickListener {
                            keyHaptic(iconChip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            if (currentAppShowsObjForIcon()) {
                                commitReplacingComposing(composingLen, word)
                            } else {
                                createMengchuangjiangheIconSpannable()?.let { span ->
                                    commitReplacingComposing(composingLen, span)
                                } ?: commitReplacingComposing(composingLen, word)
                            }
                            UserMemory.record(applicationContext, pinyin, word)
                            pinyinBuffer26.clear()
                            lastCommittedChar = if (currentAppShowsObjForIcon()) word.takeLast(1) else null
                            setComposingText("")
                            refreshCandidates()
                        }
                        candidateList?.addView(iconChip)
                        // 候选2：四个字，点击上屏「萌创匠盒」
                        val textChip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                        textChip?.text = word
                        textChip?.setOnClickListener {
                            keyHaptic(textChip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            commitReplacingComposing(composingLen, word)
                            UserMemory.record(applicationContext, pinyin, word)
                            pinyinBuffer26.clear()
                            lastCommittedChar = word.takeLast(1)
                            setComposingText("")
                            refreshCandidates()
                        }
                        candidateList?.addView(textChip)
                    } else {
                        val chip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                        chip?.text = word
                        chip?.setOnClickListener {
                            keyHaptic(chip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            commitReplacingComposing(composingLen, word)
                            UserMemory.record(applicationContext, pinyin, word)
                            pinyinBuffer26.clear()
                            lastCommittedChar = word.takeLast(1)
                            setComposingText("")
                            refreshCandidates()
                        }
                        candidateList?.addView(chip)
                    }
                }
                // 3）单字候选：最长可匹配后缀（方便只选一个字）
                var charCands = emptyList<String>()
                for (len in pinyin.length downTo 1) {
                    val suffix = pinyin.substring(pinyin.length - len)
                    charCands = PinyinDict.getCandidates(suffix)
                    if (charCands.isEmpty() && len <= 2) charCands = PinyinDict.getCandidatesByPrefix(suffix)
                    if (charCands.isNotEmpty()) {
                        lastSuffixLen = len
                        break
                    }
                }
                var candidates = charCands
                if (lastSuffixLen > 0) {
                    val suffix = pinyin.substring(pinyin.length - lastSuffixLen)
                    candidates = UserMemory.mergePreferred(applicationContext, suffix, candidates)
                }
                candidates = candidates.take(50)
                val suffixLenToRemove = lastSuffixLen
                candidates.forEach { hanzi ->
                    val chip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                    chip?.text = hanzi
                    chip?.setOnClickListener {
                        keyHaptic(chip)
                        hasSelectedCandidateFromBar = true
                        finishComposing()
                        commitReplacingComposing(suffixLenToRemove, hanzi)
                        if (suffixLenToRemove > 0) {
                            val suf = pinyin.substring(pinyin.length - suffixLenToRemove)
                            UserMemory.record(applicationContext, suf, hanzi)
                        }
                        lastCommittedChar = hanzi.take(1)
                        if (suffixLenToRemove > 0 && suffixLenToRemove <= pinyinBuffer26.length) {
                            pinyinBuffer26.setLength(pinyinBuffer26.length - suffixLenToRemove)
                            setComposingText(pinyinBuffer26)
                        } else {
                            pinyinBuffer26.clear()
                            setComposingText("")
                        }
                        refreshCandidates()
                    }
                    candidateList?.addView(chip)
                }
            } else if (!isEnglishMode26 && pinyinBuffer26.isEmpty() && lastCommittedChar != null) {
                // 联想：下一词预测 + 下一字（如 你→你好、你们、好、们）
                pinyinRow?.visibility = View.INVISIBLE
                val phrases = PinyinDict.getAssociationPhrases(lastCommittedChar!!).take(12)
                val chars = PinyinDict.getAssociationCandidates(lastCommittedChar!!)
                val assocOrdered = phrases + chars.filter { c -> c !in phrases }
                assocOrdered.take(20).forEach { item ->
                    if (item == "萌创匠盒") {
                        // 候选1：图标，支持则上屏图标，否则上屏「萌创匠盒」
                        val iconChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, candidateList, false)
                        iconChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)
                            ?.setImageResource(R.drawable.mengchuangjianghe)
                        iconChip.setOnClickListener {
                            keyHaptic(iconChip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            if (currentAppShowsObjForIcon()) {
                                commitText(item)
                            } else {
                                createMengchuangjiangheIconSpannable()?.let { commitText(it) } ?: commitText(item)
                            }
                            lastCommittedChar = if (currentAppShowsObjForIcon()) item.takeLast(1) else null
                            refreshCandidates()
                        }
                        candidateList?.addView(iconChip)
                        // 候选2：四个字
                        val textChip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                        textChip?.text = item
                        textChip?.setOnClickListener {
                            keyHaptic(textChip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            commitText(item)
                            lastCommittedChar = item.takeLast(1)
                            refreshCandidates()
                        }
                        candidateList?.addView(textChip)
                    } else {
                        val chip = layoutInflater.inflate(R.layout.item_candidate_chip, candidateList, false) as? Button
                        chip?.text = item
                        chip?.setOnClickListener {
                            keyHaptic(chip)
                            hasSelectedCandidateFromBar = true
                            finishComposing()
                            commitText(item)
                            lastCommittedChar = item.takeLast(1)
                            refreshCandidates()
                        }
                        candidateList?.addView(chip)
                    }
                }
            } else if (!isEnglishMode26 && pinyinBuffer26.isEmpty() && lastCommittedChar == null) {
                // 无输入时：两图标放在居中层，整栏垂直居中，可顶到拼音区上不裁切
                pinyinRow?.visibility = View.INVISIBLE
                val keyboardChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, emptyChipsContainer, false)
                keyboardChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)
                    ?.setImageResource(R.drawable.ic_candidate_keyboard)
                keyboardChip.setOnClickListener {
                    keyHaptic(it)
                    selectedTypeInOverlay = prefs.getString(MainActivity.KEY_DEFAULT_KEYBOARD_TYPE, MainActivity.KEYBOARD_TYPE_26KEY) ?: MainActivity.KEYBOARD_TYPE_26KEY
                    updateImeCardSelection()
                    mainContent?.visibility = View.INVISIBLE
                    switchOverlay?.visibility = View.VISIBLE
                }
                emptyChipsContainer?.addView(keyboardChip)
                val scissorsChip = layoutInflater.inflate(R.layout.item_candidate_chip_icon, emptyChipsContainer, false)
                scissorsChip.findViewById<android.widget.ImageView>(R.id.candidate_chip_icon)
                    ?.setImageResource(R.drawable.ic_candidate_clipboard)
                scissorsChip.setOnClickListener {
                    keyHaptic(it)
                    mainContent?.visibility = View.INVISIBLE
                    clipboardOverlay?.visibility = View.VISIBLE
                    clipboardList?.removeAllViews()
                    val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clipboardEmpty?.visibility = View.GONE
                        clipboardList?.visibility = View.VISIBLE
                        for (i in 0 until clip.itemCount) {
                            val item = clip.getItemAt(i)
                            val text = item?.text?.toString() ?: continue
                            val row = android.widget.TextView(this@MengBoxInputMethodService).apply {
                                setTextColor(Color.WHITE)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
                                this.text = text
                                maxLines = 4
                                ellipsize = android.text.TextUtils.TruncateAt.END
                                setOnClickListener {
                                    keyHaptic(it)
                                    commitText(text)
                                    clipboardOverlay?.visibility = View.GONE
                                    mainContent?.visibility = View.VISIBLE
                                }
                            }
                            clipboardList?.addView(row)
                        }
                    } else {
                        clipboardEmpty?.visibility = View.VISIBLE
                        clipboardList?.visibility = View.GONE
                    }
                }
                emptyChipsContainer?.addView(scissorsChip)
                emptyChipsContainer?.visibility = View.VISIBLE
            } else {
                pinyinRow?.visibility = View.INVISIBLE
                lastCommittedChar = null
            }

            // 候选栏右侧：有候选或联想时显示向下箭头/叉号，无输入时隐藏
            val showExpandBtn = !isEnglishMode26 && (pinyinBuffer26.isNotEmpty() || lastCommittedChar != null)
            expandBtn?.visibility = if (showExpandBtn) View.VISIBLE else View.GONE
            if (showExpandBtn) {
                expandBtn?.setImageResource(
                    if (hasSelectedCandidateFromBar) R.drawable.ic_candidate_expand_close else R.drawable.ic_candidate_expand_down
                )
            }
        }

        // 展开单字选择区：点击向下箭头打开，6字一行竖滑，右下角固定删除键
        fun fillExpandGrid() {
            expandGrid?.removeAllViews()
            val pinyin = pinyinBuffer26.toString()
            if (pinyin.isEmpty()) return
            val pre = pinyin.lowercase()
            val raw = PinyinDict.getCandidatesByPrefix(pinyin)
            // 只保留拼音真正以当前前缀开头的字，避免如按 f 却出现 不(bu)
            val chars = raw.filter { it.length == 1 && PinyinDict.getPinyinForChar(it[0]).any { py -> py.startsWith(pre) } }
            val COLS = 6
            chars.chunked(COLS).forEach { rowChars ->
                val row = android.widget.LinearLayout(this@MengBoxInputMethodService).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dpToPx() }
                }
                rowChars.forEach { c ->
                    val btn = layoutInflater.inflate(R.layout.item_candidate_chip, row, false) as? Button
                    btn?.text = c
                    btn?.layoutParams = android.widget.LinearLayout.LayoutParams(0, 44.dpToPx(), 1f)
                    btn?.setOnClickListener {
                        keyHaptic(it)
                        hasSelectedCandidateFromBar = true
                        finishComposing()
                        commitText(c)
                        pinyinBuffer26.clear()
                        setComposingText("")
                        lastCommittedChar = c
                        expandContainer?.visibility = View.GONE
                        keysContainer?.visibility = View.VISIBLE
                        refreshCandidates()
                    }
                    row.addView(btn)
                }
                expandGrid?.addView(row)
            }
        }

        expandBtn?.setOnClickListener {
            keyHaptic(it)
            // 已展开时再点向下箭头：关闭展开（toggle）
            if (expandContainer?.visibility == View.VISIBLE) {
                expandContainer?.visibility = View.GONE
                keysContainer?.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (hasSelectedCandidateFromBar) {
                // 点叉号：收起，恢复剪贴板/键盘两图标
                hasSelectedCandidateFromBar = false
                pinyinBuffer26.clear()
                lastCommittedChar = null
                finishComposing()
                setComposingText("")
                expandContainer?.visibility = View.GONE
                keysContainer?.visibility = View.VISIBLE
                refreshCandidates()
            } else {
                // 点向下箭头：展开单字列表
                expandContainer?.visibility = View.VISIBLE
                keysContainer?.visibility = View.GONE
                fillExpandGrid()
            }
        }

        expandDelete?.setOnClickListener {
            keyHaptic(it)
            if (pinyinBuffer26.isNotEmpty()) {
                pinyinBuffer26.setLength(pinyinBuffer26.length - 1)
                setComposingText(pinyinBuffer26)
                fillExpandGrid()
                if (pinyinBuffer26.isEmpty()) {
                    expandContainer?.visibility = View.GONE
                    keysContainer?.visibility = View.VISIBLE
                    refreshCandidates()
                }
            }
        }

        letterIds.forEachIndexed { i, id ->
            view.findViewById<Button>(id)?.let { btn ->
                btn.setOnClickListener {
                    keyHaptic(btn)
                    val c = "qwertyuiopasdfghjklzxcvbnm"[i]
                    if (isEnglishMode26) {
                        val toCommit = if (isShiftOn26) c.uppercaseChar().toString() else c.toString()
                        commitText(toCommit)
                        if (isShiftOn26) {
                            isShiftOn26 = false
                            refresh26Keys()
                        }
                    } else {
                        lastCommittedChar = null
                        hasSelectedCandidateFromBar = false  // 新输入时显示向下箭头
                        pinyinBuffer26.append(c)
                        setComposingText(pinyinBuffer26)
                        refreshCandidates()
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.k26_zh_en)?.let { btn ->
            btn.setOnClickListener {
                keyHaptic(btn)
                isEnglishMode26 = !isEnglishMode26
                if (isEnglishMode26) {
                    pinyinBuffer26.clear()
                    lastCommittedChar = null
                    finishComposing()
                    setComposingText("")
                    refreshCandidates()
                }
                if (!isEnglishMode26) isShiftOn26 = false
                refresh26Keys()
            }
        }
        view.findViewById<Button>(R.id.k26_shift)?.let { btn ->
            btn.setOnClickListener {
                keyHaptic(btn)
                if (isEnglishMode26) {
                    isShiftOn26 = !isShiftOn26
                    refresh26Keys()
                }
            }
        }
        // 删除键：点击删一个；长按连续删（先 500ms 再每 80ms 一次）
        view.findViewById<Button>(R.id.k26_back)?.let { backBtn ->
            var repeatRunnable: Runnable? = null
            fun doBack() {
                keyHaptic(backBtn)
                if (!isEnglishMode26 && pinyinBuffer26.isNotEmpty()) {
                    pinyinBuffer26.deleteCharAt(pinyinBuffer26.length - 1)
                    setComposingText(pinyinBuffer26)
                    refreshCandidates()
                } else {
                    deleteBack()
                }
            }
            backBtn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        doBack()
                        repeatRunnable = object : Runnable {
                            override fun run() {
                                doBack()
                                backBtn.postDelayed(this, 80)
                            }
                        }
                        backBtn.postDelayed(repeatRunnable!!, 500)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeatRunnable?.let { backBtn.removeCallbacks(it) }
                        repeatRunnable = null
                        true
                    }
                    else -> false
                }
            }
        }
        view.findViewById<Button>(R.id.k26_space)?.let { btn ->
            btn.setOnClickListener {
                keyHaptic(btn)
                if (!isEnglishMode26 && pinyinBuffer26.isNotEmpty()) {
                    val pinyin = pinyinBuffer26.toString()
                    var baseCands = PinyinDict.getPinyinCandidates(pinyin)
                    val dedup = PinyinDict.deduplicateRepeatedChars(pinyin)
                    if (dedup != pinyin) {
                        baseCands = (baseCands + PinyinDict.getPinyinCandidates(dedup)).distinct()
                    }
                    var combined = UserMemory.mergePreferred(applicationContext, pinyin, baseCands).take(25)
                    if (pinyin.startsWith("meng") && "萌创匠盒" !in combined) {
                        combined = listOf("萌创匠盒") + combined.filter { it != "萌创匠盒" }.take(24)
                    }
                    if (combined.isNotEmpty()) {
                        finishComposing()
                        val word = combined.first()
                        commitReplacingComposing(pinyin.length, word)
                        UserMemory.record(applicationContext, pinyin, word)
                        pinyinBuffer26.clear()
                        lastCommittedChar = word.takeLast(1)
                        setComposingText("")
                        refreshCandidates()
                    } else {
                        var cands = emptyList<String>()
                        var len = 0
                        for (l in pinyin.length downTo 1) {
                            val suf = pinyin.substring(pinyin.length - l)
                            cands = PinyinDict.getCandidates(suf)
                            if (cands.isEmpty() && l <= 2) cands = PinyinDict.getCandidatesByPrefix(suf)
                            if (cands.isNotEmpty()) { len = l; break }
                        }
                        if (cands.isNotEmpty() && len > 0) {
                            finishComposing()
                            val char = UserMemory.mergePreferred(applicationContext, pinyin.substring(pinyin.length - len), cands).first()
                            val suf = pinyin.substring(pinyin.length - len)
                            commitReplacingComposing(len, char)
                            UserMemory.record(applicationContext, suf, char)
                            lastCommittedChar = char.take(1)
                            pinyinBuffer26.setLength(pinyinBuffer26.length - len)
                            setComposingText(pinyinBuffer26)
                            refreshCandidates()
                        } else {
                            lastCommittedChar = null
                            commitText(" ")
                        }
                    }
                } else {
                    lastCommittedChar = null
                    commitText(" ")
                }
            }
        }
        view.findViewById<Button>(R.id.k26_comma)?.let { btn ->
            btn.setOnClickListener { keyHaptic(btn); commitText(",") }
        }
        view.findViewById<Button>(R.id.k26_period)?.let { btn ->
            btn.setOnClickListener { keyHaptic(btn); commitText(".") }
        }
        // 有拼音时：第一次回车 = 把拼音当文字上屏（如 ni），不上屏第一个汉字；第二次回车 = 换行。无拼音时直接换行。
        view.findViewById<Button>(R.id.k26_enter)?.let { btn ->
            btn.setOnClickListener {
                keyHaptic(btn)
                if (!isEnglishMode26 && pinyinBuffer26.isNotEmpty()) {
                    val pinyin = pinyinBuffer26.toString()
                    finishComposing()
                    commitReplacingComposing(pinyin.length, pinyin)
                    pinyinBuffer26.clear()
                    lastCommittedChar = null
                    setComposingText("")
                    refreshCandidates()
                } else {
                    commitText("\n")
                }
            }
        }
        view.findViewById<Button>(R.id.k26_123)?.let { b ->
            b.setOnClickListener { keyHaptic(b) /* 后续可切数字键盘 */ }
        }
        refresh26Keys()
        refreshCandidates()
    }
}
