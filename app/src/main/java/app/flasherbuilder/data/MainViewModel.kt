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
    val supportsCustomOdm: Boolean get() = form.device == Catalog.R13

    // Cihazdan seçilen OTA/BASE dosyaları.
    var otaFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    var baseFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    var odmFile by mutableStateOf<LocalInbox.Staged?>(null); private set
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
        odmFile = LocalInbox.existing(app, "ODM")
    }

    fun stageFile(role: String, uri: Uri) {
        stageJob?.cancel()
        stageError = null
        stageProgress = 0f
        stageJob = viewModelScope.launch {
            try {
                val staged = LocalInbox.stage(getApplication(), uri, role) { stageProgress = it }
                when (role) { "OTA" -> otaFile = staged; "BASE" -> baseFile = staged; else -> odmFile = staged }
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
        when (role) { "OTA" -> otaFile = null; "BASE" -> baseFile = null; else -> odmFile = null }
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
            customOdm = if (supportsCustomOdm) odmFile?.path else null,
        )
        if (LocalEngine.ui.value.busy) app.startForegroundService(Intent(app, BuildService::class.java))
    }

    fun cancel() = LocalEngine.cancel()
}
