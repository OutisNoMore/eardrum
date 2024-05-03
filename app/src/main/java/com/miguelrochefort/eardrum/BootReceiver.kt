package com.miguelrochefort.eardrum

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val int = Intent(context, AudioRecorderService::class.java)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            context.startForegroundService(int)
        } else {
            context.startService(int)
        }
    }
}
