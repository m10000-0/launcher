package br.com.familia.launcher

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Configurações escolhidas pelo administrador (neto/filho), salvas no aparelho. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    /** Play Store pode instalar/atualizar/desinstalar apps? (fora da Play Store é SEMPRE bloqueado) */
    var allowInstalls: Boolean
        get() = sp.getBoolean("allowInstalls", false)
        set(v) = sp.edit().putBoolean("allowInstalls", v).apply()

    /** Se > agora, a instalação está liberada temporariamente até esse horário (millis). */
    var installsUnlockedUntil: Long
        get() = sp.getLong("installsUnlockedUntil", 0L)
        set(v) = sp.edit().putLong("installsUnlockedUntil", v).apply()

    fun installsAllowedNow(): Boolean =
        allowInstalls || installsUnlockedUntil > System.currentTimeMillis()

    /** Impede mexer em Wi-Fi, dados móveis e modo avião. */
    var lockNetwork: Boolean
        get() = sp.getBoolean("lockNetwork", true)
        set(v) = sp.edit().putBoolean("lockNetwork", v).apply()

    /** Religa o Wi-Fi automaticamente se alguém desligar. */
    var keepWifiOn: Boolean
        get() = sp.getBoolean("keepWifiOn", true)
        set(v) = sp.edit().putBoolean("keepWifiOn", v).apply()

    /** Impede mudar o brilho. */
    var lockBrightness: Boolean
        get() = sp.getBoolean("lockBrightness", true)
        set(v) = sp.edit().putBoolean("lockBrightness", v).apply()

    var autoBrightness: Boolean
        get() = sp.getBoolean("autoBrightness", false)
        set(v) = sp.edit().putBoolean("autoBrightness", v).apply()

    /** Brilho fixo, 0..255 */
    var brightness: Int
        get() = sp.getInt("brightness", 200)
        set(v) = sp.edit().putInt("brightness", v.coerceIn(10, 255)).apply()

    /** Volume mínimo permitido (0..100 %). 0 = não controla. */
    var minVolumePercent: Int
        get() = sp.getInt("minVolumePercent", 70)
        set(v) = sp.edit().putInt("minVolumePercent", v.coerceIn(0, 100)).apply()

    /** Bloqueia a barra de notificações / atalhos rápidos (onde eles desligam Wi-Fi, brilho...). */
    var blockStatusBar: Boolean
        get() = sp.getBoolean("blockStatusBar", false)
        set(v) = sp.edit().putBoolean("blockStatusBar", v).apply()

    /** Apps da tela inicial, em ordem. Cada item é "pacote/atividade". */
    var homeApps: List<String>
        get() = sp.getString("homeApps", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("homeApps", v.joinToString("\n")).apply()

    val homeAppsConfigured: Boolean get() = sp.contains("homeApps")

    // ---------- PIN ----------

    val hasPin: Boolean get() = sp.contains("pinHash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("pinSalt", salt.toHex())
            .putString("pinHash", hash(salt, pin))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pinSalt", null)?.fromHex() ?: return false
        return hash(salt, pin) == sp.getString("pinHash", null)
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray()).toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
