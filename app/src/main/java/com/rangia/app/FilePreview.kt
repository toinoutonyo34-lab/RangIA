package com.rangia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

object FilePreviewLoader {
    fun load(context: Context, doc: IndexedDocument, targetPx: Int = 220): Bitmap? =
        load(context, doc.contentUri, doc.mimeType, doc.originalName, targetPx)

    fun load(context: Context, uri: Uri, mimeType: String, fileName: String, targetPx: Int = 220): Bitmap? = runCatching {
        when {
            mimeType.startsWith("image/") -> loadImage(context, uri, targetPx)
            mimeType.startsWith("video/") -> loadVideo(context, uri)
            mimeType == "application/pdf" || fileName.endsWith(".pdf", true) -> loadPdf(context, uri, targetPx)
            else -> null
        }
    }.getOrNull()

    private fun loadImage(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide > 0 && maxSide / sample > targetPx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun loadVideo(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                retriever.setDataSource(path)
            } else retriever.setDataSource(context, uri)
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

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists()) null else FileInputStream(file)
        } else context.contentResolver.openInputStream(uri)
    }

    private fun openPfd(context: Context, uri: Uri): ParcelFileDescriptor? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists()) null else ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else context.contentResolver.openFileDescriptor(uri, "r")
    }
}
