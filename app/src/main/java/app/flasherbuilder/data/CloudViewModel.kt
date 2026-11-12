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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CloudViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    val catalog: List<DeviceEntry> = Catalog.load(app)

    var conn by mutableStateOf(prefs.loadConn()); private set
    var form by mutableStateOf(prefs.loadForm()); private set
    val ui: StateFlow<Ui> = BuildEngine.ui
    val lastRunId: Long get() = prefs.lastRunId

    // Seçime bağlı türetilmiş durum (form Compose state'i olduğundan arayüz otomatik güncellenir).
    val currentDevice: DeviceEntry? get() = catalog.firstOrNull { it.name == form.device }
    val currentType: KindEntry? get() = currentDevice?.types?.firstOrNull { it.kind == form.kind }
    val needsBase: Boolean get() = currentType?.regions?.firstOrNull { it.region == form.region }?.needsBase == true
    val needsAbl: Boolean get() = form.device == Catalog.X9U

    // Cihazdan seçilen OTA/BASE dosyaları (yalnızca cihazda build için).
    var otaFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    var baseFile by mutableStateOf<LocalInbox.Staged?>(null); private set
    var stageProgress by mutableStateOf<Float?>(null); private set     // null: kopyalama yok
    var stageError by mutableStateOf<String?>(null); private set
    private var stageJob: Job? = null

    fun refreshStaged() {
        val app = getApplication<Application>()
        otaFile = LocalInbox.existing(app, "OTA")
        baseFile = LocalInbox.existing(app, "BASE")
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
                stageError = e.message ?: "Dosya kopyalanamadı."
            } finally {
                stageProgress = null
            }
        }
    }

    fun clearStaged(role: String) {
        LocalInbox.clear(getApplication(), role)
        if (role == "OTA") otaFile = null else baseFile = null
    }

    init {
        refreshStaged()
        if (currentType?.regions?.any { it.region == form.region } != true) {
            selectDevice(catalog.firstOrNull { it.name == form.device }?.name ?: catalog.first().name)
        }
    }

    fun saveConn(c: Conn) { conn = c; prefs.saveConn(c) }
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

    private fun problem(requireConn: Boolean = true): String? = when {
        requireConn && !conn.isComplete() -> "Önce Ayarlar'dan repo ve token girin."
        currentType == null -> "Geçerli bir cihaz, tür ve bölge seçin."
        stageProgress != null -> "Dosya kopyalanıyor; bitmesini bekleyin."
        requireConn && !form.otaUrl.startsWith("http") -> "Geçerli bir tam OTA ZIP bağlantısı girin."
        requireConn && needsBase && !form.baseOtaUrl.startsWith("http") -> "Bu seçim için BASE OTA bağlantısı da gerekli."
        !requireConn && otaFile == null && !form.otaUrl.startsWith("http") -> "Tam OTA için bağlantı girin ya da cihazdan dosya seçin."
        !requireConn && needsBase && baseFile == null && !form.baseOtaUrl.startsWith("http") -> "BASE OTA için bağlantı girin ya da dosya seçin."
        needsAbl && form.abl !in setOf("yes", "no") -> "Zafiyetli ABL için Evet veya Hayır seçin."
        else -> null
    }

    fun start() {
        problem()?.let { BuildEngine.fail(it); return }
        BuildEngine.start(getApplication(), conn, form)
        launchService(local = false)
    }

    /** Cihazda build: GitHub bağlantısı gerekmez. */
    fun startLocal() {
        problem(requireConn = false)?.let { LocalEngine.fail(it); return }
        LocalEngine.start(
            getApplication(), form,
            ota = otaFile?.path ?: form.otaUrl,
            base = if (needsBase) (baseFile?.path ?: form.baseOtaUrl) else "",
        )
        launchService(local = true)
    }

    fun cancelLocal() = LocalEngine.cancel()

    fun resume() {
        if (!conn.isComplete()) { BuildEngine.fail("Önce Ayarlar'dan repo ve token girin."); return }
        BuildEngine.resume(getApplication(), conn)
        launchService(local = false)
    }

    fun cancel() = BuildEngine.cancel(getApplication())

    private fun launchService(local: Boolean) {
        val app = getApplication<Application>()
        val busy = if (local) LocalEngine.ui.value.busy else BuildEngine.ui.value.busy
        if (busy) {
            app.startForegroundService(
                Intent(app, BuildService::class.java)
                    .putExtra(BuildService.EXTRA_MODE, if (local) BuildService.MODE_LOCAL else BuildService.MODE_CLOUD)
            )
        }
    }
}
