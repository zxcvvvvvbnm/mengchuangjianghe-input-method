package com.mengchuangbox.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * 语音输入弹窗中的波纹：横着的大条带里从左到右一排竖条（竖着条），随节奏起伏。
 */
class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6200EE.toInt()
        style = Paint.Style.FILL
    }
    private val barCount = 24
    private fun barWidth(): Float = (width / (barCount * 2f)).coerceAtLeast(4f)
    private fun gap(): Float = barWidth() * 0.4f
    private val heights = FloatArray(barCount) { 0.2f + it * 0.02f }
    private val speeds = FloatArray(barCount) { 0.02f + it * 0.002f }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            for (i in 0 until barCount) {
                heights[i] += speeds[i]
                if (heights[i] > 0.95f) { heights[i] = 0.95f; speeds[i] = -speeds[i] }
                if (heights[i] < 0.15f) { heights[i] = 0.15f; speeds[i] = -speeds[i] }
            }
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    fun startWaveform() {
        running = true
        handler.post(tick)
    }

    fun stopWaveform() {
        running = false
        handler.removeCallbacks(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val bw = barWidth()
        val g = gap()
        val totalW = barCount * (bw + g) - g
        var x = (w - totalW) / 2f
        for (i in 0 until barCount) {
            val barH = h * (0.5f * heights[i] + 0.2f)
            val top = (h - barH) / 2f
            canvas.drawRoundRect(x, top, x + bw, top + barH, bw / 2f, bw / 2f, paint)
            x += bw + g
        }
    }
}
