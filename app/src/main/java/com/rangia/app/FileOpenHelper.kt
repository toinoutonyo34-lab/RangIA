package com.rangia.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileOpenHelper {
    fun open(context: Context, doc: IndexedDocument): Result<Unit> = runCatching {
        val uri = if (doc.contentUri.scheme == "file") {
            val file = File(doc.contentUri.path ?: error("Chemin de fichier invalide"))
            require(file.exists()) { "Fichier introuvable" }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } else doc.contentUri

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, doc.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
