package com.rangia.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.Locale

private val R2Purple = Color(0xFF6D4AFF)
private val R2Blue = Color(0xFF3478F6)
private val R2Green = Color(0xFF0EAA72)
private val R2Orange = Color(0xFFF08A35)
private val R2Pink = Color(0xFFD24FAF)
private val R2Cyan = Color(0xFF08A7C4)
private val R2Red = Color(0xFFE14E55)
private val R2Yellow = Color(0xFFF0B428)
private val R2Text = Color(0xFF201D2A)
private val R2Muted = Color(0xFF716B7C)
private val R2Bg = Color(0xFFF7F6FB)

private val R2Scheme = lightColorScheme(
    primary = R2Purple,
    onPrimary = Color.White,
    secondary = R2Blue,
    tertiary = R2Cyan,
    background = R2Bg,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EDF6),
    onBackground = R2Text,
    onSurface = R2Text,
    onSurfaceVariant = R2Muted,
    error = R2Red
)

class RangIaV2Activity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val explorerVm: ExplorerViewModel by viewModels()
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billing = BillingManager(applicationContext).also { it.start() }
        setContent {
            MaterialTheme(colorScheme = R2Scheme) {
                val purchasedPro by billing.isPro.collectAsState()
                val price by billing.price.collectAsState()
                val billingStatus by billing.status.collectAsState()
                RangIaV2(
                    vm = vm,
                    explorerVm = explorerVm,
                    isPro = BuildConfig.DEBUG || purchasedPro,
                    proPrice = price,
                    billingStatus = billingStatus,
                    requestAllFilesAccess = ::requestAllFilesAccess,
                    buyPro = { billing.launchPurchase(this) },
                    restorePro = billing::restorePurchases,
                    dismissBillingStatus = billing::consumeStatus
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAllFilesAccess()
        if (::billing.isInitialized) billing.refresh()
    }

    override fun onDestroy() {
        if (::billing.isInitialized) billing.close()
        super.onDestroy()
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            vm.refreshAllFilesAccess()
            return
        }
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(appIntent) }.onFailure {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

private enum class R2Tab(val label: String, val icon: ImageVector, val color: Color) {
    HOME("Accueil", Icons.Default.Home, R2Purple),
    FILES("Fichiers", Icons.Default.Folder, R2Blue),
    CLEANUP("Nettoyage", Icons.Default.CleaningServices, R2Green),
    SEARCH("Recherche", Icons.Default.Search, R2Orange),
    SETTINGS("Réglages", Icons.Default.Settings, R2Pink)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangIaV2(
    vm: MainViewModel,
    explorerVm: ExplorerViewModel,
    isPro: Boolean,
    proPrice: String?,
    billingStatus: String?,
    requestAllFilesAccess: () -> Unit,
    buyPro: () -> Unit,
    restorePro: () -> Unit,
    dismissBillingStatus: () -> Unit
) {
    val docs by vm.documents.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val fullAccess by vm.allFilesAccess.collectAsState()
    val categories = remember { (vm.aiCategories + SmartCategoryRefiner.categories).distinct().sorted() }

    var tab by remember { mutableStateOf(R2Tab.HOME) }
    var showPro by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.acceptTree(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    Scaffold(
        containerColor = R2Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = R2Bg),
                title = { R2Brand() },
                actions = {
                    if (isPro) R2ProBadge() else TextButton(onClick = { showPro = true }) { Text("Pro", fontWeight = FontWeight.ExtraBold) }
                    IconButton(onClick = vm::scanNow, enabled = !busy) {
                        Icon(Icons.Default.Refresh, "Analyser", tint = if (busy) R2Muted else R2Purple)
                    }
                }
            )
        },
        bottomBar = { R2BottomBar(tab) { tab = it } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                R2Tab.HOME -> R2Home(docs, fullAccess, busy, progress, isPro, requestAllFilesAccess, vm::scanNow, vm::organizeAllSafe) { tab = R2Tab.CLEANUP }
                R2Tab.FILES -> R2Explorer(explorerVm, fullAccess, requestAllFilesAccess)
                R2Tab.CLEANUP -> R2Cleanup(docs, isPro, vm::cleanupDuplicates, vm::moveToTrash, vm::emptyTrash) { showPro = true }
                R2Tab.SEARCH -> R2Search(docs, categories, vm::organize, vm::moveToTrash, vm::correctCategory)
                R2Tab.SETTINGS -> R2Settings(vm, fullAccess, isPro, proPrice, requestAllFilesAccess, { folderPicker.launch(null) }, { showPro = true }, restorePro)
            }
        }
    }

    if (showPro) {
        AlertDialog(
            onDismissRequest = { showPro = false },
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = R2Yellow) },
            title = { Text("RangIA Pro", fontWeight = FontWeight.Black) },
            text = { Text("Scan du téléphone complet, rangement automatique, nettoyage des doublons et fonctions avancées. Achat unique${proPrice?.let { " · $it" } ?: ""}.") },
            confirmButton = { Button(onClick = { showPro = false; buyPro() }) { Text("Débloquer Pro") } },
            dismissButton = { TextButton(onClick = { showPro = false }) { Text("Plus tard") } }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text("RangIA", fontWeight = FontWeight.ExtraBold) },
            text = { Text(it) },
            confirmButton = { Button(onClick = vm::dismissMessage) { Text("OK") } }
        )
    }

    billingStatus?.let {
        AlertDialog(
            onDismissRequest = dismissBillingStatus,
            title = { Text("RangIA Pro", fontWeight = FontWeight.ExtraBold) },
            text = { Text(it) },
            confirmButton = { Button(onClick = dismissBillingStatus) { Text("OK") } }
        )
    }
}

@Composable
private fun R2Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(R2Purple, R2Blue, R2Cyan))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.FolderSpecial, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(9.dp))
        Column {
            Text("RangIA", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("Range · Organise · Retrouve", color = R2Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun R2ProBadge() {
    Surface(color = Color(0xFFFFF1C5), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFA26900), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (BuildConfig.DEBUG) "PRO TEST" else "PRO", color = Color(0xFF8A5B00), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun R2BottomBar(current: R2Tab, select: (R2Tab) -> Unit) {
    Surface(color = Color.White, tonalElevation = 7.dp, shadowElevation = 18.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            R2Tab.entries.forEach { item ->
                val selected = item == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { select(item) }
                        .background(if (selected) item.color.copy(alpha = .10f) else Color.Transparent)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        Modifier.size(if (selected) 40.dp else 36.dp).clip(RoundedCornerShape(13.dp))
                            .background(if (selected) item.color else item.color.copy(alpha = .13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, item.label, tint = if (selected) Color.White else item.color, modifier = Modifier.size(21.dp))
                    }
                    Text(
                        item.label,
                        color = item.color,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    if (selected) Box(Modifier.width(22.dp).height(3.dp).clip(CircleShape).background(item.color))
                    else Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}

@Composable
private fun R2Home(
    docs: List<IndexedDocument>,
    fullAccess: Boolean,
    busy: Boolean,
    progress: String,
    isPro: Boolean,
    requestAccess: () -> Unit,
    scan: () -> Unit,
    organize: () -> Unit,
    openCleanup: () -> Unit
) {
    val duplicateGroups = remember(docs) { r2DuplicateGroups(docs) }
    val extra = duplicateGroups.sumOf { (it.size - 1).coerceAtLeast(0) }
    val families = remember(docs) { r2FamilyCounts(docs).entries.sortedByDescending { it.value }.take(8) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(R2Purple, R2Blue, R2Cyan))).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Ton téléphone, enfin rangé", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Aperçus · IA locale · OCR · doublons · recherche", color = Color.White.copy(alpha = .88f))
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(alpha = .25f))
                        Text(progress.ifBlank { "Analyse en cours…" }, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(
                            onClick = if (fullAccess) scan else requestAccess,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = R2Purple),
                            shape = RoundedCornerShape(17.dp)
                        ) {
                            Icon(if (fullAccess) Icons.Default.Search else Icons.Default.Security, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (fullAccess) "Analyser tout le téléphone" else "Autoriser l’accès aux fichiers", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                R2Stat("Fichiers", docs.size.toString(), Icons.Default.Inventory2, R2Purple, Modifier.weight(1f))
                R2Stat("Catégories", r2FamilyCounts(docs).size.toString(), Icons.Default.Folder, R2Blue, Modifier.weight(1f))
                R2Stat("Doublons", extra.toString(), Icons.Default.ContentCopy, R2Green, Modifier.weight(1f))
            }
        }

        if (docs.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                R2Quick(Icons.Default.AutoFixHigh, "Ranger", "Fichiers sûrs", R2Purple, Modifier.weight(1f)) { if (isPro) organize() }
                R2Quick(Icons.Default.CleaningServices, "Nettoyer", "$extra doublon(s)", R2Green, Modifier.weight(1f), openCleanup)
            }
        }

        item { R2Title("Classement intelligent", if (families.isEmpty()) "Lance une analyse" else "Grandes familles détectées") }
        items(families) { (family, count) -> R2FamilyCard(family, count, docs.count { it.categoryPath.substringBefore('/') == family }) }
    }
}

@Composable
private fun R2Explorer(
    explorerVm: ExplorerViewModel,
    fullAccess: Boolean,
    requestAccess: () -> Unit
) {
    val state by explorerVm.state.collectAsState()
    val context = LocalContext.current
    var createFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    val filtered = remember(state.entries, state.query) {
        if (state.query.isBlank()) state.entries
        else state.entries.filter { it.name.contains(state.query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.currentPath.isNotBlank()) {
                IconButton(onClick = explorerVm::goUp) { Icon(Icons.Default.ArrowBack, "Retour", tint = R2Blue) }
            }
            Column(Modifier.weight(1f)) {
                Text("Explorer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    if (state.currentPath.isBlank()) "Stockage interne" else state.currentPath,
                    color = R2Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = explorerVm::refresh) { Icon(Icons.Default.Refresh, "Actualiser", tint = R2Blue) }
            IconButton(onClick = { createFolder = true }) { Icon(Icons.Default.CreateNewFolder, "Nouveau dossier", tint = R2Green) }
        }

        if (!fullAccess) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Accès au stockage requis", fontWeight = FontWeight.ExtraBold)
                    Text("Autorise RangIA à gérer les fichiers accessibles du téléphone.", color = R2Muted)
                    Button(onClick = requestAccess) { Text("Autoriser l’accès") }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = explorerVm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = R2Blue) },
            trailingIcon = {
                if (state.query.isNotBlank()) IconButton(onClick = { explorerVm.setQuery("") }) {
                    Icon(Icons.Default.Close, "Effacer")
                }
            },
            placeholder = { Text("Rechercher dans ce dossier") }
        )

        if (state.selected.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                color = R2Blue.copy(alpha = .08f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("\${state.selected.size} sélectionné(s)", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = R2Blue)
                    FilledTonalButton(onClick = { showMove = true }) {
                        Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp)); Text("Déplacer")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.DeleteOutline, "Supprimer", tint = R2Red)
                    }
                    IconButton(onClick = explorerVm::clearSelection) { Icon(Icons.Default.Close, "Annuler") }
                }
            }
        }

        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = R2Blue)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.currentPath.isBlank() && state.selected.isEmpty()) {
                val favorites = explorerVm.favoriteEntries()
                if (favorites.isNotEmpty()) {
                    item { R2Title("Favoris", "\${favorites.size} emplacement(s)") }
                    items(favorites.take(6), key = { "fav:" + it.absolutePath }) { entry ->
                        R2ExplorerEntry(entry, false, explorerVm, context) {
                            renameEntry = entry
                            renameValue = entry.name
                        }
                    }
                    item { R2Title("Stockage", "\${filtered.size} élément(s)") }
                }
            }

            items(filtered, key = { it.absolutePath }) { entry ->
                R2ExplorerEntry(entry, entry.relativePath in state.selected, explorerVm, context) {
                    renameEntry = entry
                    renameValue = entry.name
                }
            }

            if (filtered.isEmpty() && !state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun fichier dans ce dossier", color = R2Muted)
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
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, singleLine = true, label = { Text("Nom") })
            },
            confirmButton = {
                Button(onClick = {
                    explorerVm.createFolder(newFolderName)
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
                OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Nouveau nom") })
            },
            confirmButton = {
                Button(onClick = {
                    explorerVm.rename(entry.relativePath, renameValue)
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
                            onClick = { explorerVm.moveSelected(target); showMove = false },
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
            text = { Text("\${state.selected.size} élément(s) seront supprimés. Cette action est irréversible.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; explorerVm.deleteSelected() },
                    colors = ButtonDefaults.buttonColors(containerColor = R2Red)
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = explorerVm::clearMessage,
            title = { Text("RangIA", fontWeight = FontWeight.Bold) },
            text = { Text(msg) },
            confirmButton = { Button(onClick = explorerVm::clearMessage) { Text("OK") } }
        )
    }
}

@Composable
private fun R2ExplorerEntry(
    entry: ExplorerEntry,
    selected: Boolean,
    vm: ExplorerViewModel,
    context: android.content.Context,
    rename: () -> Unit
) {
    var menu by remember(entry.absolutePath) { mutableStateOf(false) }
    val accent = when {
        entry.isDirectory -> R2Blue
        entry.mimeType.startsWith("image/") -> Color(0xFF9A55D4)
        entry.mimeType.startsWith("video/") -> R2Red
        entry.mimeType.startsWith("audio/") -> Color(0xFF8058D7)
        entry.mimeType == "application/pdf" -> R2Orange
        else -> R2Purple
    }

    ElevatedCard(
        Modifier.fillMaxWidth().clickable {
            if (entry.isDirectory) vm.openFolder(entry.relativePath)
            else {
                runCatching {
                    val file = java.io.File(entry.absolutePath)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, entry.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
                }
            }
        },
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = if (selected) accent.copy(alpha = .08f) else Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { vm.toggleSelection(entry.relativePath) })
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (entry.isDirectory) Icons.Default.Folder else r2ExplorerIcon(entry), null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.isDirectory) "\${entry.childCount} élément(s)" else formatBytesR2(entry.size),
                    color = R2Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = { vm.toggleFavorite(entry.relativePath) }) {
                Icon(
                    if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    "Favori",
                    tint = if (entry.isFavorite) R2Yellow else R2Muted
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

private fun r2ExplorerIcon(entry: ExplorerEntry): ImageVector = when {
    entry.mimeType.startsWith("image/") -> Icons.Default.Image
    entry.mimeType.startsWith("video/") -> Icons.Default.Movie
    entry.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    entry.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    entry.name.endsWith(".apk", true) -> Icons.Default.Android
    entry.name.endsWith(".zip", true) || entry.name.endsWith(".rar", true) || entry.name.endsWith(".7z", true) -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

@Composable
private fun R2Stat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    ElevatedCard(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            R2IconBubble(icon, color, 34)
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label, color = R2Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun R2Quick(icon: ImageVector, title: String, subtitle: String, color: Color, modifier: Modifier, click: () -> Unit) {
    ElevatedCard(modifier.clickable(onClick = click), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = color.copy(alpha = .08f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            R2IconBubble(icon, color, 42)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private enum class R2LibraryMode { FILES, CATEGORIES }
private enum class R2Sort { RECENT, NAME, SIZE }

@Composable
private fun R2Library(
    docs: List<IndexedDocument>,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var mode by remember { mutableStateOf(R2LibraryMode.CATEGORIES) }
    var family by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(R2Sort.RECENT) }
    val familyCounts = remember(docs) { r2FamilyCounts(docs) }
    val families = remember(familyCounts) { familyCounts.keys.sorted() }

    val filtered = remember(docs, family, sort) {
        val base = if (family == null) docs else docs.filter { it.categoryPath.substringBefore('/') == family }
        when (sort) {
            R2Sort.RECENT -> base.sortedByDescending { it.modifiedAt }
            R2Sort.NAME -> base.sortedBy { it.originalName.lowercase(Locale.FRENCH) }
            R2Sort.SIZE -> base.sortedByDescending { it.size }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bibliothèque", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("${docs.size} fichiers · ${familyCounts.size} familles", color = R2Muted)
            }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), tonalElevation = 2.dp) {
                Row(Modifier.padding(4.dp)) {
                    R2ModeButton(Icons.Default.Category, mode == R2LibraryMode.CATEGORIES, R2Blue) { mode = R2LibraryMode.CATEGORIES }
                    R2ModeButton(Icons.Default.ViewList, mode == R2LibraryMode.FILES, R2Purple) { mode = R2LibraryMode.FILES }
                }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { R2FamilyChip(null, "Tout", docs.size, R2Purple, family == null) { family = null } }
            items(families) { f ->
                val visual = r2VisualForFamily(f)
                R2FamilyChip(f, r2Pretty(f), familyCounts[f] ?: 0, visual.color, family == f) { family = f; mode = R2LibraryMode.FILES }
            }
        }

        if (mode == R2LibraryMode.CATEGORIES) {
            LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                val shownFamilies = if (family == null) families else listOf(family!!)
                items(shownFamilies) { f ->
                    val familyDocs = docs.filter { it.categoryPath.substringBefore('/') == f }
                    val sub = familyDocs.groupingBy { it.categoryPath }.eachCount().entries.sortedByDescending { it.value }
                    R2CategoryBrowserCard(f, familyDocs.size, sub) { family = f; mode = R2LibraryMode.FILES }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { R2SortChip("Récent", Icons.Default.Schedule, sort == R2Sort.RECENT) { sort = R2Sort.RECENT } }
                item { R2SortChip("Nom", Icons.Default.SortByAlpha, sort == R2Sort.NAME) { sort = R2Sort.NAME } }
                item { R2SortChip("Taille", Icons.Default.DataUsage, sort == R2Sort.SIZE) { sort = R2Sort.SIZE } }
            }
            LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered, key = { it.uri }) { doc -> R2FileCard(doc, categories, organize, trash, correct) }
            }
        }
    }
}

@Composable
private fun R2ModeButton(icon: ImageVector, selected: Boolean, color: Color, click: () -> Unit) {
    Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) color else Color.Transparent).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (selected) Color.White else color)
    }
}

@Composable
private fun R2FamilyChip(key: String?, label: String, count: Int, color: Color, selected: Boolean, click: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = click,
        label = { Text("$label  $count", fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(if (key == null) Icons.Default.GridView else r2VisualForFamily(key).icon, null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = Color.White,
            iconColor = color
        )
    )
}

@Composable
private fun R2SortChip(label: String, icon: ImageVector, selected: Boolean, click: () -> Unit) {
    FilterChip(selected = selected, onClick = click, label = { Text(label) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(17.dp)) })
}

@Composable
private fun R2CategoryBrowserCard(family: String, count: Int, sub: List<Map.Entry<String, Int>>, click: () -> Unit) {
    val visual = r2VisualForFamily(family)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                R2IconBubble(visual.icon, visual.color, 46)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(r2Pretty(family), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("$count fichier(s) · ${sub.size} sous-catégorie(s)", color = R2Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, null, tint = visual.color)
            }
            sub.take(4).forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(visual.color))
                    Spacer(Modifier.width(8.dp))
                    Text(r2Pretty(entry.key.substringAfterLast('/')), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(entry.value.toString(), color = visual.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (sub.size > 4) Text("+ ${sub.size - 4} autres catégories", color = R2Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun R2FileCard(
    doc: IndexedDocument,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var expanded by remember(doc.uri) { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val visual = r2VisualForFamily(doc.categoryPath.substringBefore('/'))

    ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                R2Preview(doc, visual.color, Modifier.size(68.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.originalName, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(r2PrettyPath(doc.categoryPath), color = visual.color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        R2Tiny(formatBytesR2(doc.size), Color(0xFF6D6878))
                        if (doc.extractedText.isNotBlank()) R2Tiny("OCR", R2Purple)
                        if (doc.duplicate) R2Tiny("Doublon", R2Red)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = visual.color)
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE9F2))
                Text("Emplacement : ${doc.relativePath.ifBlank { "Dossier autorisé" }}", color = R2Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doc.suggestedName != doc.originalName) Text("Nom proposé : ${doc.suggestedName}", style = MaterialTheme.typography.bodySmall, color = R2Purple)
                doc.amount?.let { Text("Montant détecté : ${DecimalFormat("0.00").format(it)} €", style = MaterialTheme.typography.bodySmall) }
                doc.detectedDate?.let { Text("Date détectée : $it", style = MaterialTheme.typography.bodySmall) }
                if (doc.extractedText.isNotBlank()) {
                    Surface(color = Color(0xFFF6F4FA), shape = RoundedCornerShape(13.dp)) {
                        Text(doc.extractedText.take(380), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = R2Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilledTonalButton(onClick = { organize(doc) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Ranger")
                    }
                    Box {
                        FilledTonalButton(onClick = { categoryMenu = true }) {
                            Icon(Icons.Default.Category, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Catégorie")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { c -> DropdownMenuItem(text = { Text(r2PrettyPath(c)) }, onClick = { categoryMenu = false; correct(doc, c) }) }
                        }
                    }
                    IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.DeleteOutline, "Corbeille", tint = R2Red) }
                }
            }
        }
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("Mettre à la corbeille ?", fontWeight = FontWeight.ExtraBold) },
            text = { Text(doc.originalName) },
            confirmButton = { Button(onClick = { confirmTrash = false; trash(doc) }, colors = ButtonDefaults.buttonColors(containerColor = R2Red)) { Text("Corbeille") } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun R2Preview(doc: IndexedDocument, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, doc.uri, doc.modifiedAt, doc.size) {
        value = withContext(Dispatchers.IO) { FilePreviewLoader.load(context, doc, 240) }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = doc.originalName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(r2FallbackIcon(doc), null, tint = accent, modifier = Modifier.size(31.dp))
            Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), color = accent, shape = RoundedCornerShape(7.dp)) {
                Text(doc.originalName.substringAfterLast('.', "FILE").take(4).uppercase(Locale.ROOT), Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
        if (doc.duplicate) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(21.dp).clip(CircleShape).background(R2Red), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun R2Cleanup(
    docs: List<IndexedDocument>,
    isPro: Boolean,
    cleanup: () -> Unit,
    trash: (IndexedDocument) -> Unit,
    emptyTrash: () -> Unit,
    showPro: () -> Unit
) {
    val groups = remember(docs) { r2DuplicateGroups(docs) }
    val extras = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
    val bytes = groups.sumOf { (it.size - 1).coerceAtLeast(0) * (it.firstOrNull()?.size ?: 0L) }
    var confirm by remember { mutableStateOf(false) }
    var emptyConfirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { R2Title("Nettoyage", "Doublons exacts vérifiés par SHA-256") }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(Brush.linearGradient(listOf(R2Green, R2Cyan))).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$extras copie(s) en trop", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("${formatBytesR2(bytes)} potentiellement récupérables", color = Color.White.copy(alpha = .9f))
                    Button(onClick = { if (isPro) confirm = true else showPro() }, enabled = extras > 0, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = R2Green), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(7.dp)); Text("Nettoyer les doublons", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    R2IconBubble(Icons.Default.DeleteOutline, R2Red, 42)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Corbeille RangIA", fontWeight = FontWeight.ExtraBold); Text("Suppression définitive uniquement quand tu la vides", color = R2Muted, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { emptyConfirm = true }) { Text("Vider", color = R2Red, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { R2Title("Groupes identiques", if (groups.isEmpty()) "Aucun doublon" else "${groups.size} groupe(s)") }
        items(groups, key = { it.first().hash }) { group -> R2DuplicateGroup(group, trash) }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Nettoyer les doublons ?", fontWeight = FontWeight.Black) },
        text = { Text("RangIA conserve une copie et déplace les copies supprimables dans sa corbeille. Les dossiers Android/data et Android/obb restent protégés.") },
        confirmButton = { Button(onClick = { confirm = false; cleanup() }) { Text("Nettoyer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )

    if (emptyConfirm) AlertDialog(
        onDismissRequest = { emptyConfirm = false },
        title = { Text("Vider définitivement ?", fontWeight = FontWeight.Black) },
        text = { Text("Les fichiers présents dans la corbeille RangIA seront définitivement supprimés.") },
        confirmButton = { Button(onClick = { emptyConfirm = false; emptyTrash() }, colors = ButtonDefaults.buttonColors(containerColor = R2Red)) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { emptyConfirm = false }) { Text("Annuler") } }
    )
}

@Composable
private fun R2DuplicateGroup(group: List<IndexedDocument>, trash: (IndexedDocument) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                R2Preview(group.first(), R2Green, Modifier.size(62.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${group.size} copies identiques", fontWeight = FontWeight.Black)
                    Text("${formatBytesR2(group.first().size)} chacune", color = R2Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = R2Green)
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE9F2))
                group.forEachIndexed { index, doc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        R2Preview(doc, if (index == 0) R2Green else R2Blue, Modifier.size(48.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(doc.relativePath, color = R2Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index == 0) R2Tiny("Conserver", R2Green) else IconButton(onClick = { trash(doc) }) { Icon(Icons.Default.DeleteOutline, "Corbeille", tint = R2Red) }
                    }
                }
            }
        }
    }
}

@Composable
private fun R2Search(
    docs: List<IndexedDocument>,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, docs) { SearchEngine.search(query, docs) }
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 7.dp)) {
        Text("Recherche intelligente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("Nom, texte OCR, montant, date ou catégorie", color = R2Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = R2Orange) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Effacer") } },
            placeholder = { Text("Ex. facture mars, diplôme, vidéo…") }
        )
        Spacer(Modifier.height(8.dp))
        Text("${results.size} résultat(s)", color = R2Orange, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 9.dp, horizontal = 0.dp)) {
            items(results, key = { it.uri }) { doc -> R2FileCard(doc, categories, organize, trash, correct) }
        }
    }
}

@Composable
private fun R2Settings(
    vm: MainViewModel,
    fullAccess: Boolean,
    isPro: Boolean,
    price: String?,
    requestAccess: () -> Unit,
    pickFolder: () -> Unit,
    showPro: () -> Unit,
    restore: () -> Unit
) {
    var autoScan by remember { mutableStateOf(vm.automaticScan) }
    var wholePhone by remember { mutableStateOf(vm.wholePhoneMode) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { R2Title("Réglages", "Contrôle du scan, de l’IA et du stockage") }
        item {
            R2SettingCard(Icons.Default.Security, if (fullAccess) "Accès complet activé" else "Accès complet désactivé", if (fullAccess) "Le stockage partagé peut être analysé" else "Nécessaire pour analyser tout le téléphone", if (fullAccess) R2Green else R2Orange) {
                if (!fullAccess) Button(onClick = requestAccess) { Text("Autoriser") }
            }
        }
        item {
            R2SettingCard(Icons.Default.Folder, "Dossier manuel", "Analyser uniquement un emplacement choisi", R2Blue) {
                OutlinedButton(onClick = pickFolder) { Text("Choisir un dossier") }
            }
        }
        item {
            R2SettingCard(Icons.Default.Refresh, "Analyse automatique", "Relance périodiquement l’index local", R2Purple) {
                Switch(checked = autoScan, onCheckedChange = { autoScan = it; vm.automaticScan = it })
            }
        }
        item {
            R2SettingCard(Icons.Default.PhoneAndroid, "Téléphone complet", "Analyse tout le stockage partagé accessible", R2Cyan) {
                Switch(checked = wholePhone && isPro, onCheckedChange = { value -> if (isPro) { wholePhone = value; vm.wholePhoneMode = value } else showPro() })
            }
        }
        item {
            R2SettingCard(Icons.Default.AutoAwesome, "IA locale", "${vm.learnedExamplesCount} correction(s) apprises · les documents restent sur le téléphone", R2Green) {
                TextButton(onClick = vm::resetAiLearning) { Text("Réinitialiser") }
            }
        }
        item {
            R2SettingCard(Icons.Default.WorkspacePremium, if (isPro) "RangIA Pro actif" else "RangIA Pro", if (isPro) "Fonctions avancées débloquées" else "Achat unique${price?.let { " · $it" } ?: ""}", R2Yellow) {
                if (isPro) R2ProBadge() else Button(onClick = showPro) { Text("Voir Pro") }
            }
        }
        item { TextButton(onClick = restore, modifier = Modifier.fillMaxWidth()) { Text("Restaurer mes achats Google Play") } }
    }
}

@Composable
private fun R2SettingCard(icon: ImageVector, title: String, subtitle: String, color: Color, trailing: @Composable () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(21.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            R2IconBubble(icon, color, 44)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = R2Muted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.width(8.dp)); trailing()
        }
    }
}

@Composable
private fun R2FamilyCard(family: String, count: Int, ignored: Int) {
    val visual = r2VisualForFamily(family)
    ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            R2IconBubble(visual.icon, visual.color, 44)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(r2Pretty(family), fontWeight = FontWeight.ExtraBold); Text("$count fichier(s)", color = R2Muted, style = MaterialTheme.typography.bodySmall) }
            Surface(color = visual.color.copy(alpha = .10f), shape = RoundedCornerShape(12.dp)) { Text(count.toString(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = visual.color, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun R2Title(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(subtitle, color = R2Muted, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun R2IconBubble(icon: ImageVector, color: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * .52f).dp))
    }
}

@Composable
private fun R2Tiny(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

private data class R2Visual(val icon: ImageVector, val color: Color)

private fun r2VisualForFamily(family: String): R2Visual = when (family.lowercase(Locale.FRENCH)) {
    "documents" -> R2Visual(Icons.Default.Description, R2Blue)
    "photos" -> R2Visual(Icons.Default.Image, Color(0xFF9A55D4))
    "vidéos", "videos" -> R2Visual(Icons.Default.Movie, R2Red)
    "audio" -> R2Visual(Icons.Default.AudioFile, Color(0xFF8058D7))
    "entreprise" -> R2Visual(Icons.Default.BusinessCenter, R2Purple)
    "travail" -> R2Visual(Icons.Default.Work, R2Blue)
    "administratif" -> R2Visual(Icons.Default.AccountBalance, R2Cyan)
    "sante" -> R2Visual(Icons.Default.HealthAndSafety, Color(0xFF16A77B))
    "etudes" -> R2Visual(Icons.Default.School, Color(0xFF5575D8))
    "voiture" -> R2Visual(Icons.Default.DirectionsCar, R2Orange)
    "banque" -> R2Visual(Icons.Default.AccountBalanceWallet, R2Green)
    "logement" -> R2Visual(Icons.Default.HomeWork, Color(0xFFB16C45))
    "identite" -> R2Visual(Icons.Default.Badge, R2Pink)
    "assurances" -> R2Visual(Icons.Default.VerifiedUser, Color(0xFF3D8A9B))
    "achats" -> R2Visual(Icons.Default.ShoppingBag, Color(0xFFB96B31))
    "voyages" -> R2Visual(Icons.Default.Flight, R2Cyan)
    "archives" -> R2Visual(Icons.Default.Archive, Color(0xFF8A6849))
    "applications_apk" -> R2Visual(Icons.Default.Android, R2Green)
    "livres" -> R2Visual(Icons.Default.MenuBook, Color(0xFF6D58A6))
    "impots" -> R2Visual(Icons.Default.ReceiptLong, R2Orange)
    else -> R2Visual(Icons.Default.Folder, R2Blue)
}

private fun r2FallbackIcon(doc: IndexedDocument): ImageVector = when {
    doc.mimeType.startsWith("image/") -> Icons.Default.Image
    doc.mimeType.startsWith("video/") -> Icons.Default.Movie
    doc.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    doc.originalName.endsWith(".apk", true) -> Icons.Default.Android
    doc.originalName.endsWith(".zip", true) || doc.originalName.endsWith(".rar", true) || doc.originalName.endsWith(".7z", true) -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

private fun r2FamilyCounts(docs: List<IndexedDocument>): Map<String, Int> = docs.groupingBy { it.categoryPath.substringBefore('/').ifBlank { "Autres" } }.eachCount()

private fun r2DuplicateGroups(docs: List<IndexedDocument>): List<List<IndexedDocument>> = docs.filter { it.hash.isNotBlank() }.groupBy { it.hash }.values.filter { it.size > 1 }.sortedByDescending { it.firstOrNull()?.size ?: 0L }

private fun r2Pretty(value: String): String = value.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
private fun r2PrettyPath(value: String): String = value.split('/').joinToString(" › ") { r2Pretty(it) }

private fun formatBytesR2(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f Ko".format(Locale.FRENCH, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f Mo".format(Locale.FRENCH, mb)
    return "%.2f Go".format(Locale.FRENCH, mb / 1024.0)
}
