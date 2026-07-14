package com.seoktaedev.tteona.core.services

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seoktaedev.tteona.MainActivity
import com.seoktaedev.tteona.R
import com.seoktaedev.tteona.core.i18n.LocaleManager
import java.util.Calendar

/**
 * 즉흥 '나의 오늘' 미종료 리마인더 — iOS SavedImpromptuSession.scheduleReminderIfNeeded 이식본.
 * 세션 저장 시 오늘 오후 8시에 "Vlog로 남겨볼까요?" 넛지 알림을 예약하고, 세션 종료 시 취소한다.
 */
object ImpromptuReminder {
    private const val REQUEST_CODE = 8020  // 오후 8시(20:00) 리마인더 전용 고정 코드
    private const val EXTRA_PLACES_COUNT = "placesCount"
    private const val NOTIFICATION_ID = 8020

    /** 기존 예약 취소 후 재예약 (장소 수 업데이트 반영). 이미 오후 8시가 지났으면 예약 안 함 — iOS와 동일. */
    fun scheduleIfNeeded(context: Context, placesCount: Int) {
        cancel(context)

        val fireAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (fireAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 정확 알람 권한(SCHEDULE_EXACT_ALARM) 없이 도즈 모드에서도 근사 시각에 울리는 넛지용 알람
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent(context, placesCount))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, 0))
    }

    private fun pendingIntent(context: Context, placesCount: Int): PendingIntent {
        val intent = Intent(context, ImpromptuReminderReceiver::class.java)
            .putExtra(EXTRA_PLACES_COUNT, placesCount)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun showNotification(context: Context, placesCount: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        TteonaMessagingService.ensureChannel(context)

        // 탭하면 '나의 오늘' 세션을 연다 (iOS userInfo action == "openTodaySession")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("action", "openTodaySession")
        }
        val pending = PendingIntent.getActivity(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = LocaleManager.string(context, R.string.impromptu_reminder_body, placesCount)
        val notification = NotificationCompat.Builder(context, "tteona_default")
            .setSmallIcon(R.drawable.ic_stat_tteona)
            .setContentTitle(LocaleManager.string(context, R.string.impromptu_reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class ImpromptuReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 취소 경쟁 등으로 세션이 이미 정리됐으면 알림을 띄우지 않는다
        ImpromptuSessionStore.initialize(context)
        val session = ImpromptuSessionStore.loadTodaySession() ?: return
        val count = intent.getIntExtra("placesCount", session.places.size)
        ImpromptuReminder.showNotification(context, count)
    }
}
