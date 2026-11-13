package app.flasherbuilder.data

import app.flasherbuilder.R
import app.flasherbuilder.Str
import android.content.Context
import android.os.StatFs
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Python tarafı satırları bu nesnenin onLine metoduna gönderir (Chaquopy üzerinden çağrılır). */
class LogSink(private val handler: (String) -> Unit) {
    @Suppress("unused")
    fun onLine(line: String) = handler(line)
}

/**
 * Cihazda build: Flasher_Builder'ın Python backend'i Chaquopy ile uygulama içinde çalışır,
 * otaripper ve lpmake APK'daki arm64 binary'leridir.
 * İki aşama: (0) bloat indirme, (1) OTA indirme + paket oluşturma. Çıktı: tek ZIP.
 */
object LocalEngine {
    private const val GB = 1_000_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(Ui())
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** OTA taramasının sonucu (null: henüz taranmadı). */
    data class ImageInfo(val name: String, val size: Long)
    private val _scan = MutableStateFlow<List<ImageInfo>?>(null)
    val scan: StateFlow<List<ImageInfo>?> = _scan.asStateFlow()
    private val scanTmp: MutableList<ImageInfo> = java.util.Collections.synchronizedList(mutableListOf())

    private var job: Job? = null
    @Volatile private var cancelRequested = false
    @Volatile private var cancelFlag: File? = null

    // Çalışan aşamanın durumu (handleLine tarafından güncellenir).
    @Volatile private var stage = 0
    @Volatile private var step = 0
    @Volatile private var stepTitle = ""
    @Volatile private var lastError: String? = null
    @Volatile private var result: JSONObject? = null

    // Dahili depolama: FUSE katmanı yok (çok GB'lık G/Ç daha hızlı); boş alan harici "emulated" ile aynı havuzdur.
    fun workspace(ctx: Context): File = File(ctx.filesDir, "local").apply { mkdirs() }

    fun freeBytes(ctx: Context): Long = StatFs(workspace(ctx).path).availableBytes

    fun workspaceBytes(ctx: Context): Long = workspace(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearWorkspace(ctx: Context) {
        if (_ui.value.busy) return
        File(workspace(ctx), "builds").deleteRecursively()
        File(workspace(ctx), "bloat").deleteRecursively()
        LocalInbox.clear(ctx)
        _ui.value = Ui()
    }

    fun fail(msg: String) { _ui.value = Ui(Phase.Failed, msg) }

    fun start(ctx: Context, form: Form, ota: String, base: String, replacements: List<LocalInbox.Staged> = emptyList()) {
        if (_ui.value.busy) return
        val app = ctx.applicationContext
        _log.value = emptyList()
        cancelRequested = false
        stage = 0; step = 0; stepTitle = ""; lastError = null; result = null
        _ui.value = Ui(Phase.Running, Str.get(R.string.local_preparing))
        job = scope.launch {
            try {
                pipeline(app, form, ota, base, replacements)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = Ui(Phase.Failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** OTA bağlantısındaki imajları listeler (yalnızca manifest indirilir). Sonuç [scan] akışında. */
    fun scanImages(ctx: Context, source: String) {
        if (_ui.value.busy) return
        val app = ctx.applicationContext
        beginSideTask(Str.get(R.string.scan_running))
        _scan.value = null
        scanTmp.clear()
        job = scope.launch {
            try {
                val (root, nativeDir, cancel) = prepare(app)
                if (cancelRequested) { _ui.value = Ui(Phase.Idle, Str.get(R.string.cancelled)); return@launch }
                val code = callPython(app, "scan_images", root.path, nativeDir, source, cancel.path)
                _ui.value = when {
                    code == 130 || cancelRequested -> Ui(Phase.Idle, Str.get(R.string.cancelled))
                    code == 0 -> {
                        _scan.value = synchronized(scanTmp) { scanTmp.toList() }
                        Ui(Phase.Idle, Str.get(R.string.scan_done, scanTmp.size))
                    }
                    else -> Ui(Phase.Failed, Str.get(R.string.err_scan, lastError ?: ""))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = Ui(Phase.Failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Seçilen imajları OTA bağlantısından çıkarır (yalnızca o bölümler indirilir) ve images klasörüne koyar. */
    fun extractImages(ctx: Context, source: String, names: List<String>, totalBytes: Long) {
        if (_ui.value.busy) return
        val app = ctx.applicationContext
        val free = freeBytes(app)
        if (free < totalBytes + 500_000_000L) {
            fail(Str.get(R.string.err_images_space, "%.1f".format(totalBytes / 1e9), "%.1f".format(free / 1e9)))
            return
        }
        beginSideTask(Str.get(R.string.extract_running))
        job = scope.launch {
            try {
                val (root, nativeDir, cancel) = prepare(app)
                if (cancelRequested) { _ui.value = Ui(Phase.Idle, Str.get(R.string.cancelled)); return@launch }
                val code = callPython(
                    app, "extract_images", root.path, nativeDir, source,
                    names.toTypedArray(), LocalInbox.imagesDir(app).path, cancel.path,
                )
                _ui.value = when {
                    code == 130 || cancelRequested -> Ui(Phase.Idle, Str.get(R.string.cancelled))
                    code == 0 -> Ui(Phase.Idle, Str.get(R.string.extract_done, names.joinToString(", ") { "$it.img" }))
                    else -> Ui(Phase.Failed, Str.get(R.string.err_extract, lastError ?: ""))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = Ui(Phase.Failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun clearScan() { _scan.value = null }

    private fun beginSideTask(message: String) {
        _log.value = emptyList()
        cancelRequested = false
        lastError = null
        _ui.value = Ui(Phase.Running, message)
    }

    /** Araçların varlığını doğrular, repo paketini açar, iptal bayrağını hazırlar. */
    private fun prepare(app: Context): Triple<File, String, File> {
        val nativeDir = app.applicationInfo.nativeLibraryDir
        for (name in listOf("libotaripper.so", "liblpmake.so")) {
            if (!File(nativeDir, name).isFile) error(Str.get(R.string.err_tool_missing, name))
        }
        val root = RepoBundle.ensure(app)
        val cancel = File(app.cacheDir, "local-cancel.flag").also { it.delete() }
        cancelFlag = cancel
        return Triple(root, nativeDir, cancel)
    }

    private fun callPython(app: Context, fn: String, vararg args: Any): Int {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        return Python.getInstance().getModule("android_entry")
            .callAttr(fn, *args, LogSink(::handleLine))
            .toInt()
    }

    fun cancel() {
        if (!_ui.value.busy) return
        cancelRequested = true
        cancelFlag?.writeText("1")
        _ui.update { it.copy(message = Str.get(R.string.local_cancelling)) }
    }

    private fun pipeline(app: Context, form: Form, ota: String, base: String, replacements: List<LocalInbox.Staged>) {
        val nativeDir = app.applicationInfo.nativeLibraryDir
        for (name in listOf("libotaripper.so", "liblpmake.so")) {
            if (!File(nativeDir, name).isFile) {
                error(Str.get(R.string.err_tool_missing, name))
            }
        }
        val ws = workspace(app)
        val free = freeBytes(app)
        if (free < 6 * GB) {
            error(Str.get(R.string.err_low_space_local, "%.1f".format(free / 1e9)))
        }

        setMessage(Str.get(R.string.local_unpacking))
        val root = RepoBundle.ensure(app)
        val cancel = File(app.cacheDir, "local-cancel.flag").also { it.delete() }
        cancelFlag = cancel
        if (cancelRequested) { _ui.value = Ui(Phase.Idle, Str.get(R.string.cancelled)); return }

        val bloat = File(ws, "bloat")
        val out = File(ws, "builds")
        val common = listOf("--device", form.device, "--kind", form.kind, "--region", form.region, "--cancel", cancel.path)

        // Aşama 0: bloat dosyalarını indir ve doğrula (önbelleğe alınır, tekrar indirilmez).
        stage = 0
        if (!finish(py(app, root, nativeDir, listOf("--download-bloat", "--bloat-dir", bloat.path) + common))) return

        // Aşama 1: OTA'yı indir, partition'ları çıkar, süper imajı ve paketi oluştur.
        stage = 1; step = 0; stepTitle = ""
        val args = mutableListOf("--bloat-dir", bloat.path, "--output", out.path, "--ota", ota)
        if (base.isNotBlank()) args += listOf("--base", base)
        replacements.forEach { args += listOf("--replace-image", "${it.name}=${it.path}") }
        if (form.device == Catalog.X9U) args += if (form.abl == "yes") "--vulnerable-abl" else "--stock-abl"
        if (!finish(py(app, root, nativeDir, args + common))) return

        val r = result ?: error(Str.get(R.string.err_no_result))
        val path = r.optString("zip").ifEmpty { r.optString("folder") }
        _ui.value = Ui(Phase.Done, Str.get(R.string.local_done), resultPath = path)
    }

    /** Aşama çıkış kodunu değerlendirir; devam edilebiliyorsa true döner. */
    private fun finish(code: Int): Boolean {
        when {
            code == 0 && !cancelRequested -> return true
            code == 130 || cancelRequested -> _ui.value = Ui(Phase.Idle, Str.get(R.string.cancelled))
            else -> _ui.value = Ui(Phase.Failed, lastError ?: Str.get(R.string.local_failed_code, code))
        }
        return false
    }

    private fun py(app: Context, root: File, nativeDir: String, argv: List<String>): Int {
        if (!Python.isStarted()) Python.start(AndroidPlatform(app))
        return Python.getInstance().getModule("android_entry")
            .callAttr("run", root.path, nativeDir, argv.toTypedArray(), LogSink(::handleLine))
            .toInt()
    }

    private fun setMessage(msg: String) = _ui.update { it.copy(message = msg) }

    private fun overall(download: Float?): Float? {
        if (stage == 0) return null                       // bloat: dosya sayısı çok, belirsiz ilerleme
        val s = (step - 1).coerceAtLeast(0)
        return 0.05f + 0.95f * ((s + (if (step == 1) download ?: 0f else 0f)) / 5f)
    }

    private fun handleLine(line: String) {
        when {
            line.startsWith("@@DL ") -> {
                val p = line.split(" ", limit = 4)
                val done = p.getOrNull(1)?.toLongOrNull() ?: return
                val total = p.getOrNull(2)?.toLongOrNull() ?: 0L
                val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
                val label = Str.get(if (stage == 0) R.string.dl_bloat else R.string.dl_ota)
                val size = if (total > 0) "${"%.2f".format(done / 1e9)} / ${"%.2f".format(total / 1e9)} GB"
                else "${"%.2f".format(done / 1e9)} GB"
                _ui.update { it.copy(message = "$label · $size", progress = overall(fraction)) }
            }
            line.startsWith("@@IMG ") -> {
                val p = line.split(" ")
                val size = p.getOrNull(2)?.toLongOrNull()
                if (p.size >= 3 && size != null) scanTmp.add(ImageInfo(p[1], size))
            }
            line.startsWith("@@RESULT ") -> result = runCatching { JSONObject(line.removePrefix("@@RESULT ")) }.getOrNull()
            line.startsWith("@@") -> Unit
            line.startsWith("Invalid sparse file format at header magic") -> Unit   // lpmake: ham imaj sparse değil, zararsız
            else -> {
                if (line.isNotBlank()) _log.update { (it + line).takeLast(500) }
                val m = STEP.matchEntire(line)
                if (m != null) {
                    step = m.groupValues[1].toInt()
                    stepTitle = m.groupValues[2]
                    val prefix = Str.get(if (stage == 0) R.string.stage_bloat else R.string.stage_build)
                    _ui.update { it.copy(message = Str.get(R.string.step_fmt, prefix, step, stepTitle), progress = overall(null)) }
                } else if (line.startsWith("ERROR:")) {
                    lastError = line.removePrefix("ERROR:").trim()
                }
            }
        }
    }

    private val STEP = Regex("""STEP (\d)/5 • (.*)""")
}
