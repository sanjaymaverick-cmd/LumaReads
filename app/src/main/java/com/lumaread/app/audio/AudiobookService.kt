package com.lumaread.app.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import com.lumaread.app.MainActivity
import com.lumaread.app.R
import com.lumaread.app.data.LibraryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudiobookState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val title: String = "",
    val uri: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val sleepRemainingMs: Long = 0,
    val error: String? = null
)

class AudiobookService : Service() {
    private var player: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var resumeAfterTransientLoss = false
    private var sleepEndsAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            publishPosition()
            if (sleepEndsAt > 0L) {
                val remaining = (sleepEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _state.value = _state.value.copy(sleepRemainingMs = remaining)
                if (remaining == 0L) { pause(); sleepEndsAt = 0L }
            }
            if (_state.value.active) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        configureAudioFocus()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startBook(intent)
            ACTION_TOGGLE -> if (player?.isPlaying == true) pause() else resume()
            ACTION_PAUSE -> pause()
            ACTION_SEEK -> seek(intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_SKIP_BACK -> skipBy(-SKIP_MS)
            ACTION_SKIP_FORWARD -> skipBy(SKIP_MS)
            ACTION_SPEED -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_SLEEP -> setSleepTimer(intent.getLongExtra(EXTRA_SLEEP_MS, 0L))
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startBook(intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Audiobook" }
        val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        val speed = LibraryRules.normalizeSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
        releasePlayer()
        _state.value = AudiobookState(active = true, loading = true, title = title, uri = uriString, speed = speed)
        startForeground(NOTIFICATION_ID, notification("Preparing audiobook…"))
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setWakeMode(this@AudiobookService, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AudiobookService, Uri.parse(uriString))
            setOnPreparedListener {
                seekTo(position.coerceIn(0L, duration.toLong()).toInt())
                playbackParams = playbackParams.setSpeed(speed)
                if (requestAudioFocus()) start()
                _state.value = _state.value.copy(loading = false, playing = isPlaying, durationMs = duration.toLong(), positionMs = currentPosition.toLong(), error = if (isPlaying) null else "Audio is in use by another app")
                updateNotification(); handler.removeCallbacks(tick); handler.post(tick)
            }
            setOnCompletionListener { stopPlayback() }
            setOnErrorListener { _, _, _ ->
                _state.value = _state.value.copy(loading = false, playing = false, error = "This audio file is missing, damaged or unsupported")
                updateNotification(); true
            }
            runCatching { prepareAsync() }.onFailure {
                _state.value = _state.value.copy(loading = false, playing = false, error = "LumaRead could not open this audiobook")
                updateNotification()
            }
        }
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        publishPosition(); _state.value = _state.value.copy(playing = false); abandonAudioFocus(); updateNotification()
    }

    private fun resume() {
        val current = player ?: return
        if (!requestAudioFocus()) { _state.value = _state.value.copy(error = "Audio is in use by another app"); return }
        current.start(); _state.value = _state.value.copy(playing = true, error = null); updateNotification()
    }

    private fun seek(positionMs: Long) {
        val current = player ?: return
        current.seekTo(positionMs.coerceIn(0L, current.duration.toLong()).toInt()); publishPosition()
    }

    private fun skipBy(deltaMs: Long) = seek((_state.value.positionMs + deltaMs).coerceAtLeast(0L))

    private fun setSpeed(requested: Float) {
        val speed = LibraryRules.normalizeSpeed(requested)
        player?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
        _state.value = _state.value.copy(speed = speed); updateNotification()
    }

    private fun setSleepTimer(durationMs: Long) {
        sleepEndsAt = if (durationMs > 0L) SystemClock.elapsedRealtime() + durationMs else 0L
        _state.value = _state.value.copy(sleepRemainingMs = durationMs.coerceAtLeast(0L))
    }

    private fun publishPosition() {
        val current = player ?: return
        runCatching { _state.value = _state.value.copy(positionMs = current.currentPosition.toLong(), durationMs = current.duration.toLong().coerceAtLeast(0L)) }
    }

    private fun stopPlayback() {
        publishPosition(); handler.removeCallbacks(tick); sleepEndsAt = 0L
        val final = _state.value.copy(active = false, playing = false, loading = false, sleepRemainingMs = 0L)
        releasePlayer(); abandonAudioFocus(); _state.value = final
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun releasePlayer() { runCatching { player?.stop() }; player?.release(); player = null }

    private fun configureAudioFocus() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeAfterTransientLoss = player?.isPlaying == true
                        if (resumeAfterTransientLoss) { player?.pause(); _state.value = _state.value.copy(playing = false) }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterTransientLoss) { resumeAfterTransientLoss = false; resume() }
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterTransientLoss = false; pause() }
                }
            }.build()
    }

    private fun requestAudioFocus() = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    private fun abandonAudioFocus() { runCatching { audioManager.abandonAudioFocusRequest(focusRequest) } }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Audiobooks", NotificationManager.IMPORTANCE_LOW))
    }

    private fun updateNotification() = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(formatTime(_state.value.positionMs)))
    private fun notification(line: String): Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(_state.value.title.ifBlank { "LumaRead" }).setContentText(line).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(_state.value.active).setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_headphones, "Back 15", serviceIntent(ACTION_SKIP_BACK, 21))
            .addAction(R.drawable.ic_headphones, if (_state.value.playing) "Pause" else "Play", serviceIntent(ACTION_TOGGLE, 22))
            .addAction(R.drawable.ic_headphones, "Forward 15", serviceIntent(ACTION_SKIP_FORWARD, 23)).build()
    }

    private fun serviceIntent(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AudiobookService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacks(tick); releasePlayer(); abandonAudioFocus(); super.onDestroy() }

    companion object {
        const val ACTION_PLAY = "com.lumaread.audio.PLAY"
        const val ACTION_TOGGLE = "com.lumaread.audio.TOGGLE"
        const val ACTION_PAUSE = "com.lumaread.audio.PAUSE"
        const val ACTION_SEEK = "com.lumaread.audio.SEEK"
        const val ACTION_SKIP_BACK = "com.lumaread.audio.SKIP_BACK"
        const val ACTION_SKIP_FORWARD = "com.lumaread.audio.SKIP_FORWARD"
        const val ACTION_SPEED = "com.lumaread.audio.SPEED"
        const val ACTION_SLEEP = "com.lumaread.audio.SLEEP"
        const val ACTION_STOP = "com.lumaread.audio.STOP"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_SLEEP_MS = "sleep_ms"
        val SPEEDS = LibraryRules.PLAYBACK_SPEEDS
        private const val SKIP_MS = 15_000L
        private const val CHANNEL_ID = "lumaread_audiobooks"
        private const val NOTIFICATION_ID = 8207
        private val _state = MutableStateFlow(AudiobookState())
        val state: StateFlow<AudiobookState> = _state.asStateFlow()
    }
}
