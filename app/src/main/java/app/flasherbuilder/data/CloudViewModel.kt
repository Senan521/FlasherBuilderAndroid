package app.flasherbuilder.data

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.flasherbuilder.BuildService
import kotlinx.coroutines.flow.StateFlow

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

    init {
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

    private fun problem(): String? = when {
        !conn.isComplete() -> "Önce Ayarlar'dan repo ve token girin."
        currentType == null -> "Geçerli bir cihaz, tür ve bölge seçin."
        !form.otaUrl.startsWith("http") -> "Geçerli bir tam OTA ZIP bağlantısı girin."
        needsBase && !form.baseOtaUrl.startsWith("http") -> "Bu seçim için BASE OTA bağlantısı da gerekli."
        needsAbl && form.abl !in setOf("yes", "no") -> "Zafiyetli ABL için Evet veya Hayır seçin."
        else -> null
    }

    fun start() {
        problem()?.let { BuildEngine.fail(it); return }
        BuildEngine.start(getApplication(), conn, form)
        launchService()
    }

    fun resume() {
        if (!conn.isComplete()) { BuildEngine.fail("Önce Ayarlar'dan repo ve token girin."); return }
        BuildEngine.resume(getApplication(), conn)
        launchService()
    }

    fun cancel() = BuildEngine.cancel(getApplication())

    private fun launchService() {
        val app = getApplication<Application>()
        if (BuildEngine.ui.value.busy) app.startForegroundService(Intent(app, BuildService::class.java))
    }
}
