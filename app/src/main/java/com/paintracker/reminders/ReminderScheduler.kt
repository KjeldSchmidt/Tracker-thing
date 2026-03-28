package com.paintracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {
    private const val REQUEST_CODE_BASE = 3000
    private const val MAX_REMINDERS = 24

    fun scheduleAll(context: Context, times: List<String>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, alarmManager)

        times.forEachIndexed { index, timeString ->
            if (index >= MAX_REMINDERS) return@forEachIndexed
            val parts = timeString.split(":")
            if (parts.size != 2) return@forEachIndexed
            val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
            val minute = parts[1].toIntOrNull() ?: return@forEachIndexed

            val pending = reminderIntent(context, index, PendingIntent.FLAG_UPDATE_CURRENT)
                ?: return@forEachIndexed
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis(hour, minute),
                pending
            )
        }
    }

    fun cancelAll(context: Context, alarmManager: AlarmManager? = null) {
        val am = alarmManager ?: (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        for (i in 0 until MAX_REMINDERS) {
            val pending = reminderIntent(context, i, PendingIntent.FLAG_NO_CREATE)
            if (pending != null) {
                am.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun reminderIntent(context: Context, index: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_INDEX, index)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + index,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
