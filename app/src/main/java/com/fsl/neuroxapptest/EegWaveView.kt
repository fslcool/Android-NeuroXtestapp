package com.fsl.neuroxapptest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class EegWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Marker(
        val type: String,
        val labelZh: String,
        val labelEn: String,
        var queueIndex: Int
    )

    private val markers = mutableListOf<Marker>()

    private val channelNames = mutableListOf(
        "AF7", "AF3", "AFZ", "AF4", "AF8", "P3", "PZ", "P4"
    )

    private val queueSize = 200
    private val intervalMs = 100L
    private val amplitudeDp = 12f

    private val topPaddingDp = 28f
    private val bottomPaddingDp = 24f

    private val queues: MutableList<ArrayDeque<Float>> = mutableListOf()

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            generateSamples()
            invalidate()
            handler.postDelayed(this, intervalMs)
        }
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1BA39A")
        strokeWidth = dpToPx(1.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = spToPx(10f)
        textAlign = Paint.Align.LEFT
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = dpToPx(0.5f)
    }

    private val markerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f
        )
    }

    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = spToPx(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val markerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        for (i in 0 until channelNames.size) {
            queues.add(ArrayDeque())
        }
    }

    fun addMarker(type: String) {
        val (zh, en) = when (type) {
            "START" -> "实验开始" to "START"
            "PAUSE" -> "实验暂停" to "PAUSE"
            "RESUME" -> "实验继续" to "RESUME"
            "END" -> "实验结束" to "END"
            "TERMINATE" -> "实验终止" to "TERMINATE"
            else -> "事件" to type
        }
        val idx = ((queues.getOrNull(0)?.size ?: 1) - 1).coerceAtLeast(0)
        markers.add(Marker(type, zh, en, idx))
        invalidate()
    }

    fun clearMarkers() {
        markers.clear()
        invalidate()
    }

    fun setChannelNames(names: List<String>) {
        channelNames.clear()
        channelNames.addAll(names)
        queues.clear()
        for (i in 0 until channelNames.size) {
            queues.add(ArrayDeque())
        }
        invalidate()
    }

    fun pushSample(channelIndex: Int, value: Float) {
        if (channelIndex !in queues.indices) return
        val q = queues[channelIndex]
        if (q.size >= queueSize) q.removeFirst()
        q.addLast(value)
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    private fun generateSamples() {
        // ⭐ 记录"这一 tick 之前，队列是否已满"
        val wasFull = (queues.getOrNull(0)?.size ?: 0) >= queueSize

        for (q in queues) {
            val last = q.lastOrNull() ?: 0f
            val newValue = last * 0.85f + (Random.nextFloat() * 2f - 1f) * amplitudeDp * 0.4f
            val clamped = newValue.coerceIn(-amplitudeDp, amplitudeDp)
            if (q.size >= queueSize) q.removeFirst()
            q.addLast(clamped)
        }

        // ⭐ 队列满时，标记的 queueIndex 才递减
        for (m in markers) {
            if (wasFull) m.queueIndex--
        }
        markers.removeAll { it.queueIndex < 0 }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val n = channelNames.size
        if (n == 0) return

        val topPadding = dpToPx(topPaddingDp)
        val bottomPadding = dpToPx(bottomPaddingDp)
        val availableHeight = height - topPadding - bottomPadding
        val bandHeight = availableHeight / n

        val paddingLeft = dpToPx(40f)
        val paddingRight = dpToPx(8f)
        val drawWidth = width - paddingLeft - paddingRight

        for (i in 0 until n) {
            val centerY = topPadding + bandHeight * i + bandHeight / 2f
            val name = channelNames[i]
            val queue = queues[i]

            canvas.drawLine(
                paddingLeft, centerY,
                width - paddingRight, centerY,
                baselinePaint
            )

            val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(name, dpToPx(4f), textY, textPaint)

            if (queue.isEmpty()) continue

            val path = Path()
            val step = if (queueSize > 1) drawWidth / (queueSize - 1) else 0f
            val startOffset = drawWidth - (queue.size - 1) * step

            var first = true
            var x = paddingLeft + startOffset
            for (v in queue) {
                val y = centerY + dpToPx(v)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            canvas.drawPath(path, linePaint)
        }

        drawMarkers(
            canvas,
            paddingLeft,
            drawWidth,
            topPadding,
            topPadding + availableHeight
        )
    }

    private fun drawMarkers(
        canvas: Canvas,
        paddingLeft: Float,
        drawWidth: Float,
        waveTopY: Float,
        waveBottomY: Float
    ) {
        if (markers.isEmpty()) return

        val step = if (queueSize > 1) drawWidth / (queueSize - 1) else 0f
        val currentQueueSize = queues.getOrNull(0)?.size ?: 0
        val startOffset = drawWidth - (currentQueueSize - 1) * step

        for (m in markers) {
            // ⭐ 用 startOffset + queueIndex * step（和波形一致）
            val x = paddingLeft + startOffset + m.queueIndex * step

            if (x < paddingLeft || x > paddingLeft + drawWidth) continue

            val color = when (m.type) {
                "START" -> Color.parseColor("#4CAF50")
                "PAUSE" -> Color.parseColor("#FF9800")       // 橙
                "RESUME" -> Color.parseColor("#03A9F4")      // 浅蓝
                "END" -> Color.parseColor("#2196F3")
                "TERMINATE" -> Color.parseColor("#F44336")
                else -> Color.GRAY
            }
            markerLinePaint.color = color

            canvas.drawLine(x, waveTopY, x, waveBottomY, markerLinePaint)
            drawLabel(canvas, x, waveTopY - dpToPx(12f), m.labelZh, color)
            drawLabel(canvas, x, waveBottomY + dpToPx(12f), m.labelEn, color)
        }
    }

    private fun drawLabel(canvas: Canvas, cx: Float, cy: Float, text: String, bgColor: Int) {
        val textWidth = markerTextPaint.measureText(text)
        val paddingH = dpToPx(6f)
        val paddingV = dpToPx(3f)
        val rectW = textWidth + paddingH * 2
        val rectH = markerTextPaint.textSize + paddingV * 2

        markerBgPaint.color = bgColor
        val rect = RectF(
            cx - rectW / 2f, cy - rectH / 2f,
            cx + rectW / 2f, cy + rectH / 2f
        )
        val radius = dpToPx(4f)
        canvas.drawRoundRect(rect, radius, radius, markerBgPaint)

        val textY = cy - (markerTextPaint.descent() + markerTextPaint.ascent()) / 2f
        canvas.drawText(text, cx, textY, markerTextPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}