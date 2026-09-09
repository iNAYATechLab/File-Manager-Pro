package com.inayatechlab.filemanagerpro.preview

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView with pinch-zoom, one-finger panning (while zoomed) and double-tap
 * zoom/reset, built on a fit-center base matrix. While zoomed it disallows the
 * parent (the ViewPager2) from intercepting touches; at 1x zoom the pager is
 * free to swipe between pages.
 *
 * [onZoomChanged] reports the current zoom factor so the host can disable
 * paging while zoomed in.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    interface OnZoomChangedListener {
        fun onZoomChanged(zoom: Float)
    }

    private val matrix = Matrix()
    private val tmpRect = RectF()
    private val contentRect = RectF()
    private val fitRect = RectF()
    private val drawableRect = RectF()

    private var viewW = 0
    private var viewH = 0
    private var fitContentW = 1f
    private var fitContentH = 1f

    private var dragMode = false
    private var lastX = 0f
    private var lastY = 0f

    var onZoomChanged: OnZoomChangedListener? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentZoom() > 1.02f) {
                    resetZoom()
                } else {
                    zoomBy(2.5f, e.x, e.y)
                }
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { refit() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w
        viewH = h
        refit()
    }

    /** True when the image is zoomed in beyond 1x. */
    fun isZoomed(): Boolean = currentZoom() > 1.02f

    /** Current zoom factor relative to the fitted image. */
    fun currentZoom(): Float {
        val rect = mappedRect()
        if (rect.width() <= 0f) return 1f
        return rect.width() / fitContentW
    }

    /** Reset zoom and panning back to the fitted view. */
    fun resetZoom() {
        refit()
    }

    private fun refit() {
        val dr = drawable
        if (dr == null || viewW <= 0 || viewH <= 0) return
        val dw = dr.intrinsicWidth.toFloat()
        val dh = dr.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        drawableRect.set(0f, 0f, dw, dh)
        val scale = min(viewW / dw, viewH / dh)
        fitContentW = dw * scale
        fitContentH = dh * scale
        val dx = (viewW - fitContentW) / 2f
        val dy = (viewH - fitContentH) / 2f
        fitRect.set(dx, dy, dx + fitContentW, dy + fitContentH)

        matrix.reset()
        matrix.setRectToRect(drawableRect, fitRect, Matrix.ScaleToFit.FILL)
        imageMatrix = matrix
        notifyZoom()
    }

    private fun mappedRect(): RectF {
        contentRect.set(drawableRect)
        matrix.mapRect(contentRect)
        return contentRect
    }

    /** Multiply zoom by [factor] keeping the point ([fx], [fy]) stationary. */
    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val safe = factor.coerceIn(0.5f, 4f)
        val zoom = currentZoom() * safe
        if (zoom < 1f) {
            // Zooming out below 1x snaps back to the fitted state.
            if (currentZoom() > 1.02f) resetZoom()
            return
        }
        val capped = min(zoom, MAX_ZOOM)
        val applied = capped / currentZoom()
        if (applied <= 1.001f) return
        matrix.preScale(applied, applied, fx, fy)
        clampPan()
        imageMatrix = matrix
        notifyZoom()
    }

    private fun panBy(dx: Float, dy: Float) {
        val rect = mappedRect()
        var nx = dx
        var ny = dy
        if (rect.width() <= viewW) {
            nx = 0f
        } else {
            if (rect.left + nx > 0f) nx = -rect.left
            else if (rect.right + nx < viewW) nx = viewW - rect.right
        }
        if (rect.height() <= viewH) {
            ny = 0f
        } else {
            if (rect.top + ny > 0f) ny = -rect.top
            else if (rect.bottom + ny < viewH) ny = viewH - rect.bottom
        }
        if (nx != 0f || ny != 0f) {
            matrix.preTranslate(nx, ny)
            imageMatrix = matrix
        }
    }

    private fun clampPan() {
        val rect = mappedRect()
        var dx = 0f
        var dy = 0f
        if (rect.width() <= viewW) {
            dx = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0f) dx = -rect.left
            else if (rect.right < viewW) dx = viewW - rect.right
        }
        if (rect.height() <= viewH) {
            dy = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0f) dy = -rect.top
            else if (rect.bottom < viewH) dy = viewH - rect.bottom
        }
        if (dx != 0f || dy != 0f) matrix.preTranslate(dx, dy)
    }

    private fun notifyZoom() {
        onZoomChanged?.onZoomChanged(currentZoom())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || drawable == null) {
            return super.onTouchEvent(event)
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragMode = isZoomed()
                if (dragMode) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    if (dx != 0f || dy != 0f) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        panBy(dx, dy)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = false
                if (!isZoomed()) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    companion object {
        private const val MAX_ZOOM = 5f
    }
}
