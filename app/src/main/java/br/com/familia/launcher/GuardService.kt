package br.com.familia.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Fica rodando em segundo plano e desfaz na hora:
 *  - volume abaixado / modo silencioso
 *  - Wi-Fi desligado
 *  - brilho alterado
 * e encerra a liberação temporária da Play Store quando o tempo acaba.
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            Policies.enforceRuntime(this@GuardService)
            handler.postDelayed(this, 30_000)
        }
    }
    private val pending = Runnable { Policies.enforceRuntime(this) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // pequeno atraso para não brigar com o botão enquanto ele ainda é pressionado
            handler.removeCallbacks(pending)
            handler.postDelayed(pending, 800)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Proteção", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Celular protegido pela família")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        val f = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "guard"
        fun start(ctx: Context) {
            if (!Policies.isDeviceOwner(ctx)) return
            try {
                ctx.startForegroundService(Intent(ctx, GuardService::class.java))
            } catch (_: Exception) { }
        }
    }
}
