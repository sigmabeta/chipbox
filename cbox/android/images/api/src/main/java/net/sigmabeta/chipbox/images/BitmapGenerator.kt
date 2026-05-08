package net.sigmabeta.chipbox.images

import android.animation.ArgbEvaluator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlin.random.Random

object BitmapGenerator {
    fun generateBitmap(
        sourceInfo: Any,
        squareImage: Boolean = true,
    ): ImageBitmap {
        val rng = Random(sourceInfo.hashCode())
        val width = rng.nextInt(RNG_LIMIT) + RNG_LIMIT
        val height = if (squareImage) {
            width
        } else {
            rng.nextInt(RNG_LIMIT) + RNG_LIMIT
        }

        val sizeInBytes = width * height * BYTES_PER_PIXEL

        val byteArray = ByteArray(sizeInBytes)
        byteArray.forEachIndexed { index, _ ->
            byteArray[index] = index.toByte()
        }

        val bitmap = createBitmap(width, height)

        val canvas = Canvas(bitmap)
        val paint = Paint()

        val colorTopLeft = rng.nextInt()
        val colorTopRight = rng.nextInt()
        val colorBottomLeft = rng.nextInt()
        val colorBottomRight = rng.nextInt()

        val evaluator = ArgbEvaluator()

        repeat(height) { row ->
            val heightFraction = row.toFloat() / (height - 1)

            val colorLeft = evaluator.evaluate(heightFraction, colorTopLeft, colorBottomLeft) as Int
            val colorRight = evaluator.evaluate(heightFraction, colorTopRight, colorBottomRight) as Int

            val shader = LinearGradient(
                0.0f,
                0.0f,
                width.toFloat(),
                1.0f,
                colorLeft,
                colorRight,
                Shader.TileMode.MIRROR,
            ) as Shader

            paint.shader = shader
            paint.isAntiAlias = false
            canvas.drawLine(0.0f, row.toFloat(), width.toFloat(), row.toFloat(), paint)
        }

        return bitmap.asImageBitmap()
    }

    private const val RNG_LIMIT = 16
    private const val BYTES_PER_PIXEL = 4
}
