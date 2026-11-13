@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.flasherbuilder.ui

import android.Manifest
import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flasherbuilder.R
import app.flasherbuilder.Str
import app.flasherbuilder.data.LocalEngine
import app.flasherbuilder.data.LocalExport
import app.flasherbuilder.data.LocalInbox
import app.flasherbuilder.data.MainViewModel
import app.flasherbuilder.data.NativeTools
import app.flasherbuilder.data.Phase
import app.flasherbuilder.data.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun App(vm: MainViewModel = viewModel()) = MainScreen(vm)

// ───────────────────────── Ana ekran ─────────────────────────

@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsState()
    val log by vm.log.collectAsState()
    var showLog by rememberSaveable { mutableStateOf(false) }       // günlük varsayılan olarak gizli
    var freeGb by remember { mutableStateOf(0.0) }
    var workGb by remember { mutableStateOf(0.0) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var probes by remember { mutableStateOf<List<NativeTools.Probe>?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Seçilen dosya build sırasında taşınır; durum her değişimde diskten yenilenir.
    LaunchedEffect(ui.phase) { vm.refreshStaged() }

    // Boş alan / çalışma alanı boyutu: ekran açılınca ve her build durumu değişince yenilenir.
    LaunchedEffect(ui.phase, exportProgress == null) {
        withContext(Dispatchers.IO) {
            freeGb = LocalEngine.freeBytes(ctx) / 1e9
            workGb = LocalEngine.workspaceBytes(ctx) / 1e9
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val onStart = {
        exportMessage = null
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.home_subtitle)) },
                actions = { if (Build.VERSION.SDK_INT >= 33) LanguageMenu() },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val fm = Modifier.fillMaxWidth()
            ElevatedCard(fm) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.local_space_line, "%.1f".format(freeGb), "%.1f".format(workGb)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LinearProgressIndicator(progress = { (freeGb / 80.0).toFloat().coerceIn(0f, 1f) }, modifier = fm)
                    Text(stringResource(R.string.local_hint), style = MaterialTheme.typography.bodySmall)
                }
            }

            BuildForm(vm, enabled = !ui.busy)
            StatusCard(ui)

            if (ui.busy) {
                FilledTonalButton(onClick = vm::cancel, modifier = fm) { Text(stringResource(R.string.cancel)) }
            } else {
                Button(onClick = onStart, modifier = fm) { Text(stringResource(R.string.start_build)) }
            }

            // Günlük: isteğe bağlı, varsayılan olarak gizli.
            Row(fm, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_log), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = showLog, onCheckedChange = { showLog = it })
            }
            if (showLog) LogCard(log)

            // Biten ZIP'i İndirilenler klasörüne kopyala (uygulama klasörü dosya yöneticilerinde gizli olabilir).
            val zipPath = ui.resultPath
            if (ui.phase == Phase.Done && zipPath != null && zipPath.endsWith(".zip")) {
                val p = exportProgress
                if (p != null) {
                    LinearWavyProgressIndicator(progress = { p }, modifier = fm)
                } else {
                    Button(
                        onClick = {
                            exportProgress = 0f
                            scope.launch {
                                exportMessage = try {
                                    Str.get(R.string.export_ok, LocalExport.toDownloads(ctx, File(zipPath)) { exportProgress = it })
                                } catch (e: Exception) {
                                    Str.get(R.string.export_fail, e.message ?: "")
                                } finally {
                                    exportProgress = null
                                }
                            }
                        },
                        modifier = fm,
                    ) { Text(stringResource(R.string.export_btn)) }
                }
                exportMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            OutlinedButton(
                onClick = { LocalEngine.clearWorkspace(ctx); vm.refreshStaged(); exportMessage = null },
                enabled = !ui.busy && exportProgress == null && workGb > 0.0,
                modifier = fm,
            ) { Text(stringResource(R.string.clear_workspace, "%.1f".format(workGb))) }

            // Tanılama: yerel araçlar bu cihazda çalışıyor mu?
            TextButton(onClick = { scope.launch { probes = NativeTools.probe(ctx) } }, modifier = fm) {
                Text(stringResource(R.string.probe_btn))
            }
            probes?.forEach { r ->
                Text(
                    (if (r.ok) "✔ " else "✘ ") + r.name + ": " + r.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Box(Modifier.height(24.dp))
        }
    }
}

// ───────────────────────── Dil seçimi (Android 13+) ─────────────────────────

@RequiresApi(33)
@Composable
private fun LanguageMenu() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Language, stringResource(R.string.language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("" to R.string.lang_system, "en" to R.string.lang_en, "tr" to R.string.lang_tr).forEach { (tag, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        // Boş etiket = sistem dili. Etkinlik otomatik yeniden oluşturulur.
                        ctx.getSystemService(LocaleManager::class.java).applicationLocales =
                            LocaleList.forLanguageTags(tag)
                    },
                )
            }
        }
    }
}

// ───────────────────────── Günlük ─────────────────────────

/** Sabit yükseklikli, kaydırılabilir günlük. Kullanıcı en alttaysa yeni satırları takip eder. */
@Composable
private fun LogCard(lines: List<String>) {
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    val atEnd by remember { derivedStateOf { !state.canScrollForward } }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress) follow = atEnd }
    LaunchedEffect(lines.size) { if (follow && lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.log_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(
                    enabled = lines.isNotEmpty(),
                    onClick = {
                        ctx.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("log", lines.joinToString("\n")))
                        Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    },
                ) { Text(stringResource(R.string.copy_log)) }
            }
            if (lines.isEmpty()) {
                Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(280.dp), state = state) {
                    items(lines.size) { i ->
                        Text(lines[i], style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Durum kartı ─────────────────────────

@Composable
private fun StatusCard(ui: Ui) {
    if (ui.phase == Phase.Idle && ui.message.isEmpty()) return
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
            if (ui.busy) {
                val p = ui.progress
                if (p != null) LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ui.resultPath?.let { Text(stringResource(R.string.location, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ───────────────────────── Form ─────────────────────────

@Composable
private fun BuildForm(vm: MainViewModel, enabled: Boolean) {
    val form = vm.form
    val fm = Modifier.fillMaxWidth()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Dropdown(stringResource(R.string.field_device), form.device, vm.catalog.map { it.name }, enabled, vm::selectDevice)
        Dropdown(stringResource(R.string.field_kind), form.kind, vm.currentDevice?.types?.map { it.kind }.orEmpty(), enabled, vm::selectKind)
        Dropdown(stringResource(R.string.field_region), form.region, vm.currentType?.regions?.map { it.region }.orEmpty(), enabled, vm::selectRegion)

        SourceField(
            label = stringResource(R.string.field_ota), role = "OTA", url = form.otaUrl,
            onUrl = { vm.saveForm(form.copy(otaUrl = it.trim())) },
            staged = vm.otaFile, vm = vm, enabled = enabled,
        )
        if (vm.needsBase) {
            SourceField(
                label = stringResource(R.string.field_base), role = "BASE", url = form.baseOtaUrl,
                onUrl = { vm.saveForm(form.copy(baseOtaUrl = it.trim())) },
                staged = vm.baseFile, vm = vm, enabled = enabled,
            )
        }
        vm.stageProgress?.let { p ->
            Text(stringResource(R.string.staging_progress, (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { p }, modifier = fm)
        }
        vm.stageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        ChangeImageSection(vm, enabled)

        if (vm.needsAbl) {
            Text(stringResource(R.string.abl_question), style = MaterialTheme.typography.titleSmall)
            val choices = listOf("yes" to stringResource(R.string.yes), "no" to stringResource(R.string.no))
            SingleChoiceSegmentedButtonRow(fm) {
                choices.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = form.abl == value,
                        onClick = { vm.saveForm(form.copy(abl = value)) },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                        enabled = enabled,
                    ) { Text(label) }
                }
            }
        }
    }
}

/** "Change image": ROM'daki imajların yerine OTA bağlantısından seçilen ya da cihazdan seçilen imajlar konur. */
@Composable
private fun ChangeImageSection(vm: MainViewModel, enabled: Boolean) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickImage(uri)
    }
    val scan by vm.scan.collectAsState()
    val fm = Modifier.fillMaxWidth()
    val canAct = enabled && vm.stageProgress == null

    Text(stringResource(R.string.img_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.img_hint), style = MaterialTheme.typography.bodySmall)

    // Build'e eklenecek (ROM'dakinin yerine geçecek) imajlar
    if (vm.images.isNotEmpty()) {
        Text(stringResource(R.string.img_replacing), style = MaterialTheme.typography.labelMedium)
        vm.images.forEach { img ->
            ElevatedCard(fm) {
                Row(
                    Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${img.name}.img · ${sizeLabel(img.bytes)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.removeImage(img.name) }, enabled = enabled) { Text(stringResource(R.string.remove)) }
                }
            }
        }
    }

    // OTA bağlantısından tarama
    OutlinedTextField(
        vm.form.imageUrl, { vm.saveForm(vm.form.copy(imageUrl = it.trim())) }, fm,
        enabled = enabled, label = { Text(stringResource(R.string.img_link_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    FilledTonalButton(
        onClick = vm::scanImages,
        enabled = canAct && vm.form.imageUrl.startsWith("http"),
        modifier = fm,
    ) { Text(stringResource(R.string.img_scan_btn)) }

    scan?.let { list ->
        val offered = list.filter { it.name !in LocalInbox.BLOCKED }
        Text(stringResource(R.string.img_list_title), style = MaterialTheme.typography.labelMedium)
        ElevatedCard(fm) {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                offered.forEach { info ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canAct) { vm.toggleImage(info.name) }.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = info.name in vm.selected, onCheckedChange = { vm.toggleImage(info.name) }, enabled = canAct)
                        Text("${info.name}.img", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(sizeLabel(info.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = vm::extractSelected,
            enabled = canAct && vm.selected.isNotEmpty(),
            modifier = fm,
        ) { Text(stringResource(R.string.img_extract_btn, vm.selected.size)) }
    }

    // Cihazdan imaj dosyası
    OutlinedButton(
        onClick = { picker.launch(arrayOf("*/*")) },
        enabled = canAct,
        modifier = fm,
    ) { Text(stringResource(R.string.img_pick_btn)) }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.2f GB".format(bytes / 1e9) else "%.1f MB".format(bytes / 1e6)

/** OTA/BASE kaynağı: bağlantı yazılır ya da cihazdan ZIP seçilir. */
@Composable
private fun SourceField(
    label: String, role: String, url: String, onUrl: (String) -> Unit,
    staged: LocalInbox.Staged?, vm: MainViewModel, enabled: Boolean,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.stageFile(role, uri)
    }
    val fm = Modifier.fillMaxWidth()
    if (staged != null) {
        ElevatedCard(fm) {
            Row(
                Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.staged_title, role), style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${staged.name} · ${"%.2f".format(staged.bytes / 1e9)} GB",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { vm.clearStaged(role) }, enabled = enabled) { Text(stringResource(R.string.remove)) }
            }
        }
    } else {
        OutlinedTextField(url, onUrl, fm, enabled = enabled, label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = enabled && vm.stageProgress == null,
            modifier = fm,
        ) { Text(stringResource(R.string.pick_file, role)) }
    }
}

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
