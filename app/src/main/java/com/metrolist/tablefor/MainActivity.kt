package com.metrolist.tablefor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: TableForViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TableForTheme {
                MainAppScaffold(viewModel)
            }
        }
    }

    /** The phone's time or time zone changed while the app is open. */
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onTimeChanged()
        }
    }

    override fun onStart() {
        super.onStart()
        // Rebuilds the Schedule week if the date changed while the app was away.
        viewModel.onAppForeground()
        val timeChanges = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Both are system broadcasts, so the receiver needn't be visible to other apps.
        ContextCompat.registerReceiver(this, timeChangeReceiver, timeChanges, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(timeChangeReceiver)
        viewModel.onAppBackground()
        super.onStop()
    }
}
