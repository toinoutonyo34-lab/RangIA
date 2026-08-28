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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

private val RangPurple = Color(0xFF6D4AFF)
private val RangPurpleDark = Color(0xFF4E31C9)
private val RangBlue = Color(0xFF3D7BFF)
private val RangCyan = Color(0xFF00A9C7)
private val RangGreen = Color(0xFF16A36A)
private val RangOrange = Color(0xFFF39A3E)
private val RangRed = Color(0xFFE65555)
private val RangPink = Color(0xFFD754B7)
private val RangBg = Color(0xFFF7F5FC)
private val RangSurface = Color(0xFFFFFFFF)
private val RangText = Color(0xFF211D2B)
private val RangMuted = Color(0xFF6F687A)

private val RangColorScheme = lightColorScheme(
    primary = RangPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = Color(0xFF2A176F),
    secondary = RangBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8FF),
    tertiary = RangCyan,
    surface = RangSurface,
    surfaceVariant = Color(0xFFF0EDF6),
    background = RangBg,
    onBackground = RangText,
    onSurface = RangText,
    onSurfaceVariant = RangMuted,
    error = RangRed
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = RangColorScheme) {
                RangIaApp(vm, ::requestAllFilesAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAllFilesAccess()
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

private enum class Tab { HOME, DOCUMENTS, SEARCH, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangIaApp(vm: MainViewModel, requestAllFilesAccess: () -> Unit) {
    val documents by vm.documents.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val fullAccess by vm.allFilesAccess.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.acceptTree(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = RangText
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Brush.linearGradient(listOf(RangPurple, RangBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("RangIA", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = vm::scanNow, enabled = !busy) {
                        Icon(Icons.Default.Refresh, "Analyser", tint = if (busy) RangMuted else RangPurple)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
                NavItem(Tab.HOME, tab, Icons.Default.Home, "Accueil") { tab = it }
                NavItem(Tab.DOCUMENTS, tab, Icons.Default.Folder, "Fichiers") { tab = it }
                NavItem(Tab.SEARCH, tab, Icons.Default.Search, "Recherche") { tab = it }
                NavItem(Tab.SETTINGS, tab, Icons.Default.Settings, "Réglages") { tab = it }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    docs = documents,
                    fullAccess = fullAccess,
                    hasFolder = vm.selectedTreeUri != null,
                    busy = busy,
                    progress = progress,
                    requestFullAccess = requestAllFilesAccess,
                    pickFolder = { folderPicker.launch(null) },
                    scan = vm::scanNow,
                    organizeAll = vm::organizeAllSafe
                )
                Tab.DOCUMENTS -> DocumentsScreen(documents, vm::organize, vm.aiCategories, vm::correctCategory)
                Tab.SEARCH -> SearchScreen(documents, vm::organize, vm.aiCategories, vm::correctCategory)
                Tab.SETTINGS -> SettingsScreen(vm, fullAccess, requestAllFilesAccess, { folderPicker.launch(null) })
            }
        }
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            confirmButton = { Button(onClick = vm::dismissMessage) { Text("OK") } },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = RangPurple) },
            title = { Text("RangIA", fontWeight = FontWeight.Bold) },
            text = { Text(message!!) }
        )
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, selected: Tab, icon: ImageVector, label: String, onClick: (Tab) -> Unit) {
    NavigationBarItem(
        selected = tab == selected,
        onClick = { onClick(tab) },
        icon = { Icon(icon, label) },
        label = { Text(label, fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Medium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = RangPurpleDark,
            selectedTextColor = RangPurpleDark,
            indicatorColor = Color(0xFFEAE3FF),
            unselectedIconColor = RangMuted,
            unselectedTextColor = RangMuted
        )
    )
}

@Composable
private fun HomeScreen(
    docs: List<IndexedDocument>,
    fullAccess: Boolean,
    hasFolder: Boolean,
    busy: Boolean,
    progress: String,
    requestFullAccess: () -> Unit,
    pickFolder: () -> Unit,
    scan: () -> Unit,
    organizeAll: () -> Unit
) {
    val categorized = docs.count { it.categoryPath != "Autres" && it.categoryPath != "Fichiers/Autres" }
    val duplicates = docs.count { it.duplicate }
    val groups = docs.groupingBy { it.categoryPath }.eachCount().entries.sortedByDescending { it.value }.take(12)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroDashboard(fullAccess, busy, progress, scan, requestFullAccess) }
        if (!fullAccess) item { AccessCard(requestFullAccess, pickFolder) }
        else item { AccessGrantedCard() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Indexés", docs.size.toString(), Icons.Default.Inventory2, RangPurple, Modifier.weight(1f))
                StatCard("Classés", categorized.toString(), Icons.Default.AutoAwesome, RangGreen, Modifier.weight(1f))
                StatCard("Doublons", duplicates.toString(), Icons.Default.ContentCopy, RangOrange, Modifier.weight(1f))
            }
        }

        if ((fullAccess || hasFolder) && docs.isNotEmpty()) item {
            ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DriveFileMove, null, tint = RangPurple)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Rangement intelligent", fontWeight = FontWeight.Bold)
                            Text("Déplace seulement les fichiers utilisateur sans risque", style = MaterialTheme.typography.bodySmall, color = RangMuted)
                        }
                    }
                    Button(onClick = organizeAll, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.AutoFixHigh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ranger les fichiers sûrs", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { SectionTitle("Catégories détectées", if (docs.isEmpty()) "Lance une analyse pour les afficher" else "${groups.size} principales catégories") }
        if (groups.isEmpty()) item { EmptyCategoriesCard() }
        else items(groups) { (name, count) -> CategoryCard(name, count) }
    }
}

@Composable
private fun HeroDashboard(fullAccess: Boolean, busy: Boolean, progress: String, scan: () -> Unit, requestFullAccess: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7047FF), Color(0xFF3A79FF), Color(0xFF19A3D1))))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ton téléphone, enfin rangé", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("PDF, photos, vidéos et documents", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Text(if (progress.isBlank()) "Analyse en cours…" else progress, color = Color.White, fontWeight = FontWeight.SemiBold)
            } else {
                Button(
                    onClick = if (fullAccess) scan else requestFullAccess,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RangPurpleDark),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(if (fullAccess) Icons.Default.Search else Icons.Default.Security, null)
                    Spacer(Modifier.width(9.dp))
                    Text(if (fullAccess) "Analyser tout mon téléphone" else "Autoriser puis analyser", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun AccessCard(requestFullAccess: () -> Unit, pickFolder: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF8E9))) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.Security, RangOrange)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Autorisation nécessaire", fontWeight = FontWeight.ExtraBold)
                    Text("Pour scanner tout le stockage partagé", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Android doit autoriser RangIA à gérer tous les fichiers. Les zones privées des autres applications restent protégées.")
            Button(onClick = requestFullAccess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Text("Autoriser l’accès complet", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = pickFolder, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ou analyser seulement un dossier") }
        }
    }
}

@Composable
private fun AccessGrantedCard() {
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFEAF9F1))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Default.VerifiedUser, RangGreen)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Accès téléphone complet activé", fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D7048))
                Text("RangIA peut indexer le stockage partagé", style = MaterialTheme.typography.bodySmall, color = Color(0xFF39745D))
            }
            Icon(Icons.Default.CheckCircle, null, tint = RangGreen)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color, size: Int = 42) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            IconBubble(icon, accent, 34)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = RangText)
            Text(label, style = MaterialTheme.typography.labelMedium, color = RangMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RangMuted)
    }
}

@Composable
private fun EmptyCategoriesCard() {
    OutlinedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = RangPurple, modifier = Modifier.size(42.dp))
            Text("Aucun fichier analysé", fontWeight = FontWeight.Bold)
            Text("Appuie sur “Analyser tout mon téléphone”", color = RangMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CategoryCard(name: String, count: Int) {
    val visual = categoryVisual(name)
    ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(visual.icon, visual.color, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name.replace('_', ' ').replace("/", " › "), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count fichier${if (count > 1) "s" else ""}", color = RangMuted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = visual.color.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)) {
                Text(count.toString(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = visual.color, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private data class CategoryVisual(val icon: ImageVector, val color: Color)

private fun categoryVisual(path: String): CategoryVisual {
    val p = path.lowercase()
    return when {
        "facture" in p || "devis" in p -> CategoryVisual(Icons.Default.ReceiptLong, RangPurple)
        "paie" in p || "travail" in p || "france" in p -> CategoryVisual(Icons.Default.Work, RangBlue)
        "urssaf" in p || "imp" in p || "administr" in p -> CategoryVisual(Icons.Default.AccountBalance, RangCyan)
        "voiture" in p || "auto" in p -> CategoryVisual(Icons.Default.DirectionsCar, RangOrange)
        "banque" in p -> CategoryVisual(Icons.Default.AccountBalanceWallet, RangGreen)
        "ident" in p -> CategoryVisual(Icons.Default.Badge, RangPink)
        "photo" in p || "image" in p -> CategoryVisual(Icons.Default.Image, Color(0xFFB34CD4))
        "vid" in p -> CategoryVisual(Icons.Default.Movie, RangRed)
        "audio" in p || "musique" in p -> CategoryVisual(Icons.Default.AudioFile, Color(0xFF8457D9))
        "archive" in p -> CategoryVisual(Icons.Default.Archive, Color(0xFF8C6A4B))
        "apk" in p || "application" in p -> CategoryVisual(Icons.Default.Android, RangGreen)
        else -> CategoryVisual(Icons.Default.Folder, RangBlue)
    }
}

@Composable
private fun DocumentsScreen(docs: List<IndexedDocument>, organize: (IndexedDocument) -> Unit, aiCategories: List<String>, correct: (IndexedDocument, String) -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    val detectedCategories = remember(docs) { docs.map { it.categoryPath }.distinct().sorted() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Mes fichiers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("${docs.size} fichier(s) indexé(s)", color = RangMuted)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("Tout") },
                    leadingIcon = { Icon(Icons.Default.GridView, null, modifier = Modifier.size(18.dp)) }
                )
            }
            items(detectedCategories) { c ->
                val visual = categoryVisual(c)
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c.substringAfterLast('/').replace('_', ' '), maxLines = 1) },
                    leadingIcon = { Icon(visual.icon, null, modifier = Modifier.size(18.dp), tint = visual.color) }
                )
            }
        }
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { EmptyCategoriesCard() }
        } else {
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val shown = if (category == null) docs else docs.filter { it.categoryPath == category }
                items(shown, key = { it.uri }) { doc -> DocumentCard(doc, organize, aiCategories, correct) }
            }
        }
    }
}

@Composable
private fun SearchScreen(docs: List<IndexedDocument>, organize: (IndexedDocument) -> Unit, categories: List<String>, correct: (IndexedDocument, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, docs) { SearchEngine.search(query, docs) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Recherche intelligente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Cherche par nom, contenu, date ou montant", color = RangMuted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = RangPurple) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Effacer") } },
            placeholder = { Text("Ex. facture mars 100 €, contrôle technique…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RangPurple,
                unfocusedBorderColor = Color(0xFFD7D1E2),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Spacer(Modifier.height(10.dp))
        Text("${results.size} résultat(s)", style = MaterialTheme.typography.labelLarge, color = RangPurpleDark, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(results, key = { it.uri }) { DocumentCard(it, organize, categories, correct) }
        }
    }
}

@Composable
private fun DocumentCard(doc: IndexedDocument, organize: (IndexedDocument) -> Unit, categories: List<String>, correct: (IndexedDocument, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val visual = categoryVisual(doc.categoryPath)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(iconFor(doc), visual.color, 44)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.originalName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(doc.categoryPath.replace('_', ' ').replace("/", " › "), style = MaterialTheme.typography.labelMedium, color = visual.color, fontWeight = FontWeight.SemiBold)
                }
                if (doc.duplicate) {
                    Surface(color = Color(0xFFFFE9E8), shape = RoundedCornerShape(10.dp)) {
                        Text("Doublon", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = RangRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (doc.suggestedName != doc.originalName) {
                Surface(color = Color(0xFFF3F0F9), shape = RoundedCornerShape(11.dp)) {
                    Text("→ ${doc.suggestedName}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniTag("${(doc.confidence * 100).toInt()} %", visual.color)
                doc.amount?.let { MiniTag("${DecimalFormat("0.00").format(it)} €", RangGreen) }
                doc.detectedDate?.let { MiniTag(it, RangBlue) }
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE8F3))
                if (doc.relativePath.isNotBlank()) Text("Emplacement : ${doc.relativePath}", style = MaterialTheme.typography.bodySmall, color = RangMuted)
                doc.organization?.let { Text("Organisation : $it", style = MaterialTheme.typography.bodySmall) }
                if (doc.extractedText.isNotBlank()) {
                    Text("Contenu détecté", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(doc.extractedText.take(700), style = MaterialTheme.typography.bodySmall, color = RangMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { organize(doc) }, enabled = !doc.duplicate, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.DriveFileMove, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ranger")
                    }
                    var categoryMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Corriger IA")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    leadingIcon = { val v = categoryVisual(c); Icon(v.icon, null, tint = v.color) },
                                    text = { Text(c.replace('_', ' ')) },
                                    onClick = { categoryMenu = false; correct(doc, c) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(9.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun iconFor(doc: IndexedDocument) = when {
    doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    doc.mimeType.startsWith("image/") -> Icons.Default.Image
    doc.mimeType.startsWith("video/") -> Icons.Default.Movie
    doc.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    doc.categoryPath == "Archives" -> Icons.Default.Archive
    doc.categoryPath == "Applications_APK" -> Icons.Default.Android
    else -> Icons.Default.Description
}

@Composable
private fun SettingsScreen(vm: MainViewModel, fullAccess: Boolean, requestAllFilesAccess: () -> Unit, pick: () -> Unit) {
    var automatic by remember { mutableStateOf(vm.automaticScan) }
    var wholePhone by remember { mutableStateOf(vm.wholePhoneMode) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Contrôle du scan, de l’IA et de la confidentialité", color = RangMuted)
        }
        item {
            SettingsCard(Icons.Default.Security, RangPurple, "Accès au stockage") {
                StatusRow("Tous les fichiers", fullAccess)
                Spacer(Modifier.height(8.dp))
                Button(onClick = requestAllFilesAccess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(if (fullAccess) "Ouvrir le réglage d’accès" else "Autoriser tout le téléphone")
                }
                Spacer(Modifier.height(6.dp))
                Text("Les données privées d’autres applications et Android/data restent protégées par Android.", style = MaterialTheme.typography.bodySmall, color = RangMuted)
            }
        }
        item {
            SettingsCard(Icons.Default.PhoneAndroid, RangBlue, "Mode téléphone complet") {
                SwitchSetting("Scanner le stockage partagé", "Parcourt automatiquement les fichiers accessibles.", wholePhone) {
                    wholePhone = it; vm.wholePhoneMode = it
                }
            }
        }
        item {
            SettingsCard(Icons.Default.FolderOpen, RangOrange, "Dossier manuel") {
                Text(vm.selectedTreeUri ?: "Aucun dossier manuel", maxLines = 2, overflow = TextOverflow.Ellipsis, color = RangMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Choisir un dossier") }
            }
        }
        item {
            SettingsCard(Icons.Default.Schedule, RangCyan, "Analyse automatique") {
                SwitchSetting("Réanalyse automatique", "Environ toutes les 12 heures pour les fichiers modifiés.", automatic) {
                    automatic = it; vm.automaticScan = it
                }
            }
        }
        item {
            SettingsCard(Icons.Default.Psychology, RangPink, "IA locale") {
                Text("${vm.learnedExamplesCount} correction(s) apprise(s)", fontWeight = FontWeight.Bold)
                Text("Le classificateur fonctionne hors ligne et s’améliore quand tu corriges une catégorie.", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::resetAiLearning, enabled = vm.learnedExamplesCount > 0, shape = RoundedCornerShape(14.dp)) {
                    Text("Réinitialiser l’apprentissage")
                }
            }
        }
        item {
            SettingsCard(Icons.Default.Lock, RangGreen, "Confidentialité") {
                Text("Le classement, l’OCR et l’apprentissage restent sur le téléphone. Aucun document n’est envoyé à une API d’IA distante dans cette version.")
                Spacer(Modifier.height(6.dp))
                Text("Le déplacement utilise copie + vérification avant suppression de l’original.", style = MaterialTheme.typography.bodySmall, color = RangMuted)
            }
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, accent: Color, title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, accent, 40)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (ok) RangGreen else RangRed)
        Spacer(Modifier.width(8.dp))
        Text("$label : ${if (ok) "autorisé" else "non autorisé"}", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RangMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RangPurple)
        )
    }
}
