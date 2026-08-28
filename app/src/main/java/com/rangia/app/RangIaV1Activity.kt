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

private val V1Purple = Color(0xFF7650FF)
private val V1PurpleDark = Color(0xFF5234D6)
private val V1Blue = Color(0xFF3C82FF)
private val V1Cyan = Color(0xFF12B9D7)
private val V1Green = Color(0xFF15A66A)
private val V1Orange = Color(0xFFFF963D)
private val V1Red = Color(0xFFE45A5A)
private val V1Pink = Color(0xFFCE55B7)
private val V1Navy = Color(0xFF111A31)
private val V1Bg = Color(0xFFF8F7FC)
private val V1Text = Color(0xFF211E2B)
private val V1Muted = Color(0xFF736D7E)

private val V1Colors = lightColorScheme(
    primary = V1Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FF),
    onPrimaryContainer = Color(0xFF2D176F),
    secondary = V1Blue,
    onSecondary = Color.White,
    tertiary = V1Cyan,
    background = V1Bg,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EDF6),
    onBackground = V1Text,
    onSurface = V1Text,
    onSurfaceVariant = V1Muted,
    error = V1Red
)

class RangIaV1Activity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billing = BillingManager(applicationContext).also { it.start() }
        setContent {
            MaterialTheme(colorScheme = V1Colors) {
                RangIaCommercialApp(
                    vm = vm,
                    billing = billing,
                    activity = this,
                    requestAllFilesAccess = ::requestAllFilesAccess
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

private enum class V1Tab(val label: String, val icon: ImageVector, val accent: Color) {
    HOME("Accueil", Icons.Default.Home, V1Purple),
    FILES("Fichiers", Icons.Default.Folder, V1Blue),
    SEARCH("Recherche", Icons.Default.Search, V1Green),
    SETTINGS("Réglages", Icons.Default.Tune, V1Orange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangIaCommercialApp(
    vm: MainViewModel,
    billing: BillingManager,
    activity: ComponentActivity,
    requestAllFilesAccess: () -> Unit
) {
    val documents by vm.documents.collectAsState()
    val busy by vm.busy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val fullAccess by vm.allFilesAccess.collectAsState()
    val purchasedPro by billing.isPro.collectAsState()
    val price by billing.price.collectAsState()
    val billingStatus by billing.status.collectAsState()

    val isPro = BuildConfig.DEBUG || purchasedPro
    var tab by remember { mutableStateOf(V1Tab.HOME) }
    var showPro by remember { mutableStateOf(false) }

    LaunchedEffect(isPro) {
        if (!isPro && vm.wholePhoneMode) vm.wholePhoneMode = false
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.acceptTree(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { BrandTitle() },
                actions = {
                    if (isPro) {
                        Surface(
                            color = Color(0xFFFFF1C8),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFB77900), modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (BuildConfig.DEBUG) "PRO TEST" else "PRO", color = Color(0xFF8A5B00), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        TextButton(onClick = { showPro = true }) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFB77900))
                            Spacer(Modifier.width(4.dp))
                            Text("Pro", fontWeight = FontWeight.ExtraBold, color = Color(0xFF9B6500))
                        }
                    }
                    IconButton(
                        onClick = {
                            if (isPro || !vm.wholePhoneMode) vm.scanNow() else showPro = true
                        },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Refresh, "Analyser", tint = if (busy) V1Muted else V1Purple)
                    }
                }
            )
        },
        bottomBar = { RangBottomBar(tab) { tab = it } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                V1Tab.HOME -> HomeV1(
                    docs = documents,
                    fullAccess = fullAccess,
                    hasFolder = vm.selectedTreeUri != null,
                    busy = busy,
                    progress = progress,
                    isPro = isPro,
                    requestFullAccess = requestAllFilesAccess,
                    pickFolder = { folderPicker.launch(null) },
                    scan = vm::scanNow,
                    organizeAll = vm::organizeAllSafe,
                    openPro = { showPro = true }
                )
                V1Tab.FILES -> FilesV1(documents, vm::organize, vm.aiCategories, vm::correctCategory)
                V1Tab.SEARCH -> SearchV1(documents, vm::organize, vm.aiCategories, vm::correctCategory)
                V1Tab.SETTINGS -> SettingsV1(
                    vm = vm,
                    fullAccess = fullAccess,
                    isPro = isPro,
                    price = price,
                    requestAllFilesAccess = requestAllFilesAccess,
                    pickFolder = { folderPicker.launch(null) },
                    openPro = { showPro = true },
                    restore = billing::restorePurchases
                )
            }
        }
    }

    if (showPro) {
        ProDialog(
            price = price,
            onDismiss = { showPro = false },
            onBuy = {
                showPro = false
                billing.launchPurchase(activity)
            },
            onRestore = {
                showPro = false
                billing.restorePurchases()
            }
        )
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            confirmButton = { Button(onClick = vm::dismissMessage) { Text("OK") } },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = V1Purple) },
            title = { Text("RangIA", fontWeight = FontWeight.ExtraBold) },
            text = { Text(message!!) }
        )
    } else if (billingStatus != null) {
        AlertDialog(
            onDismissRequest = billing::consumeStatus,
            confirmButton = { Button(onClick = billing::consumeStatus) { Text("OK") } },
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFB77900)) },
            title = { Text("RangIA Pro", fontWeight = FontWeight.ExtraBold) },
            text = { Text(billingStatus!!) }
        )
    }
}

@Composable
private fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(V1Purple, V1Blue, V1Cyan))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.FolderSpecial, null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text("RangIA", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("Range. Organise. Retrouve.", color = V1Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RangBottomBar(current: V1Tab, onSelect: (V1Tab) -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 18.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            V1Tab.entries.forEach { item ->
                val selected = current == item
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { onSelect(item) },
                    color = if (selected) item.accent.copy(alpha = 0.11f) else Color.Transparent,
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Column(
                        Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) item.accent else item.accent.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, item.label, tint = if (selected) Color.White else item.accent, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            item.label,
                            color = if (selected) item.accent else V1Muted,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeV1(
    docs: List<IndexedDocument>,
    fullAccess: Boolean,
    hasFolder: Boolean,
    busy: Boolean,
    progress: String,
    isPro: Boolean,
    requestFullAccess: () -> Unit,
    pickFolder: () -> Unit,
    scan: () -> Unit,
    organizeAll: () -> Unit,
    openPro: () -> Unit
) {
    val classified = docs.count { it.categoryPath !in listOf("Autres", "Fichiers/Autres") }
    val duplicates = docs.count { it.duplicate }
    val groups = docs.groupingBy { it.categoryPath }.eachCount().entries.sortedByDescending { it.value }.take(10)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            HeroV1(
                busy = busy,
                progress = progress,
                isPro = isPro,
                fullAccess = fullAccess,
                hasFolder = hasFolder,
                scan = scan,
                requestFullAccess = requestFullAccess,
                pickFolder = pickFolder,
                openPro = openPro
            )
        }

        if (!isPro) item { FreeToProCard(openPro) }
        if (isPro && !fullAccess) item { FullAccessCard(requestFullAccess, pickFolder) }
        if (isPro && fullAccess) item { FullAccessOkCard() }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatV1("Analysés", docs.size.toString(), Icons.Default.Inventory2, V1Purple, Modifier.weight(1f))
                StatV1("Classés", classified.toString(), Icons.Default.AutoAwesome, V1Green, Modifier.weight(1f))
                StatV1("Doublons", duplicates.toString(), Icons.Default.ContentCopy, V1Orange, Modifier.weight(1f))
            }
        }

        if (isPro && (fullAccess || hasFolder) && docs.isNotEmpty()) {
            item {
                ElevatedCard(shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubbleV1(Icons.Default.AutoFixHigh, V1Purple)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Rangement automatique", fontWeight = FontWeight.ExtraBold)
                                Text("Uniquement les fichiers que RangIA juge sûrs", color = V1Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            ProMiniBadge()
                        }
                        Button(
                            onClick = organizeAll,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.DriveFileMove, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Ranger les fichiers sûrs", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Catégories détectées", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(if (docs.isEmpty()) "Lance une analyse pour commencer" else "${groups.size} catégories principales", color = V1Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (groups.isEmpty()) item { EmptyV1() }
        else items(groups) { (name, count) -> CategoryV1(name, count) }
    }
}

@Composable
private fun HeroV1(
    busy: Boolean,
    progress: String,
    isPro: Boolean,
    fullAccess: Boolean,
    hasFolder: Boolean,
    scan: () -> Unit,
    requestFullAccess: () -> Unit,
    pickFolder: () -> Unit,
    openPro: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6F46FF), Color(0xFF347DFF), Color(0xFF11B5D5))))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(29.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ton téléphone, enfin rangé", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                    Text("IA locale · OCR · recherche instantanée", color = Color.White.copy(alpha = 0.84f), style = MaterialTheme.typography.bodyMedium)
                }
                if (isPro) ProMiniBadge(inverted = true)
            }

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Text(if (progress.isBlank()) "Analyse en cours…" else progress, color = Color.White, fontWeight = FontWeight.SemiBold)
            } else {
                val actionLabel = when {
                    isPro && fullAccess -> "Analyser tout mon téléphone"
                    isPro -> "Autoriser le téléphone complet"
                    hasFolder -> "Analyser mon dossier"
                    else -> "Choisir un dossier gratuitement"
                }
                Button(
                    onClick = when {
                        isPro && fullAccess -> scan
                        isPro -> requestFullAccess
                        else -> pickFolder
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = V1PurpleDark),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(if (isPro) Icons.Default.Security else Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel, fontWeight = FontWeight.Black)
                }
                if (!isPro) {
                    TextButton(onClick = openPro, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFE69A))
                        Spacer(Modifier.width(5.dp))
                        Text("Débloquer le scan téléphone complet", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeToProCard(openPro: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF8E8))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleV1(Icons.Default.WorkspacePremium, Color(0xFFB77900))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("RangIA Pro", fontWeight = FontWeight.Black, color = Color(0xFF805400))
                Text("Téléphone complet, rangement auto et analyse en arrière-plan", color = Color(0xFF806A3B), style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = openPro) { Text("Voir") }
        }
    }
}

@Composable
private fun FullAccessCard(request: () -> Unit, pick: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF5E5))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubbleV1(Icons.Default.Security, V1Orange)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Autorisation Pro à activer", fontWeight = FontWeight.Black)
                    Text("Nécessaire pour parcourir le stockage partagé", color = V1Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = request, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("Autoriser l’accès complet", fontWeight = FontWeight.Bold) }
            TextButton(onClick = pick, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ou choisir seulement un dossier") }
        }
    }
}

@Composable
private fun FullAccessOkCard() {
    ElevatedCard(shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFEAF9F1))) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleV1(Icons.Default.VerifiedUser, V1Green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Téléphone complet activé", fontWeight = FontWeight.Black, color = Color(0xFF0D7048))
                Text("Les zones privées des autres applications restent protégées", color = Color(0xFF39745D), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.CheckCircle, null, tint = V1Green)
        }
    }
}

@Composable
private fun StatV1(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBubbleV1(icon, accent, 34)
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = V1Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryV1(name: String, count: Int) {
    val visual = visualFor(name)
    ElevatedCard(shape = RoundedCornerShape(19.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubbleV1(visual.icon, visual.color, 42)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(name.replace('_', ' ').replace("/", " › "), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count fichier(s)", color = V1Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = visual.color)
        }
    }
}

@Composable
private fun EmptyV1() {
    ElevatedCard(shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoAwesome, null, tint = V1Purple, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("Prêt à analyser", fontWeight = FontWeight.Black)
            Text("Tes catégories apparaîtront ici.", color = V1Muted)
        }
    }
}

@Composable
private fun FilesV1(
    docs: List<IndexedDocument>,
    organize: (IndexedDocument) -> Unit,
    aiCategories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var filter by remember { mutableStateOf<String?>(null) }
    val categories = remember(docs) { docs.map { it.categoryPath }.distinct().sorted() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Mes fichiers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${docs.size} élément(s) indexé(s)", color = V1Muted)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("Tout") },
                    leadingIcon = { Icon(Icons.Default.GridView, null, modifier = Modifier.size(18.dp)) }
                )
            }
            items(categories) { c ->
                val v = visualFor(c)
                FilterChip(
                    selected = filter == c,
                    onClick = { filter = c },
                    label = { Text(c.substringAfterLast('/').replace('_', ' '), maxLines = 1) },
                    leadingIcon = { Icon(v.icon, null, tint = v.color, modifier = Modifier.size(18.dp)) }
                )
            }
        }
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { EmptyV1() }
        } else {
            val shown = if (filter == null) docs else docs.filter { it.categoryPath == filter }
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.uri }) { doc -> DocCardV1(doc, organize, aiCategories, correct) }
            }
        }
    }
}

@Composable
private fun SearchV1(
    docs: List<IndexedDocument>,
    organize: (IndexedDocument) -> Unit,
    categories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, docs) { SearchEngine.search(query, docs) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Recherche intelligente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("Nom, contenu OCR, date, organisme ou montant", color = V1Muted)
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = V1Green) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Effacer") } },
            placeholder = { Text("Ex. facture mars 100 €, contrôle technique…") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = V1Green, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(9.dp))
        Text("${results.size} résultat(s)", color = V1Green, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(results, key = { it.uri }) { DocCardV1(it, organize, categories, correct) }
        }
    }
}

@Composable
private fun DocCardV1(
    doc: IndexedDocument,
    organize: (IndexedDocument) -> Unit,
    categories: List<String>,
    correct: (IndexedDocument, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val visual = visualFor(doc.categoryPath)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubbleV1(iconForV1(doc), visual.color, 44)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.originalName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(doc.categoryPath.replace('_', ' ').replace("/", " › "), color = visual.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                if (doc.duplicate) {
                    Surface(color = Color(0xFFFFE8E8), shape = RoundedCornerShape(10.dp)) {
                        Text("Doublon", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = V1Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (doc.suggestedName != doc.originalName) {
                Surface(color = Color(0xFFF4F1FA), shape = RoundedCornerShape(11.dp)) {
                    Text("→ ${doc.suggestedName}", Modifier.padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTagV1("${(doc.confidence * 100).toInt()} %", visual.color)
                doc.amount?.let { MiniTagV1("${DecimalFormat("0.00").format(it)} €", V1Green) }
                doc.detectedDate?.let { MiniTagV1(it, V1Blue) }
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFFEDE9F3))
                if (doc.relativePath.isNotBlank()) Text("Emplacement : ${doc.relativePath}", color = V1Muted, style = MaterialTheme.typography.bodySmall)
                doc.organization?.let { Text("Organisation : $it", style = MaterialTheme.typography.bodySmall) }
                if (doc.extractedText.isNotBlank()) {
                    Text("Contenu détecté", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                    Text(doc.extractedText.take(650), color = V1Muted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { organize(doc) }, enabled = !doc.duplicate, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.DriveFileMove, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Ranger")
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Edit, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Corriger IA")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    leadingIcon = { val v = visualFor(c); Icon(v.icon, null, tint = v.color) },
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
private fun SettingsV1(
    vm: MainViewModel,
    fullAccess: Boolean,
    isPro: Boolean,
    price: String?,
    requestAllFilesAccess: () -> Unit,
    pickFolder: () -> Unit,
    openPro: () -> Unit,
    restore: () -> Unit
) {
    var automatic by remember { mutableStateOf(vm.automaticScan) }
    var wholePhone by remember { mutableStateOf(vm.wholePhoneMode) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Personnalise RangIA et contrôle tes données", color = V1Muted)
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = if (isPro) Color(0xFFFFF7DE) else V1Navy)
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubbleV1(Icons.Default.WorkspacePremium, if (isPro) Color(0xFFB77900) else Color(0xFFFFD45A))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("RangIA Pro", color = if (isPro) V1Text else Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (isPro) "Débloqué sur cet appareil" else "Achat unique · pas d’abonnement",
                                color = if (isPro) Color(0xFF806A3B) else Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (isPro) Icon(Icons.Default.Verified, null, tint = V1Green)
                    }
                    if (!isPro) {
                        Text("Scan téléphone complet, rangement automatique, analyses périodiques et fonctions Pro futures.", color = Color.White.copy(alpha = 0.88f))
                        Button(
                            onClick = openPro,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD45A), contentColor = Color(0xFF4F3500)),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text("Débloquer Pro${price?.let { " · $it" } ?: ""}", fontWeight = FontWeight.Black) }
                        TextButton(onClick = restore, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Restaurer mon achat", color = Color.White) }
                    }
                }
            }
        }

        item {
            SettingsCardV1(Icons.Default.Security, V1Purple, "Accès au stockage") {
                StatusV1("Accès téléphone complet", fullAccess)
                Spacer(Modifier.height(8.dp))
                if (isPro) {
                    Button(onClick = requestAllFilesAccess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(if (fullAccess) "Ouvrir le réglage Android" else "Autoriser le téléphone complet")
                    }
                } else {
                    OutlinedButton(onClick = openPro, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Fonction Pro")
                    }
                }
                Text("Android garde privées certaines zones système et les données internes des autres applications.", color = V1Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SettingsCardV1(Icons.Default.PhoneAndroid, V1Blue, "Téléphone complet") {
                SwitchSettingV1(
                    title = "Scanner le stockage partagé",
                    subtitle = if (isPro) "Parcourt automatiquement les fichiers accessibles." else "Disponible avec RangIA Pro.",
                    checked = isPro && wholePhone,
                    enabled = isPro
                ) {
                    wholePhone = it
                    vm.wholePhoneMode = it
                }
                if (!isPro) TextButton(onClick = openPro) { Text("Découvrir RangIA Pro") }
            }
        }

        item {
            SettingsCardV1(Icons.Default.FolderOpen, V1Orange, "Dossier manuel") {
                Text(vm.selectedTreeUri ?: "Aucun dossier sélectionné", maxLines = 2, overflow = TextOverflow.Ellipsis, color = V1Muted)
                Spacer(Modifier.height(7.dp))
                OutlinedButton(onClick = pickFolder, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.CreateNewFolder, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Choisir un dossier")
                }
            }
        }

        item {
            SettingsCardV1(Icons.Default.Schedule, V1Cyan, "Analyse automatique") {
                SwitchSettingV1(
                    title = "Réanalyse périodique",
                    subtitle = if (isPro) "Vérifie régulièrement les nouveaux fichiers." else "Fonction Pro.",
                    checked = isPro && automatic,
                    enabled = isPro
                ) {
                    automatic = it
                    vm.automaticScan = it
                }
            }
        }

        item {
            SettingsCardV1(Icons.Default.Psychology, V1Pink, "IA locale") {
                Text("${vm.learnedExamplesCount} correction(s) apprise(s)", fontWeight = FontWeight.ExtraBold)
                Text("Le classificateur fonctionne hors ligne et s’adapte lorsque tu corriges une catégorie.", color = V1Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(7.dp))
                OutlinedButton(onClick = vm::resetAiLearning, enabled = vm.learnedExamplesCount > 0, shape = RoundedCornerShape(14.dp)) {
                    Text("Réinitialiser l’apprentissage")
                }
            }
        }

        item {
            SettingsCardV1(Icons.Default.Lock, V1Green, "Confidentialité") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = V1Green)
                    Spacer(Modifier.width(7.dp))
                    Text("Traitement local", fontWeight = FontWeight.Black, color = Color(0xFF0D7048))
                }
                Text("OCR, classement et apprentissage restent sur l’appareil. Aucun document n’est envoyé à une API d’IA distante.", color = V1Muted, style = MaterialTheme.typography.bodySmall)
                Text("Version 1.0.0 · API 36", color = V1Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProDialog(price: String?, onDismiss: () -> Unit, onBuy: () -> Unit, onRestore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFB77900), modifier = Modifier.size(38.dp)) },
        title = { Text("Passe à RangIA Pro", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProBenefit(Icons.Default.PhoneAndroid, "Analyse tout le stockage partagé")
                ProBenefit(Icons.Default.AutoFixHigh, "Range automatiquement les fichiers sûrs")
                ProBenefit(Icons.Default.Schedule, "Réanalyse périodiquement les nouveautés")
                ProBenefit(Icons.Default.WorkspacePremium, "Débloque les futures fonctions Pro")
                Spacer(Modifier.height(4.dp))
                Surface(color = Color(0xFFFFF4D2), shape = RoundedCornerShape(13.dp)) {
                    Text(
                        if (price != null) "$price · achat unique · sans abonnement" else "Achat unique · sans abonnement",
                        Modifier.fillMaxWidth().padding(11.dp),
                        color = Color(0xFF805400),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onBuy) { Text("Débloquer Pro", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRestore) { Text("Restaurer") }
                TextButton(onClick = onDismiss) { Text("Plus tard") }
            }
        }
    )
}

@Composable
private fun ProBenefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = V1Purple, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProMiniBadge(inverted: Boolean = false) {
    Surface(color = if (inverted) Color.White.copy(alpha = 0.18f) else Color(0xFFFFF1C8), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WorkspacePremium, null, tint = if (inverted) Color.White else Color(0xFFB77900), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("PRO", color = if (inverted) Color.White else Color(0xFF8A5B00), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsCardV1(icon: ImageVector, accent: Color, title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(23.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubbleV1(icon, accent, 40)
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun SwitchSettingV1(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = if (enabled) V1Text else V1Muted)
            Text(subtitle, color = V1Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = V1Purple)
        )
    }
}

@Composable
private fun StatusV1(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (ok) V1Green else V1Red)
        Spacer(Modifier.width(7.dp))
        Text("$label : ${if (ok) "autorisé" else "non autorisé"}", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconBubbleV1(icon: ImageVector, color: Color, size: Int = 42) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.53f).dp))
    }
}

@Composable
private fun MiniTagV1(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(9.dp)) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

private data class V1Visual(val icon: ImageVector, val color: Color)

private fun visualFor(category: String): V1Visual {
    val c = category.lowercase()
    return when {
        "facture" in c -> V1Visual(Icons.Default.ReceiptLong, V1Red)
        "devis" in c -> V1Visual(Icons.Default.RequestQuote, V1Orange)
        "urssaf" in c || "entreprise" in c -> V1Visual(Icons.Default.BusinessCenter, V1Purple)
        "paie" in c || "travail" in c -> V1Visual(Icons.Default.Badge, V1Green)
        "voiture" in c || "assurance" in c || "contrôle" in c -> V1Visual(Icons.Default.DirectionsCar, V1Blue)
        "banque" in c || "impôt" in c -> V1Visual(Icons.Default.AccountBalance, V1Green)
        "identité" in c || "administratif" in c -> V1Visual(Icons.Default.AccountBox, V1Purple)
        "photo" in c -> V1Visual(Icons.Default.Image, V1Cyan)
        "vidéo" in c -> V1Visual(Icons.Default.Movie, V1Pink)
        "audio" in c -> V1Visual(Icons.Default.AudioFile, V1Orange)
        "archive" in c -> V1Visual(Icons.Default.Archive, Color(0xFF8A6A4A))
        "apk" in c || "application" in c -> V1Visual(Icons.Default.Android, V1Green)
        "pdf" in c -> V1Visual(Icons.Default.PictureAsPdf, V1Red)
        else -> V1Visual(Icons.Default.Description, V1Blue)
    }
}

private fun iconForV1(doc: IndexedDocument): ImageVector = when {
    doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    doc.mimeType.startsWith("image/") -> Icons.Default.Image
    doc.mimeType.startsWith("video/") -> Icons.Default.Movie
    doc.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    doc.categoryPath == "Archives" -> Icons.Default.Archive
    doc.categoryPath == "Applications_APK" -> Icons.Default.Android
    else -> Icons.Default.Description
}
