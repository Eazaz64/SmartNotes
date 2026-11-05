package com.eazaz.smartnotes.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import com.eazaz.smartnotes.R
import com.eazaz.smartnotes.data.Note
import com.eazaz.smartnotes.ui.theme.NoteColors
import java.io.File
import java.io.FileOutputStream

/** Shares a note as a picture, a PDF or plain text, keeping its color and layout. */
object NoteSharer {

    private const val INK = 0xFF202124.toInt()
    private const val MUTED = 0xFF5F6368.toInt()

    fun shareText(context: Context, note: Note) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, plainText(note))
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }

    fun shareImage(context: Context, note: Note) {
        val width = 1080
        val pad = 72f
        val layout = buildLayout(note, (width - 2 * pad).toInt(), 44f)
        val height = (layout.height + 2 * pad).toInt().coerceAtMost(16000)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        drawCard(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), note, 0f)
        canvas.save()
        canvas.translate(pad, pad)
        layout.draw(canvas)
        canvas.restore()

        val file = outFile(context, note, "png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        sendFile(context, file, "image/png", note)
    }

    fun sharePdf(context: Context, note: Note) {
        val pageW = 595 // A4 in points
        val pageH = 842
        val margin = 48f
        val inner = 28f
        val textW = (pageW - 2 * margin - 2 * inner).toInt()
        val textH = pageH - 2 * margin - 2 * inner
        val layout = buildLayout(note, textW, 13f)

        val doc = PdfDocument()
        var startLine = 0
        var pageNo = 1
        while (startLine < layout.lineCount) {
            val top = layout.getLineTop(startLine)
            var end = startLine
            while (end < layout.lineCount && (layout.getLineBottom(end) - top).toFloat() <= textH) end++
            if (end == startLine) end = startLine + 1 // a single very tall line

            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            val usedH = layout.getLineBottom(end - 1) - top
            drawCard(c, RectF(margin, margin, pageW - margin, margin + usedH + 2 * inner), note, 14f)
            c.save()
            c.clipRect(margin + inner, margin + inner, pageW - margin - inner, margin + inner + usedH.toFloat())
            c.translate(margin + inner, margin + inner - top)
            layout.draw(c)
            c.restore()
            doc.finishPage(page)

            startLine = end
            pageNo++
        }
        val file = outFile(context, note, "pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        sendFile(context, file, "application/pdf", note)
    }

    // ------------------------------------------------------------------ helpers

    private fun drawCard(canvas: Canvas, rect: RectF, note: Note, radius: Float) {
        val bg = NoteColors.shareArgb(note.color)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (note.color == 0 && radius > 0f) {
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFFDADCE0.toInt()
            }
            canvas.drawRoundRect(rect, radius, radius, border)
        }
    }

    private fun buildLayout(note: Note, width: Int, textSize: Float): StaticLayout {
        val sb = SpannableStringBuilder()
        if (note.title.isNotBlank()) {
            val s = sb.length
            sb.append(note.title.trim())
            sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.3f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n\n")
        }
        if (note.isChecklist) {
            val ordered = note.items.filter { !it.checked } + note.items.filter { it.checked }
            ordered.filter { it.text.isNotBlank() }.forEachIndexed { i, item ->
                if (i > 0) sb.append("\n")
                val s = sb.length
                sb.append(if (item.checked) "☑  " else "☐  ")
                sb.append(item.text)
                if (item.checked) {
                    sb.setSpan(StrikethroughSpan(), s + 3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(MUTED), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            sb.append(note.content.trimEnd())
        }
        if (note.labels.isNotEmpty()) {
            sb.append("\n\n")
            val s = sb.length
            sb.append(note.labels.joinToString("    ") { "# $it" })
            sb.setSpan(ForegroundColorSpan(MUTED), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.8f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (sb.isEmpty()) sb.append(" ")

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            this.textSize = textSize
        }
        return StaticLayout.Builder.obtain(sb, 0, sb.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(true)
            .build()
    }

    private fun plainText(note: Note): String = buildString {
        if (note.title.isNotBlank()) append(note.title.trim()).append("\n\n")
        if (note.isChecklist) {
            val ordered = note.items.filter { !it.checked } + note.items.filter { it.checked }
            append(ordered.filter { it.text.isNotBlank() }
                .joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text })
        } else {
            append(note.content.trimEnd())
        }
    }

    private fun outFile(context: Context, note: Note, ext: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep the cache small
        val base = note.title.trim().take(40).replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").ifBlank { "note" }
        return File(dir, "$base.$ext")
    }

    private fun sendFile(context: Context, file: File, mime: String, note: Note) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }
}
