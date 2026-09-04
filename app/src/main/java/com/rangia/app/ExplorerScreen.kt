package com.rangia.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private val ExplorerBlue = Color(0xFF2678F3)
private val ExplorerPurple = Color(0xFF7047FF)
private val ExplorerGreen = Color(0xFF13A56E)
private val ExplorerOrange = Color(0xFFF28A30)
private val ExplorerRed = Color(0xFFE14E54)
private val ExplorerYellow = Color(0xFFF1B52A)
private val ExplorerMuted = Color(0xFF716A7C)

@Composable
fun RangIaExplorerScreen(
    vm: ExplorerViewModel,
    fullAccess: Boolean,
    requestAccess: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var createFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    val visibleEntries = remember(state.entries, state.query) {
        if (state.query.isBlank()) state.entries
        else state.entries.filter { it.name.contains(state.query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.currentPath.isNotBlank()) {
                IconButton(onClick = vm::goUp) { Icon(Icons.Default.ArrowBack, "Retour", tint = ExplorerBlue) }
            }
            Column(Modifier.weight(1f)) {
                Text("Explorer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    if (state.currentPath.isBlank()) "Stockage interne" else state.currentPath.replace("/", " › "),
                    color = ExplorerMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualiser", tint = ExplorerBlue) }
            IconButton(onClick = { createFolder = true }) { Icon(Icons.Default.CreateNewFolder, "Créer un dossier", tint = ExplorerGreen) }
        }

        if (!fullAccess) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Accès complet désactivé", fontWeight = FontWeight.Black)
                    Text("Autorise RangIA à parcourir et gérer les fichiers accessibles du téléphone.", color = ExplorerMuted)
                    Button(onClick = requestAccess) { Text("Autoriser l’accès") }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = ExplorerBlue) },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, "Effacer") }
                }
            },
            placeholder = { Text("Rechercher dans ce dossier") }
        )

        if (state.selected.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
                color = ExplorerBlue.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        state.selected.size.toString() + " sélectionné(s)",
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = ExplorerBlue
                    )
                    FilledTonalButton(onClick = { showMove = true }) {
                        Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Déplacer")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.DeleteOutline, "Supprimer", tint = ExplorerRed)
                    }
                    IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Annuler") }
                }
            }
        }

        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ExplorerBlue)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(15.dp, 6.dp, 15.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.currentPath.isBlank() && state.selected.isEmpty()) {
                val favorites = vm.favoriteEntries()
                if (favorites.isNotEmpty()) {
                    item { ExplorerSectionTitle("Favoris", favorites.size.toString() + " emplacement(s)") }
                    items(favorites.take(8), key = { "fav:" + it.absolutePath }) { entry ->
                        ExplorerEntryCard(entry, false, vm) {
                            renameEntry = entry
                            renameValue = entry.name
                        }
                    }
                    item { ExplorerSectionTitle("Stockage", visibleEntries.size.toString() + " élément(s)") }
                }
            }

            items(visibleEntries, key = { it.absolutePath }) { entry ->
                ExplorerEntryCard(entry, entry.relativePath in state.selected, vm) {
                    renameEntry = entry
                    renameValue = entry.name
                }
            }

            if (visibleEntries.isEmpty() && !state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun élément", color = ExplorerMuted)
                    }
                }
            }
        }
    }

    if (createFolder) {
        AlertDialog(
            onDismissRequest = { createFolder = false },
            title = { Text("Nouveau dossier", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Nom du dossier") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.createFolder(newFolderName)
                    newFolderName = ""
                    createFolder = false
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { createFolder = false }) { Text("Annuler") } }
        )
    }

    renameEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameEntry = null },
            title = { Text("Renommer", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Nouveau nom") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.rename(entry.relativePath, renameValue)
                    renameEntry = null
                }) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renameEntry = null }) { Text("Annuler") } }
        )
    }

    if (showMove) {
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("Déplacer vers…", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Download", "Documents", "Pictures", "Movies", "Music", "RangIA").forEach { target ->
                        OutlinedButton(
                            onClick = { vm.moveSelected(target); showMove = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, null)
                            Spacer(Modifier.width(7.dp))
                            Text(target)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMove = false }) { Text("Annuler") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer définitivement ?", fontWeight = FontWeight.Black) },
            text = {
                Text(state.selected.size.toString() + " élément(s) seront supprimés. Cette action est irréversible.")
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteSelected() },
                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerRed)
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("RangIA", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = { Button(onClick = vm::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun ExplorerEntryCard(
    entry: ExplorerEntry,
    selected: Boolean,
    vm: ExplorerViewModel,
    rename: () -> Unit
) {
    val context = LocalContext.current
    var menu by remember(entry.absolutePath) { mutableStateOf(false) }
    val accent = explorerAccent(entry)

    ElevatedCard(
        Modifier.fillMaxWidth().clickable {
            if (entry.isDirectory) vm.openFolder(entry.relativePath)
            else runCatching {
                val file = java.io.File(entry.absolutePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".files",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, entry.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
            }
        },
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) accent.copy(alpha = .08f) else Color.White
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { vm.toggleSelection(entry.relativePath) }
            )

            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.isDirectory) Icons.Default.Folder else explorerIcon(entry),
                    null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.isDirectory) entry.childCount.toString() + " élément(s)" else explorerFormatBytes(entry.size),
                    color = ExplorerMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            IconButton(onClick = { vm.toggleFavorite(entry.relativePath) }) {
                Icon(
                    if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    "Favori",
                    tint = if (entry.isFavorite) ExplorerYellow else ExplorerMuted
                )
            }

            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; rename() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (selected) "Désélectionner" else "Sélectionner") },
                        leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                        onClick = { menu = false; vm.toggleSelection(entry.relativePath) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorerSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = ExplorerMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun explorerAccent(entry: ExplorerEntry): Color = when {
    entry.isDirectory -> ExplorerBlue
    entry.mimeType.startsWith("image/") -> Color(0xFF9A55D4)
    entry.mimeType.startsWith("video/") -> ExplorerRed
    entry.mimeType.startsWith("audio/") -> Color(0xFF8058D7)
    entry.mimeType == "application/pdf" -> ExplorerOrange
    entry.name.endsWith(".apk", true) -> ExplorerGreen
    else -> ExplorerPurple
}

private fun explorerIcon(entry: ExplorerEntry): ImageVector = when {
    entry.mimeType.startsWith("image/") -> Icons.Default.Image
    entry.mimeType.startsWith("video/") -> Icons.Default.Movie
    entry.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    entry.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    entry.name.endsWith(".apk", true) -> Icons.Default.Android
    entry.name.endsWith(".zip", true) || entry.name.endsWith(".rar", true) || entry.name.endsWith(".7z", true) -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

private fun explorerFormatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " o"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.FRENCH, "%.1f Ko", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.FRENCH, "%.1f Mo", mb)
    return String.format(Locale.FRENCH, "%.2f Go", mb / 1024.0)
}
