package com.blackmesa.watch

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var priceView: TextView
    private lateinit var statusView: TextView
    private lateinit var countdownView: TextView
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 17, 19)
        window.navigationBarColor = Color.rgb(12, 17, 19)
        requestNotificationPermission()
        buildScreen()
        DailyAlarmReceiver.scheduleNext(this)
        refreshData()
    }

    private fun buildScreen() {
        val bg = Color.rgb(12, 17, 19)
        val panel = Color.rgb(25, 34, 35)
        val green = Color.rgb(164, 220, 92)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 34, 24, 24)
            setBackgroundColor(bg)
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 8, 0, 8)
            }

        root.addView(text("BLACK MESA WATCH", 13f, green, true))
        root.addView(text("STEAM PRICE & SALE TRACKER", 11f, Color.LTGRAY))
        val card1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(panel)
        }
        card1.addView(text("WINTER SALE 2026", 13f, green, true))
        countdownView = text("Calculating countdown…", 27f, Color.WHITE, true)
        card1.addView(countdownView)
        card1.addView(text("Expected start: 17 December 2026", 13f, Color.LTGRAY))
        root.addView(card1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        val card2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(panel)
        }
        card2.addView(text("HALF-LIFE (1998)", 13f, green, true))
        priceView = text("Checking Steam price…", 25f, Color.WHITE, true)
        card2.addView(priceView)
        statusView = text("Price source: Steam Store · Romania region", 12f, Color.LTGRAY)
        card2.addView(statusView)
        root.addView(card2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        val refresh = Button(this).apply {
            text = "REFRESH PRICE"
            setOnClickListener { refreshData() }
        }
        root.addView(refresh)
        val steam = Button(this).apply {
            text = "OPEN HALF-LIFE ON STEAM"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/70/HalfLife/")))
            }
        }
        root.addView(steam)
        root.addView(text("Daily notification target: around 15:00 (Romania time).", 12f, Color.LTGRAY))
        root.addView(text("Prices are retrieved from Steam. RON conversions are estimates when Steam uses another currency.", 11f, Color.GRAY))
        setContentView(root)
        updateCountdown()
    }

    private fun updateCountdown() {
        val zone = TimeZone.getTimeZone("Europe/Bucharest")
        val target = Calendar.getInstance(zone).apply {
            set(2026, Calendar.DECEMBER, 17, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diff = (target - System.currentTimeMillis()).coerceAtLeast(0)
        val days = diff / 86_400_000
        val hours = (diff / 3_600_000) % 24
        val minutes = (diff / 60_000) % 60
        countdownView.text = if (diff == 0L) "THE SALE HAS ARRIVED" else "$days days  $hours hrs  $minutes min"
    }

    private fun refreshData() {
        priceView.text = "Checking Steam price…"
        statusView.text = "Connecting to Steam Store"
        worker.execute {
            try {
                val steamUrl = URL("https://store.steampowered.com/api/appdetails?appids=70&cc=ro&l=en")
                val json = JSONObject(readUrl(steamUrl))
                val app = json.getJSONObject("70")
                if (!app.optBoolean("success")) throw Exception("Steam did not return price data")
                val data = app.getJSONObject("data")
                val price = data.optJSONObject("price_overview")
                val result = if (price == null) {
                    "Currently not listed"
                } else {
                    val cents = price.optInt("final", 0)
                    val currency = price.optString("currency", "")
                    val amount = cents / 100.0
                    val ron = if (currency == "RON") amount else convertToRon(amount, currency)
                    if (ron != null) String.format(Locale.US, "%.2f RON", ron) else
                        String.format(Locale.US, "%.2f %s (RON conversion unavailable)", amount, currency)
                }
                val discount = if (price != null) price.optInt("discount_percent", 0) else 0
                val source = if (price != null) {
                    val original = price.optInt("initial", price.optInt("final", 0)) / 100.0
                    val currency = price.optString("currency", "")
                    "Steam price · ${if (discount > 0) "$discount% off · " else ""}Original: ${String.format(Locale.US, "%.2f %s", original, currency)}"
                } else "Steam Store did not provide a current price"
                runOnUiThread {
                    priceView.text = result
                    statusView.text = source
                }
            } catch (e: Exception) {
                runOnUiThread {
                    priceView.text = "Price unavailable"
                    statusView.text = "Check your connection and tap refresh. ${e.message ?: ""}"
                }
            }
        }
    }

    private fun convertToRon(amount: Double, currency: String): Double? {
        return try {
            val j = JSONObject(readUrl(URL("https://api.frankfurter.app/latest?from=$currency&to=RON")))
            amount * j.getJSONObject("rates").getDouble("RON")
        } catch (_: Exception) { null }
    }

    private fun readUrl(url: URL): String {
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BlackMesaWatch/1.0")
        }
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }
}
