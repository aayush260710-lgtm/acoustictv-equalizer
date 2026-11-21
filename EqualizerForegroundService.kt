package com.acoustictv.equalizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.acoustictv.equalizer.MainActivity
import com.acoustictv.equalizer.data.EqualizerRepository
import com.acoustictv.equalizer.model.AudioPreset
import com.acoustictv.equalizer.model.EqualizerCatalog
import com.acoustictv.equalizer.model.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class EqualizerForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): EqualizerForegroundService = this@EqualizerForegroundService
    }

    private data class SessionAudioEffects(
        val sessionId: Int,
        val equalizer: Equalizer?,
        val bassBoost: BassBoost?,
        val virtualizer: Virtualizer?,
        val loudnessEnhancer: LoudnessEnhancer?
    ) {
        fun release() {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            runCatching { virtualizer?.release() }
            runCatching { loudnessEnhancer?.release() }
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: EqualizerRepository
    private lateinit var audioManager: AudioManager

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private val activeSessions = LinkedHashMap<Int, SessionAudioEffects>()
    private var isReceiverRegistered = false
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (sessionId <= 0) return

            when (action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    attachToSession(sessionId)
                    applyStateToAllSessions(_state.value)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    detachSession(sessionId)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = EqualizerRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        promoteToForeground()

        // Attach immediately to global output mix (sessionId = 0)
        attachToSession(GLOBAL_OUTPUT_SESSION_ID)
        registerAudioSessionListeners()

        serviceScope.launch {
            val restored = repository.stateFlow.first()
            _state.value = restored.copy(attachedSessionCount = activeSessions.size)
            applyStateToAllSessions(_state.value)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        if (!activeSessions.containsKey(GLOBAL_OUTPUT_SESSION_ID)) {
            attachToSession(GLOBAL_OUTPUT_SESSION_ID)
        }
        applyStateToAllSessions(_state.value)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unregisterAudioSessionListeners()
        activeSessions.values.forEach { it.release() }
        activeSessions.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun setMasterEnabled(enabled: Boolean) {
        updateAndPersist { current ->
            current.copy(masterEnabled = enabled)
        }
    }

    fun toggleDolbyAtmos() {
        updateAndPersist { current ->
            current.withDolbyAtmosToggled()
        }
    }

    fun selectPreset(preset: AudioPreset) {
        updateAndPersist { current ->
            current.withPresetApplied(preset)
        }
    }

    fun setBandLevel(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..6) return
        updateAndPersist { current ->
            val updatedBands = current.bandsDb.toMutableList()
            updatedBands[bandIndex] = EqualizerState.clampEqDb(gainDb)
            current.copy(
                activePresetId = EqualizerState.CUSTOM_PRESET_ID,
                activePresetName = EqualizerState.CUSTOM_PRESET_NAME,
                bandsDb = updatedBands
            )
        }
    }

    fun setSurroundPercent(percent: Int) {
        updateAndPersist { current ->
            current.copy(
                activePresetId = EqualizerState.CUSTOM_PRESET_ID,
                activePresetName = EqualizerState.CUSTOM_PRESET_NAME,
                surroundPercent = EqualizerState.clampPercent(percent)
            )
        }
    }

    fun setBassBoostPercent(percent: Int) {
        updateAndPersist { current ->
            current.copy(
                activePresetId = EqualizerState.CUSTOM_PRESET_ID,
                activePresetName = EqualizerState.CUSTOM_PRESET_NAME,
                bassBoostPercent = EqualizerState.clampPercent(percent)
            )
        }
    }

    fun setDialogueClarityPercent(percent: Int) {
        updateAndPersist { current ->
            current.copy(
                activePresetId = EqualizerState.CUSTOM_PRESET_ID,
                activePresetName = EqualizerState.CUSTOM_PRESET_NAME,
                dialogueClarityPercent = EqualizerState.clampPercent(percent)
            )
        }
    }

    fun setPreampDb(gainDb: Float) {
        updateAndPersist { current ->
            current.copy(
                activePresetId = EqualizerState.CUSTOM_PRESET_ID,
                activePresetName = EqualizerState.CUSTOM_PRESET_NAME,
                preampDb = EqualizerState.clampPreampDb(gainDb)
            )
        }
    }

    fun resetBandsToZero() {
        updateAndPersist { current ->
            current.withBandsResetToZero()
        }
    }

    fun revertToLastPreset() {
        updateAndPersist { current ->
            val targetPreset = EqualizerCatalog.allPresets
                .firstOrNull { it.id == current.lastPresetId }
                ?: EqualizerCatalog.standardPresets.first()
            current.withPresetApplied(targetPreset)
        }
    }

    private fun updateAndPersist(transform: (EqualizerState) -> EqualizerState) {
        val next = transform(_state.value).copy(attachedSessionCount = activeSessions.size)
        _state.value = next
        applyStateToAllSessions(next)
        serviceScope.launch(Dispatchers.IO) {
            repository.saveState(next)
        }
    }

    private fun attachToSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) return

        val eq = runCatching {
            Equalizer(PRIORITY, sessionId).apply { enabled = _state.value.masterEnabled }
        }.getOrNull()

        val bass = runCatching {
            BassBoost(PRIORITY, sessionId).apply { enabled = _state.value.masterEnabled }
        }.getOrNull()

        val virt = runCatching {
            Virtualizer(PRIORITY, sessionId).apply { enabled = _state.value.masterEnabled }
        }.getOrNull()

        val loudness = runCatching {
            LoudnessEnhancer(sessionId).apply { enabled = _state.value.masterEnabled }
        }.getOrNull()

        if (eq != null || bass != null || virt != null || loudness != null) {
            activeSessions[sessionId] = SessionAudioEffects(
                sessionId = sessionId,
                equalizer = eq,
                bassBoost = bass,
                virtualizer = virt,
                loudnessEnhancer = loudness
            )
            _state.value = _state.value.copy(attachedSessionCount = activeSessions.size)
        }
    }

    private fun detachSession(sessionId: Int) {
        if (sessionId == GLOBAL_OUTPUT_SESSION_ID) return
        activeSessions.remove(sessionId)?.release()
        _state.value = _state.value.copy(attachedSessionCount = activeSessions.size)
    }

    private fun applyStateToAllSessions(state: EqualizerState) {
        activeSessions.values.forEach { effects ->
            applyStateToSession(effects, state)
        }
    }

    private fun applyStateToSession(effects: SessionAudioEffects, state: EqualizerState) {
        val masterOn = state.masterEnabled

        // 1. 7-Band Graphic Equalizer + Dialogue Clarity 1kHz-4kHz dynamic vocal presence
        effects.equalizer?.let { eq ->
            runCatching {
                eq.enabled = masterOn
                if (masterOn) {
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    val numBands = eq.numberOfBands.toInt()
                    val dialogueLiftDb = (state.dialogueClarityPercent / 100f) * 4.5f

                    for (bandIdx in 0 until numBands) {
                        val centerHz = eq.getCenterFreq(bandIdx.toShort()) / 1000f
                        var targetDb = interpolate7BandCurve(centerHz, state.bandsDb)

                        // Dialogue Clarity shapes 1kHz - 4kHz vocal formant window
                        if (centerHz in 900f..4200f) {
                            val weight = when {
                                centerHz in 1500f..3200f -> 1.0f
                                else -> 0.65f
                            }
                            targetDb += dialogueLiftDb * weight
                        }

                        val targetMb = (targetDb * 100f).roundToInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(bandIdx.toShort(), targetMb.toShort())
                    }
                }
            }
        }

        // 2. Bass Boost (0% - 100% -> 0..1000)
        effects.bassBoost?.let { bass ->
            runCatching {
                bass.enabled = masterOn
                if (masterOn && bass.strengthSupported) {
                    val strength = ((state.bassBoostPercent / 100f) * 1000f)
                        .roundToInt()
                        .coerceIn(0, 1000)
                    bass.setStrength(strength.toShort())
                }
            }
        }

        // 3. Surround Sound Virtualizer (0% - 100% -> 0..1000)
        effects.virtualizer?.let { virt ->
            runCatching {
                virt.enabled = masterOn
                if (masterOn && virt.strengthSupported) {
                    val strength = ((state.surroundPercent / 100f) * 1000f)
                        .roundToInt()
                        .coerceIn(0, 1000)
                    virt.setStrength(strength.toShort())
                }
            }
        }

        // 4. Preamp / Loudness Enhancer (-6.0 dB to +12.0 dB -> millibels)
        effects.loudnessEnhancer?.let { enhancer ->
            runCatching {
                enhancer.enabled = masterOn
                if (masterOn) {
                    val targetGainMb = (state.preampDb * 100f).roundToInt().coerceAtLeast(0)
                    enhancer.setTargetGain(targetGainMb)
                }
            }
        }
    }

    private fun interpolate7BandCurve(freqHz: Float, bandsDb: List<Float>): Float {
        val anchors = EqualizerCatalog.bands.map { it.frequencyHz.toFloat() }
        if (freqHz <= anchors.first()) return bandsDb.first()
        if (freqHz >= anchors.last()) return bandsDb.last()

        for (i in 0 until anchors.size - 1) {
            val f0 = anchors[i]
            val f1 = anchors[i + 1]
            if (freqHz in f0..f1) {
                val ratio = if (abs(f1 - f0) < 0.001f) 0f else (freqHz - f0) / (f1 - f0)
                return bandsDb[i] + ratio * (bandsDb[i + 1] - bandsDb[i])
            }
        }
        return 0f
    }

    private fun registerAudioSessionListeners() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(audioSessionReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(audioSessionReceiver, filter)
            }
            isReceiverRegistered = true
        }

        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                super.onPlaybackConfigChanged(configs)
                if (!activeSessions.containsKey(GLOBAL_OUTPUT_SESSION_ID)) {
                    attachToSession(GLOBAL_OUTPUT_SESSION_ID)
                }
                applyStateToAllSessions(_state.value)
            }
        }
        playbackCallback = callback
        audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun unregisterAudioSessionListeners() {
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(audioSessionReceiver) }
            isReceiverRegistered = false
        }
        playbackCallback?.let { cb ->
            runCatching { audioManager.unregisterAudioPlaybackCallback(cb) }
        }
        playbackCallback = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Global Audio DSP Engine",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps global TV equalizer and surround DSP active across streaming apps."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun promoteToForeground() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AcousticTV Global Equalizer")
            .setContentText("Active background DSP processing")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "acoustictv_dsp_channel"
        private const val NOTIFICATION_ID = 4091
        private const val GLOBAL_OUTPUT_SESSION_ID = 0
        private const val PRIORITY = 100

        fun start(context: Context) {
            val intent = Intent(context, EqualizerForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}
