package com.farrah.sproutvision.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * Renders a shareable scan card: the captured tray photo scaled to 1080
 * wide with a dark-green banner (tray name, scan date, health/coverage,
 * issues, "Pixel & Sprout" wordmark), shared as a PNG via FileProvider.
 */
object ShareImage {
    const val WIDTH_PX = 1080
    const val BANNER_PX = 300
    const val WORDMARK = "Pixel & Sprout"

    private const val GREEN_DARK = 0xFF1B5E20.toInt()
    private const val GREEN_DEEP = 0xFF0D3311.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val WHITE_DIM = 0xFFC8E6C9.toInt()

    /** Renders the share card bitmap. Falls back to a drawn placeholder when no photo. */
    fun render(
        photoBytes: ByteArray?,
        trayName: String,
        scanDateMs: Long,
        healthPct: Int,
        coveragePct: Int,
        issues: String
    ): Bitmap {
        val photo = decodePhoto(photoBytes)
        val photoH = if (photo != null) {
            (WIDTH_PX.toFloat() * photo.height / photo.width).toInt().coerceAtLeast(1)
        } else {
            WIDTH_PX * 3 / 4
        }
        val out = Bitmap.createBitmap(WIDTH_PX, photoH + BANNER_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (photo != null) {
            val scaled = Bitmap.createScaledBitmap(photo, WIDTH_PX, photoH, true)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            if (scaled != photo) scaled.recycle()
        } else {
            canvas.drawColor(GREEN_DEEP)
            val paint = textPaint(64f, bold = true, color = WHITE_DIM)
            canvas.drawText(
                "SproutVision",
                WIDTH_PX / 2f,
                photoH / 2f - (paint.ascent() + paint.descent()) / 2f,
                paint.apply { textAlign = Paint.Align.CENTER }
            )
        }

        // Banner.
        val bannerTop = photoH.toFloat()
        canvas.drawRect(0f, bannerTop, WIDTH_PX.toFloat(), (photoH + BANNER_PX).toFloat(),
            Paint().apply { color = GREEN_DARK })

        val pad = 48f
        var y = bannerTop + 96f
        canvas.drawText(
            "$trayName · ${formatDate(scanDateMs)}",
            pad, y,
            textPaint(60f, bold = true, color = WHITE)
        )
        y += 76f
        canvas.drawText(
            "Health $healthPct% · Coverage $coveragePct%",
            pad, y,
            textPaint(52f, color = WHITE)
        )
        y += 72f
        val issuesLine = issues.ifBlank { "No issues spotted" }
        val issuesPaint = textPaint(44f, color = WHITE_DIM)
        val wordmarkPaint = textPaint(44f, bold = true, color = WHITE).apply {
            textAlign = Paint.Align.RIGHT
        }
        val wordmarkWidth = wordmarkPaint.measureText(WORDMARK)
        val issuesMax = (WIDTH_PX - pad * 3 - wordmarkWidth).coerceAtLeast(100f)
        val clipped = clipToWidth(issuesLine, issuesPaint, issuesMax)
        canvas.drawText(clipped, pad, y, issuesPaint)
        canvas.drawText(WORDMARK, WIDTH_PX - pad, y, wordmarkPaint)

        if (photo != null && !photo.isRecycled) photo.recycle()
        return out
    }

    /** Saves the card PNG under cacheDir/share and returns the file. */
    fun saveShareFile(context: Context, card: Bitmap, timestampMs: Long): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "scan_${timestampMs}.png")
        file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** Renders, saves, and fires the ACTION_SEND chooser. Throws on failure. */
    fun share(
        context: Context,
        photoBytes: ByteArray?,
        trayName: String,
        scanDateMs: Long,
        healthPct: Int,
        coveragePct: Int,
        issues: String
    ) {
        val card = render(photoBytes, trayName, scanDateMs, healthPct, coveragePct, issues)
        try {
            val file = saveShareFile(context, card, scanDateMs)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // The chooser wrapper is the intent actually started, so IT needs
            // NEW_TASK when sharing from the application context — flags on the
            // wrapped SEND intent do not transfer to the wrapper.
            val chooser = Intent.createChooser(send, "Share scan").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } finally {
            if (!card.isRecycled) card.recycle()
        }
    }

    private fun decodePhoto(photoBytes: ByteArray?): Bitmap? {
        if (photoBytes == null || photoBytes.isEmpty()) return null
        // Downsample huge captures toward 1080 wide before the final scale.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH_PX) sample *= 2
        return try {
            BitmapFactory.decodeByteArray(
                photoBytes, 0, photoBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun textPaint(sizePx: Float, bold: Boolean = false, color: Int = WHITE): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    /** Truncates [text] with an ellipsis so it fits [maxWidth]. */
    fun clipToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) end--
        return text.take(end.coerceAtLeast(1)) + ellipsis
    }
}
