package app.flasherbuilder.data

import app.flasherbuilder.R
import app.flasherbuilder.Str
import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** APK içindeki repo.zip'i (Flasher_Builder'ın build için gereken kısmı) uygulama deposuna açar. */
object RepoBundle {
    @Synchronized
    fun ensure(ctx: Context): File {
        val dest = File(ctx.filesDir, "repo")
        val stamp = File(ctx.filesDir, "repo.stamp")
        // Her uygulama güncellemesinde yeniden açılır.
        val expected = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (dest.isDirectory && stamp.isFile && stamp.readText() == expected) return dest

        stamp.delete()
        dest.deleteRecursively()
        try {
            val root = dest.apply { mkdirs() }.canonicalFile
            val buf = ByteArray(1 shl 16)
            ZipInputStream(ctx.assets.open("repo.zip").buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val out = File(root, e.name).canonicalFile
                    if (!out.path.startsWith(root.path + File.separator)) throw IOException(Str.get(R.string.err_bad_path, e.name))
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { fos ->
                            while (true) {
                                val n = zin.read(buf)
                                if (n < 0) break
                                fos.write(buf, 0, n)
                            }
                        }
                    }
                    e = zin.nextEntry
                }
            }
            stamp.writeText(expected)
        } catch (t: Throwable) {
            dest.deleteRecursively()
            throw t
        }
        return dest
    }
}
