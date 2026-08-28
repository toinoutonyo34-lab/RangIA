package com.rangia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extract(uri: Uri, mimeType: String): String = when {
        mimeType == "application/pdf" || uri.toString().endsWith(".pdf", true) -> extractPdf(uri)
        mimeType.startsWith("image/") -> extractImage(uri)
        mimeType.startsWith("text/") -> withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        else -> ""
    }

    private suspend fun extractImage(uri: Uri): String = process(InputImage.fromFilePath(context, uri))

    private suspend fun extractPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("rangia_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return@withContext ""

            android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pages = minOf(renderer.pageCount, 4)
                    val parts = mutableListOf<String>()
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val targetWidth = 1600
                            val targetHeight = (targetWidth.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            try { parts += process(InputImage.fromBitmap(bitmap, 0)) } finally { bitmap.recycle() }
                        }
                    }
                    parts.joinToString("\n")
                }
            }
        } finally { temp.delete() }
    }

    private suspend fun process(image: InputImage): String = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
            .addOnFailureListener { err -> if (cont.isActive) cont.resumeWithException(err) }
    }
}
