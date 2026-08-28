package com.rangia.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
            .filter { isIndexableInTree(it.file.name.orEmpty(), it.file.type.orEmpty()) }
        val existingByUri = existing.associateBy { it.uri }.toMutableMap()

        for ((index, scanned) in files.withIndex()) {
            val file = scanned.file
            val uriString = file.uri.toString()
            val previous = existingByUri[uriString]
            val name = file.name ?: "document"
            onProgress(index + 1, files.size, name)
            if (previous != null &&
                previous.modifiedAt == file.lastModified() &&
                previous.size == file.length() &&
                previous.classificationVersion == HybridClassifier.MODEL_VERSION
            ) continue

            val mime = file.type ?: guessMime(name)
            val text = if (isSemanticDocument(name, mime)) runCatching { ocr.extract(file.uri, mime) }.getOrDefault("") else ""
            val classification = classifyProfessional(name, mime, text, scanned.relativePath)
            val entities = if (text.isNotBlank()) DocumentIntelligence.extractEntities(text) else ExtractedEntities()
            val hash = runCatching { organizer.sha256(file.uri) }.getOrDefault("")
            val suggested = suggestedName(name, mime, classification, entities)

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
                suggestedName = suggested,
                amount = entities.amount,
                detectedDate = entities.date,
                organization = entities.organization,
                hash = hash,
                duplicate = false,
                classificationVersion = HybridClassifier.MODEL_VERSION,
                classificationEvidence = classification.matchedKeywords
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
            val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault(file.name)
            val parentRelative = relative.substringBeforeLast('/', "")
            val previous = existingByUri[uriString]
            onProgress(index + 1, files.size, relative)

            if (previous != null &&
                previous.modifiedAt == file.lastModified() &&
                previous.size == file.length() &&
                previous.classificationVersion == HybridClassifier.MODEL_VERSION
            ) continue

            val mime = guessMime(file.name)
            val canDeepAnalyze = isSemanticDocument(file.name, mime) && shouldDeepAnalyze(file, relative)
            val text = if (canDeepAnalyze) runCatching { ocr.extract(uri, mime) }.getOrDefault("") else ""
            val classification = if (canDeepAnalyze || mime == "application/pdf") {
                classifyProfessional(file.name, mime, text, parentRelative)
            } else {
                ClassificationResult(categoryFromType(file.name, mime, parentRelative), 0.99f, listOf("type de fichier"))
            }
            val entities = if (text.isNotBlank()) DocumentIntelligence.extractEntities(text) else ExtractedEntities()
            val hash = if (file.length() in 1..MAX_HASH_BYTES) runCatching { organizer.sha256(uri) }.getOrDefault("") else ""
            val suggested = suggestedName(file.name, mime, classification, entities)

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
                duplicate = false,
                classificationVersion = HybridClassifier.MODEL_VERSION,
                classificationEvidence = classification.matchedKeywords
            )
        }

        val live = files.map { Uri.fromFile(it).toString() }.toSet()
        val nonPhoneEntries = existing.filter { it.parentTreeUri != marker }
        val phoneEntries = existingByUri.values.filter { it.parentTreeUri == marker && it.uri in live }
        markDuplicates((nonPhoneEntries + phoneEntries).distinctBy { it.uri })
    }

    private fun classifyProfessional(name: String, mime: String, text: String, relativePath: String): ClassificationResult {
        // Binary/media/archive types must never be guessed by the document AI.
        if (!isSemanticDocument(name, mime)) {
            return ClassificationResult(categoryFromType(name, mime, relativePath), 0.99f, listOf("type de fichier"))
        }

        if (mime == "application/pdf" && text.isBlank()) {
            return ClassificationResult("A_verifier/Documents", 0.30f, listOf("PDF sans texte exploitable"))
        }

        val result = classifier.classify(name, text)
        if (result.categoryPath == "A_verifier/Documents" && mime.startsWith("image/")) {
            return ClassificationResult(categoryFromType(name, mime, relativePath), 0.82f, listOf("image sans signature documentaire fiable"))
        }
        return result
    }

    private fun suggestedName(
        originalName: String,
        mime: String,
        classification: ClassificationResult,
        entities: ExtractedEntities
    ): String {
        if (classification.categoryPath.startsWith("A_verifier/")) return sanitizeOriginalName(originalName)
        if (classification.categoryPath.startsWith("Fichiers/") ||
            classification.categoryPath.startsWith("Photos/") ||
            classification.categoryPath.startsWith("Vidéos/") ||
            classification.categoryPath.startsWith("Audio/") ||
            classification.categoryPath in TYPE_ONLY_CATEGORIES
        ) return sanitizeOriginalName(originalName)
        return DocumentIntelligence.suggestFileName(originalName, classification.categoryPath, entities)
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
        val lower = rel.lowercase(Locale.ROOT)
        if (lower == "android/data" || lower.startsWith("android/data/")) return true
        if (lower == "android/obb" || lower.startsWith("android/obb/")) return true
        if (lower == "rangia/corbeille" || lower.startsWith("rangia/corbeille/")) return true
        if (lower.startsWith(".trash") || lower.startsWith(".thumbnails")) return true
        return false
    }

    private fun shouldDeepAnalyze(file: File, relative: String): Boolean {
        if (file.length() <= 0L || file.length() > MAX_OCR_BYTES) return false
        val mime = guessMime(file.name)
        if (mime == "application/pdf" || mime.startsWith("text/")) return true
        if (!mime.startsWith("image/")) return false
        val p = relative.lowercase(Locale.ROOT)
        return listOf(
            "download", "document", "scan", "screenshot", "whatsapp/documents", "telegram/documents",
            "bluetooth", "received", "administratif", "facture", "papier"
        ).any { it in p }
    }

    private fun categoryFromType(name: String, mime: String, relativePath: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val p = relativePath.lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") && ("screenshot" in p || "screenshots" in p || "capture" in name.lowercase(Locale.ROOT)) -> "Photos/Captures_ecran"
            mime.startsWith("image/") && ("dcim" in p || "camera" in p) -> "Photos/Appareil_photo"
            mime.startsWith("image/") && ("whatsapp" in p || "telegram" in p || "messenger" in p) -> "Photos/Messageries"
            mime.startsWith("image/") -> "Photos/Autres"

            mime.startsWith("video/") && ("dcim" in p || "camera" in p) -> "Vidéos/Appareil_photo"
            mime.startsWith("video/") && ("whatsapp" in p || "telegram" in p || "messenger" in p) -> "Vidéos/Messageries"
            mime.startsWith("video/") -> "Vidéos/Autres"

            mime.startsWith("audio/") && ("voice" in p || "ptt" in p || "whatsapp" in p) -> "Audio/Messages_vocaux"
            mime.startsWith("audio/") && ("music" in p || "musique" in p) -> "Audio/Musique"
            mime.startsWith("audio/") -> "Audio/Autres"

            mime == "application/pdf" -> "A_verifier/Documents"
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
                    val name = child.name.orEmpty()
                    if (name.equals("RangIA_Corbeille", true) || name.equals("Corbeille", true)) return@forEach
                    val childPath = listOf(currentPath, name).filter { it.isNotBlank() }.joinToString("/")
                    stack.add(child to childPath)
                } else if (child.isFile) out += ScannedFile(child, currentPath)
            }
        }
        return out
    }

    private fun isIndexableInTree(name: String, type: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return isSemanticDocument(name, type) ||
            type.startsWith("video/") || type.startsWith("audio/") ||
            ext in setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "zip", "rar", "7z", "apk")
    }

    private fun isSemanticDocument(name: String, type: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return type == "application/pdf" || type.startsWith("image/") || type.startsWith("text/") ||
            lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".txt")
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
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
        private val TYPE_ONLY_CATEGORIES = setOf("Archives", "Applications_APK", "Livres", "Polices")
    }
}
