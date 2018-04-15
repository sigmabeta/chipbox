package net.sigmabeta.chipbox.util.animation

import android.graphics.Point
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.ColorInt
import android.support.annotation.FontRes

/**
 * Holds all data needed to describe a block of text i.e. to be able to re-create the
 * [Layout].
 */
class ReflowData : Parcelable {

    internal val text: String
    internal val textSize: Float
    @ColorInt
    internal val textColor: Int
    internal val bounds: Rect
    @FontRes
    internal val fontResId: Int
    internal val lineSpacingAdd: Float
    internal val lineSpacingMult: Float
    internal val textPosition: Point
    internal val textHeight: Int
    internal val textWidth: Int
    internal val breakStrategy: Int
    internal val letterSpacing: Float
    internal val maxLines: Int

    internal constructor(reflowable: ReflowText.Reflowable<*>) {
        text = reflowable.text
        textSize = reflowable.textSize
        textColor = reflowable.textColor
        fontResId = reflowable.fontResId
        val view = reflowable.view
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        bounds = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        textPosition = reflowable.textPosition
        textHeight = reflowable.textHeight
        lineSpacingAdd = reflowable.lineSpacingAdd
        lineSpacingMult = reflowable.lineSpacingMult
        textWidth = reflowable.textWidth
        breakStrategy = reflowable.breakStrategy
        letterSpacing = reflowable.letterSpacing
        maxLines = reflowable.maxLines
    }

    internal constructor(`in`: Parcel) {
        text = `in`.readString()
        textSize = `in`.readFloat()
        textColor = `in`.readInt()
        bounds = `in`.readValue(Rect::class.java.classLoader) as Rect
        fontResId = `in`.readInt()
        lineSpacingAdd = `in`.readFloat()
        lineSpacingMult = `in`.readFloat()
        textPosition = `in`.readValue(Point::class.java.classLoader) as Point
        textHeight = `in`.readInt()
        textWidth = `in`.readInt()
        breakStrategy = `in`.readInt()
        letterSpacing = `in`.readFloat()
        maxLines = `in`.readInt()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(text)
        dest.writeFloat(textSize)
        dest.writeInt(textColor)
        dest.writeValue(bounds)
        dest.writeInt(fontResId)
        dest.writeFloat(lineSpacingAdd)
        dest.writeFloat(lineSpacingMult)
        dest.writeValue(textPosition)
        dest.writeInt(textHeight)
        dest.writeInt(textWidth)
        dest.writeInt(breakStrategy)
        dest.writeFloat(letterSpacing)
        dest.writeInt(maxLines)
    }

    override fun toString(): String {
        return "ReflowData(text='$text', textSize=$textSize, textColor=$textColor, bounds=$bounds, fontResId=$fontResId, lineSpacingAdd=$lineSpacingAdd, lineSpacingMult=$lineSpacingMult, textPosition=$textPosition, textHeight=$textHeight, textWidth=$textWidth, breakStrategy=$breakStrategy, letterSpacing=$letterSpacing, maxLines=$maxLines)"
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ReflowData> = object : Parcelable.Creator<ReflowData> {
            override fun createFromParcel(`in`: Parcel): ReflowData {
                return ReflowData(`in`)
            }

            override fun newArray(size: Int): Array<ReflowData?> {
                return arrayOfNulls(size)
            }
        }
    }



}