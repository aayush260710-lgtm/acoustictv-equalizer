package com.acoustictv.equalizer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.acoustictv.equalizer.model.EqualizerState
import com.acoustictv.equalizer.service.EqualizerForegroundService
import com.acoustictv.equalizer.ui.EqualizerTvScreen

class MainActivity : ComponentActivity() {

    private var dspService: EqualizerForegroundService? by mutableStateOf(null)
    private var isBound = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Service runs foreground regardless on TV; notification is silent */ }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val localBinder = service as? EqualizerForegroundService.LocalBinder
            dspService = localBinder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dspService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPostNotificationsIfNeeded()
        EqualizerForegroundService.start(applicationContext)

        setContent {
            AcousticTvTheme {
                val boundService = dspService
                if (boundService != null) {
                    val state by boundService.state.collectAsState()
                    EqualizerTvScreen(
                        state = state,
                        onToggleMaster = { enabled -> boundService.setMasterEnabled(enabled) },
                        onToggleDolbyAtmos = { boundService.toggleDolbyAtmos() },
                        onSelectPreset = { preset -> boundService.selectPreset(preset) },
                        onBandChange = { idx, db -> boundService.setBandLevel(idx, db) },
                        onSurroundChange = { pct -> boundService.setSurroundPercent(pct) },
                        onBassBoostChange = { pct -> boundService.setBassBoostPercent(pct) },
                        onDialogueChange = { pct -> boundService.setDialogueClarityPercent(pct) },
                        onPreampChange = { db -> boundService.setPreampDb(db) },
                        onResetBands = { boundService.resetBandsToZero() },
                        onRevertLastPreset = { boundService.revertToLastPreset() }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF090C11))
                    ) {
                        EqualizerTvScreen(
                            state = EqualizerState(),
                            onToggleMaster = {},
                            onToggleDolbyAtmos = {},
                            onSelectPreset = {},
                            onBandChange = { _, _ -> },
                            onSurroundChange = {},
                            onBassBoostChange = {},
                            onDialogueChange = {},
                            onPreampChange = {},
                            onResetBands = {},
                            onRevertLastPreset = {}
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, EqualizerForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onStop()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AcousticTvTheme(content: @Composable () -> Unit) {
    val tvColors = darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF04131F),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF1F1402),
        background = Color(0xFF090C11),
        onBackground = Color(0xFFEEF2F7),
        surface = Color(0xFF131822),
        onSurface = Color(0xFFEEF2F7),
        surfaceVariant = Color(0xFF1B2331),
        onSurfaceVariant = Color(0xFF94A3B8)
    )
    MaterialTheme(
        colorScheme = tvColors,
        content = content
    )
}
