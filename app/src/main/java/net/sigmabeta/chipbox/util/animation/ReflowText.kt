package net.sigmabeta.chipbox.util.animation

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.*
import android.os.Build
import android.support.annotation.ColorInt
import android.support.annotation.FontRes
import android.support.v4.content.res.ResourcesCompat
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.name
import timber.log.Timber
import java.util.*

/**
 * A transition for repositioning text. This will animate changes in text size and position,
 * re-flowing line breaks as necessary.
 *
 *
 * Strongly recommended to use a curved `pathMotion` for a more natural transition.
 */
class ReflowText(context: Context, attrs: AttributeSet) : Transition(context, attrs) {

    private var enter: Boolean
    // this is hack for preventing view from drawing briefly at the end of the transition :(
    private val freezeFrame: Boolean

    init {
        val reflowAttrs = context.obtainStyledAttributes(attrs, R.styleable.ReflowText)
        val transAttrs = context.obtainStyledAttributes(attrs, R.styleable.Transitions)

        freezeFrame = reflowAttrs.getBoolean(R.styleable.ReflowText_freezeFrame, false)
        enter = transAttrs.getBoolean(R.styleable.Transitions_enter, true)

        transAttrs.recycle()
        reflowAttrs.recycle()
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        captureValues(transitionValues, true)
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        captureValues(transitionValues, false)
    }

    override fun getTransitionProperties(): Array<String> {
        return PROPERTIES
    }

    override fun createAnimator(
            sceneRoot: ViewGroup,
            startValues: TransitionValues,
            endValues: TransitionValues): Animator? {
        val view = endValues.view

        val animatorSet = AnimatorSet()
        val startData = startValues.values[PROPNAME_DATA] as ReflowData
        val endData = endValues.values[PROPNAME_DATA] as ReflowData

        // create layouts & capture a bitmaps of the text in both states
        // (with max lines variants where needed)
        val startLayout = createLayout(startData, sceneRoot.context, false)
        val endLayout = createLayout(endData, sceneRoot.context, false)
        var startLayoutMaxLines: Layout? = null
        var endLayoutMaxLines: Layout? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // StaticLayout maxLines support
            if (startData.maxLines != -1) {
                startLayoutMaxLines = createLayout(startData, sceneRoot.context, true)
            }
            if (endData.maxLines != -1) {
                endLayoutMaxLines = createLayout(endData, sceneRoot.context, true)
            }
        }


        val startText = createBitmap(startData,
                if (startLayoutMaxLines != null) startLayoutMaxLines else startLayout)
        val endText = createBitmap(endData,
                if (endLayoutMaxLines != null) endLayoutMaxLines else endLayout)

        // temporarily turn off clipping so we can draw outside of our bounds don't draw
        view.setWillNotDraw(true)
        val ancestralClipping = setAncestralClipping(view, false)

        // calculate the runs of text to move together
        val runs = getRuns(startData, startLayout, startLayoutMaxLines,
                endData, endLayout, endLayoutMaxLines)

        // create animators for moving, scaling and fading each run of text
        val runAnimators = createRunAnimators(view, startData, endData, startText, endText, runs)

        animatorSet.playTogether(runAnimators)

        if (!freezeFrame) {
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?, isReverse: Boolean) {
                }

                override fun onAnimationEnd(animation: Animator) {
                    // clean up
                    view.setWillNotDraw(false)
                    view.overlay.clear()
                    restoreAncestralClipping(view, ancestralClipping)
                    startText.recycle()
                    endText.recycle()
                }
            })
        }
        return animatorSet
    }

    private fun captureValues(transitionValues: TransitionValues, start: Boolean) {
        val view = transitionValues.view
        val reflowData = getReflowData(view, start)
        transitionValues.values[PROPNAME_DATA] = reflowData

        if (reflowData != null) {
            // add these props to the map separately (even though they are captured in the reflow
            // data) to use only them to determine whether to create an animation i.e. only
            // animate if text size or bounds have changed (see #getTransitionProperties())
            transitionValues.values[PROPNAME_TEXT_SIZE] = reflowData.textSize
            transitionValues.values[PROPNAME_BOUNDS] = reflowData.bounds
        } else {
            Timber.e("No reflow data for view ${view.name()}.")
        }
    }

    private fun getReflowData(view: View, start: Boolean): ReflowData? {
        val tag = if (enter) {
            if (start) R.id.tag_reflow_data_start else R.id.tag_reflow_data_end
        } else {
            if (start) R.id.tag_reflow_data_end else R.id.tag_reflow_data_start
        }
        val reflowData = view.getTag(tag) as ReflowData?
        if (reflowData != null) {
            return reflowData
        }
        return null
    }

    /**
     * Calculate the [Run]s i.e. diff the start and end states, see where text changes
     * line and track the bounds of sections of text that can move together.
     *
     *
     * If a text block has a max number of lines, consider both with and without this limit applied.
     * This allows simulating the correct line breaking as well as calculating the position that
     * overflowing text would have been laid out, so that it can animate from/to that position.
     */
    private fun getRuns(startData: ReflowData,
                        startLayout: Layout,
                        startLayoutMaxLines: Layout?,
                        endData: ReflowData,
                        endLayout: Layout,
                        endLayoutMaxLines: Layout?): List<Run> {
        var currentStartLine = 0
        var currentStartRunLeft = 0
        var currentStartRunTop = 0
        var currentEndLine = 0
        var currentEndRunLeft = 0
        var currentEndRunTop = 0
        val runs = ArrayList<Run>(endLayout.lineCount)

        val startLayoutText = startLayout.text
        val endLayoutText = endLayout.text

        val textLength = if (startLayoutText != endLayoutText) {
            Timber.e("Text mismatch: $startLayoutText | $endLayoutText")
            if (startLayoutText.length > endLayoutText.length) {
                endLayoutText.length
            } else {
                startLayoutText.length
            }
        } else {
            endLayoutText.length
        }

        for (charIndex in 0 until textLength) {
            // work out which line this letter is on in the start state
            var startLine = -1
            var startMax = false
            var startMaxEllipsis = false
            if (startLayoutMaxLines != null) {
                val letter = startLayoutText?.get(charIndex)
                startMaxEllipsis = letter == '…'
                if (letter != '\uFEFF'              // beyond max lines
                        && !startMaxEllipsis) {     // ellipsize inserted into layout
                    startLine = startLayoutMaxLines.getLineForOffset(charIndex)
                    startMax = true
                }
            }
            if (!startMax) {
                startLine = startLayout.getLineForOffset(charIndex)
            }

            // work out which line this letter is on in the end state
            var endLine = -1
            var endMax = false
            var endMaxEllipsis = false
            if (endLayoutMaxLines != null) {
                val letter = endLayoutMaxLines.text[charIndex]
                endMaxEllipsis = letter == '…'
                if (letter != '\uFEFF'              // beyond max lines
                        && !endMaxEllipsis) {       // ellipsize inserted into layout
                    endLine = endLayoutMaxLines.getLineForOffset(charIndex)
                    endMax = true
                }
            }
            if (!endMax) {
                endLine = endLayout.getLineForOffset(charIndex)
            }
            val lastChar = charIndex == textLength - 1

            if (startLine != currentStartLine
                    || endLine != currentEndLine
                    || lastChar) {
                // at a run boundary, store bounds in both states
                val startRunRight = getRunRight(startLayout, startLayoutMaxLines,
                        currentStartLine, charIndex, startLine, startMax, startMaxEllipsis, lastChar)
                val startRunBottom = startLayout.getLineBottom(currentStartLine)
                val endRunRight = getRunRight(endLayout, endLayoutMaxLines, currentEndLine, charIndex,
                        endLine, endMax, endMaxEllipsis, lastChar)
                val endRunBottom = endLayout.getLineBottom(currentEndLine)

                val startBound = Rect(
                        currentStartRunLeft, currentStartRunTop, startRunRight, startRunBottom)
                startBound.offset(startData.textPosition.x, startData.textPosition.y)
                val endBound = Rect(
                        currentEndRunLeft, currentEndRunTop, endRunRight, endRunBottom)
                endBound.offset(endData.textPosition.x, endData.textPosition.y)
                runs.add(Run(
                        startBound,
                        startMax || startRunBottom <= startData.textHeight,
                        endBound,
                        endMax || endRunBottom <= endData.textHeight))
                currentStartLine = startLine
                currentStartRunLeft = (if (startMax)
                    startLayoutMaxLines!!
                            .getPrimaryHorizontal(charIndex)
                else
                    startLayout.getPrimaryHorizontal(charIndex)).toInt()
                currentStartRunTop = startLayout.getLineTop(startLine)
                currentEndLine = endLine
                currentEndRunLeft = (if (endMax)
                    endLayoutMaxLines!!
                            .getPrimaryHorizontal(charIndex)
                else
                    endLayout.getPrimaryHorizontal(charIndex)).toInt()
                currentEndRunTop = endLayout.getLineTop(endLine)
            }
        }
        return runs
    }

    /**
     * Calculate the right boundary for this run (harder than it sounds). As we're a letter ahead,
     * need to grab either current letter start or the end of the previous line. Also need to
     * consider maxLines case, which inserts ellipses at the overflow point – don't include these.
     */
    private fun getRunRight(
            unrestrictedLayout: Layout, maxLinesLayout: Layout?, currentLine: Int, index: Int,
            line: Int, withinMax: Boolean, isMaxEllipsis: Boolean, isLastChar: Boolean): Int {
        val runRight: Int
        if (line != currentLine || isLastChar) {
            if (isMaxEllipsis) {
                runRight = maxLinesLayout!!.getPrimaryHorizontal(index).toInt()
            } else {
                runRight = unrestrictedLayout.getLineMax(currentLine).toInt()
            }
        } else {
            if (withinMax) {
                runRight = maxLinesLayout!!.getPrimaryHorizontal(index).toInt()
            } else {
                runRight = unrestrictedLayout.getPrimaryHorizontal(index).toInt()
            }
        }
        return runRight
    }

    /**
     * Create Animators to transition each run of text from start to end position and size.
     */
    private fun createRunAnimators(
            view: View,
            startData: ReflowData,
            endData: ReflowData,
            startText: Bitmap,
            endText: Bitmap,
            runs: List<Run>): List<Animator> {
        val animators = ArrayList<Animator>(runs.size)
        val dx = startData.bounds.left - endData.bounds.left
        val dy = startData.bounds.top - endData.bounds.top

        // move text closest to the destination first i.e. loop forward or backward over the runs
        val upward = startData.bounds.centerY() > endData.bounds.centerY()
        val linearInterpolator = LinearInterpolator()

        var i = if (upward) 0 else runs.size - 1
        while (upward && i < runs.size || !upward && i >= 0) {
            val run = runs[i]

            // skip text runs which aren't visible in either state
            if (!run.startVisible && !run.endVisible) {
                i += if (upward) 1 else -1
                continue
            }

            // create & position the drawable which displays the run; add it to the overlay.
            val drawable = SwitchDrawable(startText, run.start,
                    startData.textSize,
                    endText, run.end,
                    endData.textSize)

            val overlay = view.overlay
            overlay.add(drawable)

            val topLeft = PropertyValuesHolder.ofObject<PointF>(
                    SwitchDrawable.TOP_LEFT, TypeEvaluator<PointF> { fraction, startValue, endValue ->
                val x = ((endValue.x - startValue.x) * fraction) + startValue.x
                val y = ((endValue.y - startValue.y) * fraction) + startValue.y
                PointF(x, y)
            },
                    PointF(run.start.left.toFloat() + dx, run.start.top.toFloat() + dy),
                    PointF(run.end.left.toFloat(), run.end.top.toFloat()))

            val width = PropertyValuesHolder.ofInt(
                    SwitchDrawable.WIDTH, run.start.width(), run.end.width())
            val height = PropertyValuesHolder.ofInt(
                    SwitchDrawable.HEIGHT, run.start.height(), run.end.height())
            // the progress property drives the switching behaviour
            val progress = PropertyValuesHolder.ofFloat(
                    SwitchDrawable.PROGRESS, 0f, 1f)
            val runAnim = ObjectAnimator.ofPropertyValuesHolder(
                    drawable, topLeft, width, height, progress)

            animators.add(runAnim)

            if (run.startVisible != run.endVisible) {
                // if run is appearing/disappearing then fade it in/out
                val fade = ObjectAnimator.ofInt(
                        drawable,
                        SwitchDrawable.ALPHA,
                        if (run.startVisible) OPAQUE else TRANSPARENT,
                        if (run.endVisible) OPAQUE else TRANSPARENT)
                if (!run.startVisible) {
                    drawable.alpha = TRANSPARENT
                }
                animators.add(fade)
            } else {
                // slightly fade during transition to minimize movement
                val fade = ObjectAnimator.ofInt(
                        drawable,
                        SwitchDrawable.ALPHA,
                        OPAQUE, OPACITY_MID_TRANSITION, OPAQUE)
                fade.interpolator = linearInterpolator
                animators.add(fade)
            }
            i += if (upward) 1 else -1
        }
        return animators
    }

    private fun createLayout(data: ReflowData, context: Context, enforceMaxLines: Boolean): Layout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = data.textSize
        paint.color = data.textColor
        paint.letterSpacing = data.letterSpacing
        if (data.fontResId != 0) {
            try {
                val font = ResourcesCompat.getFont(context, data.fontResId)
                if (font != null) {
                    paint.typeface = font
                }
            } catch (nfe: Resources.NotFoundException) {
                Timber.e("Font error: ${nfe.message}")
            }
        } else {
            Timber.e("No font resource defined.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val builder = StaticLayout.Builder.obtain(
                    data.text, 0, data.text.length, paint, data.textWidth)
                    .setLineSpacing(data.lineSpacingAdd, data.lineSpacingMult)
                    .setBreakStrategy(data.breakStrategy)
            if (enforceMaxLines && data.maxLines != -1) {
                builder.setMaxLines(data.maxLines)
                builder.setEllipsize(TextUtils.TruncateAt.END)
            }
            return builder.build()
        } else {
            return StaticLayout(
                    data.text,
                    paint,
                    data.textWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    data.lineSpacingMult,
                    data.lineSpacingAdd,
                    true)
        }
    }

    private fun createBitmap(data: ReflowData, layout: Layout): Bitmap {
        val bitmap = Bitmap.createBitmap(
                data.bounds.width(), data.bounds.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(data.textPosition.x.toFloat(), data.textPosition.y.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    private fun setAncestralClipping(view: View, clipChildren: Boolean): List<Boolean> {
        return setAncestralClipping(view, clipChildren, ArrayList())
    }

    private fun setAncestralClipping(view: View, clipChildren: Boolean, was: MutableList<Boolean>): List<Boolean> {
        if (view is ViewGroup) {
            was.add(view.clipChildren)
            view.clipChildren = clipChildren
        }
        val parent = view.parent
        if (parent != null && parent is ViewGroup) {
            setAncestralClipping(parent, clipChildren, was)
        }
        return was
    }

    fun restoreAncestralClipping(view: View, was: List<Boolean>) {
        val wasMutable = was.toMutableList()
        if (view is ViewGroup) {
            val wasValue = wasMutable.removeAt(0)
            view.clipChildren = wasValue
        }
        val parent = view.parent
        if (parent != null && parent is ViewGroup) {
            restoreAncestralClipping(parent, wasMutable)
        }
    }

    /**
     * Interface describing a view which supports re-flowing i.e. it exposes enough information to
     * construct a [ReflowData] object;
     */
    interface Reflowable<T : View> {

        val view: T
        val text: String
        val textPosition: Point
        val textWidth: Int
        val textHeight: Int
        val textSize: Float
        @get:ColorInt
        val textColor: Int
        val lineSpacingAdd: Float
        val lineSpacingMult: Float
        val breakStrategy: Int
        val letterSpacing: Float
        @get:FontRes
        val fontResId: Int
        val maxLines: Int
    }

    companion object {

        private val EXTRA_REFLOW_DATA = "EXTRA_REFLOW_DATA"
        private val PROPNAME_DATA = "chipbox:reflowtext:data"
        private val PROPNAME_TEXT_SIZE = "chipbox:reflowtext:textsize"
        private val PROPNAME_BOUNDS = "chipbox:reflowtext:bounds"
        private val PROPERTIES = arrayOf(PROPNAME_TEXT_SIZE, PROPNAME_BOUNDS/*, PROPNAME_START*/)
        private val TRANSPARENT = 0
        private val OPAQUE = 255
        private val OPACITY_MID_TRANSITION = (0.8f * OPAQUE).toInt()

        /**
         * Store data about the view which will participate in a reflow transition in `intent`.
         */
        fun addExtras(intent: Intent, reflowableView: Reflowable<*>) {
            val reflowData = ReflowData(reflowableView)
            val reflowDataKey = getReflowDataKey(reflowableView.view)

            intent.putExtra(reflowDataKey, reflowData)
        }

        private fun getReflowDataKey(view: View) =
                "$EXTRA_REFLOW_DATA.${view.name()}"

        /**
         * Retrieve data about the reflow from `intent` and store it for later use.
         */
        fun reflowDataFromIntent(intent: Intent, view: View) {
            val reflowDataKey = getReflowDataKey(view)
            val reflowData = intent.getParcelableExtra<ReflowData>(reflowDataKey)

            view.setTag(R.id.tag_reflow_data_start, reflowData)
        }

        /**
         * Create data about the reflow from `reflowableView` and store it for later use.
         */
        fun reflowDataFromView(reflowableView: Reflowable<*>) {
            val view = reflowableView.view
            val reflowData = ReflowData(reflowableView)

            view.setTag(R.id.tag_reflow_data_end, reflowData)
        }
    }

}
