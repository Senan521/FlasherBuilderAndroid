@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.flasherbuilder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flasherbuilder.data.CloudViewModel
import app.flasherbuilder.data.NativeTools
import app.flasherbuilder.data.Phase
import app.flasherbuilder.data.Ui

enum class Screen { Home, Cloud, Local, Settings }

@Composable
fun App(vm: CloudViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Settings) Screen.Cloud else Screen.Home
    }
    when (screen) {
        Screen.Home -> HomeScreen({ screen = Screen.Cloud }, { screen = Screen.Local })
        Screen.Cloud -> CloudScreen(vm, { screen = Screen.Home }, { screen = Screen.Settings })
        Screen.Local -> LocalScreen { screen = Screen.Home }
        Screen.Settings -> SettingsScreen(vm) { screen = Screen.Cloud }
    }
}

// ───────────────────────── Ana ekran ─────────────────────────

@Composable
fun HomeScreen(onCloud: () -> Unit, onLocal: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Flasher Builder") },
                subtitle = { Text("Firmware paketi oluştur") },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeCard(
                title = "Bulutta Build",
                body = "Build GitHub Actions üzerinde yapılır; telefona yalnızca bitmiş paket iner. Depolama sorunu yok.",
                icon = Icons.Filled.Cloud,
                badge = MaterialShapes.Cookie9Sided,
                container = MaterialTheme.colorScheme.primaryContainer,
                tag = "Önerilen",
                onClick = onCloud,
            )
            ModeCard(
                title = "Cihazda Build",
                body = "Her şey telefonda çalışır. 40–80 GB boş alan ve arm64 araçlar gerekir.",
                icon = Icons.Filled.PhoneAndroid,
                badge = MaterialShapes.Sunny,
                container = MaterialTheme.colorScheme.secondaryContainer,
                tag = "Yakında",
                onClick = onLocal,
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String, body: String, icon: ImageVector, badge: RoundedPolygon,
    container: Color, tag: String, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, badge.toShape()),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            SuggestionChip(onClick = onClick, label = { Text(tag) })
        }
    }
}

// ───────────────────────── Bulut ekranı ─────────────────────────

@Composable
fun CloudScreen(vm: CloudViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val form = vm.form
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    // Bildirim izni (Android 13+): reddedilse de build başlar; yalnızca bildirim görünmez.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val onStart = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulutta Build") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { IconButton(onSettings) { Icon(Icons.Filled.Settings, "Ayarlar") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val fm = Modifier.fillMaxWidth()
            Dropdown("Cihaz", form.device, vm.catalog.map { it.name }, !ui.busy, vm::selectDevice)
            Dropdown("Flasher türü", form.kind, vm.currentDevice?.types?.map { it.kind }.orEmpty(), !ui.busy, vm::selectKind)
            Dropdown("Bölge", form.region, vm.currentType?.regions?.map { it.region }.orEmpty(), !ui.busy, vm::selectRegion)
            OutlinedTextField(form.otaUrl, { vm.saveForm(form.copy(otaUrl = it.trim())) }, fm,
                enabled = !ui.busy, label = { Text("Tam OTA ZIP bağlantısı") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            if (vm.needsBase) {
                OutlinedTextField(form.baseOtaUrl, { vm.saveForm(form.copy(baseOtaUrl = it.trim())) }, fm,
                    enabled = !ui.busy, label = { Text("BASE OTA ZIP bağlantısı (Ace 6 COS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
            if (vm.needsAbl) {
                Text("Zafiyetli ABL (16.0.6) paketlensin mi?", style = MaterialTheme.typography.titleSmall)
                val choices = listOf("yes" to "Evet", "no" to "Hayır")
                SingleChoiceSegmentedButtonRow(fm) {
                    choices.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = form.abl == value,
                            onClick = { vm.saveForm(form.copy(abl = value)) },
                            shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                            enabled = !ui.busy,
                        ) { Text(label) }
                    }
                }
            }

            StatusCard(ui)

            if (ui.busy) {
                FilledTonalButton(onClick = vm::cancel, modifier = fm) { Text("İptal") }
            } else {
                Button(onClick = onStart, modifier = fm) { Text("Build başlat") }
                if (vm.lastRunId > 0 && ui.phase == Phase.Idle) {
                    TextButton(onClick = vm::resume, modifier = fm) { Text("Önceki build'e devam et (#${vm.lastRunId})") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(ui: Ui) {
    if (ui.phase == Phase.Idle && ui.message.isEmpty()) return
    val ctx = LocalContext.current
    val failed = ui.phase == Phase.Failed
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.busy) LoadingIndicator(Modifier.size(48.dp))
                Text(ui.message, style = MaterialTheme.typography.titleMedium)
            }
            val p = ui.progress
            when {
                ui.phase == Phase.Downloading && p != null ->
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                ui.phase == Phase.Queued || ui.phase == Phase.Running ->
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ui.runUrl?.let { url ->
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Text("GitHub'da aç")
                }
            }
            ui.resultPath?.let { Text("Konum:\n$it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ───────────────────────── Yerel ekran (yer tutucu) ─────────────────────────

@Composable
fun LocalScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val freeGb = remember {
        (ctx.getExternalFilesDir(null)?.let { StatFs(it.path).availableBytes } ?: 0L) / 1e9
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cihazda Build") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Boş alan: ${"%.1f".format(freeGb)} GB", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { (freeGb / 80.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                "Tam build için OTA boyutunun yaklaşık 4 katı boş alan gerekir (8 GB OTA ≈ 32 GB). " +
                    "arm64 otaripper ve lpmake APK içinde hazır; Python build hattının cihaza taşınması sonraki aşama.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val scope = rememberCoroutineScope()
            var probes by remember { mutableStateOf<List<NativeTools.Probe>?>(null) }
            FilledTonalButton(
                onClick = { scope.launch { probes = NativeTools.probe(ctx) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Yerel araçları denetle") }
            probes?.forEach { r ->
                Text(
                    (if (r.ok) "✔ " else "✘ ") + r.name + ": " + r.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Build (yakında)") }
        }
    }
}

// ───────────────────────── Ayarlar ─────────────────────────

@Composable
fun SettingsScreen(vm: CloudViewModel, onBack: () -> Unit) {
    var c by remember { mutableStateOf(vm.conn) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val fm = Modifier.fillMaxWidth()
            Text(
                "Kendi fork'unuzu girin. Token: GitHub → Settings → Developer settings → " +
                    "Fine-grained token; yalnızca bu repo, izin: Actions → Read and write.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(c.owner, { c = c.copy(owner = it) }, fm, singleLine = true, label = { Text("Sahip (kullanıcı/organizasyon)") })
            OutlinedTextField(c.repo, { c = c.copy(repo = it) }, fm, singleLine = true, label = { Text("Repo") })
            OutlinedTextField(c.ref, { c = c.copy(ref = it) }, fm, singleLine = true, label = { Text("Branch") })
            OutlinedTextField(c.workflow, { c = c.copy(workflow = it) }, fm, singleLine = true, label = { Text("Workflow dosyası") })
            OutlinedTextField(c.token, { c = c.copy(token = it) }, fm, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text("Token") })
            Button(onClick = { vm.saveConn(c); onBack() }, modifier = fm) { Text("Kaydet") }
        }
    }
}

// ───────────────────────── Ortak bileşenler ─────────────────────────

@Composable
private fun Dropdown(
    label: String, value: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
