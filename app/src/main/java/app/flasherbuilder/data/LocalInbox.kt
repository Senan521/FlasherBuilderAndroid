package app.flasherbuilder.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.flasherbuilder.R
import app.flasherbuilder.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Cihazdan seçilen/çıkarılan dosyaların çalışma alanındaki "ota-inbox" klasörü.
 *  - OTA.zip / BASE.zip: backend bunları kopyalamadan job klasörüne TAŞIR (ekstra yer gerekmez).
 *  - images/<bölüm>.img: build'e eklenen, ROM'daki imajın yerine geçecek imajlar (ör. odm.img, boot.img).
 * Klasör adı Python tarafındaki INBOX_DIR ile aynı olmalıdır.
 */
object LocalInbox {
    const val DIR = "ota-inbox"
    private const val IMAGES = "images"

    /** Builder'ın kendisinin ürettiği imajlar; elle değiştirmek AVB doğrulamasını bozar (Python tarafıyla aynı liste). */
    val BLOCKED = setOf("vbmeta", "vbmeta_system", "vbmeta_vendor", "super")

    data class Staged(val path: String, val name: String, val bytes: Long)

    fun dir(ctx: Context): File = File(LocalEngine.workspace(ctx), DIR).apply { mkdirs() }
    fun imagesDir(ctx: Context): File = File(dir(ctx), IMAGES).apply { mkdirs() }

    /** Diskte duran OTA/BASE seçimini okur (uygulama yeniden açılsa ya da dosya taşınmış olsa da doğru durumu verir). */
    fun existing(ctx: Context, role: String): Staged? {
        val f = File(dir(ctx), "$role.zip")
        return if (f.isFile && f.length() > 0) Staged(f.path, f.name, f.length()) else null
    }

    /** ROM'daki yerine geçecek imajlar (ad = bölüm adı). */
    fun images(ctx: Context): List<Staged> =
        imagesDir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".img") && f.length() > 0 }
            ?.sortedBy { it.name }
            ?.map { Staged(it.path, it.name.removeSuffix(".img"), it.length()) }
            .orEmpty()

    fun removeImage(ctx: Context, name: String) { File(imagesDir(ctx), "$name.img").delete() }

    fun clear(ctx: Context, role: String? = null) {
        if (role == null) dir(ctx).deleteRecursively() else File(dir(ctx), "$role.zip").delete()
    }

    private fun displayInfo(ctx: Context, uri: Uri, fallback: String): Pair<String, Long> {
        var name = fallback
        var size = -1L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        return name to size
    }

    private fun checkSpace(ctx: Context, size: Long) {
        val free = LocalEngine.freeBytes(ctx)
        if (size > 0 && free < size + 1_000_000_000L) {
            throw IOException(Str.get(R.string.err_inbox_space, "%.1f".format(size / 1e9), "%.1f".format(free / 1e9)))
        }
    }

    private suspend fun copyTo(ctx: Context, uri: Uri, dest: File, size: Long, firstChunk: (ByteArray, Int) -> Unit, onProgress: (Float) -> Unit) {
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException(Str.get(R.string.err_open_file))
            input.use { src ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    var first = true
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        if (first) { first = false; firstChunk(buf, n) }
                        out.write(buf, 0, n)
                        done += n
                        if (size > 0) onProgress((done.toFloat() / size).coerceIn(0f, 1f))
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    /** Tam OTA / BASE ZIP dosyasını seçer ve kopyalar. */
    suspend fun stage(ctx: Context, uri: Uri, role: String, onProgress: (Float) -> Unit): Staged =
        withContext(Dispatchers.IO) {
            val (name, size) = displayInfo(ctx, uri, "$role.zip")
            checkSpace(ctx, size)
            val dest = File(dir(ctx), "$role.zip").also { it.delete() }
            copyTo(ctx, uri, dest, size, { b, n ->
                if (n < 2 || b[0] != 'P'.code.toByte() || b[1] != 'K'.code.toByte()) throw IOException(Str.get(R.string.err_not_zip))
            }, onProgress)
            Staged(dest.path, name, dest.length())
        }

    /** Cihazdan bir imaj dosyası seçer. Bölüm adı dosya adından alınır (odm.img → odm). */
    suspend fun stageImage(ctx: Context, uri: Uri, onProgress: (Float) -> Unit): Staged =
        withContext(Dispatchers.IO) {
            val (name, size) = displayInfo(ctx, uri, "image.img")
            val part = name.lowercase().removeSuffix(".img")
            if (!name.lowercase().endsWith(".img") || !Regex("[a-z0-9_]+").matches(part)) {
                throw IOException(Str.get(R.string.err_image_name))
            }
            if (part in BLOCKED) throw IOException(Str.get(R.string.err_image_blocked, part))
            checkSpace(ctx, size)
            val dest = File(imagesDir(ctx), "$part.img").also { it.delete() }
            copyTo(ctx, uri, dest, size, { b, n ->
                if (n < 8 || (b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte())) throw IOException(Str.get(R.string.err_not_img))
            }, onProgress)
            if (dest.length() == 0L) { dest.delete(); throw IOException(Str.get(R.string.err_not_img)) }
            Staged(dest.path, part, dest.length())
        }
}
