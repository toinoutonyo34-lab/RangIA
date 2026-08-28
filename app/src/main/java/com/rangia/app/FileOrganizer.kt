package com.rangia.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class FileOrganizer(private val context: Context) {
    suspend fun sha256(uri: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun organize(doc: IndexedDocument, rootTreeUri: Uri? = null): Result<Uri> = withContext(Dispatchers.IO) {
        if (doc.parentTreeUri == DocumentScanner.WHOLE_PHONE_MARKER || doc.contentUri.scheme == "file") organizeDirect(doc)
        else {
            val tree = rootTreeUri ?: return@withContext Result.failure(IllegalStateException("Dossier racine manquant"))
            organizeSaf(doc, tree)
        }
    }

    private fun organizeDirect(doc: IndexedDocument): Result<Uri> = runCatching {
        val source = File(doc.contentUri.path ?: error("Chemin source invalide"))
        require(source.exists() && source.isFile) { "Fichier source inaccessible" }
        require(isSafeUserFile(source)) { "Ce fichier appartient à un dossier géré par une autre application ; RangIA le classe virtuellement mais ne le déplace pas automatiquement." }

        val sharedRoot = Environment.getExternalStorageDirectory()
        val targetDir = File(File(sharedRoot, "RangIA"), sanitizePath(doc.categoryPath))
        if (!targetDir.exists() && !targetDir.mkdirs()) error("Impossible de créer ${targetDir.path}")
        val target = uniqueFile(targetDir, doc.suggestedName.ifBlank { doc.originalName })

        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output); output.flush() } }
        if (source.length() > 0 && target.length() != source.length()) {
            target.delete(); error("Vérification de copie échouée")
        }
        if (!source.delete()) {
            target.delete(); error("Copie créée mais suppression de l'original impossible")
        }
        Uri.fromFile(target)
    }

    fun isSafeUserFile(file: File): Boolean {
        val root = Environment.getExternalStorageDirectory()
        val rel = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
        if (rel.isBlank() || rel.startsWith("RangIA/") || rel.startsWith("Android/")) return false
        return rel.substringBefore('/').lowercase() in SAFE_TOP_LEVEL_DIRS
    }

    private fun organizeSaf(doc: IndexedDocument, rootTreeUri: Uri): Result<Uri> = runCatching {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: error("Dossier racine inaccessible")
        val targetDir = ensurePath(root, doc.categoryPath)
        val source = DocumentFile.fromSingleUri(context, doc.contentUri) ?: error("Fichier source inaccessible")
        val finalName = uniqueName(targetDir, doc.suggestedName)
        val target = targetDir.createFile(doc.mimeType.ifBlank { "application/octet-stream" }, finalName)
            ?: error("Impossible de créer le fichier cible")

        var copied = false
        try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    input.copyTo(output); output.flush(); copied = true
                } ?: error("Impossible d'écrire dans le dossier cible")
            } ?: error("Impossible de lire le fichier source")

            val sourceSize = source.length()
            if (sourceSize > 0 && target.length() != sourceSize) error("Vérification de copie échouée")
            if (!source.delete()) error("Copie créée, mais impossible de supprimer l'original")
            target.uri
        } catch (t: Throwable) {
            if (copied) target.delete()
            throw t
        }
    }

    private fun ensurePath(root: DocumentFile, path: String): DocumentFile {
        var current = root
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = current.findFile(segment)?.takeIf { it.isDirectory }
                ?: current.createDirectory(segment) ?: error("Impossible de créer le dossier $segment")
        }
        return current
    }

    private fun uniqueName(dir: DocumentFile, desired: String): String {
        if (dir.findFile(desired) == null) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        for (i in 2..999) {
            val candidate = "${base}_$i$ext"
            if (dir.findFile(candidate) == null) return candidate
        }
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    private fun uniqueFile(dir: File, desiredRaw: String): File {
        val desired = desiredRaw.replace(Regex("[\\/:*?\"<>|]"), "_")
        var f = File(dir, desired)
        if (!f.exists()) return f
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        for (i in 2..999) {
            f = File(dir, "${base}_$i$ext")
            if (!f.exists()) return f
        }
        return File(dir, "${base}_${System.currentTimeMillis()}$ext")
    }

    private fun sanitizePath(path: String): String = path.split('/').filter { it.isNotBlank() }
        .joinToString(File.separator) { it.replace(Regex("[\\:*?\"<>|]"), "_") }

    companion object {
        private val SAFE_TOP_LEVEL_DIRS = setOf(
            "download", "downloads", "documents", "document", "bluetooth", "mishare", "shareme", "received", "received files"
        )
    }
}
