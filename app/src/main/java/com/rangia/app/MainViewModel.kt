package com.rangia.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val store = IndexStore(context)
    private val scanner = DocumentScanner(context)
    private val organizer = FileOrganizer(context)
    private val prefs = Prefs(context)
    private val classifier = HybridClassifier(context)

    private val _documents = MutableStateFlow(store.load())
    val documents: StateFlow<List<IndexedDocument>> = _documents.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _allFilesAccess = MutableStateFlow(hasAllFilesPermission())
    val allFilesAccess: StateFlow<Boolean> = _allFilesAccess.asStateFlow()

    val selectedTreeUri: String? get() = prefs.treeUri

    var automaticScan: Boolean
        get() = prefs.automaticScan
        set(value) { prefs.automaticScan = value }

    var wholePhoneMode: Boolean
        get() = prefs.wholePhoneMode
        set(value) { prefs.wholePhoneMode = value }

    fun refreshAllFilesAccess() {
        val before = _allFilesAccess.value
        _allFilesAccess.value = hasAllFilesPermission()
        if (!before && _allFilesAccess.value && prefs.wholePhoneMode) scanNow()
    }

    private fun hasAllFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun acceptTree(uri: Uri, flags: Int) {
        runCatching {
            val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
        prefs.treeUri = uri.toString()
        if (!prefs.wholePhoneMode || !_allFilesAccess.value) scanNow()
    }

    fun scanNow() {
        if (_busy.value) return
        val useWholePhone = prefs.wholePhoneMode && _allFilesAccess.value
        val rawTree = prefs.treeUri
        if (!useWholePhone && rawTree == null) {
            _message.value = "Autorise l’accès à tous les fichiers, ou choisis un dossier à analyser."
            return
        }

        viewModelScope.launch {
            _busy.value = true
            _progress.value = "Préparation…"
            try {
                val updated = if (useWholePhone) {
                    scanner.scanWholePhone(_documents.value) { done, total, current ->
                        _progress.value = "$done/$total · ${current.takeLast(55)}"
                    }
                } else {
                    scanner.scanTree(Uri.parse(rawTree!!), _documents.value) { done, total, current ->
                        _progress.value = "$done/$total · $current"
                    }
                }
                _documents.value = updated
                withContext(Dispatchers.IO) { store.save(updated) }
                val mode = if (useWholePhone) "du téléphone" else "du dossier"
                val review = updated.count { it.needsReview }
                _message.value = "Analyse $mode terminée : ${updated.size} fichier(s), $review à vérifier."
            } catch (t: Throwable) {
                _message.value = "Erreur : ${t.message ?: "analyse impossible"}"
            } finally {
                _busy.value = false
                _progress.value = ""
            }
        }
    }

    fun reclassifyAll() {
        if (_busy.value) return
        val reset = _documents.value.map { it.copy(classificationVersion = 0) }
        _documents.value = reset
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.save(reset) }
            scanNow()
        }
    }

    fun organize(doc: IndexedDocument) {
        if (_busy.value) return
        if (doc.needsReview) {
            _message.value = "Ce document doit être vérifié ou reclassé avant d’être déplacé."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val tree = prefs.treeUri?.let(Uri::parse)
                organizer.organize(doc, tree)
                    .onSuccess {
                        _message.value = "Classé dans RangIA/${doc.categoryPath}"
                        rescanAfterOperation()
                    }
                    .onFailure { _message.value = "Classement impossible : ${it.message}" }
            } finally { _busy.value = false }
        }
    }

    fun moveToTrash(doc: IndexedDocument) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val tree = prefs.treeUri?.let(Uri::parse)
                organizer.moveToTrash(doc, tree)
                    .onSuccess {
                        _documents.value = _documents.value.filterNot { it.uri == doc.uri }
                        withContext(Dispatchers.IO) { store.save(_documents.value) }
                        _message.value = "${doc.originalName} a été déplacé dans la corbeille RangIA."
                    }
                    .onFailure { _message.value = "Impossible de mettre ce fichier à la corbeille : ${it.message}" }
            } finally { _busy.value = false }
        }
    }

    fun deletePermanently(doc: IndexedDocument) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                organizer.deletePermanently(doc)
                    .onSuccess {
                        _documents.value = _documents.value.filterNot { it.uri == doc.uri }
                        withContext(Dispatchers.IO) { store.save(_documents.value) }
                        _message.value = "Fichier supprimé définitivement."
                    }
                    .onFailure { _message.value = "Suppression impossible : ${it.message}" }
            } finally { _busy.value = false }
        }
    }

    fun emptyTrash() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                organizer.emptyTrash()
                    .onSuccess { count -> _message.value = "$count fichier(s) supprimé(s) définitivement de la corbeille RangIA." }
                    .onFailure { _message.value = "Impossible de vider la corbeille : ${it.message}" }
            } finally { _busy.value = false }
        }
    }

    fun organizeAllSafe() {
        if (_busy.value) return
        val fullMode = prefs.wholePhoneMode && _allFilesAccess.value
        if (!fullMode && prefs.treeUri == null) {
            _message.value = "Autorise l’accès au téléphone ou choisis un dossier."
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val candidates = _documents.value.filter { doc ->
                if (doc.duplicate || doc.needsReview || doc.confidence < 0.90f) return@filter false
                if (doc.parentTreeUri == DocumentScanner.WHOLE_PHONE_MARKER || doc.contentUri.scheme == "file") {
                    val path = doc.contentUri.path ?: return@filter false
                    val f = File(path)
                    organizer.isSafeAutoOrganizeFile(f) && !path.contains("/RangIA/")
                } else doc.relativePath != doc.categoryPath
            }

            if (candidates.isEmpty()) {
                _message.value = "Aucun fichier suffisamment sûr à déplacer. Les documents incertains restent dans À vérifier."
                _busy.value = false
                return@launch
            }

            var moved = 0
            var failed = 0
            try {
                val tree = prefs.treeUri?.let(Uri::parse)
                candidates.forEachIndexed { index, doc ->
                    _progress.value = "Rangement ${index + 1}/${candidates.size} · ${doc.originalName}"
                    organizer.organize(doc, tree).onSuccess { moved++ }.onFailure { failed++ }
                }
                rescanAfterOperation()
                _message.value = "$moved fichier(s) déplacé(s) dans le dossier RangIA" + if (failed > 0) ", $failed non déplacé(s)." else "."
            } finally {
                _progress.value = ""
                _busy.value = false
            }
        }
    }

    fun cleanupDuplicates() {
        val removable = duplicateRemovable(_documents.value)
        if (removable.isEmpty()) {
            _message.value = "Aucun doublon supprimable n’a été trouvé."
            return
        }
        moveDuplicateSetToTrash(removable)
    }

    fun cleanupDuplicateGroup(hash: String) {
        val group = _documents.value.filter { it.hash == hash && hash.isNotBlank() }
        val removable = duplicateRemovable(group)
        if (removable.isEmpty()) {
            _message.value = "Aucune copie supplémentaire supprimable dans ce groupe."
            return
        }
        moveDuplicateSetToTrash(removable)
    }

    fun deleteDuplicateGroupPermanently(hash: String) {
        if (_busy.value) return
        val group = _documents.value.filter { it.hash == hash && hash.isNotBlank() }
        val removable = duplicateRemovable(group)
        if (removable.isEmpty()) {
            _message.value = "Aucune copie supplémentaire supprimable dans ce groupe."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            var deleted = 0
            var failed = 0
            var bytes = 0L
            try {
                removable.forEachIndexed { index, doc ->
                    _progress.value = "Suppression ${index + 1}/${removable.size} · ${doc.originalName}"
                    organizer.deletePermanently(doc)
                        .onSuccess { deleted++; bytes += doc.size }
                        .onFailure { failed++ }
                }
                rescanAfterOperation()
                _message.value = "$deleted copie(s) supprimée(s) définitivement · ${formatBytes(bytes)} libérés" +
                    if (failed > 0) " · $failed ignorée(s)." else "."
            } finally {
                _progress.value = ""
                _busy.value = false
            }
        }
    }

    private fun duplicateRemovable(source: List<IndexedDocument>): List<IndexedDocument> {
        return source.filter { it.hash.isNotBlank() }
            .groupBy { it.hash }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keeper = chooseKeeper(group)
                group.filter { it.uri != keeper.uri && organizer.canModify(it) }
            }
            .distinctBy { it.uri }
    }

    private fun chooseKeeper(group: List<IndexedDocument>): IndexedDocument {
        return group.minWithOrNull(
            compareBy<IndexedDocument>(
                { if (it.relativePath.contains("Documents", ignoreCase = true)) 0 else 1 },
                { if (it.relativePath.contains("DCIM", ignoreCase = true)) 0 else 1 },
                { if (it.relativePath.contains("RangIA", ignoreCase = true)) 0 else 1 },
                { -it.modifiedAt }
            )
        ) ?: group.first()
    }

    private fun moveDuplicateSetToTrash(removable: List<IndexedDocument>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val tree = prefs.treeUri?.let(Uri::parse)
            var moved = 0
            var failed = 0
            var bytes = 0L
            try {
                removable.forEachIndexed { index, doc ->
                    _progress.value = "Doublons ${index + 1}/${removable.size} · ${doc.originalName}"
                    organizer.moveToTrash(doc, tree)
                        .onSuccess { moved++; bytes += doc.size }
                        .onFailure { failed++ }
                }
                rescanAfterOperation()
                _message.value = "$moved doublon(s) déplacé(s) dans la corbeille · ${formatBytes(bytes)} récupérables" +
                    if (failed > 0) " · $failed ignoré(s)." else "."
            } finally {
                _progress.value = ""
                _busy.value = false
            }
        }
    }

    private suspend fun rescanAfterOperation() {
        val updated = if (prefs.wholePhoneMode && _allFilesAccess.value) scanner.scanWholePhone(_documents.value)
        else {
            val raw = prefs.treeUri ?: return
            scanner.scanTree(Uri.parse(raw), _documents.value)
        }
        _documents.value = updated
        withContext(Dispatchers.IO) { store.save(updated) }
    }

    val learnedExamplesCount: Int get() = classifier.learnedExamplesCount()
    val aiCategories: List<String> get() = (SmartCategoryRefiner.categories + listOf(
        "Documents/Texte", "Documents/Word", "Documents/Tableurs", "Documents/Présentations",
        "Photos/Captures_ecran", "Photos/Appareil_photo", "Photos/Messageries", "Photos/Autres",
        "Vidéos/Appareil_photo", "Vidéos/Messageries", "Vidéos/Autres",
        "Audio/Messages_vocaux", "Audio/Musique", "Audio/Autres",
        "Archives", "Applications_APK", "Livres", "Polices", "Fichiers/Autres"
    )).distinct()

    fun correctCategory(doc: IndexedDocument, category: String) {
        classifier.learn(doc, category)
        val entities = DocumentIntelligence.extractEntities(doc.extractedText)
        val updated = _documents.value.map {
            if (it.uri == doc.uri) it.copy(
                categoryPath = category,
                confidence = 0.99f,
                suggestedName = DocumentIntelligence.suggestFileName(it.originalName, category, entities),
                classificationVersion = HybridClassifier.MODEL_VERSION,
                classificationEvidence = listOf("correction manuelle")
            ) else it
        }
        _documents.value = updated
        viewModelScope.launch(Dispatchers.IO) { store.save(updated) }
        _message.value = "Correction mémorisée. RangIA utilisera cet exemple pour les documents similaires."
    }

    fun resetAiLearning() {
        classifier.resetLearning()
        _message.value = "Apprentissage personnel de l’IA effacé."
    }

    fun dismissMessage() { _message.value = null }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes o"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f Ko".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f Mo".format(mb)
        return "%.2f Go".format(mb / 1024.0)
    }
}
