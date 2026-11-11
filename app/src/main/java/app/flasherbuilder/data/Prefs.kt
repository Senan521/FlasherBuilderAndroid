package app.flasherbuilder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** GitHub bağlantı ayarları. Token, Android Keystore ile şifreli saklanır. */
data class Conn(
    val owner: String = "",
    val repo: String = "",
    val ref: String = "main",
    val workflow: String = "cloud_build.yml",
    val token: String = "",
) {
    fun isComplete() = owner.isNotBlank() && repo.isNotBlank() &&
        token.isNotBlank() && workflow.isNotBlank() && ref.isNotBlank()
}

/** Build formu. Alanlar cloud_build.yml girdileriyle eşleşir. abl: "", "yes" veya "no" (yalnızca X9 Ultra). */
data class Form(
    val device: String = "",
    val kind: String = "",
    val region: String = "",
    val otaUrl: String = "",
    val baseOtaUrl: String = "",
    val abl: String = "",
)

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "flasher_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun s(k: String, d: String = "") = sp.getString(k, d) ?: d

    fun loadConn() = Conn(s("owner"), s("repo"), s("ref", "main"), s("workflow", "cloud_build.yml"), s("token"))
    fun saveConn(c: Conn) = sp.edit()
        .putString("owner", c.owner.trim()).putString("repo", c.repo.trim())
        .putString("ref", c.ref.trim()).putString("workflow", c.workflow.trim())
        .putString("token", c.token.trim()).apply()

    fun loadForm() = Form(s("device"), s("kind"), s("region"), s("ota_url"), s("base_ota_url"), s("abl"))
    fun saveForm(f: Form) = sp.edit()
        .putString("device", f.device).putString("kind", f.kind)
        .putString("region", f.region).putString("ota_url", f.otaUrl)
        .putString("base_ota_url", f.baseOtaUrl).putString("abl", f.abl).apply()

    var lastRunId: Long
        get() = sp.getLong("last_run", 0L)
        set(v) { sp.edit().putLong("last_run", v).apply() }
}
