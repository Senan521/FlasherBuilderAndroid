package app.flasherbuilder

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/** Arayüz dışı kodun (motorlar, servis) yerelleştirilmiş metin alabilmesi için uygulama bağlamı. */
object Str {
    private lateinit var app: Context

    fun init(context: Context) { app = context.applicationContext }

    fun get(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)
}

class FlasherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Str.init(this)
    }
}
