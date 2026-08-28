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

private val XPurple = Color(0xFF7047FF)
private val XBlue = Color(0xFF2678F3)
private val XGreen = Color(0xFF13A56E)
private val XOrange = Color(0xFFF28A30)
private val XPink = Color(0xFFD751B4)
private val XCyan = Color(0xFF05A9C4)
private val XRed = Color(0xFFE14E54)
private val XYellow = Color(0xFFF1B52A)
private val XText = Color(0xFF211D2A)
private val XMuted = Color(0xFF716A7C)
private val XBg = Color(0xFFF8F7FC)

private val ExpertColors = lightColorScheme(
    primary = XPurple,
    onPrimary = Color.White,
    secondary = XBlue,
    tertiary = XCyan,
    background = XBg,
    surface = Color.White,
    onBackground = XText,
    onSurface = XText,
    onSurfaceVariant = XMuted,
    error = XRed
)

class RangIaV3Activity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billing = BillingManager(applicationContext).also { it.start() }
        setContent {
            MaterialTheme(colorScheme = ExpertColors) {
                val paidPro by billing.isPro.collectAsState()
                val price by billing.price.collectAsState()
                val billingStatus by billing.status.collectAsState()
                ExpertRangIa(
                    vm = vm,
                    isPro = BuildConfig.DEBUG || paidPro,
                    price = price,
                    billingStatus = billingStatus,
                    requestAccess = ::requestAllFilesAccess,
                    buyPro = { billing.launchPurchase(this) },
                    restorePro = billing::restorePurchases,
                    dismissBilling = billing::consumeStatus
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

private enum class XTab { HOME, LIBRARY, DUPLICATES, SEARCH, SETTINGS }
private data class XNav(val tab: XTab, val icon: ImageVector, val label: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpertRangIa(
    vm: MainViewModel,
    isPro: Boolean,
    price: String?,
    billingStatus: String?,
    requestAccess: () -> Unit,
    buyPro: () -> Unit,
    restorePro: () -> Unit,
    dismissBilling: () -> Unit
) {
    val docs by vm.documents.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val fullAccess by vm.allFilesAccess.collectAsState()
    var tab by remember { mutableStateOf(XTab.HOME) }
    var showPro by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.acceptTree(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    val nav = listOf(
        XNav(XTab.HOME, Icons.Default.Home, "Accueil", XPurple),
        XNav(XTab.LIBRARY, Icons.Default.FolderCopy, "Bibliothèque", XBlue),
        XNav(XTab.DUPLICATES, Icons.Default.ContentCopy, "Doublons", XGreen),
        XNav(XTab.SEARCH, Icons.Default.Search, "Recherche", XOrange),
        XNav(XTab.SETTINGS, Icons.Default.Tune, "Réglages", XPink)
    )

    Scaffold(
        containerColor = XBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = XBg),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(XPurple, XBlue, XCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderSpecial, null, tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("RangIA", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                            Text("Classement documentaire intelligent", color = XMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    Surface(color = Color(0xFFEDE8FF), shape = RoundedCornerShape(13.dp)) {
                        Text("IA v3", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = XPurple, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = vm::scanNow, enabled = !busy) {
                        Icon(Icons.Default.Refresh, "Analyser", tint = if (busy) XMuted else XPurple)
                    }
                }
            )
        },
        bottomBar = { ExpertBottomBar(nav, tab) { tab = it } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                XTab.HOME -> ExpertHome(docs, fullAccess, busy, progress, requestAccess, vm::scanNow, vm::reclassifyAll, { tab = XTab.LIBRARY }, { tab = XTab.DUPLICATES })
                XTab.LIBRARY -> ExpertLibrary(docs, vm.aiCategories, vm::organize, vm::moveToTrash, vm::deletePermanently, vm::correctCategory)
                XTab.DUPLICATES -> ExpertDuplicates(docs, isPro, vm::cleanupDuplicates, vm::cleanupDuplicateGroup, vm::deleteDuplicateGroupPermanently, vm::emptyTrash) { showPro = true }
                XTab.SEARCH -> ExpertSearch(docs, vm.aiCategories, vm::organize, vm::moveToTrash, vm::deletePermanently, vm::correctCategory)
                XTab.SETTINGS -> ExpertSettings(vm, fullAccess, isPro, price, requestAccess, { folderPicker.launch(null) }, { showPro = true }, restorePro)
            }
        }
    }

    if (showPro) {
        AlertDialog(
            onDismissRequest = { showPro = false },
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = XYellow) },
            title = { Text("RangIA Pro", fontWeight = FontWeight.Black) },
            text = { Text("Scan du téléphone complet, rangement automatique et nettoyage avancé des doublons.${price?.let { " Achat unique : $it." } ?: ""}") },
            confirmButton = { Button(onClick = { showPro = false; buyPro() }) { Text("Débloquer Pro") } },
            dismissButton = { TextButton(onClick = { showPro = false }) { Text("Plus tard") } }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text("RangIA", fontWeight = FontWeight.Black) },
            text = { Text(it) },
            confirmButton = { Button(onClick = vm::dismissMessage) { Text("OK") } }
        )
    }

    billingStatus?.let {
        AlertDialog(
            onDismissRequest = dismissBilling,
            title = { Text("RangIA Pro", fontWeight = FontWeight.Black) },
            text = { Text(it) },
            confirmButton = { Button(onClick = dismissBilling) { Text("OK") } }
        )
    }
}

@Composable
private fun ExpertBottomBar(nav: List<XNav>, selected: XTab, onSelect: (XTab) -> Unit) {
    Surface(color = Color.White, shadowElevation = 18.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            nav.forEach { item ->
                val active = item.tab == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(if (active) item.color.copy(alpha = .11f) else Color.Transparent)
                        .clickable { onSelect(item.tab) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        Modifier.size(if (active) 38.dp else 34.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) item.color else item.color.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, item.label, tint = if (active) Color.White else item.color, modifier = Modifier.size(20.dp))
                    }
                    Text(item.label, color = if (active) item.color else XMuted, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ExpertHome(
    docs: List<IndexedDocument>,
    fullAccess: Boolean,
    busy: Boolean,
    progress: String,
    requestAccess: () -> Unit,
    scan: () -> Unit,
    reclassify: () -> Unit,
    openLibrary: () -> Unit,
    openDuplicates: () -> Unit
) {
    val review = docs.count { it.needsReview }
    val semantic = docs.count { !it.needsReview && isDocumentFamily(it.categoryPath) }
    val duplicates = duplicateGroupsX(docs).sumOf { (it.size - 1).coerceAtLeast(0) }
    val families = familyCountsX(docs).entries.sortedByDescending { it.value }.take(8)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF6E49FF), Color(0xFF2D7DF4), Color(0xFF08A8C5)))).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Tes fichiers, classés avec prudence", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("RangIA n’invente plus une catégorie : les documents incertains passent dans À vérifier.", color = Color.White.copy(alpha = .88f))
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(alpha = .22f))
                        Text(progress.ifBlank { "Analyse en cours…" }, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(
                            onClick = if (fullAccess) scan else requestAccess,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = XPurple),
                            shape = RoundedCornerShape(17.dp)
                        ) {
                            Icon(if (fullAccess) Icons.Default.AutoAwesome else Icons.Default.Security, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (fullAccess) "Analyser le téléphone" else "Autoriser l’accès", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpertStat("Fichiers", docs.size, Icons.Default.Inventory2, XPurple, Modifier.weight(1f))
                ExpertStat("Classés", semantic, Icons.Default.TaskAlt, XGreen, Modifier.weight(1f))
                ExpertStat("À vérifier", review, Icons.Default.Rule, XOrange, Modifier.weight(1f))
                ExpertStat("Doublons", duplicates, Icons.Default.ContentCopy, XRed, Modifier.weight(1f))
            }
        }
        if (review > 0) item {
            ExpertActionCard(Icons.Default.WarningAmber, "$review document(s) à vérifier", "Aucune catégorie risquée n’a été forcée", XOrange, openLibrary)
        }
        if (duplicates > 0) item {
            ExpertActionCard(Icons.Default.ContentCopy, "$duplicates copie(s) en trop", "Ouvre Doublons pour les supprimer en une fois", XGreen, openDuplicates)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = reclassify, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.ModelTraining, null); Spacer(Modifier.width(5.dp)); Text("Reclasser tout")
                }
                Button(onClick = openLibrary, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("Bibliothèque")
                }
            }
        }
        item { SectionX("Organisation", "Grandes familles réellement détectées") }
        items(families) { entry -> FamilyRowX(entry.key, entry.value) }
    }
}

@Composable
private fun ExpertStat(label: String, value: Int, icon: ImageVector, color: Color, modifier: Modifier) {
    ElevatedCard(modifier, shape = RoundedCornerShape(19.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            IconBubbleX(icon, color, 31)
            Text(value.toString(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(label, color = XMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ExpertActionCard(icon: ImageVector, title: String, subtitle: String, color: Color, click: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = color.copy(alpha = .07f))) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleX(icon, color, 44); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, color = XMuted, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null, tint = color)
        }
    }
}

private enum class LibraryViewX { CATEGORIES, FILES }
private enum class SortX { RECENT, NAME, SIZE }

@Composable
private fun ExpertLibrary(
    docs: List<IndexedDocument>,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    delete: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var view by remember { mutableStateOf(LibraryViewX.CATEGORIES) }
    var family by remember { mutableStateOf<String?>(null) }
    var onlyReview by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortX.RECENT) }
    val families = remember(docs) { familyCountsX(docs).keys.sortedBy { prettyFamilyX(it) } }

    val filtered = remember(docs, family, onlyReview, sort) {
        var base = docs.asSequence()
        if (onlyReview) base = base.filter { it.needsReview }
        else if (family != null) base = base.filter { it.categoryPath.substringBefore('/') == family }
        val list = base.toList()
        when (sort) {
            SortX.RECENT -> list.sortedByDescending { it.modifiedAt }
            SortX.NAME -> list.sortedBy { it.originalName.lowercase(Locale.FRENCH) }
            SortX.SIZE -> list.sortedByDescending { it.size }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bibliothèque", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("${docs.size} fichiers · ${docs.count { it.needsReview }} à vérifier", color = XMuted)
            }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), tonalElevation = 2.dp) {
                Row(Modifier.padding(4.dp)) {
                    ViewButtonX(Icons.Default.Category, view == LibraryViewX.CATEGORIES, XBlue) { view = LibraryViewX.CATEGORIES }
                    ViewButtonX(Icons.Default.ViewList, view == LibraryViewX.FILES, XPurple) { view = LibraryViewX.FILES }
                }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item { FamilyChipX("Tout", docs.size, XPurple, family == null && !onlyReview) { family = null; onlyReview = false } }
            val reviewCount = docs.count { it.needsReview }
            if (reviewCount > 0) item { FamilyChipX("À vérifier", reviewCount, XOrange, onlyReview) { onlyReview = true; family = null; view = LibraryViewX.FILES } }
            items(families) { f ->
                val color = visualX(f).color
                FamilyChipX(prettyFamilyX(f), docs.count { it.categoryPath.substringBefore('/') == f }, color, family == f && !onlyReview) {
                    family = f; onlyReview = false; view = LibraryViewX.FILES
                }
            }
        }

        if (view == LibraryViewX.CATEGORIES && !onlyReview) {
            LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 25.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val shown = if (family == null) families else listOfNotNull(family)
                items(shown) { f ->
                    val fd = docs.filter { it.categoryPath.substringBefore('/') == f }
                    CategoryCardX(f, fd) { family = f; view = LibraryViewX.FILES }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { SortChipX("Récent", Icons.Default.Schedule, sort == SortX.RECENT) { sort = SortX.RECENT } }
                item { SortChipX("Nom", Icons.Default.SortByAlpha, sort == SortX.NAME) { sort = SortX.NAME } }
                item { SortChipX("Taille", Icons.Default.DataUsage, sort == SortX.SIZE) { sort = SortX.SIZE } }
            }
            LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 25.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered, key = { it.uri }) { doc -> ExpertFileCard(doc, categories, organize, trash, delete, correct) }
            }
        }
    }
}

@Composable
private fun ViewButtonX(icon: ImageVector, active: Boolean, color: Color, click: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (active) color else Color.Transparent).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (active) Color.White else color)
    }
}

@Composable
private fun FamilyChipX(label: String, count: Int, color: Color, selected: Boolean, click: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = click,
        label = { Text("$label  $count", fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White, containerColor = Color.White)
    )
}

@Composable
private fun SortChipX(label: String, icon: ImageVector, active: Boolean, click: () -> Unit) {
    FilterChip(selected = active, onClick = click, label = { Text(label) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(17.dp)) })
}

@Composable
private fun CategoryCardX(family: String, docs: List<IndexedDocument>, click: () -> Unit) {
    val visual = visualX(family)
    val subs = docs.groupingBy { it.categoryPath }.eachCount().entries.sortedByDescending { it.value }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubbleX(visual.icon, visual.color, 46); Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) { Text(prettyFamilyX(family), fontWeight = FontWeight.Black); Text("${docs.size} fichier(s) · ${subs.size} catégorie(s)", color = XMuted, style = MaterialTheme.typography.bodySmall) }
                Icon(Icons.Default.ChevronRight, null, tint = visual.color)
            }
            subs.take(5).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(visual.color)); Spacer(Modifier.width(8.dp))
                    Text(prettySegmentX(s.key.substringAfterLast('/')), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(s.value.toString(), color = visual.color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExpertFileCard(
    doc: IndexedDocument,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    delete: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var expanded by remember(doc.uri) { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val family = doc.categoryPath.substringBefore('/')
    val visual = visualX(family)
    val confidenceColor = when {
        doc.needsReview -> XOrange
        doc.confidence >= .95f -> XGreen
        doc.confidence >= .85f -> XBlue
        else -> XOrange
    }

    ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(21.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewX(doc, visual.color, Modifier.size(72.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.originalName, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(prettyPathX(doc.categoryPath), color = if (doc.needsReview) XOrange else visual.color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        TinyX(formatBytesX(doc.size), XMuted)
                        if (doc.extractedText.isNotBlank()) TinyX("OCR", XPurple)
                        TinyX(if (doc.needsReview) "À vérifier" else "IA ${(doc.confidence * 100).toInt()}%", confidenceColor)
                        if (doc.duplicate) TinyX("Doublon", XRed)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = visual.color)
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFECE8F1))
                Text("Emplacement : ${doc.relativePath.ifBlank { "Dossier autorisé" }}", color = XMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doc.classificationEvidence.isNotEmpty()) Text("Pourquoi : ${doc.classificationEvidence.joinToString(", ")}", color = confidenceColor, style = MaterialTheme.typography.bodySmall)
                doc.amount?.let { Text("Montant : ${DecimalFormat("0.00").format(it)} €", style = MaterialTheme.typography.bodySmall) }
                doc.detectedDate?.let { Text("Date : $it", style = MaterialTheme.typography.bodySmall) }
                if (doc.extractedText.isNotBlank()) {
                    Surface(color = Color(0xFFF6F4FA), shape = RoundedCornerShape(13.dp)) {
                        Text(doc.extractedText.take(420), Modifier.padding(10.dp), color = XMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { FileOpenHelper.open(context, doc) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Ouvrir")
                    }
                    FilledTonalButton(onClick = { if (!doc.needsReview) organize(doc) }, enabled = !doc.needsReview, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Ranger")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Category, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Reclasser")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { c -> DropdownMenuItem(text = { Text(prettyPathX(c)) }, onClick = { categoryMenu = false; correct(doc, c) }) }
                        }
                    }
                    IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Default.DeleteOutline, "Corbeille", tint = XOrange) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteForever, "Supprimer", tint = XRed) }
                }
            }
        }
    }

    if (confirmTrash) AlertDialog(
        onDismissRequest = { confirmTrash = false },
        title = { Text("Mettre à la corbeille ?", fontWeight = FontWeight.Black) },
        text = { Text(doc.originalName) },
        confirmButton = { Button(onClick = { confirmTrash = false; trash(doc) }) { Text("Corbeille") } },
        dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("Annuler") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Default.Warning, null, tint = XRed) },
        title = { Text("Supprimer définitivement ?", fontWeight = FontWeight.Black) },
        text = { Text("${doc.originalName}\nCette action ne pourra pas être annulée.") },
        confirmButton = { Button(onClick = { confirmDelete = false; delete(doc) }, colors = ButtonDefaults.buttonColors(containerColor = XRed)) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
    )
}

@Composable
private fun PreviewX(doc: IndexedDocument, accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, doc.uri, doc.modifiedAt, doc.size) {
        value = withContext(Dispatchers.IO) { FilePreviewLoader.load(context, doc, 260) }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), doc.originalName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            Icon(fallbackIconX(doc), null, tint = accent, modifier = Modifier.size(32.dp))
            Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), color = accent, shape = RoundedCornerShape(7.dp)) {
                Text(doc.originalName.substringAfterLast('.', "FILE").take(4).uppercase(Locale.ROOT), Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
        if (doc.duplicate) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(XRed), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ExpertDuplicates(
    docs: List<IndexedDocument>,
    isPro: Boolean,
    cleanupAll: () -> Unit,
    cleanupGroup: (String) -> Unit,
    deleteGroup: (String) -> Unit,
    emptyTrash: () -> Unit,
    showPro: () -> Unit
) {
    val groups = remember(docs) { duplicateGroupsX(docs) }
    val extras = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
    val bytes = groups.sumOf { (it.size - 1).coerceAtLeast(0) * (it.firstOrNull()?.size ?: 0L) }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmPermanentHash by remember { mutableStateOf<String?>(null) }
    var emptyConfirm by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionX("Doublons", "Copies strictement identiques vérifiées par SHA-256") }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF0FA66F), Color(0xFF0AB2A6)))).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$extras copie(s) en trop", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("${formatBytesX(bytes)} potentiellement récupérables", color = Color.White.copy(alpha = .9f))
                    Button(onClick = { if (isPro) confirmAll = true else showPro() }, enabled = extras > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = XGreen), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(7.dp)); Text("Supprimer toutes les copies en trop", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubbleX(Icons.Default.DeleteOutline, XRed, 42); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("Corbeille RangIA", fontWeight = FontWeight.Black); Text("Les suppressions normales restent récupérables jusqu’au vidage", color = XMuted, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { emptyConfirm = true }) { Text("Vider", color = XRed, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { SectionX("Groupes", if (groups.isEmpty()) "Aucun doublon exact" else "${groups.size} groupe(s)") }
        items(groups, key = { it.first().hash }) { group ->
            DuplicateCardX(group, { cleanupGroup(group.first().hash) }, { confirmPermanentHash = group.first().hash })
        }
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Supprimer toutes les copies en trop ?", fontWeight = FontWeight.Black) },
        text = { Text("RangIA conserve automatiquement une copie de chaque fichier et envoie les autres copies accessibles dans sa corbeille.") },
        confirmButton = { Button(onClick = { confirmAll = false; cleanupAll() }) { Text("Supprimer les copies") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } }
    )

    confirmPermanentHash?.let { hash ->
        AlertDialog(
            onDismissRequest = { confirmPermanentHash = null },
            icon = { Icon(Icons.Default.Warning, null, tint = XRed) },
            title = { Text("Supprimer les copies définitivement ?", fontWeight = FontWeight.Black) },
            text = { Text("Une copie sera conservée. Toutes les autres copies accessibles de ce groupe seront supprimées sans passer par la corbeille.") },
            confirmButton = { Button(onClick = { confirmPermanentHash = null; deleteGroup(hash) }, colors = ButtonDefaults.buttonColors(containerColor = XRed)) { Text("Supprimer définitivement") } },
            dismissButton = { TextButton(onClick = { confirmPermanentHash = null }) { Text("Annuler") } }
        )
    }

    if (emptyConfirm) AlertDialog(
        onDismissRequest = { emptyConfirm = false },
        title = { Text("Vider la corbeille ?", fontWeight = FontWeight.Black) },
        text = { Text("Les fichiers de la corbeille RangIA seront supprimés définitivement.") },
        confirmButton = { Button(onClick = { emptyConfirm = false; emptyTrash() }, colors = ButtonDefaults.buttonColors(containerColor = XRed)) { Text("Vider") } },
        dismissButton = { TextButton(onClick = { emptyConfirm = false }) { Text("Annuler") } }
    )
}

@Composable
private fun DuplicateCardX(group: List<IndexedDocument>, cleanup: () -> Unit, permanent: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val keeper = remember(group) { chooseKeeperX(group) }
    val extras = group.size - 1
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                PreviewX(group.first(), XGreen, Modifier.size(64.dp)); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text("${group.size} copies identiques", fontWeight = FontWeight.Black); Text("${formatBytesX(group.first().size)} chacune", color = XMuted, style = MaterialTheme.typography.bodySmall) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = XGreen)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = cleanup, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Supprimer $extras copie(s)")
                }
                IconButton(onClick = permanent) { Icon(Icons.Default.DeleteForever, "Définitif", tint = XRed) }
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFECE8F1))
                group.forEach { doc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PreviewX(doc, if (doc.uri == keeper.uri) XGreen else XBlue, Modifier.size(46.dp)); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(doc.relativePath, color = XMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (doc.uri == keeper.uri) TinyX("Conserver", XGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpertSearch(
    docs: List<IndexedDocument>,
    categories: List<String>,
    organize: (IndexedDocument) -> Unit,
    trash: (IndexedDocument) -> Unit,
    delete: (IndexedDocument) -> Unit,
    correct: (IndexedDocument, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, docs) { SearchEngine.search(query, docs) }
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 8.dp)) {
        SectionX("Recherche", "Nom, contenu OCR, catégorie, montant ou date")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = XOrange) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Effacer") } },
            placeholder = { Text("Ex. CAP, facture URSSAF, contrôle technique…") }
        )
        Spacer(Modifier.height(8.dp))
        Text("${results.size} résultat(s)", color = XOrange, fontWeight = FontWeight.Bold)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results, key = { it.uri }) { ExpertFileCard(it, categories, organize, trash, delete, correct) }
        }
    }
}

@Composable
private fun ExpertSettings(
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { SectionX("Réglages", "Contrôle complet de RangIA") }
        item { SettingX(Icons.Default.Security, if (fullAccess) "Accès téléphone actif" else "Accès téléphone désactivé", if (fullAccess) "Stockage partagé accessible" else "Nécessaire pour le scan complet", if (fullAccess) XGreen else XOrange) { if (!fullAccess) Button(onClick = requestAccess) { Text("Autoriser") } } }
        item { SettingX(Icons.Default.FolderOpen, "Dossier manuel", "Analyser un emplacement précis", XBlue) { OutlinedButton(onClick = pickFolder) { Text("Choisir") } } }
        item { SettingX(Icons.Default.Refresh, "Analyse automatique", "Index local mis à jour périodiquement", XPurple) { Switch(checked = autoScan, onCheckedChange = { autoScan = it; vm.automaticScan = it }) } }
        item { SettingX(Icons.Default.PhoneAndroid, "Téléphone complet", "Analyse tout le stockage partagé accessible", XCyan) { Switch(checked = wholePhone && isPro, onCheckedChange = { v -> if (isPro) { wholePhone = v; vm.wholePhoneMode = v } else showPro() }) } }
        item { SettingX(Icons.Default.Psychology, "Moteur IA v3", "${vm.learnedExamplesCount} correction(s) personnelle(s) apprises", XGreen) { TextButton(onClick = vm::reclassifyAll) { Text("Reclasser tout") } } }
        item { SettingX(Icons.Default.DeleteSweep, "Corbeille RangIA", "Suppression sécurisée avant effacement définitif", XRed) { TextButton(onClick = vm::emptyTrash) { Text("Vider", color = XRed) } } }
        item { SettingX(Icons.Default.WorkspacePremium, if (isPro) "RangIA Pro actif" else "RangIA Pro", if (isPro) "Toutes les fonctions avancées sont actives" else "Achat unique${price?.let { " · $it" } ?: ""}", XYellow) { if (isPro) TinyX("PRO", XYellow) else Button(onClick = showPro) { Text("Voir") } } }
        item { TextButton(onClick = restore, Modifier.fillMaxWidth()) { Text("Restaurer mes achats Google Play") } }
    }
}

@Composable
private fun SettingX(icon: ImageVector, title: String, subtitle: String, color: Color, trailing: @Composable () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(21.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleX(icon, color, 44); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, color = XMuted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.width(8.dp)); trailing()
        }
    }
}

@Composable
private fun SectionX(title: String, subtitle: String) {
    Column { Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = XMuted, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun FamilyRowX(family: String, count: Int) {
    val visual = visualX(family)
    ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleX(visual.icon, visual.color, 44); Spacer(Modifier.width(10.dp))
            Text(prettyFamilyX(family), Modifier.weight(1f), fontWeight = FontWeight.Black)
            Surface(color = visual.color.copy(alpha = .10f), shape = RoundedCornerShape(11.dp)) { Text(count.toString(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = visual.color, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun IconBubbleX(icon: ImageVector, color: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * .52f).dp))
    }
}

@Composable
private fun TinyX(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

private data class VisualX(val icon: ImageVector, val color: Color)

private fun visualX(family: String): VisualX = when (family.lowercase(Locale.FRENCH)) {
    "a_verifier" -> VisualX(Icons.Default.Rule, XOrange)
    "documents" -> VisualX(Icons.Default.Description, XBlue)
    "photos" -> VisualX(Icons.Default.Image, Color(0xFF9454D4))
    "vidéos", "videos" -> VisualX(Icons.Default.Movie, XRed)
    "audio" -> VisualX(Icons.Default.AudioFile, Color(0xFF8058D7))
    "entreprise" -> VisualX(Icons.Default.BusinessCenter, XPurple)
    "travail" -> VisualX(Icons.Default.Work, XBlue)
    "administratif" -> VisualX(Icons.Default.AccountBalance, XCyan)
    "sante" -> VisualX(Icons.Default.HealthAndSafety, XGreen)
    "etudes" -> VisualX(Icons.Default.School, Color(0xFF5575D8))
    "voiture" -> VisualX(Icons.Default.DirectionsCar, XOrange)
    "banque" -> VisualX(Icons.Default.AccountBalanceWallet, XGreen)
    "logement" -> VisualX(Icons.Default.HomeWork, Color(0xFFB16C45))
    "identite" -> VisualX(Icons.Default.Badge, XPink)
    "achats" -> VisualX(Icons.Default.ShoppingBag, Color(0xFFB96B31))
    "voyages" -> VisualX(Icons.Default.Flight, XCyan)
    "impots" -> VisualX(Icons.Default.ReceiptLong, XOrange)
    "notices" -> VisualX(Icons.Default.MenuBook, Color(0xFF6D58A6))
    "archives" -> VisualX(Icons.Default.Archive, Color(0xFF8A6849))
    "applications_apk" -> VisualX(Icons.Default.Android, XGreen)
    else -> VisualX(Icons.Default.Folder, XBlue)
}

private fun fallbackIconX(doc: IndexedDocument): ImageVector = when {
    doc.mimeType.startsWith("image/") -> Icons.Default.Image
    doc.mimeType.startsWith("video/") -> Icons.Default.Movie
    doc.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    doc.originalName.endsWith(".apk", true) -> Icons.Default.Android
    doc.originalName.endsWith(".zip", true) || doc.originalName.endsWith(".rar", true) || doc.originalName.endsWith(".7z", true) -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

private fun familyCountsX(docs: List<IndexedDocument>): Map<String, Int> = docs.groupingBy { it.categoryPath.substringBefore('/').ifBlank { "Autres" } }.eachCount()
private fun duplicateGroupsX(docs: List<IndexedDocument>): List<List<IndexedDocument>> = docs.filter { it.hash.isNotBlank() }.groupBy { it.hash }.values.filter { it.size > 1 }.sortedByDescending { it.firstOrNull()?.size ?: 0L }

private fun chooseKeeperX(group: List<IndexedDocument>): IndexedDocument = group.minWithOrNull(
    compareBy<IndexedDocument>(
        { if (it.relativePath.contains("Documents", true)) 0 else 1 },
        { if (it.relativePath.contains("DCIM", true)) 0 else 1 },
        { if (it.relativePath.contains("RangIA", true)) 0 else 1 },
        { -it.modifiedAt }
    )
) ?: group.first()

private fun isDocumentFamily(path: String): Boolean = path.substringBefore('/') in setOf(
    "Entreprise", "Travail", "Administratif", "Sante", "Etudes", "Identite", "Voiture", "Banque", "Impots", "Logement", "Achats", "Voyages", "Notices"
)

private fun prettyFamilyX(value: String): String = when (value) {
    "A_verifier" -> "À vérifier"
    "Sante" -> "Santé"
    "Etudes" -> "Études"
    "Identite" -> "Identité"
    "Impots" -> "Impôts"
    "Vidéos" -> "Vidéos"
    "Applications_APK" -> "Applications APK"
    else -> prettySegmentX(value)
}

private fun prettySegmentX(value: String): String {
    val raw = value.replace('_', ' ')
    return when (raw.lowercase(Locale.FRENCH)) {
        "controle technique" -> "Contrôle technique"
        "diplomes certificats" -> "Diplômes & certificats"
        "releves notes" -> "Relevés de notes"
        "releves" -> "Relevés"
        "etat civil" -> "État civil"
        "energie" -> "Énergie"
        "presentations" -> "Présentations"
        "carte identite" -> "Carte d’identité"
        "entretien reparation" -> "Entretien & réparation"
        "tickets recus" -> "Tickets & reçus"
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
    }
}

private fun prettyPathX(path: String): String = path.split('/').joinToString(" › ") { if (it == path.substringBefore('/')) prettyFamilyX(it) else prettySegmentX(it) }

private fun formatBytesX(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f Ko".format(Locale.FRENCH, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f Mo".format(Locale.FRENCH, mb)
    return "%.2f Go".format(Locale.FRENCH, mb / 1024.0)
}
