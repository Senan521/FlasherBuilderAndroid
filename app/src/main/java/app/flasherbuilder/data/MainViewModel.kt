package app.flasherbuilder.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.flasherbuilder.BuildService
import app.flasherbuilder.R
import app.flasherbuilder.Str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    val catalog: List<DeviceEntry> = Catalog.load(app)

    var form by mutableStateOf(prefs.loadForm()); private set
    val ui: StateFlow<Ui> = LocalEngine.ui
    val log: StateFlow<List<String>> = LocalEngine.log

    // Seçime bağlı türetilmiş durum (form Compose state'i olduğundan arayüz otomatik güncellenir).
    val currentDevice: DeviceEntry? get() = catalog.firstOrNull { it.name == form.device }
    val currentType: KindEntry? get() = currentDevice?.types?.firstOrNull { it.kind == form.kind }
    val needsBase: Boolean get() = currentType?.regions?.firstOrNull { it.region == form.region }?.needsBase == true
    val needsAbl: Boolean get() = form.device == Catalog.X9U

    // Cihazdan seçilen OTA/BASE dosyaları.
    var otaFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    var baseFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    // ROM'daki yerine geçecek imajlar + OTA tarama/seçim durumu.
    var images by mutableStateOf<List<LocalInbox.Staged>>(emptyList()); private set
    val scan: StateFlow<List<LocalEngine.ImageInfo>?> = LocalEngine.scan
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var stageProgress by mutableStateOf<Float?>(null); private set     // null: kopyalama yok
    var stageError by mutableStateOf<String?>(null); private set
    private var stageJob: Job? = null

    init {
        refreshStaged()
        if (currentType?.regions?.any { it.region == form.region } != true) {
            selectDevice(catalog.firstOrNull { it.name == form.device }?.name ?: catalog.first().name)
        }
    }

    fun refreshStaged() {
        val app = getApplication<Application>()
        otaFile = LocalInbox.existing(app, "OTA")
        baseFile = LocalInbox.existing(app, "BASE")
        images = LocalInbox.images(app)
    }

    fun stageFile(role: String, uri: Uri) {
        stageJob?.cancel()
        stageError = null
        stageProgress = 0f
        stageJob = viewModelScope.launch {
            try {
                val staged = LocalInbox.stage(getApplication(), uri, role) { stageProgress = it }
                if (role == "OTA") otaFile = staged else baseFile = staged
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stageError = e.message ?: Str.get(R.string.err_copy_failed)
            } finally {
                stageProgress = null
            }
        }
    }

    fun clearStaged(role: String) {
        LocalInbox.clear(getApplication(), role)
        if (role == "OTA") otaFile = null else baseFile = null
    }

    fun saveForm(f: Form) { form = f; prefs.saveForm(f) }

    fun selectDevice(name: String) {
        val kind = catalog.firstOrNull { it.name == name }?.types?.firstOrNull() ?: return
        saveForm(form.copy(device = name, kind = kind.kind, region = kind.regions.first().region, baseOtaUrl = "", abl = ""))
    }

    fun selectKind(kind: String) {
        val t = currentDevice?.types?.firstOrNull { it.kind == kind } ?: return
        val region = if (t.regions.any { it.region == form.region }) form.region else t.regions.first().region
        saveForm(form.copy(kind = kind, region = region))
    }

    fun selectRegion(region: String) = saveForm(form.copy(region = region))

    private fun problem(): String? = when {
        currentType == null -> Str.get(R.string.err_pick_valid)
        stageProgress != null -> Str.get(R.string.err_copy_wait)
        otaFile == null && !form.otaUrl.startsWith("http") -> Str.get(R.string.err_need_ota_any)
        needsBase && baseFile == null && !form.baseOtaUrl.startsWith("http") -> Str.get(R.string.err_need_base_any)
        needsAbl && form.abl !in setOf("yes", "no") -> Str.get(R.string.err_abl_choice)
        else -> null
    }

    fun start() {
        problem()?.let { LocalEngine.fail(it); return }
        val app = getApplication<Application>()
        LocalEngine.start(
            app, form,
            ota = otaFile?.path ?: form.otaUrl,
            base = if (needsBase) (baseFile?.path ?: form.baseOtaUrl) else "",
            replacements = images,
        )
        if (LocalEngine.ui.value.busy) app.startForegroundService(Intent(app, BuildService::class.java))
    }

    /** OTA bağlantısındaki imajları tarar. */
    fun scanImages() {
        val url = form.imageUrl.trim()
        if (!url.startsWith("http")) { LocalEngine.fail(Str.get(R.string.err_img_link)); return }
        selected = emptySet()
        val app = getApplication<Application>()
        LocalEngine.scanImages(app, url)
        if (LocalEngine.ui.value.busy) app.startForegroundService(Intent(app, BuildService::class.java))
    }

    fun toggleImage(name: String) { selected = if (name in selected) selected - name else selected + name }

    /** Taramadan seçilen imajları bağlantıdan çıkarır. */
    fun extractSelected() {
        val chosen = scan.value?.filter { it.name in selected }.orEmpty()
        if (chosen.isEmpty()) return
        val app = getApplication<Application>()
        LocalEngine.extractImages(app, form.imageUrl.trim(), chosen.map { it.name }, chosen.sumOf { it.size })
        selected = emptySet()
        if (LocalEngine.ui.value.busy) app.startForegroundService(Intent(app, BuildService::class.java))
    }

    /** Cihazdan imaj dosyası seçer (bölüm adı dosya adından: odm.img → odm). */
    fun pickImage(uri: Uri) {
        stageJob?.cancel()
        stageError = null
        stageProgress = 0f
        stageJob = viewModelScope.launch {
            try {
                LocalInbox.stageImage(getApplication(), uri) { stageProgress = it }
                refreshStaged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stageError = e.message ?: Str.get(R.string.err_copy_failed)
            } finally {
                stageProgress = null
            }
        }
    }

    fun removeImage(name: String) {
        LocalInbox.removeImage(getApplication(), name)
        refreshStaged()
    }

    fun cancel() = LocalEngine.cancel()
}
