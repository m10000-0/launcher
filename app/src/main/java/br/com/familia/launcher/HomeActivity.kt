package br.com.familia.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView

/**
 * Tela inicial simples: relógio grande + botões grandes dos apps escolhidos.
 * Não tem como arrastar, apagar ou editar nada.
 * Para abrir o painel da família: segure o relógio por alguns segundos.
 */
class HomeActivity : Activity() {

    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var warning: TextView

    private val refresh = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = 72f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d 'de' MMMM"; format24Hour = "EEEE, d 'de' MMMM"
            textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        // Acesso escondido ao painel da família
        val openAdmin = View.OnLongClickListener {
            askPin { startActivity(Intent(this, AdminActivity::class.java)) }; true
        }
        clock.setOnLongClickListener(openAdmin)
        date.setOnLongClickListener(openAdmin)

        status = TextView(this).apply {
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        warning = TextView(this).apply {
            textSize = 16f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER
            text = "⚠ Proteção não ativada (veja o README)"
            setPadding(0, 0, 0, dp(8))
        }

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(clock); root.addView(date); root.addView(status); root.addView(warning)
        root.addView(ScrollView(this).apply { addView(grid); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        Policies.apply(this)
        GuardService.start(this)
        warning.visibility = if (Policies.isDeviceOwner(this)) View.GONE else View.VISIBLE
        buildGrid()
        registerReceiver(refresh, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(refresh) } catch (_: Exception) { }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* tela inicial: voltar não faz nada */ }

    private fun buildGrid() {
        grid.removeAllViews()
        val apps = Apps.home(this)
        apps.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { row.addView(tile(it), LinearLayout.LayoutParams(0, dp(170), 1f).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                setMargins(dp(8), 0, dp(8), 0)
            })
            grid.addView(row)
        }
        if (apps.isEmpty()) grid.addView(TextView(this).apply {
            text = "Nenhum app escolhido.\nSegure o relógio para configurar."
            textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
    }

    private fun tile(app: AppEntry): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(24).toFloat()
        }
        addView(ImageView(context).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(dp(80), dp(80)))
        addView(TextView(context).apply {
            text = app.label; textSize = 22f; setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 2
            setPadding(dp(4), dp(8), dp(4), 0)
        })
        contentDescription = app.label
        setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { toast("Não foi possível abrir ${app.label}") }
        }
        // Sem clique longo: nada de editar/arrastar/apagar.
    }

    private fun updateStatus() {
        val bm = getSystemService(BatteryManager::class.java)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "❌ Sem internet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "📶 Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "📶 Dados"
            else -> "📶 Internet"
        }
        status.text = "$net    🔋 $battery%"
    }
}
