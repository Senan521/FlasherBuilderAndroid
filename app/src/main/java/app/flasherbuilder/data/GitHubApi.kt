package app.flasherbuilder.data

import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class Run(val id: Long, val status: String, val conclusion: String?, val htmlUrl: String)
data class Artifact(val id: Long, val name: String, val size: Long, val expired: Boolean)

private class CountingInputStream(`in`: InputStream) : FilterInputStream(`in`) {
    @Volatile var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) count += it }
}

class GitHubApi(private val c: Conn) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.github.com/repos/${c.owner}/${c.repo}"

    // Not: OkHttp, farklı host'a yönlenirken Authorization başlığını otomatik düşürür
    // (artifact indirmesi imzalı depolama adresine 302 ile gider; istenen davranış bu).
    private fun req(url: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer ${c.token}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun describe(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when (code) {
            401 -> "Token geçersiz veya süresi dolmuş (401)."
            403 -> "Erişim reddedildi (403). Token'da Actions: Read and write izni var mı? $msg"
            404 -> "Repo, workflow dosyası veya kayıt bulunamadı (404). Ayarları kontrol edin."
            else -> "GitHub hatası $code: $msg"
        }
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(req(base + path).build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(describe(r.code, body))
            JSONObject(body)
        }
    }

    private fun parseRun(o: JSONObject) = Run(
        id = o.getLong("id"),
        status = o.optString("status"),
        conclusion = if (o.isNull("conclusion")) null else o.getString("conclusion"),
        htmlUrl = o.optString("html_url"),
    )

    suspend fun dispatch(workflow: String, ref: String, inputs: Map<String, String>) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("ref", ref).put("inputs", JSONObject(inputs))
                .toString().toRequestBody("application/json".toMediaType())
            val request = req("$base/actions/workflows/$workflow/dispatches").post(body).build()
            http.newCall(request).execute().use { r ->
                if (r.code != 204) throw IOException(describe(r.code, r.body?.string().orEmpty()))
            }
        }

    /** workflow `run-name` değerinde clientId geçer; böylece tetiklediğimiz koşuyu buluruz. */
    suspend fun findRun(workflow: String, clientId: String): Run? {
        val arr = getJson("/actions/workflows/$workflow/runs?event=workflow_dispatch&per_page=15")
            .getJSONArray("workflow_runs")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("display_title").contains(clientId)) return parseRun(o)
        }
        return null
    }

    suspend fun getRun(id: Long): Run = parseRun(getJson("/actions/runs/$id"))

    suspend fun cancelRun(id: Long) = withContext(Dispatchers.IO) {
        val request = req("$base/actions/runs/$id/cancel").post("".toRequestBody()).build()
        http.newCall(request).execute().close()
    }

    suspend fun artifacts(runId: Long): List<Artifact> {
        val arr = getJson("/actions/runs/$runId/artifacts").getJSONArray("artifacts")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Artifact(o.getLong("id"), o.optString("name"), o.optLong("size_in_bytes"), o.optBoolean("expired"))
        }
    }

    /**
     * Artifact ZIP'ini indirirken AKIŞ halinde açar: dış ZIP diske hiç yazılmaz,
     * içindeki dosyalar doğrudan hedefe çıkar (telefonda 2 kat alan gerekmez).
     */
    suspend fun downloadArtifact(a: Artifact, dest: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            dest.mkdirs()
            val free = StatFs(dest.path).availableBytes
            if (free < a.size + 256L * 1024 * 1024) {
                throw IOException(
                    "Yetersiz alan: ${free / 1_000_000_000} GB boş, ${a.size / 1_000_000_000 + 1} GB gerekli."
                )
            }
            try {
                http.newCall(req("$base/actions/artifacts/${a.id}/zip").build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException(describe(r.code, r.body?.string().orEmpty()))
                    val counter = CountingInputStream(r.body!!.byteStream())
                    val root = dest.canonicalFile
                    val buf = ByteArray(1 shl 16)
                    ZipInputStream(BufferedInputStream(counter, 1 shl 16)).use { zin ->
                        var e = zin.nextEntry
                        while (e != null) {
                            ensureActive()
                            val out = File(root, e.name).canonicalFile
                            if (!out.path.startsWith(root.path + File.separator)) {
                                throw IOException("Geçersiz dosya yolu: ${e.name}")
                            }
                            if (e.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { fos ->
                                    while (true) {
                                        val n = zin.read(buf)
                                        if (n < 0) break
                                        fos.write(buf, 0, n)
                                        ensureActive()
                                        if (a.size > 0) onProgress((counter.count.toFloat() / a.size).coerceIn(0f, 1f))
                                    }
                                }
                            }
                            e = zin.nextEntry
                        }
                    }
                }
            } catch (t: Throwable) {
                dest.deleteRecursively()   // yarım kalan dosyaları bırakma
                throw t
            }
        }
}
