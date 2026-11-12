package app.flasherbuilder.data

import android.content.Context
import org.json.JSONObject

data class RegionEntry(val region: String, val needsBase: Boolean)
data class KindEntry(val kind: String, val regions: List<RegionEntry>)
data class DeviceEntry(val name: String, val types: List<KindEntry>)

/** assets/catalog.json: repo'daki build_tools.yml tablolarından üretilen geçerli 86 kombinasyon. */
object Catalog {
    const val X9U = "OPPO Find X9 Ultra"
    const val R13 = "Oneplus 13R"      // özel odm.img yalnızca bu cihazda sunulur (Ace 5 kullanıcıları için)

    fun load(ctx: Context): List<DeviceEntry> {
        val text = ctx.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val devs = JSONObject(text).getJSONArray("devices")
        return (0 until devs.length()).map { i ->
            val d = devs.getJSONObject(i)
            val types = d.getJSONArray("types")
            DeviceEntry(
                d.getString("name"),
                (0 until types.length()).map { j ->
                    val t = types.getJSONObject(j)
                    val regs = t.getJSONArray("regions")
                    KindEntry(
                        t.getString("kind"),
                        (0 until regs.length()).map { k ->
                            val r = regs.getJSONObject(k)
                            RegionEntry(r.getString("region"), r.optBoolean("needsBase"))
                        },
                    )
                },
            )
        }
    }
}
