package com.fitrater.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object ShareCard {
    private const val W = 1080
    private const val H = 1920 // 9:16
    private val cream = Color.parseColor("#F3EEE4")
    private val ink = Color.parseColor("#141210")
    private val bronze = Color.parseColor("#B0743A")
    private val muted = Color.parseColor("#6B6459")

    /**
     * Roast share card: full-bleed photo top, pull-quote below, hem attribution + brand.
     * Returns a content:// URI to the generated JPEG (cached under `roasts/`).
     */
    fun renderRoast(context: Context, photoBytes: ByteArray, quote: String): Uri {
        val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.drawColor(cream)

        val photo = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        if (photo != null) {
            val photoH = (H * 0.62f).toInt()
            val src = centerCropRect(photo, W, photoH)
            val dst = Rect(0, 0, W, photoH)
            c.drawBitmap(photo, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 62f
            isSubpixelText = true
        }
        drawWrappedText(c, "“$quote”", quotePaint, 96f, (H * 0.66f), W - 192f, 76f)

        val attrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 40f
        }
        c.drawText("— Hem", 96f, (H - 220f), attrPaint)

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bronze
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 32f
            letterSpacing = 0.2f
        }
        c.drawText("FITRATER", 96f, (H - 120f).toFloat(), brand)

        return writeBitmap(context, bm, subdir = "roasts")
    }

    /**
     * Versus share card: 2 side-by-side photos with WINNER badge, scores + comment.
     */
    fun renderVersus(
        context: Context,
        photoA: ByteArray,
        photoB: ByteArray,
        scoreA: Double,
        scoreB: Double,
        winner: String, // "A" | "B" | "tie"
        comment: String,
    ): Uri {
        val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.drawColor(cream)

        val boxH = (H * 0.48f).toInt()
        val boxW = ((W - 96 - 32) / 2f).toInt() // 48 padding sides + 32 gap
        val yTop = 220
        val a = BitmapFactory.decodeByteArray(photoA, 0, photoA.size)
        val b = BitmapFactory.decodeByteArray(photoB, 0, photoB.size)
        if (a != null) {
            val src = centerCropRect(a, boxW, boxH)
            c.drawBitmap(a, src, Rect(48, yTop, 48 + boxW, yTop + boxH), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (b != null) {
            val leftB = 48 + boxW + 32
            val src = centerCropRect(b, boxW, boxH)
            c.drawBitmap(b, src, Rect(leftB, yTop, leftB + boxW, yTop + boxH), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Title
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); textSize = 82f
        }
        c.drawText("A vs B", 48f, 140f, title)

        // Scores
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); textSize = 84f; textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 28f; letterSpacing = 0.2f; textAlign = Paint.Align.CENTER
        }
        val scoreY = (yTop + boxH + 100).toFloat()
        val cxA = 48f + boxW / 2f
        val cxB = 48f + boxW + 32f + boxW / 2f
        c.drawText("A", cxA, scoreY - 90, labelPaint)
        c.drawText("B", cxB, scoreY - 90, labelPaint)
        c.drawText(String.format(Locale.US, "%.1f", scoreA), cxA, scoreY, scorePaint)
        c.drawText(String.format(Locale.US, "%.1f", scoreB), cxB, scoreY, scorePaint)

        // Winner badge
        if (winner == "A" || winner == "B") {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bronze }
            val cx = if (winner == "A") cxA else cxB
            val badgeW = 220f; val badgeH = 56f
            val bx = cx - badgeW / 2f
            val by = yTop + 24f
            c.drawRoundRect(bx, by, bx + badgeW, by + badgeH, 28f, 28f, badgePaint)
            val bt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 30f; letterSpacing = 0.3f; textAlign = Paint.Align.CENTER
            }
            c.drawText("WINNER", cx, by + 38f, bt)
        }

        // Comment pull-quote
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); textSize = 48f
        }
        drawWrappedText(c, "“$comment”", quotePaint, 48f, (scoreY + 90f), W - 96f, 60f)

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bronze; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 32f; letterSpacing = 0.2f
        }
        c.drawText("FITRATER", 48f, (H - 80f), brand)

        return writeBitmap(context, bm, subdir = "versus")
    }

    fun launchShare(context: Context, uri: Uri, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- helpers ----

    private fun writeBitmap(context: Context, bitmap: Bitmap, subdir: String): Uri {
        val dir = File(context.cacheDir, subdir).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun centerCropRect(src: Bitmap, targetW: Int, targetH: Int): Rect {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val dstRatio = targetW.toFloat() / targetH.toFloat()
        return if (srcRatio > dstRatio) {
            // too wide → crop sides
            val newW = (src.height * dstRatio).toInt()
            val x = (src.width - newW) / 2
            Rect(x, 0, x + newW, src.height)
        } else {
            val newH = (src.width / dstRatio).toInt()
            val y = (src.height - newH) / 2
            Rect(0, y, src.width, y + newH)
        }
    }

    private fun drawWrappedText(
        c: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        yStart: Float,
        maxWidth: Float,
        lineHeight: Float,
    ) {
        val words = text.split(" ")
        var line = ""
        var y = yStart
        for (w in words) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(trial) > maxWidth) {
                c.drawText(line, x, y, paint)
                y += lineHeight
                line = w
            } else line = trial
        }
        if (line.isNotEmpty()) c.drawText(line, x, y, paint)
    }
}
