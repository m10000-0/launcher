package br.com.familia.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Painel da família (aberto segurando o relógio + PIN). */
class AdminActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        box.removeAllViews()
        val owner = Policies.isDeviceOwner(this)

        header(if (owner) "✅ Proteção ativa (dono do dispositivo)" else "❌ Proteção NÃO ativa")
        if (!owner) {
            text(
                "Para ativar, conecte o celular no computador e rode:\n\n" +
                    "adb shell dpm set-device-owner $packageName/.AdminReceiver\n\n" +
                    "(precisa remover as contas Google antes; veja o README)."
            )
        }

        header("Instalação de apps")
        text("Fora da Play Store: sempre bloqueado.")
        switch("Permitir instalar/atualizar pela Play Store", prefs.allowInstalls) {
            prefs.allowInstalls = it; applyAll()
        }
        val until = prefs.installsUnlockedUntil
        if (!prefs.allowInstalls && until > System.currentTimeMillis()) {
            text("Liberado temporariamente até " + DateFormat.getTimeFormat(this).format(until))
            button("Bloquear agora") { prefs.installsUnlockedUntil = 0; applyAll() }
        } else if (!prefs.allowInstalls) {
            button("Liberar Play Store por 1 hora (para atualizar)") {
                prefs.installsUnlockedUntil = System.currentTimeMillis() + 60 * 60 * 1000L; applyAll()
            }
        }

        header("Internet")
        switch("Travar Wi-Fi, dados móveis e modo avião", prefs.lockNetwork) {
            prefs.lockNetwork = it; applyAll()
        }
        switch("Religar o Wi-Fi sozinho se desligarem", prefs.keepWifiOn) {
            prefs.keepWifiOn = it; applyAll()
        }

        header("Brilho")
        switch("Travar brilho", prefs.lockBrightness) { prefs.lockBrightness = it; applyAll() }
        if (prefs.lockBrightness) {
            switch("Brilho automático", prefs.autoBrightness) { prefs.autoBrightness = it; applyAll() }
            if (!prefs.autoBrightness) {
                slider("Nível do brilho", prefs.brightness * 100 / 255, 5) {
                    prefs.brightness = it * 255 / 100; applyAll()
                }
            }
        }
        if (Build.VERSION.SDK_INT < 28) text("(Travar brilho precisa de Android 9 ou mais novo)")

        header("Volume")
        slider("Volume mínimo (0% = não controlar)", prefs.minVolumePercent, 0) {
            prefs.minVolumePercent = it; applyAll()
        }

        header("Barra de cima")
        switch("Bloquear barra de notificações e atalhos rápidos", prefs.blockStatusBar) {
            prefs.blockStatusBar = it; applyAll()
        }
        text("Bloqueando, eles não conseguem desligar nada pelos atalhos, mas também não veem notificações na barra.")

        header("Tela inicial")
        button("Escolher apps da tela inicial") { chooseApps() }

        header("Manutenção")
        button("Abrir configurações do Android") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        button("Trocar PIN") { changePin() }
        if (owner) button("Desativar proteção (remove dono do dispositivo)") { confirmRemove() }
    }

    private fun applyAll() {
        Policies.apply(this)
        render()
    }

    // ---------- diálogos ----------

    private fun chooseApps() {
        val all = Apps.launchable(this)
        val current = Apps.home(this).map { it.component.flattenToString() }
        val checked = BooleanArray(all.size) { all[it].component.flattenToString() in current }
        AlertDialog.Builder(this)
            .setTitle("Apps na tela inicial")
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Salvar") { _, _ ->
                val selected = all.indices.filter { checked[it] }.map { all[it].component.flattenToString() }
                // mantém a ordem antiga e coloca os novos no final
                prefs.homeApps = current.filter { it in selected } + selected.filter { it !in current }
                toast("Tela inicial atualizada")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun changePin() {
        val e = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 28f; gravity = android.view.Gravity.CENTER
        }
        AlertDialog.Builder(this)
            .setTitle("Novo PIN (4+ dígitos)")
            .setView(e)
            .setPositiveButton("Salvar") { _, _ ->
                val pin = e.text.toString()
                if (pin.length >= 4) { prefs.setPin(pin); toast("PIN trocado") } else toast("PIN muito curto")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle("Desativar proteção?")
            .setMessage("Todas as travas serão removidas e o app deixa de ser dono do dispositivo. " +
                "Para ativar de novo será preciso o computador (adb).")
            .setPositiveButton("Desativar") { _, _ -> Policies.removeProtection(this); render() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- componentes ----------

    private fun header(t: String) = box.addView(TextView(this).apply {
        text = t; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(6)); setTextColor(Color.parseColor("#1B3A5C"))
    })

    private fun text(t: String) = box.addView(TextView(this).apply {
        text = t; textSize = 15f; setTextIsSelectable(true); setPadding(0, dp(2), 0, dp(6))
    })

    private fun switch(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
        box.addView(Switch(this).apply {
            text = label; textSize = 17f; isChecked = value
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })

    private fun button(label: String, onClick: () -> Unit) = box.addView(Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
    })

    private fun slider(label: String, percent: Int, min: Int, onDone: (Int) -> Unit) {
        val title = TextView(this).apply { textSize = 17f; text = "$label: $percent%" }
        val bar = SeekBar(this).apply {
            max = 100; progress = percent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    title.text = "$label: ${p.coerceAtLeast(min)}%"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) = onDone(s.progress.coerceAtLeast(min))
            })
        }
        box.addView(title); box.addView(bar)
        box.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
    }
}
