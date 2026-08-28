package com.rangia.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentScanner(private val context: Context) {
    private val ocr = OcrEngine(context)
    private val organizer = FileOrganizer(context)
    private val classifier = HybridClassifier(context)

    suspend fun scanTree(
        treeUri: Uri,
        existing: List<IndexedDocument>,
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> }
    ): List<IndexedDocument> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return existing
        val files = withContext(Dispatchers.IO) { collectFiles(root) }
            .filter { isDeepAnalyzable(it.file.name.orEmpty(), it.file.type.orEmpty()) }
        val existingByUri = existing.associateBy { it.uri }.toMutableMap()

        for ((index, scanned) in files.withIndex()) {
            val file = scanned.file
            val uriString = file.uri.toString()
            val previous = existingByUri[uriString]
            val name = file.name ?: "document"
            onProgress(index + 1, files.size, name)
            if (previous != null && previous.modifiedAt == file.lastModified() && previous.size == file.length()) continue

            val mime = file.type ?: guessMime(name)
            val text = runCatching { ocr.extract(file.uri, mime) }.getOrDefault("")
            val classification = classify(name, mime, text)
            val entities = DocumentIntelligence.extractEntities(text)
            val hash = runCatching { organizer.sha256(file.uri) }.getOrDefault("")

            existingByUri[uriString] = IndexedDocument(
                uri = uriString,
                parentTreeUri = treeUri.toString(),
                relativePath = scanned.relativePath,
                originalName = name,
                displayName = name,
                mimeType = mime,
                size = file.length(),
                modifiedAt = file.lastModified(),
                extractedText = text.take(120_000),
                categoryPath = classification.categoryPath,
                confidence = classification.confidence,
                suggestedName = DocumentIntelligence.suggestFileName(name, classification.categoryPath, entities),
                amount = entities.amount,
                detectedDate = entities.date,
                organization = entities.organization,
                hash = hash,
                duplicate = false
            )
        }

        val liveUris = files.map { it.file.uri.toString() }.toSet()
        val otherTrees = existing.filter { it.parentTreeUri != treeUri.toString() }
        val currentTree = existingByUri.values.filter { it.parentTreeUri == treeUri.toString() && it.uri in liveUris }
        return markDuplicates((otherTrees + currentTree).distinctBy { it.uri })
    }

    suspend fun scanWholePhone(
        existing: List<IndexedDocument>,
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> }
    ): List<IndexedDocument> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists() || !root.canRead()) return@withContext existing

        val files = collectWholePhoneFiles(root)
        val existingByUri = existing.associateBy { it.uri }.toMutableMap()
        val marker = WHOLE_PHONE_MARKER

        for ((index, file) in files.withIndex()) {
            val uri = Uri.fromFile(file)
            val uriString = uri.toString()
            val relative = runCatching { file.relativeTo(root).path }.getOrDefault(file.name)
            val parentRelative = relative.substringBeforeLast('/', "")
            val previous = existingByUri[uriString]
            onProgress(index + 1, files.size, relative)
            if (previous != null && previous.modifiedAt == file.lastModified() && previous.size == file.length()) continue

            val mime = guessMime(file.name)
            val deep = isDeepAnalyzable(file.name, mime) && shouldDeepAnalyze(file, relative)
            val text = if (deep) runCatching { ocr.extract(uri, mime) }.getOrDefault("") else ""
            val classification = classify(file.name, mime, text)
            val entities = if (text.isNotBlank()) DocumentIntelligence.extractEntities(text) else ExtractedEntities()
            val hash = if (file.length() in 1..MAX_HASH_BYTES) runCatching { organizer.sha256(uri) }.getOrDefault("") else ""

            val suggested = if (classification.categoryPath.startsWith("Fichiers/") || classification.categoryPath in TYPE_ONLY_CATEGORIES) {
                sanitizeOriginalName(file.name)
            } else DocumentIntelligence.suggestFileName(file.name, classification.categoryPath, entities)

            existingByUri[uriString] = IndexedDocument(
                uri = uriString,
                parentTreeUri = marker,
                relativePath = parentRelative,
                originalName = file.name,
                displayName = file.name,
                mimeType = mime,
                size = file.length(),
                modifiedAt = file.lastModified(),
                extractedText = text.take(120_000),
                categoryPath = classification.categoryPath,
                confidence = classification.confidence,
                suggestedName = suggested,
                amount = entities.amount,
                detectedDate = entities.date,
                organization = entities.organization,
                hash = hash,
                duplicate = false
            )
        }

        val live = files.map { Uri.fromFile(it).toString() }.toSet()
        val nonPhoneEntries = existing.filter { it.parentTreeUri != marker }
        val phoneEntries = existingByUri.values.filter { it.parentTreeUri == marker && it.uri in live }
        markDuplicates((nonPhoneEntries + phoneEntries).distinctBy { it.uri })
    }

    private fun collectWholePhoneFiles(root: File): List<File> {
        val out = ArrayList<File>(2048)
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            for (child in children) {
                if (child.isDirectory) {
                    if (!shouldSkipDirectory(root, child)) stack.add(child)
                } else if (child.isFile && child.canRead()) out += child
            }
        }
        return out.sortedByDescending { it.lastModified() }
    }

    private fun shouldSkipDirectory(root: File, dir: File): Boolean {
        val rel = runCatching { dir.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
        if (rel == "Android/data" || rel.startsWith("Android/data/")) return true
        if (rel == "Android/obb" || rel.startsWith("Android/obb/")) return true
        if (rel.startsWith(".Trash") || rel.startsWith(".thumbnails")) return true
        return false
    }

    private fun shouldDeepAnalyze(file: File, relative: String): Boolean {
        if (file.length() <= 0L || file.length() > MAX_OCR_BYTES) return false
        val mime = guessMime(file.name)
        if (mime == "application/pdf" || mime.startsWith("text/")) return true
        if (!mime.startsWith("image/")) return false
        val p = relative.lowercase()
        return listOf("download", "document", "scan", "screenshot", "whatsapp/documents", "telegram/documents", "bluetooth").any { it in p }
    }

    private fun classify(name: String, mime: String, text: String): ClassificationResult {
        val ai = classifier.classify(name, text)
        if (ai.categoryPath != "Autres" || text.isNotBlank()) return ai
        return ClassificationResult(categoryFromType(name, mime), 0.94f, listOf("type de fichier"))
    }

    private fun categoryFromType(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "Photos"
            mime.startsWith("video/") -> "Vidéos"
            mime.startsWith("audio/") -> "Audio"
            mime == "application/pdf" -> "Documents/PDF"
            mime.startsWith("text/") -> "Documents/Texte"
            ext in setOf("doc", "docx", "odt", "rtf") -> "Documents/Word"
            ext in setOf("xls", "xlsx", "ods", "csv") -> "Documents/Tableurs"
            ext in setOf("ppt", "pptx", "odp") -> "Documents/Présentations"
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> "Archives"
            ext == "apk" -> "Applications_APK"
            ext in setOf("epub", "mobi", "azw", "azw3") -> "Livres"
            ext in setOf("ttf", "otf", "woff", "woff2") -> "Polices"
            else -> "Fichiers/Autres"
        }
    }

    private data class ScannedFile(val file: DocumentFile, val relativePath: String)

    private fun collectFiles(root: DocumentFile): List<ScannedFile> {
        val out = mutableListOf<ScannedFile>()
        val stack = ArrayDeque<Pair<DocumentFile, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty()) {
            val (current, currentPath) = stack.removeLast()
            current.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val childPath = listOf(currentPath, child.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/")
                    stack.add(child to childPath)
                } else if (child.isFile) out += ScannedFile(child, currentPath)
            }
        }
        return out
    }

    private fun isDeepAnalyzable(name: String, type: String): Boolean {
        val lower = name.lowercase()
        return type == "application/pdf" || type.startsWith("image/") || type.startsWith("text/") ||
            lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".txt")
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "txt", "log", "md" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun sanitizeOriginalName(name: String): String = name.replace(Regex("[\\/:*?\"<>|]"), "_")

    private fun markDuplicates(input: List<IndexedDocument>): List<IndexedDocument> {
        val duplicateHashes = input.filter { it.hash.isNotBlank() }.groupingBy { it.hash }.eachCount().filterValues { it > 1 }.keys
        return input.map { it.copy(duplicate = it.hash.isNotBlank() && it.hash in duplicateHashes) }.sortedByDescending { it.modifiedAt }
    }

    companion object {
        const val WHOLE_PHONE_MARKER = "phone://shared-storage"
        private const val MAX_OCR_BYTES = 80L * 1024 * 1024
        private const val MAX_HASH_BYTES = 256L * 1024 * 1024
        private val TYPE_ONLY_CATEGORIES = setOf("Photos", "Vidéos", "Audio", "Archives", "Applications_APK", "Livres", "Polices")
    }
}
