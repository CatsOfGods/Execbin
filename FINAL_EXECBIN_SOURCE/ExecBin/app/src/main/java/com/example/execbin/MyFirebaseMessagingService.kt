package com.example.execbin

import android.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.text.get

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val user = Firebase.auth.currentUser
        if (user != null) {
            Firebase.firestore.collection("users")
                .document(user.uid)
                .update("fcmToken", token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        S.init(applicationContext)

        val title = message.data["title"] ?: S.get("new_message_notification")
        val body = message.data["body"] ?: ""
        val senderId = message.data["senderId"] ?: ""
        val mediaType = message.data["mediaType"] ?: ""

        val currentUserId = Firebase.auth.currentUser?.uid
        if (senderId == currentUserId) {
            return
        }

        // Differentiate notification text/icon based on media type
        val displayBody = when {
            body.isNotEmpty() -> body
            mediaType == "image" -> "📷 " + S.get("photo")
            mediaType == "voice" -> "🎤 " + S.get("voice_message")
            else -> S.get("new_message_notification")
        }

        val icon = when (mediaType) {
            "image" -> R.drawable.ic_menu_camera
            "voice" -> R.drawable.ic_btn_speak_now
            else -> R.drawable.ic_dialog_email
        }

        showNotification(title, displayBody, icon)
    }

    private fun showNotification(title: String, body: String, icon: Int = R.drawable.ic_dialog_email) {
        val channelId = "chat_messages"

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
