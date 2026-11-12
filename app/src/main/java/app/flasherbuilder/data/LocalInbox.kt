package app.flasherbuilder.data

import app.flasherbuilder.R
import app.flasherbuilder.Str
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

    private fun fileName(role: String) = if (role == "ODM") "ODM.img" else "$role.zip"

    /** sparse / erofs / ext4 imaj imzası (odm.img doğrulaması). */
    private fun looksLikeImage(head: ByteArray): Boolean {
        fun at(off: Int, vararg b: Int) = head.size >= off + b.size && b.indices.all { head[off + it] == b[it].toByte() }
        return at(0, 0x3a, 0xff, 0x26, 0xed) || at(1024, 0xe2, 0xe1, 0xf5, 0xe0) || at(1080, 0x53, 0xef)
    }

    fun dir(ctx: Context): File = File(LocalEngine.workspace(ctx), DIR).apply { mkdirs() }

    /** Diskte duran seçimi okur (uygulama yeniden açılsa ya da dosya taşınmış olsa da doğru durumu verir). */
    fun existing(ctx: Context, role: String): Staged? {
        val f = File(dir(ctx), fileName(role))
        return if (f.isFile && f.length() > 0) Staged(f.path, f.name, f.length()) else null
    }

    fun clear(ctx: Context, role: String? = null) {
        if (role == null) dir(ctx).deleteRecursively() else File(dir(ctx), fileName(role)).delete()
    }

    suspend fun stage(ctx: Context, uri: Uri, role: String, onProgress: (Float) -> Unit): Staged =
        withContext(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            var name = fileName(role)
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
                throw IOException(Str.get(R.string.err_inbox_space, "%.1f".format(size / 1e9), "%.1f".format(free / 1e9)))
            }
            val dest = File(dir(ctx), fileName(role)).also { it.delete() }
            try {
                val input = resolver.openInputStream(uri) ?: throw IOException(Str.get(R.string.err_open_file))
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
                                if (role != "ODM" && (n < 2 || buf[0] != 'P'.code.toByte() || buf[1] != 'K'.code.toByte())) {
                                    throw IOException(Str.get(R.string.err_not_zip))
                                }
                            }
                            out.write(buf, 0, n)
                            done += n
                            if (size > 0) onProgress((done.toFloat() / size).coerceIn(0f, 1f))
                            ensureActive()
                        }
                    }
                }
                if (role == "ODM") {
                    val head = ByteArray(4096)
                    val n = dest.inputStream().use { it.read(head) }
                    if (n < 1088 || !looksLikeImage(head)) throw IOException(Str.get(R.string.err_not_img))
                }
            } catch (t: Throwable) {
                dest.delete()
                throw t
            }
            Staged(dest.path, name, dest.length())
        }
}
