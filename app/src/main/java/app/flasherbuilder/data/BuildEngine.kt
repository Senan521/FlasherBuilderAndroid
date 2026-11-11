package app.flasherbuilder.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class Phase { Idle, Dispatching, Queued, Running, Downloading, Done, Failed }

data class Ui(
    val phase: Phase = Phase.Idle,
    val message: String = "",
    val runUrl: String? = null,
    val progress: Float? = null,
    val resultPath: String? = null,
) {
    val busy get() = phase in setOf(Phase.Dispatching, Phase.Queued, Phase.Running, Phase.Downloading)
}

/**
 * Build takibi ve indirme işini yürüten tekil motor. Activity/ViewModel'den bağımsız olduğu için
 * BuildService (foreground) içinde arka planda çalışmaya devam eder.
 */
object BuildEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private var job: Job? = null
    @Volatile private var activeRunId = 0L

    fun fail(msg: String) { _ui.value = Ui(Phase.Failed, msg) }

    fun start(ctx: Context, conn: Conn, form: Form) {
        if (_ui.value.busy) return
        val prefs = Prefs(ctx.applicationContext)
        val dir = ctx.applicationContext.getExternalFilesDir("builds")
        _ui.value = Ui(Phase.Dispatching, "Build tetikleniyor…")
        job = scope.launch {
            guarded {
                val api = GitHubApi(conn)
                val clientId = UUID.randomUUID().toString().take(8)
                api.dispatch(
                    conn.workflow, conn.ref,
                    mapOf(
                        "device" to form.device, "flasher_type" to form.kind, "region" to form.region,
                        "ota_url" to form.otaUrl, "base_ota_url" to form.baseOtaUrl,
                        "vulnerable_abl" to form.abl.ifEmpty { "none" }, "client_id" to clientId,
                    ),
                )
                var run: Run? = null
                var tries = 0
                while (run == null && tries++ < 15) {
                    delay(4_000)
                    run = api.findRun(conn.workflow, clientId)
                }
                val r = run ?: error("Tetiklenen çalışma bulunamadı. GitHub'da Actions sekmesini kontrol edin.")
                prefs.lastRunId = r.id
                track(api, r.id, dir)
            }
        }
    }

    /** Uygulama/işlem kapanmış olsa bile GitHub'daki koşu sürer; kayıtlı run'a yeniden bağlanır. */
    fun resume(ctx: Context, conn: Conn) {
        if (_ui.value.busy) return
        val prefs = Prefs(ctx.applicationContext)
        val id = prefs.lastRunId
        if (id <= 0) { fail("Devam edilecek build yok."); return }
        val dir = ctx.applicationContext.getExternalFilesDir("builds")
        _ui.value = Ui(Phase.Queued, "GitHub'a bağlanılıyor…")
        job = scope.launch { guarded { track(GitHubApi(conn), id, dir) } }
    }

    fun cancel(ctx: Context) {
        val id = activeRunId
        val conn = Prefs(ctx.applicationContext).loadConn()
        job?.cancel()
        _ui.value = Ui(Phase.Idle, "İptal edildi.")
        if (id > 0) scope.launch { runCatching { GitHubApi(conn).cancelRun(id) } }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { fail(e.message ?: "Bilinmeyen hata") }
    }

    private suspend fun track(api: GitHubApi, runId: Long, buildsDir: File?) {
        activeRunId = runId
        while (true) {
            val r = api.getRun(runId)
            if (r.status == "completed") {
                if (r.conclusion != "success") {
                    _ui.value = Ui(Phase.Failed, "Build başarısız (${r.conclusion ?: "bilinmiyor"}). Günlüğe GitHub'dan bakın.", r.htmlUrl)
                    return
                }
                break
            }
            _ui.value = if (r.status == "in_progress")
                Ui(Phase.Running, "GitHub'da derleniyor… (birkaç saat sürebilir)", r.htmlUrl)
            else Ui(Phase.Queued, "Sırada bekliyor…", r.htmlUrl)
            delay(10_000)
        }
        val art = api.artifacts(runId).firstOrNull { !it.expired }
            ?: error("Çıktı (artifact) bulunamadı; süresi dolmuş olabilir.")
        val dest = File(buildsDir ?: error("Depolama alanı kullanılamıyor."), "run-$runId")
        _ui.value = Ui(Phase.Downloading, "Paket indiriliyor…", progress = 0f)
        var last = 0f
        api.downloadArtifact(art, dest) { p ->
            if (p - last >= 0.005f) { last = p; _ui.update { it.copy(progress = p) } }
        }
        _ui.value = Ui(Phase.Done, "Hazır. Paket indirildi.", resultPath = dest.absolutePath)
    }
}
