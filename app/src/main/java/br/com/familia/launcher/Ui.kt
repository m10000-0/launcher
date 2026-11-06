package br.com.familia.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** Pede o PIN do administrador. Se ainda não existir, cria um. */
fun Activity.askPin(onOk: () -> Unit) {
    val prefs = Prefs(this)
    fun pinField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        gravity = Gravity.CENTER
        textSize = 28f
    }
    fun wrap(e: EditText) = LinearLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0); addView(e)
    }

    if (!prefs.hasPin) {
        val a = pinField()
        AlertDialog.Builder(this)
            .setTitle("Crie um PIN de administrador (4+ dígitos)")
            .setView(wrap(a))
            .setPositiveButton("Próximo") { _, _ ->
                val first = a.text.toString()
                if (first.length < 4) { toast("PIN muito curto"); return@setPositiveButton }
                val b = pinField()
                AlertDialog.Builder(this)
                    .setTitle("Repita o PIN")
                    .setView(wrap(b))
                    .setPositiveButton("OK") { _, _ ->
                        if (b.text.toString() == first) { prefs.setPin(first); onOk() }
                        else toast("Os PINs não conferem")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
        return
    }

    val e = pinField()
    AlertDialog.Builder(this)
        .setTitle("PIN do administrador")
        .setView(wrap(e))
        .setPositiveButton("Entrar") { _, _ ->
            if (prefs.checkPin(e.text.toString())) onOk() else toast("PIN errado")
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
