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
import java.util.Locale

private val RangPurple = Color(0xFF6D4AFF)
private val RangPurpleDark = Color(0xFF4E31C9)
private val RangBlue = Color(0xFF3478F6)
private val RangCyan = Color(0xFF00A7C4)
private val RangGreen = Color(0xFF16A36A)
private val RangOrange = Color(0xFFF39A3E)
private val RangRed = Color(0xFFE24D4D)
private val RangPink = Color(0xFFD754B7)
private val RangYellow = Color(0xFFF1B72C)
private val RangBg = Color(0xFFF7F6FB)
private val RangText = Color(0xFF211D2B)
private val RangMuted = Color(0xFF6F687A)

private val RangColorScheme = lightColorScheme(
    primary = RangPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = Color(0xFF2A176F),
    secondary = RangBlue,
    onSecondary = Color.White,
    tertiary = RangCyan,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EDF6),
    background = RangBg,
    onBackground = RangText,
    onSurface = RangText,
    onSurfaceVariant = RangMuted,
    error = RangRed
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billing = BillingManager(this)
        billing.start()
        setContent {
            MaterialTheme(colorScheme = RangColorScheme) {
                val purchasedPro by billing.isPro.collectAsState()
                val price by billing.price.collectAsState()
                val billingStatus by billing.status.collectAsState()
                val effectivePro = BuildConfig.DEBUG || purchasedPro
                RangIaApp(
                    vm = vm,
                    isPro = effectivePro,
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

private enum class Tab { HOME, FILES, CLEANUP, SEARCH, SETTINGS }

private data class NavSpec(val tab: Tab, val icon: ImageVector, val label: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangIaApp(
    vm: MainViewModel,
    isPro: Boolean,
    proPrice: String?,
    billingStatus: String?,
    requestAllFilesAccess: () -> Unit,
    buyPro: () -> Unit,
    restorePro: () -> Unit,
    dismissBillingStatus: () -> Unit
) {
    val documents by vm.documents.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val fullAccess by vm.allFilesAccess.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showPro by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.acceptTree(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    val nav = listOf(
        NavSpec(Tab.HOME, Icons.Default.Home, "Accueil", RangPurple),
        NavSpec(Tab.FILES, Icons.Default.Folder, "Fichiers", RangBlue),
        NavSpec(Tab.CLEANUP, Icons.Default.CleaningServices, "Nettoyage", RangGreen),
        NavSpec(Tab.SEARCH, Icons.Default.Search, "Recherche", RangOrange),
        NavSpec(Tab.SETTINGS, Icons.Default.Settings, "Réglages", RangPink)
    )

    Scaffold(
        containerColor = RangBg,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = RangBg),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(RangPurple, RangBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("RangIA", fontWeight = FontWeight.ExtraBold, color = RangText)
                    }
                },
                actions = {
                    if (isPro) {
                        Surface(color = Color(0xFFFFF1B8), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFA56A00), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PRO", fontWeight = FontWeight.ExtraBold, color = Color(0xFFA56A00), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        TextButton(onClick = { showPro = true }) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = RangYellow)
                            Spacer(Modifier.width(4.dp))
                            Text("Pro", fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = vm::scanNow, enabled = !busy) {
                        Icon(Icons.Default.Refresh, "Analyser", tint = if (busy) RangMuted else RangPurple)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 12.dp) {
                nav.forEach { spec ->
                    NavigationBarItem(
                        selected = tab == spec.tab,
                        onClick = { tab = spec.tab },
                        icon = { Icon(spec.icon, spec.label) },
                        label = { Text(spec.label, maxLines = 1, style = MaterialTheme.typography.labelSmall, fontWeight = if (tab == spec.tab) FontWeight.ExtraBold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = spec.color,
                            indicatorColor = spec.color,
                            unselectedIconColor = RangMuted,
                            unselectedTextColor = RangMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    docs = documents,
                    fullAccess = fullAccess,
                    busy = busy,
                    progress = progress,
                    requestFullAccess = requestAllFilesAccess,
                    pickFolder = { folderPicker.launch(null) },
                    scan = vm::scanNow,
                    organizeAll = { if (isPro) vm.organizeAllSafe() else showPro = true },
                    cleanup = { if (isPro) vm.cleanupDuplicates() else showPro = true },
                    isPro = isPro,
                    openCleanup = { tab = Tab.CLEANUP }
                )
                Tab.FILES -> FilesScreen(
                    docs = documents,
                    organize = vm::organize,
                    trash = vm::moveToTrash,
                    categories = vm.aiCategories,
                    correct = vm::correctCategory
                )
                Tab.CLEANUP -> CleanupScreen(
                    docs = documents,
                    isPro = isPro,
                    cleanup = { if (isPro) vm.cleanupDuplicates() else showPro = true },
                    trash = vm::moveToTrash,
                    emptyTrash = vm::emptyTrash,
                    showPro = { showPro = true }
                )
                Tab.SEARCH -> SearchScreen(
                    docs = documents,
                    organize = vm::organize,
                    trash = vm::moveToTrash,
                    categories = vm.aiCategories,
                    correct = vm::correctCategory
                )
                Tab.SETTINGS -> SettingsScreen(
                    vm = vm,
                    fullAccess = fullAccess,
                    requestAllFilesAccess = requestAllFilesAccess,
                    pick = { folderPicker.launch(null) },
                    isPro = isPro,
                    proPrice = proPrice,
                    showPro = { showPro = true },
                    restorePro = restorePro,
                    emptyTrash = vm::emptyTrash
                )
            }
        }
    }

    if (showPro) {
        ProDialog(
            price = proPrice,
            dismiss = { showPro = false },
            buy = buyPro,
            restore = restorePro
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            confirmButton = { Button(onClick = vm::dismissMessage) { Text("OK") } },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = RangPurple) },
            title = { Text("RangIA", fontWeight = FontWeight.Bold) },
            text = { Text(it) }
        )
    }

    billingStatus?.let {
        AlertDialog(
            onDismissRequest = dismissBillingStatus,
            confirmButton = { Button(onClick = dismissBillingStatus) { Text("OK") } },
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = RangYellow) },
            title = { Text("RangIA Pro", fontWeight = FontWeight.Bold) },
            text = { Text(it) }
        )
    }
}

@Composable
private fun HomeScreen(
    docs: List<IndexedDocument>,
    fullAccess: Boolean,
    busy: Boolean,
    progress: String,
    requestFullAccess: () -> Unit,
    pickFolder: () -> Unit,
    scan: () -> Unit,
    organizeAll: () -> Unit,
    cleanup: () -> Unit,
    isPro: Boolean,
    openCleanup: () -> Unit
) {
    val categorized = docs.count { it.categoryPath !in setOf("Autres", "Fichiers/Autres") }
    val duplicateGroups = remember(docs) { duplicateGroups(docs) }
    val extraDuplicates = duplicateGroups.sumOf { (it.size - 1).coerceAtLeast(0) }
    val recoverable = duplicateGroups.sumOf { group -> (group.size - 1).coerceAtLeast(0) * (group.firstOrNull()?.size ?: 0L) }
    val groups = docs.groupingBy { it.categoryPath }.eachCount().entries.sortedByDescending { it.value }.take(10)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroDashboard(fullAccess, busy, progress, scan, requestFullAccess) }
        if (!fullAccess) item { AccessCard(requestFullAccess, pickFolder) }
        else item { AccessGrantedCard() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard("Analysés", docs.size.toString(), Icons.Default.Inventory2, RangPurple, Modifier.weight(1f))
                StatCard("Classés", categorized.toString(), Icons.Default.AutoAwesome, RangBlue, Modifier.weight(1f))
                StatCard("Doublons", extraDuplicates.toString(), Icons.Default.ContentCopy, RangGreen, Modifier.weight(1f))
            }
        }

        if (docs.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Default.AutoFixHigh, "Ranger", "Automatique", RangPurple, Modifier.weight(1f), organizeAll, locked = !isPro)
                    QuickAction(Icons.Default.CleaningServices, "Nettoyer", formatBytes(recoverable), RangGreen, Modifier.weight(1f), if (extraDuplicates > 0) cleanup else openCleanup, locked = !isPro)
                }
            }
        }

        if (extraDuplicates > 0) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = openCleanup),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFECFAF3))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Default.ContentCopy, RangGreen, 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$extraDuplicates doublon(s) détecté(s)", fontWeight = FontWeight.ExtraBold, color = Color(0xFF116A48))
                            Text("Jusqu’à ${formatBytes(recoverable)} récupérables", color = Color(0xFF39745D), style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = RangGreen)
                    }
                }
            }
        }

        item { SectionTitle("Organisation intelligente", if (docs.isEmpty()) "Lance une analyse pour commencer" else "${groups.size} catégories principales") }
        if (groups.isEmpty()) item { EmptyCategoriesCard() }
        else items(groups) { (name, count) -> CategoryCard(name, count) }
    }
}

@Composable
private fun HeroDashboard(fullAccess: Boolean, busy: Boolean, progress: String, scan: () -> Unit, requestFullAccess: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6F49FF), Color(0xFF3478F6), Color(0xFF02A9C5))))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(29.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ton téléphone, enfin rangé", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("IA locale · OCR · doublons · recherche", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Text(if (progress.isBlank()) "Analyse en cours…" else progress, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
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
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit, locked: Boolean) {
    ElevatedCard(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, color, 40)
                Spacer(Modifier.weight(1f))
                if (locked) Icon(Icons.Default.Lock, null, tint = RangYellow, modifier = Modifier.size(18.dp))
            }
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(subtitle.ifBlank { "Prêt" }, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    Text("Pour analyser le stockage partagé du téléphone", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("RangIA demande l’accès aux fichiers pour les analyser, rechercher les doublons et ranger les emplacements que tu autorises.")
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
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Default.VerifiedUser, RangGreen)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Accès téléphone activé", fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D7048))
                Text("Analyse locale, aucun document envoyé à une IA distante", style = MaterialTheme.typography.bodySmall, color = Color(0xFF39745D))
            }
            Icon(Icons.Default.CheckCircle, null, tint = RangGreen)
        }
    }
}

@Composable
private fun FilesScreen(
    docs: List<IndexedDocument>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    categories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var filter by remember { mutableStateOf("Tout") }
    val detected = remember(docs) { docs.map { it.categoryPath }.distinct().sorted() }
    val shown = remember(docs, filter) {
        when (filter) {
            "Tout" -> docs
            "Doublons" -> docs.filter { it.duplicate }
            else -> docs.filter { it.categoryPath == filter }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Mes fichiers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("${shown.size} affiché(s) · ${docs.size} analysé(s)", color = RangMuted)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FileFilterChip("Tout", Icons.Default.GridView, RangPurple, filter) { filter = "Tout" } }
            if (docs.any { it.duplicate }) item { FileFilterChip("Doublons", Icons.Default.ContentCopy, RangGreen, filter) { filter = "Doublons" } }
            items(detected) { c ->
                val visual = categoryVisual(c)
                FileFilterChip(c, visual.icon, visual.color, filter) { filter = c }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { EmptyCategoriesCard() }
        } else {
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.uri }) { doc -> DocumentCard(doc, organize, trash, categories, correct) }
            }
        }
    }
}

@Composable
private fun FileFilterChip(value: String, icon: ImageVector, color: Color, selected: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = onClick,
        label = { Text(if (value.contains('/')) value.substringAfterLast('/').replace('_', ' ') else value, maxLines = 1) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (selected == value) Color.White else color) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            containerColor = Color.White
        )
    )
}

@Composable
private fun CleanupScreen(
    docs: List<IndexedDocument>,
    isPro: Boolean,
    cleanup: () -> Unit,
    trash: (IndexedDocument) -> Unit,
    emptyTrash: () -> Unit,
    showPro: () -> Unit
) {
    val groups = remember(docs) { duplicateGroups(docs) }
    val extraCopies = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
    val recoverable = groups.sumOf { group -> (group.size - 1).coerceAtLeast(0) * (group.firstOrNull()?.size ?: 0L) }
    var confirmCleanup by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Text("Nettoyage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Doublons exacts détectés par empreinte SHA-256", color = RangMuted)
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0BA777), Color(0xFF18B8A2))))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("$extraCopies copie(s) en trop", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            Text("${formatBytes(recoverable)} potentiellement récupérables", color = Color.White.copy(alpha = .86f))
                        }
                    }
                    Button(
                        onClick = { if (isPro) confirmCleanup = true else showPro() },
                        enabled = extraCopies > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF08795C)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (isPro) Icons.Default.DeleteSweep else Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPro) "Nettoyer les doublons" else "Débloquer le nettoyage Pro", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Default.DeleteOutline, RangRed, 42)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Corbeille RangIA", fontWeight = FontWeight.ExtraBold)
                        Text("Les suppressions passent d’abord par une corbeille locale", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { confirmEmptyTrash = true }) { Text("Vider", color = RangRed, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { SectionTitle("Groupes de doublons", if (groups.isEmpty()) "Aucun doublon exact détecté" else "${groups.size} groupe(s)") }
        if (groups.isEmpty()) {
            item {
                OutlinedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = RangGreen, modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Stockage propre", fontWeight = FontWeight.ExtraBold)
                        Text("Aucun doublon exact n’a été trouvé.", color = RangMuted)
                    }
                }
            }
        } else {
            items(groups, key = { it.first().hash }) { group -> DuplicateGroupCard(group, trash) }
        }
    }

    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = RangGreen) },
            title = { Text("Nettoyer les doublons ?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("RangIA conserve au moins une copie de chaque fichier et déplace uniquement les copies modifiables vers sa corbeille. Rien n’est supprimé définitivement à cette étape.") },
            confirmButton = { Button(onClick = { confirmCleanup = false; cleanup() }) { Text("Nettoyer") } },
            dismissButton = { TextButton(onClick = { confirmCleanup = false }) { Text("Annuler") } }
        )
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            icon = { Icon(Icons.Default.Warning, null, tint = RangRed) },
            title = { Text("Vider définitivement ?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Les fichiers déjà placés dans la corbeille RangIA seront supprimés définitivement. Cette action ne pourra pas être annulée.") },
            confirmButton = { Button(onClick = { confirmEmptyTrash = false; emptyTrash() }, colors = ButtonDefaults.buttonColors(containerColor = RangRed)) { Text("Supprimer définitivement") } },
            dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun DuplicateGroupCard(group: List<IndexedDocument>, trash: (IndexedDocument) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val first = group.first()
    val visual = categoryVisual(first.categoryPath)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.ContentCopy, RangGreen, 44)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("${group.size} copies identiques", fontWeight = FontWeight.ExtraBold)
                    Text("${formatBytes(first.size)} chacune · ${first.categoryPath.replace("/", " › ")}", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = visual.color)
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE8F3))
                group.forEachIndexed { index, doc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (index == 0) Color(0xFFE9F8F0) else Color(0xFFF5F2F8)), contentAlignment = Alignment.Center) {
                            Icon(if (index == 0) Icons.Default.Bookmark else Icons.Default.Description, null, tint = if (index == 0) RangGreen else RangMuted, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(doc.relativePath.ifBlank { "Emplacement sélectionné" }, color = RangMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index > 0) {
                            IconButton(onClick = { trash(doc) }) { Icon(Icons.Default.DeleteOutline, "Corbeille", tint = RangRed) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    docs: List<IndexedDocument>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    categories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, docs) { SearchEngine.search(query, docs) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Recherche intelligente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Nom, contenu OCR, date, montant ou catégorie", color = RangMuted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = RangOrange) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Effacer") } },
            placeholder = { Text("Ex. facture mars 100 €, contrôle technique…") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RangOrange, unfocusedBorderColor = Color(0xFFD7D1E2), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(10.dp))
        Text("${results.size} résultat(s)", style = MaterialTheme.typography.labelLarge, color = RangOrange, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(results, key = { it.uri }) { DocumentCard(it, organize, trash, categories, correct) }
        }
    }
}

@Composable
private fun DocumentCard(
    doc: IndexedDocument,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    categories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val visual = categoryVisual(doc.categoryPath)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(iconFor(doc), visual.color, 46)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.originalName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(doc.categoryPath.replace('_', ' ').replace("/", " › "), style = MaterialTheme.typography.labelMedium, color = visual.color, fontWeight = FontWeight.SemiBold)
                }
                if (doc.duplicate) {
                    Surface(color = Color(0xFFEAF9F1), shape = RoundedCornerShape(10.dp)) {
                        Text("Doublon", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = RangGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniTag("${(doc.confidence * 100).toInt()} % IA", visual.color)
                MiniTag(formatBytes(doc.size), RangMuted)
                doc.amount?.let { MiniTag("${DecimalFormat("0.00").format(it)} €", RangGreen) }
            }
            if (doc.suggestedName != doc.originalName) {
                Surface(color = Color(0xFFF3F0F9), shape = RoundedCornerShape(11.dp)) {
                    Text("Nom suggéré : ${doc.suggestedName}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE8F3))
                if (doc.relativePath.isNotBlank()) Text("Emplacement : ${doc.relativePath}", style = MaterialTheme.typography.bodySmall, color = RangMuted)
                doc.organization?.let { Text("Organisation détectée : $it", style = MaterialTheme.typography.bodySmall) }
                doc.detectedDate?.let { Text("Date détectée : $it", style = MaterialTheme.typography.bodySmall) }
                if (doc.extractedText.isNotBlank()) {
                    Text("Contenu détecté", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(doc.extractedText.take(550), style = MaterialTheme.typography.bodySmall, color = RangMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { organize(doc) }, enabled = !doc.duplicate, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.DriveFileMove, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Ranger")
                    }
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Corriger")
                    }
                    FilledTonalIconButton(onClick = { confirmTrash = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFFFFEAEA), contentColor = RangRed)) {
                        Icon(Icons.Default.DeleteOutline, "Corbeille")
                    }
                }
                Box {
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { c ->
                            val v = categoryVisual(c)
                            DropdownMenuItem(
                                leadingIcon = { Icon(v.icon, null, tint = v.color) },
                                text = { Text(c.replace('_', ' ').replace("/", " › ")) },
                                onClick = { categoryMenu = false; correct(doc, c) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = RangRed) },
            title = { Text("Mettre à la corbeille ?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("${doc.originalName} sera déplacé dans la corbeille RangIA. Il ne sera pas supprimé définitivement tant que tu ne vides pas la corbeille.") },
            confirmButton = { Button(onClick = { confirmTrash = false; trash(doc) }, colors = ButtonDefaults.buttonColors(containerColor = RangRed)) { Text("Mettre à la corbeille") } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun SettingsScreen(
    vm: MainViewModel,
    fullAccess: Boolean,
    requestAllFilesAccess: () -> Unit,
    pick: () -> Unit,
    isPro: Boolean,
    proPrice: String?,
    showPro: () -> Unit,
    restorePro: () -> Unit,
    emptyTrash: () -> Unit
) {
    var automatic by remember { mutableStateOf(vm.automaticScan) }
    var wholePhone by remember { mutableStateOf(vm.wholePhoneMode) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Analyse, rangement, confidentialité et RangIA Pro", color = RangMuted)
        }
        item {
            SettingsCard(Icons.Default.WorkspacePremium, RangYellow, if (isPro) "RangIA Pro activé" else "Passer à RangIA Pro") {
                Text(if (isPro) "Rangement automatique et nettoyage intelligent des doublons sont débloqués." else "Débloque le rangement automatique et le nettoyage des doublons en un appui.", color = RangMuted)
                Spacer(Modifier.height(8.dp))
                if (!isPro) Button(onClick = showPro, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.WorkspacePremium, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Débloquer Pro${proPrice?.let { " · $it" } ?: ""}", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = restorePro, modifier = Modifier.fillMaxWidth()) { Text("Restaurer mes achats") }
            }
        }
        item {
            SettingsCard(Icons.Default.Security, RangPurple, "Accès au stockage") {
                StatusRow("Tous les fichiers", fullAccess)
                Spacer(Modifier.height(8.dp))
                Button(onClick = requestAllFilesAccess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(if (fullAccess) "Ouvrir le réglage d’accès" else "Autoriser tout le téléphone")
                }
                Spacer(Modifier.height(6.dp))
                Text("Android/data et d’autres zones privées restent protégées par Android.", style = MaterialTheme.typography.bodySmall, color = RangMuted)
            }
        }
        item {
            SettingsCard(Icons.Default.PhoneAndroid, RangBlue, "Analyse du téléphone") {
                SwitchSetting("Scanner le stockage partagé", "Analyse les fichiers accessibles sans les envoyer sur un serveur.", wholePhone) {
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
                Text("L’IA s’améliore quand tu corriges une catégorie.", color = RangMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::resetAiLearning, enabled = vm.learnedExamplesCount > 0, shape = RoundedCornerShape(14.dp)) { Text("Réinitialiser l’apprentissage") }
            }
        }
        item {
            SettingsCard(Icons.Default.DeleteSweep, RangRed, "Corbeille") {
                Text("Les fichiers supprimés depuis RangIA sont d’abord déplacés dans une corbeille locale.", color = RangMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmEmpty = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.DeleteForever, null, tint = RangRed)
                    Spacer(Modifier.width(7.dp))
                    Text("Vider la corbeille", color = RangRed)
                }
            }
        }
        item {
            SettingsCard(Icons.Default.Lock, RangGreen, "Confidentialité") {
                Text("Le classement, l’OCR, la recherche et l’apprentissage restent sur le téléphone. Aucun document n’est envoyé à une API d’IA distante par RangIA.")
                Spacer(Modifier.height(6.dp))
                Text("Les déplacements utilisent copie + vérification avant suppression de l’original.", style = MaterialTheme.typography.bodySmall, color = RangMuted)
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Default.Warning, null, tint = RangRed) },
            title = { Text("Vider la corbeille ?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Cette action supprimera définitivement le contenu de la corbeille RangIA.") },
            confirmButton = { Button(onClick = { confirmEmpty = false; emptyTrash() }, colors = ButtonDefaults.buttonColors(containerColor = RangRed)) { Text("Supprimer définitivement") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun ProDialog(price: String?, dismiss: () -> Unit, buy: () -> Unit, restore: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Default.WorkspacePremium, null, tint = RangYellow, modifier = Modifier.size(34.dp)) },
        title = { Text("RangIA Pro", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Débloque les outils automatiques les plus puissants :")
                ProLine(Icons.Default.AutoFixHigh, "Rangement automatique des fichiers sûrs")
                ProLine(Icons.Default.DeleteSweep, "Nettoyage automatique des doublons")
                ProLine(Icons.Default.Psychology, "Apprentissage IA personnalisé illimité")
                ProLine(Icons.Default.WorkspacePremium, "Achat unique, pas d’abonnement")
                Surface(color = Color(0xFFFFF4CC), shape = RoundedCornerShape(14.dp)) {
                    Text(price ?: "Prix affiché par Google Play", modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.ExtraBold, color = Color(0xFF8B6500))
                }
            }
        },
        confirmButton = { Button(onClick = { dismiss(); buy() }) { Text("Débloquer Pro") } },
        dismissButton = {
            Row {
                TextButton(onClick = restore) { Text("Restaurer") }
                TextButton(onClick = dismiss) { Text("Plus tard") }
            }
        }
    )
}

@Composable
private fun ProLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RangPurple, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
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
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RangPurple))
    }
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = RangPurple, modifier = Modifier.size(42.dp))
            Text("Rien à afficher", fontWeight = FontWeight.Bold)
            Text("Lance une analyse complète du téléphone.", color = RangMuted, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun MiniTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(9.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private data class CategoryVisual(val icon: ImageVector, val color: Color)

private fun categoryVisual(path: String): CategoryVisual {
    val p = path.lowercase(Locale.ROOT)
    return when {
        "facture" in p -> CategoryVisual(Icons.Default.ReceiptLong, Color(0xFFE86D4A))
        "devis" in p -> CategoryVisual(Icons.Default.RequestQuote, RangPurple)
        "urssaf" in p || "impot" in p || "administr" in p -> CategoryVisual(Icons.Default.AccountBalance, RangCyan)
        "paie" in p || "travail" in p || "contrat" in p || "france_travail" in p -> CategoryVisual(Icons.Default.Work, RangBlue)
        "voiture" in p || "controle_technique" in p -> CategoryVisual(Icons.Default.DirectionsCar, RangOrange)
        "banque" in p || "releve" in p -> CategoryVisual(Icons.Default.AccountBalanceWallet, RangGreen)
        "ident" in p -> CategoryVisual(Icons.Default.Badge, RangPink)
        "capture" in p -> CategoryVisual(Icons.Default.CropFree, Color(0xFF8C5EEA))
        "photo" in p || "image" in p -> CategoryVisual(Icons.Default.Image, Color(0xFFB34CD4))
        "vid" in p -> CategoryVisual(Icons.Default.Movie, RangRed)
        "messages_vocaux" in p -> CategoryVisual(Icons.Default.Mic, RangPink)
        "audio" in p || "musique" in p -> CategoryVisual(Icons.Default.AudioFile, Color(0xFF8457D9))
        "archive" in p -> CategoryVisual(Icons.Default.Archive, Color(0xFF8C6A4B))
        "apk" in p || "application" in p -> CategoryVisual(Icons.Default.Android, RangGreen)
        "pdf" in p -> CategoryVisual(Icons.Default.PictureAsPdf, RangRed)
        "tableur" in p -> CategoryVisual(Icons.Default.TableChart, RangGreen)
        "presentation" in p -> CategoryVisual(Icons.Default.Slideshow, RangOrange)
        "document" in p || "word" in p || "texte" in p -> CategoryVisual(Icons.Default.Description, RangBlue)
        "voyage" in p -> CategoryVisual(Icons.Default.FlightTakeoff, RangCyan)
        "garantie" in p -> CategoryVisual(Icons.Default.VerifiedUser, RangGreen)
        "notice" in p || "livre" in p -> CategoryVisual(Icons.Default.MenuBook, RangOrange)
        else -> CategoryVisual(Icons.Default.Folder, RangBlue)
    }
}

private fun iconFor(doc: IndexedDocument): ImageVector = when {
    doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    doc.mimeType.startsWith("image/") -> Icons.Default.Image
    doc.mimeType.startsWith("video/") -> Icons.Default.Movie
    doc.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    doc.categoryPath == "Archives" -> Icons.Default.Archive
    doc.categoryPath == "Applications_APK" -> Icons.Default.Android
    else -> categoryVisual(doc.categoryPath).icon
}

private fun duplicateGroups(docs: List<IndexedDocument>): List<List<IndexedDocument>> =
    docs.filter { it.hash.isNotBlank() }
        .groupBy { it.hash }
        .values
        .filter { it.size > 1 }
        .sortedByDescending { group -> (group.size - 1) * (group.firstOrNull()?.size ?: 0L) }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes o"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.FRANCE, "%.1f Ko", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.FRANCE, "%.1f Mo", mb)
    return String.format(Locale.FRANCE, "%.2f Go", mb / 1024.0)
}
