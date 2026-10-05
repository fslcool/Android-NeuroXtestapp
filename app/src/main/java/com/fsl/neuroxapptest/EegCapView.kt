package com.fsl.neuroxapptest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.json.JSONArray

/**
 * 脑电帽示意图的自定义 View。
 *
 * <p>背景（脑电帽外形）由 xml 里另一个 ImageView 提供，
 * 本 View 只负责画 8 个动态通道点。</p>
 *
 * <p>外部调 setChannels() 更新状态，自动重绘。</p>
 */
class EegCapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ============ 8 个通道的位置（相对 View 宽高的比例） ============
    // 键：通道名（和大写一致）
    // 值：Pair(x比例, y比例)
    private val positions: Map<String, Pair<Float, Float>> = mapOf(
        "AF7" to (0.20f to 0.32f),
        "AF3" to (0.35f to 0.35f),
        "AFZ" to (0.50f to 0.35f),
        "AF4" to (0.65f to 0.35f),
        "AF8" to (0.80f to 0.32f),
        "P3"  to (0.35f to 0.70f),
        "PZ"  to (0.50f to 0.70f),
        "P4"  to (0.65f to 0.70f)
    )

    // ============ 状态存储 ============
    // 键：通道名；值：状态字符串（"OK" / "Bad" / "Open"）
    private val channelStates = mutableMapOf<String, String>()

    // ============ 样式 ============
    // 圆半径（dp）
    private val circleRadiusDp = 22f

    // 通道名字号（sp）
    private val textSizeSp = 14f

    // 颜色
    private val colorOk = Color.parseColor("#1BA39A")     // 青绿
    private val colorBad = Color.parseColor("#F44336")    // 红
    private val colorOpen = Color.parseColor("#FF5722")   // 橙红
    private val colorText = Color.WHITE

    // ============ Paint ============
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ============ 外部接口 ============

    /**
     * 更新 8 个通道的状态。
     *
     * @param channels CS 发来的 channels JSONArray，每个元素有 name / status 字段
     */
    fun setChannels(channels: JSONArray) {
        channelStates.clear()
        for (i in 0 until channels.length()) {
            val ch = channels.getJSONObject(i)
            val name = ch.optString("name", "")
            val status = ch.optString("status", "Open")
            channelStates[name] = status
        }
        invalidate()   // 触发重绘
    }

    // ============ 绘制 ============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val r = dpToPx(circleRadiusDp)
        textPaint.textSize = spToPx(textSizeSp)

        // 8 个通道点
        for ((name, pos) in positions) {
            val cx = pos.first * w
            val cy = pos.second * h

            // 按状态选颜色
            val status = channelStates[name] ?: "Open"
            circlePaint.color = when (status) {
                "OK" -> colorOk
                "Bad" -> colorBad
                else -> colorOpen
            }

            // 画实心圆
            canvas.drawCircle(cx, cy, r, circlePaint)

            // 画通道名（垂直居中）
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(name, cx, textY, textPaint)
        }
    }

    // ============ 工具 ============

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return sp * resources.displayMetrics.scaledDensity
    }
}