package com.example.employeepos2 // CHANGE TO YOUR PACKAGE IF DIFFERENT

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Grab the title and body if they exist
        val title = remoteMessage.notification?.title
        val body = remoteMessage.notification?.body

        // ONLY show a notification if the sender provided a real title.
        // This drops the phantom "New Job" pings, but lets your
        // 8:30 AM "Inventory Overdue!" strikes pass through perfectly.
        if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
            showNotification(title, body)
        } else {
            Log.d("FCM_SERVICE", "Blocked a blank background ping.")
        }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "integrity_fcm"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FCM Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo) // Make sure this matches your app's icon name
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        manager.notify(1002, notification)
    }
}