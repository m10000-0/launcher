package br.com.familia.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reaplica a proteção ao ligar o celular e após atualizar o app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Policies.apply(context)
        GuardService.start(context)
    }
}
