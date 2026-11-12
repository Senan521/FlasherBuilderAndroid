package app.flasherbuilder.data

import app.flasherbuilder.R
import app.flasherbuilder.Str
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Biten ZIP'i (ve .sha256 dosyasını) Download/FlasherBuilder klasörüne kopyalar; izin gerekmez (MediaStore). */
object LocalExport {
    suspend fun toDownloads(ctx: Context, zip: File, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (!zip.isFile) throw IOException(Str.get(R.string.err_file_missing, zip.name))
            val free = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            if (free < zip.length() + 200_000_000L) {
                throw IOException(Str.get(R.string.err_export_space))
            }
            val sha = File(zip.path + ".sha256")
            val total = zip.length().toFloat().coerceAtLeast(1f)
            copyOne(ctx, zip, "application/zip") { onProgress((it / total).coerceIn(0f, 1f)) }
            if (sha.isFile) copyOne(ctx, sha, "text/plain") { }
            "Download/FlasherBuilder/${zip.name}"
        }

    private suspend fun copyOne(ctx: Context, src: File, mime: String, onBytes: (Long) -> Unit) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlasherBuilder")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException(Str.get(R.string.err_mediastore))
        try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { input ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onBytes(done)
                        kotlin.coroutines.coroutineContext.ensureActive()
                    }
                }
            } ?: throw IOException(Str.get(R.string.err_out_stream))
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
