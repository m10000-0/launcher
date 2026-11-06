package br.com.familia.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import kotlin.math.ceil

/**
 * Aplica as regras de proteção. Só funciona de verdade quando o app é
 * "Dono do dispositivo" (Device Owner) — ver README.
 */
object Policies {
    private const val TAG = "Policies"

    fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)!!
    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    fun isDeviceOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    /** Restrições que valem sempre. */
    private fun alwaysOn(): List<String> = buildList {
        add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)        // nada de APK baixado do navegador/WhatsApp
        if (Build.VERSION.SDK_INT >= 29) add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        add(UserManager.DISALLOW_FACTORY_RESET)                  // não apaga o celular sem querer
        add(UserManager.DISALLOW_SAFE_BOOT)                      // não dá pra fugir do launcher pelo modo seguro
        add(UserManager.DISALLOW_ADD_USER)
        // Obs.: a depuração USB (adb) NÃO é bloqueada de propósito, para você conseguir dar manutenção.
    }

    private fun installRestrictions() = listOf(
        UserManager.DISALLOW_INSTALL_APPS,      // bloqueia também a Play Store
        UserManager.DISALLOW_UNINSTALL_APPS,    // ninguém apaga o WhatsApp sem querer
    )

    private fun networkRestrictions(): List<String> = buildList {
        add(UserManager.DISALLOW_CONFIG_WIFI)
        add(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
        if (Build.VERSION.SDK_INT >= 28) add(UserManager.DISALLOW_AIRPLANE_MODE)
        if (Build.VERSION.SDK_INT >= 33) add(UserManager.DISALLOW_CHANGE_WIFI_STATE)
    }

    private fun brightnessRestrictions(): List<String> =
        if (Build.VERSION.SDK_INT >= 28) listOf(UserManager.DISALLOW_CONFIG_BRIGHTNESS) else emptyList()

    /** Aplica tudo. Seguro chamar várias vezes. */
    fun apply(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        val p = Prefs(ctx)

        // 1) Este app é a tela inicial PARA SEMPRE (o Android não consegue trocar sozinho)
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(ctx, HomeActivity::class.java))
        dpm.setUninstallBlocked(admin, ctx.packageName, true)

        // 2) Restrições
        alwaysOn().forEach { set(ctx, it, true) }
        installRestrictions().forEach { set(ctx, it, !p.installsAllowedNow()) }
        networkRestrictions().forEach { set(ctx, it, p.lockNetwork) }
        brightnessRestrictions().forEach { set(ctx, it, p.lockBrightness) }
        // Garante que o volume nunca fique "travado no mudo"
        set(ctx, UserManager.DISALLOW_ADJUST_VOLUME, false)
        safe { dpm.setMasterVolumeMuted(admin, false) }

        // 3) Barra de notificações / atalhos rápidos
        safe { dpm.setStatusBarDisabled(admin, p.blockStatusBar) }

        // 4) Permissão de notificação para o próprio app (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) safe {
            dpm.setPermissionGrantState(
                admin, ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }

        // 5) Chrome: bloqueia notificações de sites (a maior fonte de "vírus" falsos),
        //    pop-ups e downloads perigosos. Políticas desconhecidas são ignoradas pelo Chrome.
        safe {
            dpm.setApplicationRestrictions(admin, "com.android.chrome", Bundle().apply {
                putInt("DefaultNotificationsSetting", 2)   // 2 = bloquear
                putInt("DefaultPopupsSetting", 2)
                putInt("SafeBrowsingProtectionLevel", 1)   // proteção padrão, sempre ligada
                putInt("DownloadRestrictions", 1)          // bloqueia downloads perigosos
            })
        }

        enforceRuntime(ctx)
    }

    /** Coisas que o usuário consegue mudar mesmo com restrições: corrige na hora. */
    fun enforceRuntime(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val p = Prefs(ctx)

        // Fim da liberação temporária da Play Store
        if (p.installsUnlockedUntil in 1..System.currentTimeMillis()) {
            p.installsUnlockedUntil = 0
            installRestrictions().forEach { set(ctx, it, !p.installsAllowedNow()) }
        }

        // Volume mínimo
        if (p.minVolumePercent > 0) {
            val am = ctx.getSystemService(AudioManager::class.java)
            safe { if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) am.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            for (stream in intArrayOf(
                AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL,
                AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM
            )) safe {
                val max = am.getStreamMaxVolume(stream)
                val min = ceil(max * p.minVolumePercent / 100.0).toInt().coerceIn(1, max)
                if (am.getStreamVolume(stream) < min) am.setStreamVolume(stream, min, 0)
                if (am.isStreamMute(stream)) am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            }
        }

        // Wi-Fi sempre ligado (dono do dispositivo pode religar)
        if (p.keepWifiOn) safe {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            if (!wm.isWifiEnabled) wm.isWifiEnabled = true
        }

        // Brilho fixo
        if (p.lockBrightness && Build.VERSION.SDK_INT >= 28) safe {
            val dpm = dpm(ctx); val admin = admin(ctx)
            if (p.autoBrightness) {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString())
            } else {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString())
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, p.brightness.toString())
            }
        }
    }

    /** Desfaz tudo e deixa de ser dono do dispositivo. */
    fun removeProtection(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx); val admin = admin(ctx)
        (alwaysOn() + installRestrictions() + networkRestrictions() + brightnessRestrictions())
            .forEach { set(ctx, it, false) }
        safe { dpm.setStatusBarDisabled(admin, false) }
        safe { dpm.setApplicationRestrictions(admin, "com.android.chrome", Bundle()) }
        safe { dpm.setUninstallBlocked(admin, ctx.packageName, false) }
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        ctx.stopService(Intent(ctx, GuardService::class.java))
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(ctx.packageName)
    }

    private fun set(ctx: Context, restriction: String, on: Boolean) = safe {
        if (on) dpm(ctx).addUserRestriction(admin(ctx), restriction)
        else dpm(ctx).clearUserRestriction(admin(ctx), restriction)
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "falhou: ${e.message}") }
    }
}
