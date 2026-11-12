package app.flasherbuilder.data

import app.flasherbuilder.R
import app.flasherbuilder.Str
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * APK'ya jniLibs/arm64-v8a içinde lib*.so adıyla gömülü yerel araçlar.
 * Android 10+ yalnızca nativeLibraryDir'den çalıştırmaya izin verir (uygulama veri klasöründen değil).
 */
object NativeTools {
    data class Probe(val name: String, val ok: Boolean, val detail: String)

    private fun dir(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir)
    fun otaripper(ctx: Context) = File(dir(ctx), "libotaripper.so")
    fun lpmake(ctx: Context) = File(dir(ctx), "liblpmake.so")

    /** Her aracı bir kez çalıştırıp ilk çıktı satırını döndürür (cihazda çalıştığını doğrular). */
    suspend fun probe(ctx: Context): List<Probe> = withContext(Dispatchers.IO) {
        listOf(
            run("otaripper", otaripper(ctx), "--version"),
            run("lpmake", lpmake(ctx), "--help"),
        )
    }

    private fun run(name: String, exe: File, arg: String): Probe {
        if (!exe.isFile) return Probe(name, false, Str.get(R.string.probe_missing))
        return try {
            val p = ProcessBuilder(exe.path, arg).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readLine().orEmpty() }
            if (!p.waitFor(10, TimeUnit.SECONDS)) p.destroyForcibly()
            Probe(name, out.isNotBlank(), out.ifBlank { Str.get(R.string.probe_no_output) })
        } catch (e: Exception) {
            Probe(name, false, e.message ?: e.javaClass.simpleName)
        }
    }
}
