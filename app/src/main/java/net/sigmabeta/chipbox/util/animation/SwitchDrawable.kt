package net.sigmabeta.chipbox.util.animation

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Property

/**
 * A drawable which shows (a portion of) one of two given bitmaps, switching between them once
 * a progress property passes a threshold.
 *
 *
 * This is helpful when animating text size change as small text scaled up is blurry but larger
 * text scaled down has different kerning. Instead we use images of both states and switch
 * during the transition. We use images as animating text size thrashes the font cache.
 */
class SwitchDrawable internal constructor(
        private var currentBitmap: Bitmap?,
        private var currentBitmapSrcBounds: Rect?,
        startFontSize: Float,
        private val endBitmap: Bitmap,
        private val endBitmapSrcBounds: Rect,
        endFontSize: Float) : Drawable() {

    private val paint: Paint
    private val switchThreshold: Float
    private var hasSwitched = false
    internal var topLeft: PointF? = null
        set(topLeft) {
            field = topLeft
            updateBounds()
        }
    internal var width: Int = 0
        set(width) {
            field = width
            updateBounds()
        }
    internal var height: Int = 0
        set(height) {
            field = height
            updateBounds()
        }

    init {
        switchThreshold = startFontSize / (startFontSize + endFontSize)
        paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(currentBitmap!!, currentBitmapSrcBounds, bounds, paint)
//        Timber.v("Outputting to bounds: $bounds")
//        val paint = Paint()
//        paint.color = Color.RED
//        canvas.drawRect(bounds, paint)

    }

    override fun getAlpha(): Int {
        return paint.alpha
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getColorFilter(): ColorFilter? {
        return paint.colorFilter
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    internal fun setProgress(progress: Float) {
        if (!hasSwitched && progress >= switchThreshold) {
            currentBitmap = endBitmap
            currentBitmapSrcBounds = endBitmapSrcBounds
            hasSwitched = true
        }
    }

    private fun updateBounds() {
        val left = Math.round(this.topLeft?.x ?: 0.0f)
        val top = Math.round(this.topLeft?.y ?: 0.0f)
        setBounds(left, top, left + this.width, top + this.height)
    }

    companion object {

        internal val TOP_LEFT: Property<SwitchDrawable, PointF> = object : Property<SwitchDrawable, PointF>(PointF::class.java, "topLeft") {
            override fun set(drawable: SwitchDrawable, topLeft: PointF) {
                drawable.topLeft = topLeft
            }

            override fun get(drawable: SwitchDrawable): PointF? {
                return drawable.topLeft
            }
        }

        internal val WIDTH: Property<SwitchDrawable, Int> = object : Property<SwitchDrawable, Int>(Int::class.java, "width") {
            override fun set(drawable: SwitchDrawable, width: Int) {
                drawable.width = width
            }

            override fun get(drawable: SwitchDrawable): Int {
                return drawable.width
            }
        }

        internal val HEIGHT: Property<SwitchDrawable, Int> = object : Property<SwitchDrawable, Int>(Int::class.java, "height") {
            override fun set(drawable: SwitchDrawable, height: Int) {
                drawable.height = height
            }

            override fun get(drawable: SwitchDrawable): Int {
                return drawable.height
            }
        }

        internal val ALPHA: Property<SwitchDrawable, Int> = object : Property<SwitchDrawable, Int>(Int::class.java, "alpha") {
            override fun set(drawable: SwitchDrawable, alpha: Int) {
                drawable.alpha = alpha
            }

            override fun get(drawable: SwitchDrawable): Int {
                return drawable.alpha
            }
        }

        internal val PROGRESS: Property<SwitchDrawable, Float> = object : Property<SwitchDrawable, Float>(Float::class.java, "progress") {
            override fun set(drawable: SwitchDrawable, progress: Float?) {
                drawable.setProgress(progress!!)
            }

            override fun get(drawable: SwitchDrawable): Float {
                return 0f
            }
        }
    }
}