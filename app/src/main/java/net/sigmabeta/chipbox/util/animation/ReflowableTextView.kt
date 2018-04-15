package net.sigmabeta.chipbox.util.animation

import android.graphics.Point
import android.os.Build
import android.widget.TextView

/**
 * Wraps a [TextView] and implements [Reflowable].
 */
class ReflowableTextView(private val textView: CustomTextView) : ReflowText.Reflowable<TextView> {

    override val view: TextView
        get() = textView

    override val text: String
        get() = textView.text.toString()

    override val textPosition: Point
        get() = Point(textView.compoundPaddingLeft, textView.compoundPaddingTop)

    override val textWidth: Int
        get() = (textView.width
                - textView.compoundPaddingLeft - textView.compoundPaddingRight)

    override val textHeight: Int
        get() = if (textView.maxLines != -1) {
            textView.maxLines * textView.lineHeight + 1
        } else {
            (textView.height - textView.compoundPaddingTop
                    - textView.compoundPaddingBottom)
        }

    override val lineSpacingAdd: Float
        get() = textView.lineSpacingExtra

    override val lineSpacingMult: Float
        get() = textView.lineSpacingMultiplier

    override val breakStrategy: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.breakStrategy
        } else -1

    override val letterSpacing: Float
        get() = textView.letterSpacing

    override val fontResId: Int
        get() = textView.fontResId

    override val textSize: Float
        get() = textView.textSize

    override val textColor: Int
        get() = textView.currentTextColor

    override val maxLines: Int
        get() = textView.maxLines
}