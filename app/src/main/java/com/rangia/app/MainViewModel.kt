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
                _message.value = "Analyse $mode terminée : ${updated.size} fichier(s) indexé(s)."
            } catch (t: Throwable) {
                _message.value = "Erreur : ${t.message ?: "analyse impossible"}"
            } finally {
                _busy.value = false
                _progress.value = ""
            }
        }
    }

    fun organize(doc: IndexedDocument) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val tree = prefs.treeUri?.let(Uri::parse)
                organizer.organize(doc, tree)
                    .onSuccess {
                        _message.value = "Classé dans RangIA/${doc.categoryPath}"
                        scanNowAfterOperation()
                    }
                    .onFailure { _message.value = "Classement impossible : ${it.message}" }
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
                if (doc.duplicate || doc.categoryPath == "Autres" || doc.categoryPath == "Fichiers/Autres" || doc.confidence < 0.88f) return@filter false
                if (doc.parentTreeUri == DocumentScanner.WHOLE_PHONE_MARKER || doc.contentUri.scheme == "file") {
                    val path = doc.contentUri.path ?: return@filter false
                    val f = File(path)
                    organizer.isSafeUserFile(f) && !path.contains("/RangIA/")
                } else doc.relativePath != doc.categoryPath
            }

            if (candidates.isEmpty()) {
                _message.value = "Aucun fichier sûr à déplacer automatiquement. Les fichiers d’applications restent seulement classés dans RangIA pour éviter de casser WhatsApp, la galerie ou Android."
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
                scanNowAfterOperation()
                _message.value = "$moved fichier(s) déplacé(s) dans le dossier RangIA" + if (failed > 0) ", $failed non déplacé(s)." else "."
            } finally {
                _progress.value = ""
                _busy.value = false
            }
        }
    }

    private suspend fun scanNowAfterOperation() {
        val updated = if (prefs.wholePhoneMode && _allFilesAccess.value) scanner.scanWholePhone(_documents.value)
        else {
            val raw = prefs.treeUri ?: return
            scanner.scanTree(Uri.parse(raw), _documents.value)
        }
        _documents.value = updated
        withContext(Dispatchers.IO) { store.save(updated) }
    }

    val learnedExamplesCount: Int get() = classifier.learnedExamplesCount()
    val aiCategories: List<String> get() = (LocalAiEngine.categories + listOf(
        "Documents/PDF", "Documents/Texte", "Documents/Word", "Documents/Tableurs", "Documents/Présentations",
        "Photos", "Vidéos", "Audio", "Archives", "Applications_APK", "Livres", "Polices", "Fichiers/Autres"
    )).distinct()

    fun correctCategory(doc: IndexedDocument, category: String) {
        classifier.learn(doc, category)
        val entities = DocumentIntelligence.extractEntities(doc.extractedText)
        val updated = _documents.value.map {
            if (it.uri == doc.uri) it.copy(
                categoryPath = category,
                confidence = 0.99f,
                suggestedName = DocumentIntelligence.suggestFileName(it.originalName, category, entities)
            ) else it
        }
        _documents.value = updated
        viewModelScope.launch(Dispatchers.IO) { store.save(updated) }
        _message.value = "Correction mémorisée. RangIA apprendra de ce fichier."
    }

    fun resetAiLearning() {
        classifier.resetLearning()
        _message.value = "Apprentissage personnel de l’IA effacé."
    }

    fun dismissMessage() { _message.value = null }
}
