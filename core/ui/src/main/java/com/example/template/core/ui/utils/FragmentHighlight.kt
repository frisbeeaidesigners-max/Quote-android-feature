package com.example.template.core.ui.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asAndroidPath

/**
 * Строит unified Path (объединённая фигура) для multi-line фрагмента подсветки. Все углы
 * (convex outer + concave inner на step-переходах между строками) скругляются на [cornerPx].
 * Координаты Path — relative к [offsetX, offsetY] (обычно union top-left rect'ов).
 *
 * Алгоритм: walk perimeter clockwise, при каждой смене строки добавлять step-vertex'ы
 * (1 если ширины одинаковые — нет vertex, 2 если разные — concave + convex). Для каждого
 * vertex'а считать convex/concave по направлениям (in, out) и рисовать arc 90° с
 * cornerPx радиусом тангенциально к рёбрам. Convex arc curves INTO полигон, concave —
 * OUT (smoothing the inward notch).
 */
internal fun buildUnifiedFragmentPath(
    rects: List<Rect>,
    cornerPx: Float,
    stepCornerPx: Float,
    offsetX: Int,
    offsetY: Int,
): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    if (rects.isEmpty()) return path

    data class V(val x: Float, val y: Float, val inDir: Char, val outDir: Char, val isStep: Boolean)
    val verts = mutableListOf<V>()

    fun lx(v: Int) = (v - offsetX).toFloat()
    fun ly(v: Int) = (v - offsetY).toFloat()

    verts += V(lx(rects[0].left), ly(rects[0].top), 'U', 'R', false)
    verts += V(lx(rects[0].right), ly(rects[0].top), 'R', 'D', false)
    for (i in 0 until rects.size - 1) {
        val curr = rects[i]; val next = rects[i + 1]
        when {
            curr.right == next.right -> Unit
            curr.right < next.right -> {
                verts += V(lx(curr.right), ly(curr.bottom), 'D', 'R', true)
                verts += V(lx(next.right), ly(next.top), 'R', 'D', true)
            }
            else -> {
                verts += V(lx(curr.right), ly(curr.bottom), 'D', 'L', true)
                verts += V(lx(next.right), ly(next.top), 'L', 'D', true)
            }
        }
    }
    verts += V(lx(rects.last().right), ly(rects.last().bottom), 'D', 'L', false)
    verts += V(lx(rects.last().left), ly(rects.last().bottom), 'L', 'U', false)
    for (i in rects.size - 1 downTo 1) {
        val curr = rects[i]; val prev = rects[i - 1]
        when {
            curr.left == prev.left -> Unit
            curr.left > prev.left -> {
                verts += V(lx(curr.left), ly(curr.top), 'U', 'L', true)
                verts += V(lx(prev.left), ly(prev.bottom), 'L', 'U', true)
            }
            else -> {
                verts += V(lx(curr.left), ly(curr.top), 'U', 'R', true)
                verts += V(lx(prev.left), ly(prev.bottom), 'R', 'U', true)
            }
        }
    }

    for ((idx, v) in verts.withIndex()) {
        val r = if (v.isStep) stepCornerPx else cornerPx
        val ax = v.x + when (v.inDir) { 'U' -> 0f; 'D' -> 0f; 'L' -> r; 'R' -> -r; else -> 0f }
        val ay = v.y + when (v.inDir) { 'U' -> r; 'D' -> -r; 'L' -> 0f; 'R' -> 0f; else -> 0f }
        if (idx == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
        val key = "${v.inDir}${v.outDir}"
        val (arc, start, sweep) = when (key) {
            "UR" -> Triple(androidx.compose.ui.geometry.Rect(v.x, v.y, v.x + 2*r, v.y + 2*r), 180f, 90f)
            "RD" -> Triple(androidx.compose.ui.geometry.Rect(v.x - 2*r, v.y, v.x, v.y + 2*r), 270f, 90f)
            "DL" -> Triple(androidx.compose.ui.geometry.Rect(v.x - 2*r, v.y - 2*r, v.x, v.y), 0f, 90f)
            "LU" -> Triple(androidx.compose.ui.geometry.Rect(v.x, v.y - 2*r, v.x + 2*r, v.y), 90f, 90f)
            "DR" -> Triple(androidx.compose.ui.geometry.Rect(v.x, v.y - 2*r, v.x + 2*r, v.y), 180f, -90f)
            "RU" -> Triple(androidx.compose.ui.geometry.Rect(v.x - 2*r, v.y - 2*r, v.x, v.y), 90f, -90f)
            "UL" -> Triple(androidx.compose.ui.geometry.Rect(v.x - 2*r, v.y, v.x, v.y + 2*r), 0f, -90f)
            "LD" -> Triple(androidx.compose.ui.geometry.Rect(v.x, v.y, v.x + 2*r, v.y + 2*r), 270f, -90f)
            else -> Triple(androidx.compose.ui.geometry.Rect.Zero, 0f, 0f)
        }
        path.arcTo(arc, start, sweep, forceMoveTo = false)
    }
    path.close()
    return path
}

/**
 * Drawable, рисующий quote-highlight под текстом сообщения. Используется как `background`
 * для `SelectableEditText` (messageView внутри `BubblesView`/`MediaBubbleView`) —
 * z-order: bubble fill (на parent View) → TextView.background (этот drawable) → glyphs.
 * Поэтому подсветка ложится над заливкой бабла, но под буквами.
 *
 * Rects — в TV-local координатах. Scale-pivot — центр union-bbox; alpha и scale меняются
 * извне через [setHighlightScale] и стандартный [setAlpha], каждое изменение вызывает
 * `invalidateSelf` → TextView redraw'ит свой background без layout-pass'а.
 */
internal class FragmentHighlightDrawable(
    color: Int,
    private val cornerPx: Float,
    private val stepCornerPx: Float,
    private val expandPx: Int,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private var rects: List<Rect> = emptyList()
    private var androidPath: android.graphics.Path? = null
    private var unionCx = 0f
    private var unionCy = 0f
    private var highlightScale = 1f
    private var currentAlpha = 0

    fun setRects(newRects: List<Rect>) {
        if (rects == newRects) return
        rects = newRects
        rebuildPath()
        invalidateSelf()
    }

    fun setHighlightScale(scale: Float) {
        if (highlightScale == scale) return
        highlightScale = scale
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (currentAlpha == clamped) return
        currentAlpha = clamped
        invalidateSelf()
    }

    override fun getAlpha(): Int = currentAlpha

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    private fun rebuildPath() {
        if (rects.isEmpty()) { androidPath = null; return }
        // Mid-point clipping для multi-line — тот же приём, что был в Compose-варианте
        // (см. предыдущий Stage B overlay в MessageList): expandPx сверху/снизу, но не
        // дальше midpoint'а с соседней строкой → нет overlap'а и gap'а между строками.
        val expanded = rects.mapIndexed { idx, r ->
            val top = if (idx == 0) r.top - expandPx else {
                val midpoint = (rects[idx - 1].bottom + r.top) / 2
                maxOf(r.top - expandPx, midpoint)
            }
            val bottom = if (idx == rects.size - 1) r.bottom + expandPx else {
                val midpoint = (r.bottom + rects[idx + 1].top) / 2
                minOf(r.bottom + expandPx, midpoint)
            }
            Rect(r.left - expandPx, top, r.right + expandPx, bottom)
        }
        unionCx = (expanded.minOf { it.left } + expanded.maxOf { it.right }) / 2f
        unionCy = (expanded.minOf { it.top } + expanded.maxOf { it.bottom }) / 2f
        androidPath = buildUnifiedFragmentPath(
            rects = expanded,
            cornerPx = cornerPx,
            stepCornerPx = stepCornerPx,
            offsetX = 0,
            offsetY = 0,
        ).asAndroidPath()
    }

    override fun draw(canvas: Canvas) {
        val p = androidPath ?: return
        if (currentAlpha <= 0) return
        paint.alpha = currentAlpha
        canvas.save()
        canvas.scale(highlightScale, highlightScale, unionCx, unionCy)
        canvas.drawPath(p, paint)
        canvas.restore()
    }
}
