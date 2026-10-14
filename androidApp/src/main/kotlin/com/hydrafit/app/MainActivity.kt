package com.hydrafit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    /** The tab a notification tap asked for; cleared once the shell has acted on it. */
    private var requestedTabName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Manifest orientation cannot use size qualifiers; resolve the phone/tablet policy here.
        requestedOrientation = resources.getInteger(R.integer.screen_orientation)

        // A restored activity brings back its own navigation state, so only a fresh launch may route
        // a tap.
        if (savedInstanceState == null) {
            requestedTabName = intent.consumeNotificationTabName()
        }

        setContent {
            App(
                requestedTabName = requestedTabName,
                onTabRequestConsumed = { requestedTabName = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTabName = intent.consumeNotificationTabName()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
