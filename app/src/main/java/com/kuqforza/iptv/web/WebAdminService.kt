package com.kuqforza.iptv.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kuqforza.iptv.R

class WebAdminService : Service() {

    private var server: WebAdminServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kuqforza Admin")
            .setContentText("Panel web actif sur le port 8089")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        try {
            server = WebAdminServer(applicationContext, 8089)
            server?.start()
            Log.i(TAG, "Web admin started on port 8089")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web admin", e)
        }
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Web Admin",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Panel web Kuqforza" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "WebAdmin"
        private const val CHANNEL_ID = "kuqforza_web_admin"
        private const val NOTIFICATION_ID = 8089
    }
}
