package com.seoktaedev.tteona.core.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.seoktaedev.tteona.MainActivity
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import com.seoktaedev.tteona.core.network.ApiClient
import com.seoktaedev.tteona.core.network.PushRegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM 수신 서비스 — iOS FCMService + AppNotificationManager(willPresent)의 이식본.
 * 서버(fcmRequests 처리기)가 보내는 data 페이로드: type/roomId/senderUserId/targetUserId 등.
 */
class TteonaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 토큰 갱신 시 로그인 상태면 즉시 재등록 (iOS didReceiveRegistrationToken 대응)
        Firebase.auth.currentUser?.uid?.let { saveToken(it, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val roomId = data["roomId"]

        // 현재 보고 있는 채팅방의 알림은 억제 (iOS activeChatRoom 처리와 동일)
        if (roomId != null && roomId == AppNotificationManager.activeChatRoomId) return

        val title = message.notification?.title ?: data["title"] ?: LocaleManager.string(R.string.onboarding_logoDesc)
        val body = message.notification?.body ?: data["body"] ?: return

        showNotification(title, body, data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tteona)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "tteona_default"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, LocaleManager.string(R.string.notif_channelName), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = LocaleManager.string(R.string.notif_channelDesc)
                    }
                )
            }
        }

        // FCM 토큰을 userPrivate에 저장 (iOS FCMService.saveFCMToken과 동일 — Cloud Functions 그룹 알림용)
        // + 서버(server.js) device_tokens에도 등록 — 채팅·Vlog 완성 등 서버 직발송 푸시용
        // lang은 두 발송 경로 모두 문구를 수신자 언어로 쓰도록 함께 저장한다.
        fun saveToken(userId: String, token: String) {
            val lang = LocaleManager.current().code
            Firebase.firestore.collection("userPrivate").document(userId)
                .set(mapOf("fcmToken" to token, "lang" to lang), SetOptions.merge())
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ApiClient.api.registerPush(PushRegisterRequest(token, platform = "android", lang = lang)) }
            }
        }

        fun registerCurrentToken(userId: String) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> saveToken(userId, token) }
        }
    }
}
