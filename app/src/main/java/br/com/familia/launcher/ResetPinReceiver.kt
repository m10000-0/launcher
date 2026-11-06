package br.com.familia.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * "Esqueci o PIN": apaga o PIN. Protegido pela permissão DUMP, que apps comuns
 * não conseguem ter — na prática só o computador via adb consegue chamar:
 *   adb shell am broadcast -a br.com.familia.launcher.RESET_PIN -n br.com.familia.launcher/.ResetPinReceiver
 */
class ResetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit()
            .remove("pinHash").remove("pinSalt").apply()
        resultData = "PIN apagado"
    }
    companion object { const val ACTION = "br.com.familia.launcher.RESET_PIN" }
}
