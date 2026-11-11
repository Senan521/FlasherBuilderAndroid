package app.flasherbuilder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import app.flasherbuilder.data.BuildEngine
import app.flasherbuilder.data.Phase
import app.flasherbuilder.data.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Build takibi/indirme sürerken süreci canlı tutar ve ilerlemeyi bildirimde gösterir. */
class BuildService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Build ilerlemesi", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Build sonucu", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) BuildEngine.cancel(applicationContext)
        startForeground(NOTIF_PROGRESS, progressNotification(BuildEngine.ui.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        acquireWakeLock()
        if (watcher == null) {
            watcher = scope.launch {
                BuildEngine.ui.collect { ui ->
                    if (ui.busy) nm.notify(NOTIF_PROGRESS, progressNotification(ui)) else finish(ui)
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15+: dataSync servisi ~6 saat sonra durdurulur; ilerleme GitHub'da sürer, uygulamadan devam edilir. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun finish(ui: Ui) {
        watcher?.cancel(); watcher = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (ui.phase == Phase.Done || ui.phase == Phase.Failed) {
            val done = Notification.Builder(this, CH_DONE)
                .setSmallIcon(
                    if (ui.phase == Phase.Done) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setContentTitle(if (ui.phase == Phase.Done) "Build tamamlandı" else "Build başarısız")
                .setContentText(ui.message)
                .setStyle(Notification.BigTextStyle().bigText(ui.resultPath?.let { "${ui.message}\n$it" } ?: ui.message))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_DONE, done)
        }
        stopSelf()
    }

    private fun progressNotification(ui: Ui): Notification {
        val b = Notification.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Flasher Builder")
            .setContentText(ui.message.ifEmpty { "Hazırlanıyor…" })
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null, "İptal",
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, BuildService::class.java).setAction(ACTION_CANCEL),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
            )
        val p = ui.progress
        if (ui.phase == Phase.Downloading && p != null) b.setProgress(100, (p * 100).toInt(), false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "flasherbuilder:build")
            .apply { acquire(6 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_CANCEL = "app.flasherbuilder.CANCEL"
        private const val CH_PROGRESS = "progress"
        private const val CH_DONE = "done"
        private const val NOTIF_PROGRESS = 1
        private const val NOTIF_DONE = 2
    }
}
