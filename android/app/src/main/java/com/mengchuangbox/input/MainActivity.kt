package com.mengchuangbox.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

/**
 * 引导页：启用→切换→选择键盘类型(9键/26键/手写/笔画)→确定进入首页。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var onboardingContainer: LinearLayout
    private var keyboardStyleContainer: View? = null
    private lateinit var homeContainer: View
    private lateinit var btnPillEnable: Button
    private lateinit var btnPillSwitch: Button

    private var selectedKeyboardType: String = KEYBOARD_TYPE_26KEY
    private val cardIds = listOf(
        R.id.card_9key to KEYBOARD_TYPE_9KEY,
        R.id.card_26key to KEYBOARD_TYPE_26KEY,
        R.id.card_handwrite to KEYBOARD_TYPE_HANDWRITE,
        R.id.card_stroke to KEYBOARD_TYPE_STROKE
    )

    /** 系统里可能存 "package/.ServiceName" 或 "package/package.ServiceName" */
    private val imeComponent: String by lazy {
        ComponentName(this, MengBoxInputMethodService::class.java).flattenToString()
    }
    private val imeComponentShort: String by lazy {
        packageName + "/." + MengBoxInputMethodService::class.java.simpleName
    }

    /** 监听系统当前输入法、已启用输入法列表变化，实时刷新按钮状态 */
    private var settingsObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        onboardingContainer = findViewById(R.id.onboarding_container)
        keyboardStyleContainer = findViewById(R.id.keyboard_style_container)
        homeContainer = findViewById(R.id.home_container)
        btnPillEnable = findViewById(R.id.btn_pill_enable)
        btnPillSwitch = findViewById(R.id.btn_pill_switch)

        // 若曾完成引导但当前未启用或未切换为本输入法 → 回到引导页，让用户重新启用/切换
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            if (isOurImeEnabled() && isOurImeSelected()) {
                showHome()
                return
            }
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
        }

        setupPills()
        setupKeyboardStyleCards()
        refreshPillState()
    }

    override fun onResume() {
        super.onResume()
        // 在首页时也检查：若用户切走了输入法或禁用了本输入法，回到引导页
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            if (!isOurImeEnabled() || !isOurImeSelected()) {
                prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
                showOnboardingAgain()
            }
            registerImeSettingsObserver()
            return
        }
        refreshPillState()
        registerImeSettingsObserver()
    }

    /** 从首页回到引导页（未启用或未切换为本输入法时调用） */
    private fun showOnboardingAgain() {
        homeContainer.visibility = View.GONE
        keyboardStyleContainer?.visibility = View.GONE
        onboardingContainer.visibility = View.VISIBLE
        refreshPillState()
    }

    override fun onPause() {
        super.onPause()
        unregisterImeSettingsObserver()
    }

    /** 注册对「当前输入法」「已启用输入法」的监听，切换键盘时立即刷新 */
    private fun registerImeSettingsObserver() {
        if (settingsObserver != null) return
        val handler = Handler(Looper.getMainLooper())
        settingsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                runOnUiThread { refreshPillState() }
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            settingsObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
            false,
            settingsObserver!!
        )
    }

    private fun unregisterImeSettingsObserver() {
        settingsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            settingsObserver = null
        }
    }

    private fun setupPills() {
        // 第一：启用萌创匠盒输入法 → 打开输入法设置
        btnPillEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        // 第二：切换萌创匠盒输入法 → 弹出键盘列表，选本输入法
        btnPillSwitch.setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }
    }

    private fun setupKeyboardStyleCards() {
        for ((id, type) in cardIds) {
            findViewById<FrameLayout>(id).setOnClickListener {
                selectedKeyboardType = type
                updateKeyboardStyleCardSelection()
            }
        }
        findViewById<Button>(R.id.btn_keyboard_style_confirm).setOnClickListener {
            prefs.edit()
                .putBoolean(KEY_ONBOARDING_DONE, true)
                .putString(KEY_DEFAULT_KEYBOARD_TYPE, selectedKeyboardType)
                .apply()
            showHome()
        }
        updateKeyboardStyleCardSelection()
    }

    private fun updateKeyboardStyleCardSelection() {
        for ((id, type) in cardIds) {
            val card = findViewById<FrameLayout>(id)
            card.setBackgroundResource(
                if (type == selectedKeyboardType) R.drawable.bg_card_rounded_selected
                else R.drawable.bg_card_rounded
            )
        }
    }

    private fun refreshPillState() {
        val enabled = isOurImeEnabled()
        val selected = isOurImeSelected()

        if (enabled && selected) {
            // 两步都完成：隐藏两个胶囊，显示键盘类型选择（9键/26键/手写/笔画）
            onboardingContainer.visibility = View.GONE
            keyboardStyleContainer?.visibility = View.VISIBLE
            return
        }

        onboardingContainer.visibility = View.VISIBLE
        keyboardStyleContainer?.visibility = View.GONE

        // 第一胶囊：启用萌创匠盒输入法（已启用则变灰）
        btnPillEnable.isEnabled = !enabled
        btnPillEnable.setBackgroundResource(
            if (enabled) R.drawable.bg_capsule_disabled else R.drawable.bg_capsule
        )
        btnPillEnable.alpha = if (enabled) 0.9f else 1f

        // 第二胶囊：切换萌创匠盒输入法（已选为当前键盘则变灰）
        btnPillSwitch.isEnabled = !selected
        btnPillSwitch.setBackgroundResource(
            if (selected) R.drawable.bg_capsule_disabled else R.drawable.bg_capsule
        )
        btnPillSwitch.alpha = if (selected) 0.9f else 1f
    }

    private fun isOurImeEnabled(): Boolean {
        val list = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        // 系统可能存 "包名/.Service名" 或 "包名/包名.Service名"
        if (list.contains(imeComponent)) return true
        if (list.contains(imeComponentShort)) return true
        return list.contains(packageName) && list.contains("MengBoxInputMethodService")
    }

    private fun isOurImeSelected(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        if (current == imeComponent) return true
        if (current == imeComponentShort) return true
        // 系统可能带子类型后缀，如 "包名/.Service;子类型"
        if (current.startsWith(imeComponent)) return true
        if (current.startsWith(imeComponentShort)) return true
        return current.contains(packageName) && current.contains("MengBoxInputMethodService")
    }

    private fun showHome() {
        onboardingContainer.visibility = View.GONE
        keyboardStyleContainer?.visibility = View.GONE
        homeContainer.visibility = View.VISIBLE
        setupHomeContent()
    }

    /** 首页：搜索框 + 两列皮肤列表，仅从静态托管远程拉取，无本地皮肤 */
    private var skinAdapter: SkinAdapter? = null
    private var allSkins: List<Skin> = emptyList()

    private fun setupHomeContent() {
        val searchEdit = findViewById<EditText>(R.id.home_search_edit)
        val recycler = findViewById<RecyclerView>(R.id.home_skin_list)
        searchEdit?.isFocusableInTouchMode = true
        searchEdit?.requestFocus()
        recycler?.layoutManager = GridLayoutManager(this, 2)
        val staticBase = prefs.getString(KEY_STATIC_BASE_URL, null)?.trim()?.ifBlank { null } ?: DEFAULT_STATIC_BASE_URL
        skinAdapter = SkinAdapter(emptyList()) { skin -> // 点开查看，不在此处拉取；详情页内才可下载、应用
            val isDownloaded = File(SkinLoader.getSkinsDir(this), skin.id).exists()
            val intent = android.content.Intent(this, SkinDetailActivity::class.java).apply {
                putExtra(SkinDetailActivity.EXTRA_SKIN_ID, skin.id)
                putExtra(SkinDetailActivity.EXTRA_SKIN_NAME, skin.name)
                putExtra(SkinDetailActivity.EXTRA_PREVIEW_URL, skin.previewUrl)
                putExtra(SkinDetailActivity.EXTRA_BACKGROUND_URL, skin.backgroundUrl)
                putExtra(SkinDetailActivity.EXTRA_SKIN_MANIFEST_URL, skin.skinManifestUrl)
                putExtra(SkinDetailActivity.EXTRA_INTRO_URL, skin.introUrl)
                putExtra(SkinDetailActivity.EXTRA_BASE_URL, staticBase)
                putExtra(SkinDetailActivity.EXTRA_IS_DOWNLOADED, isDownloaded)
            }
            startActivityForResult(intent, REQUEST_SKIN_DETAIL)
        }
        recycler?.adapter = skinAdapter
        RemoteSkinFetcher.fetchSkinList(staticBase) { success, list ->
            allSkins = if (success) list else emptyList()
            skinAdapter?.setItems(allSkins)
        }
        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                val filtered = if (q.isEmpty()) allSkins else allSkins.filter { it.name.lowercase().contains(q) }
                skinAdapter?.setItems(filtered)
            }
        })
    }

    companion object {
        internal const val PREFS_NAME = "mengbox_ime"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        internal const val KEY_DEFAULT_KEYBOARD_TYPE = "default_keyboard_type"
        /** 远程词库 TXT 的 URL（与 RemoteWordListFetcher 共用，存于此便于在设置里持久化） */
        internal const val KEY_REMOTE_WORDLIST_URL = "remote_wordlist_url"
        /** 默认远程词库地址（GitHub 仓库 wordlist-public，社区维护，合并后 APK 自动拉取最新） */
        internal const val DEFAULT_REMOTE_WORDLIST_URL = "https://raw.githubusercontent.com/zxcvvvvvbnm/mengchuangjianghe-input-method/main/wordlist-public/wordlist.txt"
        /** 静态站根 URL（皮肤列表 skins/list.json 拉取用，默认指向本仓库 main 分支，社区更新皮肤后 APK 拉取即得最新） */
        internal const val DEFAULT_STATIC_BASE_URL = "https://raw.githubusercontent.com/zxcvvvvvbnm/mengchuangjianghe-input-method/main"
        internal const val KEY_STATIC_BASE_URL = "static_base_url"
        /** 已选皮肤背景图 URL（键盘壁纸，仅当未选「已下载皮肤」时使用） */
        internal const val KEY_SELECTED_SKIN_BACKGROUND_URL = "selected_skin_background_url"
        /** 已选皮肤 ID（已下载到本地的皮肤，优先于 BACKGROUND_URL） */
        internal const val KEY_SELECTED_SKIN_ID = "selected_skin_id"

        const val KEYBOARD_TYPE_9KEY = "9key"
        const val KEYBOARD_TYPE_26KEY = "26key"
        const val KEYBOARD_TYPE_HANDWRITE = "handwrite"
        const val KEYBOARD_TYPE_STROKE = "stroke"
        const val REQUEST_SKIN_DETAIL = 100
    }
}
