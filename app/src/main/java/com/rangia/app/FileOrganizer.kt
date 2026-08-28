package com.rangia.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    suspend fun moveToTrash(doc: IndexedDocument, rootTreeUri: Uri? = null): Result<Uri> = withContext(Dispatchers.IO) {
        if (doc.parentTreeUri == DocumentScanner.WHOLE_PHONE_MARKER || doc.contentUri.scheme == "file") trashDirect(doc)
        else {
            val tree = rootTreeUri ?: return@withContext Result.failure(IllegalStateException("Dossier racine manquant"))
            trashSaf(doc, tree)
        }
    }

    suspend fun deletePermanently(doc: IndexedDocument): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (doc.contentUri.scheme == "file" || doc.parentTreeUri == DocumentScanner.WHOLE_PHONE_MARKER) {
                val file = File(doc.contentUri.path ?: error("Chemin invalide"))
                require(canUserModify(file) || isInRangIaTrash(file)) { "Suppression refusée pour ce dossier protégé." }
                if (file.exists() && !file.delete()) error("Impossible de supprimer le fichier")
                notifyMediaChanged(file.path)
            } else {
                val source = DocumentFile.fromSingleUri(context, doc.contentUri) ?: error("Fichier inaccessible")
                if (source.exists() && !source.delete()) error("Impossible de supprimer le fichier")
            }
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trash = trashRoot()
            if (!trash.exists()) return@runCatching 0
            val files = trash.walkBottomUp().filter { it.isFile }.toList()
            var deleted = 0
            files.forEach { f -> if (f.delete()) deleted++ }
            trash.walkBottomUp().filter { it.isDirectory && it != trash }.forEach { it.delete() }
            notifyMediaChanged(trash.path)
            deleted
        }
    }

    fun canModify(doc: IndexedDocument): Boolean {
        if (doc.contentUri.scheme != "file" && doc.parentTreeUri != DocumentScanner.WHOLE_PHONE_MARKER) return true
        val file = File(doc.contentUri.path ?: return false)
        return canUserModify(file) || isInRangIaTrash(file)
    }

    fun isSafeAutoOrganizeFile(file: File): Boolean {
        val rel = relativePath(file)
        if (rel.isBlank() || rel.startsWith("RangIA/") || isProtectedPath(rel)) return false
        return rel.substringBefore('/').lowercase(Locale.ROOT) in SAFE_AUTO_TOP_LEVEL_DIRS
    }

    fun isSafeUserFile(file: File): Boolean = isSafeAutoOrganizeFile(file)

    private fun canUserModify(file: File): Boolean {
        val rel = relativePath(file)
        if (rel.isBlank() || isProtectedPath(rel)) return false
        val p = rel.lowercase(Locale.ROOT)
        if (p.startsWith("rangia/corbeille/")) return true
        if (p == "android/media" || p.startsWith("android/media/")) return true
        if (p == "telegram" || p.startsWith("telegram/")) return true
        if (p == "whatsapp" || p.startsWith("whatsapp/")) return true
        val top = p.substringBefore('/')
        return top in USER_MANAGED_TOP_LEVEL_DIRS
    }

    private fun organizeDirect(doc: IndexedDocument): Result<Uri> = runCatching {
        val source = File(doc.contentUri.path ?: error("Chemin source invalide"))
        require(source.exists() && source.isFile) { "Fichier source inaccessible" }
        require(isSafeAutoOrganizeFile(source)) {
            "Ce fichier est classé dans RangIA mais son emplacement n’est pas déplacé automatiquement pour éviter de perturber une autre application."
        }

        val targetDir = File(rangIaRoot(), sanitizePath(doc.categoryPath))
        copyThenDelete(source, targetDir, doc.suggestedName.ifBlank { doc.originalName })
    }

    private fun trashDirect(doc: IndexedDocument): Result<Uri> = runCatching {
        val source = File(doc.contentUri.path ?: error("Chemin source invalide"))
        require(source.exists() && source.isFile) { "Fichier source inaccessible" }
        require(canUserModify(source) && !isInRangIaTrash(source)) {
            "RangIA ne peut pas mettre ce fichier à la corbeille depuis cet emplacement protégé."
        }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val targetDir = File(trashRoot(), day)
        copyThenDelete(source, targetDir, source.name)
    }

    private fun copyThenDelete(source: File, targetDir: File, desiredName: String): Uri {
        if (!targetDir.exists() && !targetDir.mkdirs()) error("Impossible de créer ${targetDir.path}")
        val target = uniqueFile(targetDir, desiredName)
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output); output.flush() } }
        if (source.length() > 0 && target.length() != source.length()) {
            target.delete(); error("Vérification de copie échouée")
        }
        val oldPath = source.path
        if (!source.delete()) {
            target.delete(); error("Copie créée mais suppression de l'original impossible")
        }
        notifyMediaChanged(oldPath)
        notifyMediaChanged(target.path)
        return Uri.fromFile(target)
    }

    private fun organizeSaf(doc: IndexedDocument, rootTreeUri: Uri): Result<Uri> = runCatching {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: error("Dossier racine inaccessible")
        val targetDir = ensurePath(root, doc.categoryPath)
        copyThenDeleteSaf(doc, targetDir, doc.suggestedName.ifBlank { doc.originalName })
    }

    private fun trashSaf(doc: IndexedDocument, rootTreeUri: Uri): Result<Uri> = runCatching {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: error("Dossier racine inaccessible")
        val targetDir = ensurePath(root, "RangIA_Corbeille")
        copyThenDeleteSaf(doc, targetDir, doc.originalName)
    }

    private fun copyThenDeleteSaf(doc: IndexedDocument, targetDir: DocumentFile, desiredName: String): Uri {
        val source = DocumentFile.fromSingleUri(context, doc.contentUri) ?: error("Fichier source inaccessible")
        val finalName = uniqueName(targetDir, desiredName)
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
            return target.uri
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

    private fun relativePath(file: File): String {
        val root = Environment.getExternalStorageDirectory()
        return runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
    }

    private fun isProtectedPath(rel: String): Boolean {
        val p = rel.lowercase(Locale.ROOT)
        return p == "android/data" || p.startsWith("android/data/") ||
            p == "android/obb" || p.startsWith("android/obb/") ||
            p.startsWith(".trash") || p.startsWith(".thumbnails")
    }

    private fun isInRangIaTrash(file: File): Boolean = relativePath(file).lowercase(Locale.ROOT).startsWith("rangia/corbeille/")

    private fun rangIaRoot(): File = File(Environment.getExternalStorageDirectory(), "RangIA")
    private fun trashRoot(): File = File(rangIaRoot(), "Corbeille")

    private fun notifyMediaChanged(path: String) {
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) }
    }

    private fun sanitizePath(path: String): String = path.split('/').filter { it.isNotBlank() }
        .joinToString(File.separator) { it.replace(Regex("[\\:*?\"<>|]"), "_") }

    companion object {
        private val SAFE_AUTO_TOP_LEVEL_DIRS = setOf(
            "download", "downloads", "documents", "document", "bluetooth", "mishare", "shareme", "received", "received files"
        )

        private val USER_MANAGED_TOP_LEVEL_DIRS = SAFE_AUTO_TOP_LEVEL_DIRS + setOf(
            "dcim", "pictures", "movies", "music", "podcasts", "ringtones", "alarms", "notifications", "rangia", "whatsapp", "telegram"
        )
    }
}
