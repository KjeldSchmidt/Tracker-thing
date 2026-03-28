package com.paintracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paintracker.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val repository = ReminderRepository(context)
                ReminderScheduler.scheduleAll(context, repository.readTimesOnce())
            }
        }
    }
}
