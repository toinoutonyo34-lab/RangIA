package com.rangia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

object FilePreviewLoader {
    fun load(context: Context, doc: IndexedDocument, targetPx: Int = 220): Bitmap? = runCatching {
        when {
            doc.mimeType.startsWith("image/") -> loadImage(context, doc.contentUri, targetPx)
            doc.mimeType.startsWith("video/") -> loadVideo(context, doc.contentUri)
            doc.mimeType == "application/pdf" || doc.originalName.endsWith(".pdf", true) -> loadPdf(context, doc.contentUri, targetPx)
            else -> null
        }
    }.getOrNull()

    private fun loadImage(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > targetPx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun loadVideo(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path)
            else retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadPdf(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val pfd = openPfd(context, uri) ?: return null
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val ratio = page.height.toFloat() / page.width.coerceAtLeast(1)
                    val width = targetPx.coerceAtLeast(120)
                    val height = (width * ratio).toInt().coerceIn(120, targetPx * 2)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun openStream(context: Context, uri: Uri) =
        if (uri.scheme == "file") FileInputStream(File(uri.path ?: return null))
        else context.contentResolver.openInputStream(uri)

    private fun openPfd(context: Context, uri: Uri): ParcelFileDescriptor? =
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (!file.exists()) null else ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else context.contentResolver.openFileDescriptor(uri, "r")
}
