package com.rangia.app

import android.app.Application
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ExplorerEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String,
    val childCount: Int,
    val isFavorite: Boolean
)

data class ExplorerUiState(
    val currentPath: String = "",
    val entries: List<ExplorerEntry> = emptyList(),
    val query: String = "",
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val message: String? = null
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("rangia_explorer", Application.MODE_PRIVATE)
    private val root: File get() = Environment.getExternalStorageDirectory()

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    init { refresh() }

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun openFolder(relativePath: String) {
        if (!isSafePath(relativePath)) {
            _state.value = _state.value.copy(message = "Ce dossier Android est protégé.")
            return
        }
        _state.value = _state.value.copy(currentPath = normalize(relativePath), selected = emptySet(), query = "")
        refresh()
    }

    fun goUp() {
        val current = _state.value.currentPath
        if (current.isBlank()) return
        openFolder(current.substringBeforeLast('/', ""))
    }

    fun refresh() {
        val current = _state.value.currentPath
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val loaded = withContext(Dispatchers.IO) { listEntries(current) }
            _state.value = _state.value.copy(entries = loaded, loading = false)
        }
    }

    fun toggleSelection(path: String) {
        val selected = _state.value.selected.toMutableSet()
        if (!selected.add(path)) selected.remove(path)
        _state.value = _state.value.copy(selected = selected)
    }

    fun clearSelection() { _state.value = _state.value.copy(selected = emptySet()) }

    fun toggleFavorite(path: String) {
        val fav = favorites().toMutableSet()
        if (!fav.add(path)) fav.remove(path)
        prefs.edit().putStringSet(KEY_FAVORITES, fav).apply()
        refresh()
    }

    fun createFolder(nameRaw: String) {
        val name = sanitizeName(nameRaw)
        if (name.isBlank()) {
            _state.value = _state.value.copy(message = "Nom de dossier invalide.")
            return
        }
        val parent = resolve(_state.value.currentPath)
        viewModelScope.launch(Dispatchers.IO) {
            val target = File(parent, name)
            val ok = !target.exists() && target.mkdirs()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(message = if (ok) "Dossier créé." else "Impossible de créer ce dossier.")
                refresh()
            }
        }
    }

    fun rename(path: String, newNameRaw: String) {
        val newName = sanitizeName(newNameRaw)
        if (newName.isBlank()) {
            _state.value = _state.value.copy(message = "Nom invalide.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val source = resolve(path)
            val target = File(source.parentFile, newName)
            val ok = canModify(source) && source.exists() && !target.exists() && source.renameTo(target)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(message = if (ok) "Renommé." else "Renommage impossible.")
                refresh()
            }
        }
    }

    fun moveSelected(destinationTopLevel: String) {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) return
        val destination = resolve(destinationTopLevel)
        viewModelScope.launch(Dispatchers.IO) {
            if (!destination.exists()) destination.mkdirs()
            var moved = 0
            var failed = 0
            selected.forEach { path ->
                val source = resolve(path)
                if (!canModify(source) || source == destination || destination.path.startsWith(source.path + File.separator)) failed++
                else if (moveVerified(source, uniqueTarget(destination, source.name))) moved++ else failed++
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    selected = emptySet(),
                    message = "$moved élément(s) déplacé(s)" + if (failed > 0) " · $failed échec(s)." else "."
                )
                refresh()
            }
        }
    }

    fun deleteSelected() {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var deleted = 0
            var failed = 0
            selected.sortedByDescending { it.length }.forEach { path ->
                val f = resolve(path)
                if (!canModify(f)) failed++
                else {
                    val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                    if (ok) deleted++ else failed++
                }
            }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    selected = emptySet(),
                    message = "$deleted élément(s) supprimé(s)" + if (failed > 0) " · $failed refusé(s)." else "."
                )
                refresh()
            }
        }
    }

    fun favoriteEntries(): List<ExplorerEntry> = favorites().mapNotNull { path ->
        val f = resolve(path)
        if (!f.exists()) null else toEntry(f)
    }.sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.FRENCH) })

    private fun listEntries(relative: String): List<ExplorerEntry> {
        val dir = resolve(relative)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
        return runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            .asSequence()
            .filter { child -> child.name != "." && child.name != ".." && isSafePath(relative(child)) }
            .map(::toEntry)
            .sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.FRENCH) })
            .toList()
    }

    private fun toEntry(file: File): ExplorerEntry {
        val rel = relative(file)
        return ExplorerEntry(
            name = file.name,
            relativePath = rel,
            absolutePath = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) folderSizeFast(file) else file.length(),
            modifiedAt = file.lastModified(),
            mimeType = if (file.isDirectory) "inode/directory" else guessMime(file.name),
            childCount = if (file.isDirectory) runCatching { file.list()?.size ?: 0 }.getOrDefault(0) else 0,
            isFavorite = rel in favorites()
        )
    }

    private fun folderSizeFast(dir: File): Long = runCatching {
        dir.listFiles()?.asSequence()?.take(300)?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }.getOrDefault(0L)

    private fun moveVerified(source: File, target: File): Boolean {
        if (source.renameTo(target)) return true
        return runCatching {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = false)
                if (!target.exists()) return false
                if (!source.deleteRecursively()) { target.deleteRecursively(); return false }
            } else {
                source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                if (source.length() != target.length()) { target.delete(); return false }
                if (!source.delete()) { target.delete(); return false }
            }
            true
        }.getOrDefault(false)
    }

    private fun uniqueTarget(dir: File, original: String): File {
        var target = File(dir, original)
        if (!target.exists()) return target
        val dot = original.lastIndexOf('.')
        val baseName = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var i = 2
        while (target.exists() && i < 1000) {
            target = File(dir, "$baseName" + "_" + i + ext)
            i++
        }
        return target
    }

    private fun resolve(relative: String): File = if (relative.isBlank()) root else File(root, normalize(relative))
    private fun relative(file: File): String = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
    private fun normalize(value: String): String = value.replace('\\', '/').trim('/')
    private fun sanitizeName(value: String): String = value.trim().replace(Regex("[\\\\/:*?\\\"<>|]"), "_").take(120)

    private fun canModify(file: File): Boolean {
        val rel = relative(file).lowercase(Locale.ROOT)
        if (rel.isBlank()) return false
        if (rel == "android/data" || rel.startsWith("android/data/")) return false
        if (rel == "android/obb" || rel.startsWith("android/obb/")) return false
        return file.parentFile?.canWrite() == true
    }

    private fun isSafePath(path: String): Boolean {
        val p = normalize(path).lowercase(Locale.ROOT)
        return !(p == "android/data" || p.startsWith("android/data/") || p == "android/obb" || p.startsWith("android/obb/"))
    }

    private fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    companion object { private const val KEY_FAVORITES = "favorites" }
}
