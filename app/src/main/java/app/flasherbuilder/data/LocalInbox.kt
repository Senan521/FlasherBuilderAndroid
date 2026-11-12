package app.flasherbuilder.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Cihazdan seçilen OTA/BASE ZIP dosyaları çalışma alanındaki "ota-inbox" klasörüne bir kez kopyalanır.
 * Backend (android_entry.py) bu klasördeki dosyayı kopyalamadan job klasörüne TAŞIR; ekstra yer gerekmez.
 * Klasör adı Python tarafındaki INBOX_DIR ile aynı olmalıdır.
 */
object LocalInbox {
    const val DIR = "ota-inbox"

    data class Staged(val path: String, val name: String, val bytes: Long)

    fun dir(ctx: Context): File = File(LocalEngine.workspace(ctx), DIR).apply { mkdirs() }

    /** Diskte duran seçimi okur (uygulama yeniden açılsa ya da dosya taşınmış olsa da doğru durumu verir). */
    fun existing(ctx: Context, role: String): Staged? {
        val f = File(dir(ctx), "$role.zip")
        return if (f.isFile && f.length() > 0) Staged(f.path, "$role.zip", f.length()) else null
    }

    fun clear(ctx: Context, role: String? = null) {
        if (role == null) dir(ctx).deleteRecursively() else File(dir(ctx), "$role.zip").delete()
    }

    suspend fun stage(ctx: Context, uri: Uri, role: String, onProgress: (Float) -> Unit): Staged =
        withContext(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            var name = "$role.zip"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            val free = LocalEngine.freeBytes(ctx)
            if (size > 0 && free < size + 1_000_000_000L) {
                throw IOException(
                    "Yetersiz alan: dosya ${"%.1f".format(size / 1e9)} GB, boş ${"%.1f".format(free / 1e9)} GB. " +
                        "Çalışma alanını temizleyin ya da bağlantıyla indirin."
                )
            }
            val dest = File(dir(ctx), "$role.zip").also { it.delete() }
            try {
                val input = resolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı.")
                input.use { src ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var done = 0L
                        var first = true
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            if (first) {
                                first = false
                                if (n < 2 || buf[0] != 'P'.code.toByte() || buf[1] != 'K'.code.toByte()) {
                                    throw IOException("Seçilen dosya bir ZIP değil. Tam OTA ZIP dosyasını seçin.")
                                }
                            }
                            out.write(buf, 0, n)
                            done += n
                            if (size > 0) onProgress((done.toFloat() / size).coerceIn(0f, 1f))
                            ensureActive()
                        }
                    }
                }
            } catch (t: Throwable) {
                dest.delete()
                throw t
            }
            Staged(dest.path, name, dest.length())
        }
}
