package com.lumaread.app.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lumaread.app.MainActivity
import com.lumaread.app.R
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
    val error: String? = null
)

class AudiobookService : Service() {
    private var player: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            publishPosition()
            if (_state.value.active) progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startBook(intent)
            ACTION_TOGGLE -> if (player?.isPlaying == true) pause() else resume()
            ACTION_PAUSE -> pause()
            ACTION_SEEK -> seek(intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_SPEED -> setSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startBook(intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Audiobook" }
        val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
        val speed = normalizeSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
        player?.release()
        _state.value = AudiobookState(active = true, loading = true, title = title, uri = uriString, speed = speed)
        startForeground(NOTIFICATION_ID, notification("Preparing audiobook…"))
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(this@AudiobookService, Uri.parse(uriString))
            setOnPreparedListener {
                seekTo(position.coerceIn(0L, duration.toLong()).toInt())
                playbackParams = playbackParams.setSpeed(speed)
                start()
                _state.value = _state.value.copy(loading = false, playing = true, durationMs = duration.toLong(), positionMs = currentPosition.toLong())
                updateNotification()
                progressHandler.removeCallbacks(progressTick)
                progressHandler.post(progressTick)
            }
            setOnCompletionListener { stopPlayback() }
            setOnErrorListener { _, _, _ ->
                _state.value = _state.value.copy(loading = false, playing = false, error = "This audio file could not be played")
                updateNotification()
                true
            }
            prepareAsync()
        }
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        publishPosition()
        _state.value = _state.value.copy(playing = false)
        updateNotification()
    }

    private fun resume() {
        val current = player ?: return
        current.start()
        _state.value = _state.value.copy(playing = true, error = null)
        updateNotification()
    }

    private fun seek(positionMs: Long) {
        val current = player ?: return
        current.seekTo(positionMs.coerceIn(0L, current.duration.toLong()).toInt())
        publishPosition()
    }

    private fun setSpeed(requested: Float) {
        val speed = normalizeSpeed(requested)
        player?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
        _state.value = _state.value.copy(speed = speed)
        updateNotification()
    }

    private fun normalizeSpeed(value: Float): Float = SPEEDS.minBy { kotlin.math.abs(it - value) }

    private fun publishPosition() {
        val current = player ?: return
        _state.value = _state.value.copy(positionMs = current.currentPosition.toLong(), durationMs = current.duration.toLong().coerceAtLeast(0L))
    }

    private fun stopPlayback() {
        progressHandler.removeCallbacks(progressTick)
        player?.stop()
        player?.release()
        player = null
        _state.value = AudiobookState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audiobooks", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun updateNotification() = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(formatTime(_state.value.positionMs)))

    private fun notification(line: String): android.app.Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggle = PendingIntent.getService(this, 21, Intent(this, AudiobookService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 22, Intent(this, AudiobookService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones).setContentTitle(_state.value.title.ifBlank { "LumaRead" })
            .setContentText(line).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(_state.value.active)
            .addAction(R.drawable.ic_headphones, if (_state.value.playing) "Pause" else "Play", toggle)
            .addAction(R.drawable.ic_headphones, "Stop", stop).build()
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { progressHandler.removeCallbacks(progressTick); player?.release(); super.onDestroy() }

    companion object {
        const val ACTION_PLAY = "com.lumaread.audio.PLAY"
        const val ACTION_TOGGLE = "com.lumaread.audio.TOGGLE"
        const val ACTION_PAUSE = "com.lumaread.audio.PAUSE"
        const val ACTION_SEEK = "com.lumaread.audio.SEEK"
        const val ACTION_SPEED = "com.lumaread.audio.SPEED"
        const val ACTION_STOP = "com.lumaread.audio.STOP"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_SPEED = "speed"
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)
        private const val CHANNEL_ID = "lumaread_audiobooks"
        private const val NOTIFICATION_ID = 8207
        private val _state = MutableStateFlow(AudiobookState())
        val state: StateFlow<AudiobookState> = _state.asStateFlow()
    }
}
