package com.blackmesa.watch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        scheduleNext(context)
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("https://store.steampowered.com/api/appdetails?appids=70&cc=ro&l=en")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val root = JSONObject(body).getJSONObject("70")
                val price = if (root.optBoolean("success")) root.getJSONObject("data").optJSONObject("price_overview") else null
                val priceText = if (price == null) "Steam price currently unavailable" else {
                    val amount = price.optInt("final", 0) / 100.0
                    val curr = price.optString("currency", "")
                    val ron = if (curr == "RON") amount else try {
                        val fx = URL("https://api.frankfurter.app/latest?from=$curr&to=RON").openConnection() as HttpURLConnection
                        fx.connectTimeout = 8000
                        fx.readTimeout = 8000
                        val fxJson = JSONObject(fx.inputStream.bufferedReader().use { it.readText() })
                        fx.disconnect()
                        amount * fxJson.getJSONObject("rates").getDouble("RON")
                    } catch (_: Exception) { -1.0 }
                    if (ron >= 0) String.format(Locale.US, "%.2f RON", ron)
                    else String.format(Locale.US, "%.2f %s", amount, curr)
                }
                val target = Calendar.getInstance(TimeZone.getTimeZone("Europe/Bucharest")).apply {
                    set(2026, Calendar.DECEMBER, 17, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val diff = (target - System.currentTimeMillis()).coerceAtLeast(0)
                val days = diff / 86_400_000
                val hours = (diff / 3_600_000) % 24
                val minutes = (diff / 60_000) % 60
                val countdown = if (diff == 0L) "Winter Sale should have started" else "$days days, $hours hours, $minutes minutes left"
                postNotification(context, "Half-Life: $priceText", "$countdown until the Winter Sale (17 Dec 2026).")
            } catch (_: Exception) {
                postNotification(context, "BLACK MESA WATCH", "Couldn't retrieve today's Steam price. Open the app and refresh.")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 701
        fun scheduleNext(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Bucharest")).apply {
                set(Calendar.HOUR_OF_DAY, 15); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }

        fun postNotification(context: Context, title: String, body: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "black_mesa_daily"
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(channelId, "BLACK MESA WATCH", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(context, 702,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open).setAutoCancel(true).build()
            manager.notify(701, notification)
        }
    }
}
