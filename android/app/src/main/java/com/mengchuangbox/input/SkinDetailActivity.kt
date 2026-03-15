package com.mengchuangbox.input

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.URL
import java.util.concurrent.Executors

/**
 * 皮肤详情：查看介绍、下载到本地、应用为当前键盘。仅用户点开并点「下载」才拉取资源，点「应用」才生效。
 */
class SkinDetailActivity : AppCompatActivity() {

    private val loadExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var skinId: String = ""
    private var skinName: String = ""
    private var previewUrl: String? = null
    private var isDownloaded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_detail)
        skinId = intent.getStringExtra(EXTRA_SKIN_ID) ?: ""
        skinName = intent.getStringExtra(EXTRA_SKIN_NAME) ?: skinId
        previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL)
        isDownloaded = intent.getBooleanExtra(EXTRA_IS_DOWNLOADED, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = skinName

        val titleView = findViewById<TextView>(R.id.skin_detail_title)
        val previewView = findViewById<ImageView>(R.id.skin_detail_preview)
        val introView = findViewById<TextView>(R.id.skin_detail_intro)
        val btnDownload = findViewById<Button>(R.id.skin_detail_download)
        val btnApply = findViewById<Button>(R.id.skin_detail_apply)

        titleView.text = skinName
        refreshDownloadState()
        loadPreview(previewView)
        loadIntro(introView)

        btnDownload.setOnClickListener {
            val skin = Skin(
                id = skinId,
                name = skinName,
                previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL),
                backgroundUrl = intent.getStringExtra(EXTRA_BACKGROUND_URL),
                skinManifestUrl = intent.getStringExtra(EXTRA_SKIN_MANIFEST_URL),
                introUrl = intent.getStringExtra(EXTRA_INTRO_URL)
            )
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: MainActivity.DEFAULT_STATIC_BASE_URL
            SkinDownloader.download(this, baseUrl, skin) { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (success) {
                    isDownloaded = true
                    refreshDownloadState()
                }
            }
        }

        btnApply.setOnClickListener {
            if (!isDownloaded) {
                Toast.makeText(this, "请先下载该皮肤", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(MainActivity.KEY_SELECTED_SKIN_ID, skinId)
                .remove(MainActivity.KEY_SELECTED_SKIN_BACKGROUND_URL) // 改用本地皮肤，不再用远程背景 URL
                .apply()
            Toast.makeText(this, "已应用「$skinName」", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun refreshDownloadState() {
        isDownloaded = File(SkinLoader.getSkinsDir(this), skinId).exists()
        val btnDownload = findViewById<Button>(R.id.skin_detail_download)
        val btnApply = findViewById<Button>(R.id.skin_detail_apply)
        btnDownload.isEnabled = !isDownloaded
        btnDownload.text = if (isDownloaded) "已下载" else "下载"
        btnApply.isEnabled = isDownloaded
    }

    private fun loadPreview(imageView: ImageView) {
        val localPreview = File(SkinLoader.getSkinsDir(this), "$skinId/preview.png")
        if (localPreview.exists()) {
            BitmapFactory.decodeFile(localPreview.absolutePath)?.let { imageView.setImageBitmap(it) }
            return
        }
        val url = previewUrl
        if (url.isNullOrBlank()) return
        loadExecutor.execute {
            try {
                URL(url).openStream().use { stream ->
                    val bm = BitmapFactory.decodeStream(stream)
                    mainHandler.post { if (bm != null) imageView.setImageBitmap(bm) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadIntro(textView: TextView) {
        val introUrl = intent.getStringExtra(EXTRA_INTRO_URL) ?: return
        loadExecutor.execute {
            try {
                val conn = java.net.URL(introUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.connect()
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val obj = org.json.JSONObject(json)
                    val arr = obj.optJSONArray("items")
                    val sb = StringBuilder()
                    if (arr != null) for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        val type = item?.optString("type", "")
                        val name = item?.optString("name", "")
                        sb.append("• ").append(name).append(" (").append(type).append(")\n")
                    }
                    mainHandler.post { textView.text = if (sb.isEmpty()) "暂无介绍" else sb.toString() }
                }
            } catch (_: Exception) {
                mainHandler.post { textView.text = "暂无介绍" }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_SKIN_ID = "skin_id"
        const val EXTRA_SKIN_NAME = "skin_name"
        const val EXTRA_PREVIEW_URL = "preview_url"
        const val EXTRA_BACKGROUND_URL = "background_url"
        const val EXTRA_SKIN_MANIFEST_URL = "skin_manifest_url"
        const val EXTRA_INTRO_URL = "intro_url"
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_IS_DOWNLOADED = "is_downloaded"
    }
}
