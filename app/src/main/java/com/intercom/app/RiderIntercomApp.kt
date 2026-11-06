package com.intercom.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.intercom.app.util.Prefs

class RiderIntercomApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALL,
                "Intercom call",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "The persistent notification that keeps the call alive"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "Intercom alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Told when the call stopped and needs restarting from the app"
            }
        )
    }

    companion object {
        const val CHANNEL_CALL = "call"
        const val CHANNEL_ALERT = "alert"
    }
}
