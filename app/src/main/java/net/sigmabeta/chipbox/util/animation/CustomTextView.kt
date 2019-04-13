package net.sigmabeta.chipbox.util.animation

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.FontRes
import androidx.appcompat.widget.AppCompatTextView
import net.sigmabeta.chipbox.R







class CustomTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle) : AppCompatTextView(context, attrs, defStyleAttr) {
    @FontRes
    @get:FontRes
    var fontResId = 0
        private set

    init {
        val a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomTextView, defStyleAttr, 0)

        // first check TextAppearance for line height & font attributes
        if (a.hasValue(R.styleable.CustomTextView_android_textAppearance)) {
            val textAppearanceId = a.getResourceId(R.styleable.CustomTextView_android_textAppearance,
                    android.R.style.TextAppearance)
            val ta = context.obtainStyledAttributes(
                    textAppearanceId, R.styleable.CustomTextView)
            if (ta.hasValue(R.styleable.CustomTextView_android_fontFamily)) {
                fontResId = ta.getResourceId(R.styleable.CustomTextView_android_fontFamily, 0)
            }
            ta.recycle()
        }


        // then check view attrs
        if (a.hasValue(R.styleable.CustomTextView_android_fontFamily)) {
            fontResId = a.getResourceId(R.styleable.CustomTextView_android_fontFamily, 0)
        }
    }

}
