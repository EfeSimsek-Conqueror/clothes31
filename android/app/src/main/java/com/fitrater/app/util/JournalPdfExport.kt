package com.fitrater.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.fitrater.app.data.model.Outfit
import com.fitrater.app.data.repo.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class JournalExportData(
    val monthLabel: String,
    val year: Int,
    val outfits: List<Outfit>,
    val urlsByOutfitId: Map<String, String?>,
    val sparkline: List<Double>,
    val palette: List<Int>,
    val average: Double?,
    val delta: Double?,
)

object JournalPdfExport {
    private const val PAGE_W = 612 // 8.5in
    private const val PAGE_H = 792 // 11in
    private const val MARGIN = 40f

    suspend fun exportAndShare(context: Context, data: JournalExportData) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val fileName = "journal-${"%04d".format(data.year)}-${data.monthLabel}.pdf"
                .replace(" ", "-").lowercase()
            val file = File(dir, fileName)
            val doc = PdfDocument()
            try {
                var pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
                var page = doc.startPage(pageInfo)
                var canvas = page.canvas
                var y = drawHeader(canvas, data)
                y = drawSparkline(canvas, y, data.sparkline)
                y = drawPalette(canvas, y, data.palette)
                y = drawSummary(canvas, y, data)
                doc.finishPage(page)

                // Outfits pages
                var pageIndex = 2
                var currentY = MARGIN
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex).create())
                canvas = page.canvas
                drawPageTitle(canvas, "Fits", currentY)
                currentY += 40f

                data.outfits.forEach { o ->
                    val cardH = 150f
                    if (currentY + cardH > PAGE_H - MARGIN) {
                        doc.finishPage(page)
                        pageIndex += 1
                        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex).create())
                        canvas = page.canvas
                        currentY = MARGIN
                    }
                    drawOutfitCard(context, canvas, currentY, o, data.urlsByOutfitId[o.id])
                    currentY += cardH + 12f
                }
                doc.finishPage(page)

                FileOutputStream(file).use { doc.writeTo(it) }
            } finally {
                doc.close()
            }

            // Share
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(send, "Share Journal PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun drawHeader(canvas: Canvas, data: JournalExportData): Float {
        val title = Paint().apply {
            color = Color.rgb(20, 18, 16)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = 28f
            isAntiAlias = true
        }
        val eyebrow = Paint().apply {
            color = Color.rgb(176, 116, 58)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10f
            letterSpacing = 0.15f
            isAntiAlias = true
        }
        canvas.drawText("FITRATER · JOURNAL", MARGIN, MARGIN + 12f, eyebrow)
        canvas.drawText("${data.monthLabel} ${data.year}", MARGIN, MARGIN + 44f, title)
        return MARGIN + 72f
    }

    private fun drawPageTitle(canvas: Canvas, text: String, y: Float) {
        val title = Paint().apply {
            color = Color.rgb(20, 18, 16)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = 22f
            isAntiAlias = true
        }
        canvas.drawText(text, MARGIN, y + 20f, title)
    }

    private fun drawSparkline(canvas: Canvas, yStart: Float, values: List<Double>): Float {
        val h = 60f
        val w = PAGE_W - MARGIN * 2
        val paint = Paint().apply {
            color = Color.rgb(176, 116, 58)
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        if (values.size >= 2) {
            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
            val stepX = w / (values.size - 1).toFloat()
            var prevX = MARGIN
            var prevY = yStart + h - ((values[0] - minV) / range).toFloat() * h
            for (i in 1 until values.size) {
                val x = MARGIN + i * stepX
                val y = yStart + h - ((values[i] - minV) / range).toFloat() * h
                canvas.drawLine(prevX, prevY, x, y, paint)
                prevX = x; prevY = y
            }
        }
        return yStart + h + 12f
    }

    private fun drawPalette(canvas: Canvas, yStart: Float, colors: List<Int>): Float {
        if (colors.isEmpty()) return yStart
        val h = 20f
        val w = (PAGE_W - MARGIN * 2) / colors.size.toFloat()
        val paint = Paint().apply { isAntiAlias = true }
        colors.forEachIndexed { i, c ->
            paint.color = c
            canvas.drawRect(MARGIN + i * w, yStart, MARGIN + (i + 1) * w, yStart + h, paint)
        }
        return yStart + h + 20f
    }

    private fun drawSummary(canvas: Canvas, yStart: Float, data: JournalExportData): Float {
        val sub = Paint().apply {
            color = Color.rgb(20, 18, 16)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = 16f
            isAntiAlias = true
        }
        val muted = Paint().apply {
            color = Color.rgb(107, 100, 89)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 11f
            isAntiAlias = true
        }
        canvas.drawText("This month at a glance", MARGIN, yStart + 16f, sub)
        val avgText = data.average?.let { "Average %.1f".format(Locale.US, it) } ?: "Average –"
        val deltaText = data.delta?.let {
            val sign = if (it >= 0) "+" else ""
            " · $sign%.1f vs last".format(Locale.US, it)
        } ?: ""
        canvas.drawText("$avgText$deltaText · ${data.outfits.size} fits", MARGIN, yStart + 34f, muted)
        return yStart + 50f
    }

    private fun drawOutfitCard(context: Context, canvas: Canvas, yStart: Float, outfit: Outfit, url: String?) {
        val cardH = 150f
        val imgW = 96f
        val hairline = Paint().apply {
            color = Color.argb(0x22, 0, 0, 0)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(MARGIN, yStart, PAGE_W - MARGIN, yStart + cardH, hairline)

        // Try to fetch image (already downloaded — attempt within timeout)
        if (!url.isNullOrBlank()) {
            runCatching {
                val bytes = Repo.let {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 6000
                    conn.inputStream.use { it.readBytes() }
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()?.let { bmp ->
                val ratio = 4f / 5f
                val targetH = cardH - 16f
                val targetW = targetH * ratio
                val dst = RectF(MARGIN + 8f, yStart + 8f, MARGIN + 8f + targetW, yStart + 8f + targetH)
                val src = fitCropSrc(bmp, ratio)
                canvas.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }

        val textX = MARGIN + 8f + imgW + 12f
        val score = Paint().apply {
            color = Color.rgb(176, 116, 58)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = 30f
            isAntiAlias = true
        }
        val occ = Paint().apply {
            color = Color.rgb(20, 18, 16)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 12f
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.rgb(20, 18, 16)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 11f
            isAntiAlias = true
        }
        outfit.score?.let {
            canvas.drawText("%.1f".format(Locale.US, it), textX, yStart + 32f, score)
        }
        canvas.drawText((outfit.occasion ?: "LOOK").uppercase(), textX, yStart + 52f, occ)
        val note = outfit.hem_comment ?: outfit.notes ?: ""
        if (note.isNotBlank()) {
            drawWrapped(canvas, note.take(280), textX, yStart + 72f, PAGE_W - MARGIN - textX - 8f, body, 14f)
        }
    }

    private fun fitCropSrc(bmp: Bitmap, targetRatio: Float): Rect {
        val bw = bmp.width
        val bh = bmp.height
        val srcRatio = bw.toFloat() / bh.toFloat()
        return if (srcRatio > targetRatio) {
            // too wide — crop sides
            val newW = (bh * targetRatio).toInt()
            val x = (bw - newW) / 2
            Rect(x, 0, x + newW, bh)
        } else {
            val newH = (bw / targetRatio).toInt()
            val y = (bh - newH) / 2
            Rect(0, y, bw, y + newH)
        }
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        yStart: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
    ) {
        val words = text.split(" ")
        val line = StringBuilder()
        var y = yStart
        for (w in words) {
            val trial = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(trial) > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
                line.clear()
                line.append(w)
                if (y > yStart + lineHeight * 5) return
            } else {
                if (line.isNotEmpty()) line.append(" ")
                line.append(w)
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line.toString(), x, y, paint)
    }

    fun defaultPalette(): List<Int> = listOf(
        Color.rgb(176, 116, 58),
        Color.rgb(122, 106, 85),
        Color.rgb(62, 54, 46),
        Color.rgb(196, 168, 131),
        Color.rgb(139, 115, 85),
    )

    fun parseIso(iso: String?): ZonedDateTime? =
        iso?.let { runCatching { ZonedDateTime.parse(it.replace(" ", "T")) }.getOrNull() }

    fun monthLabelOf(d: ZonedDateTime): String =
        d.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
}
