package net.sigmabeta.chipbox.server.http

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Decode-resize-encode helper for the image endpoints. Reuses the same `GridImageSize` bucket
 * set the JS client picks from (see [WebImageLoader]) — every client-side request lands on
 * one of those values, so we resize to exactly that max-dimension. The browser's HTTP cache
 * (`Cache-Control: max-age=86400`) means a given (image, size) pair only hits this code once
 * per day per client, so per-request cost is acceptable without a server-side cache layer.
 *
 * Output is always JPEG at quality 0.85 — small, broad-decoder support, fine for thumbnails.
 * The original format (PNG/GIF/etc.) on disk is preserved by the unresized passthrough; only
 * the resized variant is JPEG.
 */
internal object ImageResizer {

    private const val OUTPUT_FORMAT = "jpeg"
    private const val OUTPUT_MIME = "image/jpeg"

    /** Returns (bytes, mimeType). Null if [bytes] doesn't decode as an image. */
    fun resize(bytes: ByteArray, maxDimension: Int): Pair<ByteArray, String>? {
        val original = ByteArrayInputStream(bytes).use { ImageIO.read(it) } ?: return null
        val srcW = original.width
        val srcH = original.height
        if (srcW <= 0 || srcH <= 0) return null
        // Already at or below target — skip the resize work and re-encode to JPEG straight.
        // (Caller already paid the decode cost in ImageIO.read; re-encoding keeps output type
        // consistent so the client sees image/jpeg either way.)
        val (targetW, targetH) = scaledDimensions(srcW, srcH, maxDimension)

        val resized = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        try {
            // BILINEAR is the sweet spot for thumbnail downscaling — fast (no per-pixel cubic
            // kernel) and produces visibly smoother results than nearest-neighbor. BICUBIC is
            // perceptually similar at thumbnail size and noticeably slower.
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            graphics.drawImage(original, 0, 0, targetW, targetH, null)
        } finally {
            graphics.dispose()
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(resized, OUTPUT_FORMAT, output)
        return output.toByteArray() to OUTPUT_MIME
    }

    /** Scale (srcW, srcH) so the larger dimension equals [maxDim], preserving aspect ratio. */
    private fun scaledDimensions(srcW: Int, srcH: Int, maxDim: Int): Pair<Int, Int> {
        if (srcW <= maxDim && srcH <= maxDim) return srcW to srcH
        val ratio = maxDim.toDouble() / maxOf(srcW, srcH)
        val targetW = (srcW * ratio).toInt().coerceAtLeast(1)
        val targetH = (srcH * ratio).toInt().coerceAtLeast(1)
        return targetW to targetH
    }
}
