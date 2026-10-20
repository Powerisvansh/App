package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.DailyTrackApplication
import com.example.data.service.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val app = context.applicationContext as DailyTrackApplication
                    val resumeOnBoot = app.preferencesRepository.resumeOnBoot.first()
                    val trackingEnabled = app.preferencesRepository.isTrackingEnabled.first()

                    if (resumeOnBoot && trackingEnabled) {
                        LocationTrackingService.startService(context)
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
