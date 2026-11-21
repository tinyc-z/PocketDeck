package com.pocketdeck.launcher.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pocketdeck.launcher.MainActivity
import com.pocketdeck.launcher.PocketDeckApp
import com.pocketdeck.launcher.R

/**
 * 局域网 Web 管理与游戏快传前台保活服务：
 * - 在 Web 管理端或快传任一服务开启期间以 `dataSync` 前台服务运行，避免息屏或切后台后进程网络被系统冻结；
 * - 同步持有 `PARTIAL_WAKE_LOCK`（保持 CPU 调度）与 `WifiLock`（避免 Wi-Fi 息屏降频断连），服务全停时立即释放。
 */
class LanForegroundService : Service() {

    private var partialWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        if (intent?.action == ACTION_STOP) {
            runCatching {
                (application as? PocketDeckApp)?.container?.lanManager?.stopAll()
            }
            shutdownForeground()
            return START_NOT_STICKY
        }

        val state = (application as? PocketDeckApp)?.container?.lanManager?.state?.value
        val webOn = state?.webRunning ?: (intent?.getBooleanExtra(EXTRA_WEB_RUNNING, false) == true)
        val shareOn = state?.shareRunning ?: (intent?.getBooleanExtra(EXTRA_SHARE_RUNNING, false) == true)
        val webAddr = state?.webUrl ?: intent?.getStringExtra(EXTRA_WEB_URL).orEmpty()
        val shareAddr = state?.shareIp ?: intent?.getStringExtra(EXTRA_SHARE_IP).orEmpty()

        val notification = buildNotification(webOn, shareOn, webAddr, shareAddr)
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)

        if (!webOn && !shareOn) {
            shutdownForeground()
            return START_NOT_STICKY
        }

        active = true
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ dataSync 前台服务达到单日时限时优雅停止，避免系统异常终止进程
        runCatching {
            (application as? PocketDeckApp)?.container?.lanManager?.stopAll()
        }
        shutdownForeground()
    }

    override fun onDestroy() {
        active = false
        releaseLocks()
        super.onDestroy()
    }

    private fun shutdownForeground() {
        active = false
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.lan_fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(
        webOn: Boolean,
        shareOn: Boolean,
        webUrl: String,
        shareIp: String,
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        val stopIntent = Intent(this, LanForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        val bodyText = when {
            webOn && shareOn -> getString(R.string.lan_fgs_body_both, webUrl.ifBlank { shareIp })
            webOn -> getString(R.string.lan_fgs_body_web, webUrl.ifBlank { shareIp })
            shareOn -> getString(R.string.lan_fgs_body_share, shareIp)
            else -> getString(R.string.lan_fgs_title)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.lan_fgs_title))
            .setContentText(bodyText)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.lan_fgs_stop), stopPi)
            .build()
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            if (partialWakeLock == null && pm != null) {
                partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            if (partialWakeLock?.isHeld == false) {
                partialWakeLock?.acquire(MAX_WAKE_LOCK_MS)
            }
        }
        runCatching {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            if (wifiLock == null && wm != null) {
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, WIFI_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (partialWakeLock?.isHeld == true) partialWakeLock?.release()
        }
        partialWakeLock = null
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wifiLock = null
    }

    companion object {
        private const val TAG = "LanForegroundService"
        private const val CHANNEL_ID = "pocketdeck_lan_service"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "com.pocketdeck.launcher.lan.ACTION_STOP"
        private const val EXTRA_WEB_RUNNING = "web_running"
        private const val EXTRA_SHARE_RUNNING = "share_running"
        private const val EXTRA_WEB_URL = "web_url"
        private const val EXTRA_SHARE_IP = "share_ip"
        private const val WAKE_LOCK_TAG = "PocketDeck:LanService"
        private const val WIFI_LOCK_TAG = "PocketDeck:LanWifi"
        private const val MAX_WAKE_LOCK_MS = 6L * 60L * 60L * 1000L

        @Volatile
        private var active: Boolean = false

        fun sync(context: Context, state: LanServerState) {
            val appCtx = context.applicationContext
            if (state.anyRunning) {
                val intent = Intent(appCtx, LanForegroundService::class.java).apply {
                    putExtra(EXTRA_WEB_RUNNING, state.webRunning)
                    putExtra(EXTRA_SHARE_RUNNING, state.shareRunning)
                    putExtra(EXTRA_WEB_URL, state.webUrl.orEmpty())
                    putExtra(EXTRA_SHARE_IP, state.shareIp.orEmpty())
                }
                runCatching {
                    ContextCompat.startForegroundService(appCtx, intent)
                }.onFailure { err ->
                    Log.w(TAG, "Failed to start LanForegroundService", err)
                }
            } else if (active) {
                runCatching {
                    appCtx.stopService(Intent(appCtx, LanForegroundService::class.java))
                }
            }
        }
    }
}
