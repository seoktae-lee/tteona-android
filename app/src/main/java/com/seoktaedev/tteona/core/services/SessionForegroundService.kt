package com.seoktaedev.tteona.core.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.seoktaedev.tteona.MainActivity
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager

/**
 * 세션 진행 상시 알림 + 백그라운드 위치 유지 — iOS Live Activity(TodaySessionActivityManager) 대응.
 * 코스/즉흥 세션 동안 foregroundServiceType=location 으로 떠 있어
 * 앱이 백그라운드로 가도 위치 추적·그룹 위치 공유(소켓)가 끊기지 않는다.
 */
class SessionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: LocaleManager.string(R.string.session_foregroundTitle)
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        ensureChannel(this)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tteona)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "tteona_session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"

        /** 세션 시작/진행 갱신 — 이미 떠 있으면 알림 내용만 갱신된다 */
        fun start(context: Context, title: String, body: String) {
            val intent = Intent(context, SessionForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, LocaleManager.string(R.string.session_channelName), NotificationManager.IMPORTANCE_LOW).apply {
                        description = LocaleManager.string(R.string.session_channelDesc)
                        setShowBadge(false)
                    }
                )
            }
        }
    }
}
