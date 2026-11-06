package br.com.familia.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Telephony

data class AppEntry(val component: ComponentName, val label: String, val icon: Drawable)

object Apps {
    /** Todos os apps que aparecem numa gaveta de apps comum. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString(),
                    it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Apps escolhidos para a tela inicial (ou uma seleção padrão sensata). */
    fun home(ctx: Context): List<AppEntry> {
        val all = launchable(ctx).associateBy { it.component.flattenToString() }
        val prefs = Prefs(ctx)
        val chosen = if (prefs.homeAppsConfigured) prefs.homeApps else defaults(ctx, all.values.toList())
        return chosen.mapNotNull { all[it] }
    }

    private fun defaults(ctx: Context, all: List<AppEntry>): List<String> {
        val pm = ctx.packageManager
        val packages = mutableListOf<String>()
        fun addFor(intent: Intent) {
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName
                ?.takeIf { it != "android" }?.let { packages += it }
        }
        addFor(Intent(Intent.ACTION_DIAL))                                       // Telefone
        packages += "com.whatsapp"
        addFor(Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.dir/contact")) // Contatos
        Telephony.Sms.getDefaultSmsPackage(ctx)?.let { packages += it }          // Mensagens
        addFor(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))         // Câmera
        packages += listOf("com.google.android.apps.photos", "com.google.android.youtube")

        val byPkg = all.groupBy { it.component.packageName }
        return packages.distinct().mapNotNull { byPkg[it]?.firstOrNull()?.component?.flattenToString() }
    }
}
