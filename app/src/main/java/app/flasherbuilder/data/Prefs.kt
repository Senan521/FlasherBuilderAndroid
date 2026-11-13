package app.flasherbuilder.data

import android.content.Context

/** Build formu. abl: "", "yes" veya "no" (yalnızca X9 Ultra). */
data class Form(
    val device: String = "",
    val kind: String = "",
    val region: String = "",
    val otaUrl: String = "",
    val baseOtaUrl: String = "",
    val abl: String = "",
    val odmUrl: String = "",
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("flasher", Context.MODE_PRIVATE)
    private fun s(k: String) = sp.getString(k, "") ?: ""

    fun loadForm() = Form(s("device"), s("kind"), s("region"), s("ota_url"), s("base_ota_url"), s("abl"), s("odm_url"))

    fun saveForm(f: Form) = sp.edit()
        .putString("device", f.device).putString("kind", f.kind)
        .putString("region", f.region).putString("ota_url", f.otaUrl)
        .putString("base_ota_url", f.baseOtaUrl).putString("abl", f.abl)
        .putString("odm_url", f.odmUrl).apply()
}
