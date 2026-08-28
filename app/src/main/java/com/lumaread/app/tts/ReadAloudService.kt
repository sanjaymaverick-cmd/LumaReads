package com.lumaread.app.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lumaread.app.MainActivity
import com.lumaread.app.R
import com.lumaread.app.pdf.TextPageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.lumaread.app.data.ReadingText
import com.lumaread.app.data.SkipRules
import com.lumaread.app.data.SpokenUnit
import java.util.Locale


data class PlaybackState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val bookTitle: String = "",
    val bookUri: String = "",
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val paragraphIndex: Int = 0,
    val paragraphStartLine: Int = 0,
    val paragraphEndLine: Int = 0,
    val message: String = ""
)

class ReadAloudService : Service(), TextToSpeech.OnInitListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private var initReady = false
    private var currentUri: Uri? = null
    private var currentTitle: String = "Book"
    private var currentPage = 0
    private var totalPages = 0
    private var sentenceIndex = 0
    private var units: List<SpokenUnit> = emptyList()
    private var skipRules = SkipRules()
    private var paused = false
    private var pausedByAudioFocus = false
    private var speed = 1.0f
    private var pitch = 1.0f
    private var voiceName = ""
    private var pageLoadJob: Job? = null

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        configureAudioFocus()
        configureWakeLock()

        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                updateState(playing = true, loading = false, message = "Reading")
            }

            override fun onDone(utteranceId: String?) {
                serviceScope.launch { advanceSentence() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                serviceScope.launch { handleSpeechError() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                serviceScope.launch { handleSpeechError() }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> pauseReading()
            ACTION_RESUME -> resumeReading()
            ACTION_STOP -> stopReading()
            ACTION_NEXT_PAGE -> jumpPage(+1)
            ACTION_PREVIOUS_PAGE -> jumpPage(-1)
            ACTION_SET_SPEED -> {
                speed = intent.getFloatExtra(EXTRA_SPEED, speed).coerceIn(0.6f, 2.0f)
                tts?.setSpeechRate(speed)
            }
            ACTION_SKIP_LINE -> skipLine()
            ACTION_SKIP_PARAGRAPH -> skipParagraph()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val uriString = intent.getStringExtra(EXTRA_URI) ?: return
        currentUri = Uri.parse(uriString)
        currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Book" }
        currentPage = intent.getIntExtra(EXTRA_PAGE, 0).coerceAtLeast(0)
        totalPages = intent.getIntExtra(EXTRA_TOTAL_PAGES, 0).coerceAtLeast(1)
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f).coerceIn(0.6f, 2.0f)
        pitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f).coerceIn(0.5f, 2.0f)
        voiceName = intent.getStringExtra(EXTRA_VOICE_NAME).orEmpty()

        val saved = getSharedPreferences(PREF_PLAYBACK, MODE_PRIVATE)
        val canResumeSentence = saved.getString("uri", null) == uriString &&
            saved.getInt("page", -1) == currentPage
        sentenceIndex = intent.getIntExtra(EXTRA_LINE, if (canResumeSentence) saved.getInt("sentence", 0) else 0).coerceAtLeast(0)
        skipRules = SkipRules(
            intent.getStringExtra(EXTRA_SKIP_RULES).orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        )

        units = emptyList()
        paused = false
        pausedByAudioFocus = false

        updateState(active = true, playing = false, loading = true, message = "Preparing page")
        startForeground(NOTIFICATION_ID, buildNotification("Preparing voice…"))

        if (initReady) {
            tts?.stop()
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
            loadPageAndSpeak()
        }
    }

    override fun onInit(status: Int) {
        initReady = status == TextToSpeech.SUCCESS
        if (initReady) {
            tts?.setSpeechRate(speed)
            if (currentUri != null) loadPageAndSpeak()
        } else {
            updateState(active = false, playing = false, loading = false, message = "Text-to-speech is unavailable")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun loadPageAndSpeak() {
        val uri = currentUri ?: return
        pageLoadJob?.cancel()
        tts?.stop()
        releaseWakeLock()
        updateState(active = true, playing = false, loading = true, message = "Reading page text")
        updateNotification("Preparing page ${currentPage + 1}")

        pageLoadJob = serviceScope.launch {
            val text = try {
                TextPageLoader.load(this@ReadAloudService, uri, currentPage)
            } catch (_: Throwable) {
                updateState(playing = false, loading = false, message = "Could not read this PDF page")
                updateNotification("Could not read page ${currentPage + 1}")
                return@launch
            }

            if (text.isBlank()) {
                if (currentPage < totalPages - 1) {
                    currentPage++
                    sentenceIndex = 0
                    persistPosition()
                    loadPageAndSpeak()
                } else {
                    finishBook("No readable text found")
                }
                return@launch
            }

            val locale = ReadingText.detectLocale(text)
            configureVoice(locale)
            units = ReadingText.units(text, currentPage, locale)
            sentenceIndex = nextSpeakableIndex(sentenceIndex.coerceAtLeast(0))
            persistPosition()
            if (!paused) {
                if (sentenceIndex < 0) advancePastPage() else speakCurrentSentence()
            }
        }
    }

    private fun speakCurrentSentence() {
        if (paused) return
        if (units.isEmpty()) {
            loadPageAndSpeak()
            return
        }
        sentenceIndex = nextSpeakableIndex(sentenceIndex)
        if (sentenceIndex < 0 || sentenceIndex >= units.size) {
            advancePastPage()
            return
        }

        if (!requestAudioFocus()) {
            updateState(playing = false, loading = false, message = "Waiting for audio")
            updateNotification("Waiting for audio")
            return
        }

        acquireWakeLock()
        val sentence = units[sentenceIndex].text
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
        val result = tts?.speak(
            sentence,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "p${currentPage}_s$sentenceIndex"
        ) ?: TextToSpeech.ERROR

        if (result == TextToSpeech.ERROR) {
            releaseWakeLock()
            updateState(playing = false, loading = false, message = "Unable to speak this page")
        } else {
            persistPosition()
            updateState(active = true, playing = true, loading = false, message = sentence.take(90))
            updateNotification("Page ${currentPage + 1} · line ${sentenceIndex + 1} · ${sentence.take(40)}")
        }
    }

    private fun nextSpeakableIndex(from: Int): Int {
        if (units.isEmpty()) return -1
        return units.indexOfFirst { it.lineIndex >= from && !skipRules.skips(it.locator) }
    }

    private fun advanceSentence() {
        releaseWakeLock()
        if (paused) return
        sentenceIndex = nextSpeakableIndex(sentenceIndex + 1)
        if (sentenceIndex >= 0) {
            persistPosition()
            speakCurrentSentence()
            return
        }
        advancePastPage()
    }

    private fun advancePastPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            sentenceIndex = 0
            units = emptyList()
            persistPosition()
            loadPageAndSpeak()
        } else {
            finishBook("Finished")
        }
    }

    private fun skipLine() {
        if (!_state.value.active || units.isEmpty()) return
        val current = units.getOrNull(sentenceIndex) ?: return
        skipRules = skipRules.plus(current.locator)
        paused = false
        tts?.stop()
        advanceSentence()
    }

    private fun skipParagraph() {
        if (!_state.value.active || units.isEmpty()) return
        skipRules = skipRules.plusAll(ReadingText.skipParagraphFrom(units, sentenceIndex))
        paused = false
        tts?.stop()
        advanceSentence()
    }

    private fun pauseReading() {
        if (!_state.value.active) return
        pausedByAudioFocus = false
        pauseInternal("Paused", abandonFocus = true)
    }

    private fun pauseInternal(message: String, abandonFocus: Boolean) {
        paused = true
        tts?.stop()
        releaseWakeLock()
        if (abandonFocus) abandonAudioFocus()
        updateState(playing = false, loading = false, message = message)
        updateNotification("$message · page ${currentPage + 1}")
    }

    private fun resumeReading() {
        if (!_state.value.active) return
        paused = false
        pausedByAudioFocus = false
        if (units.isEmpty()) loadPageAndSpeak() else speakCurrentSentence()
    }

    private fun jumpPage(delta: Int) {
        if (!_state.value.active) return
        currentPage = (currentPage + delta).coerceIn(0, totalPages - 1)
        sentenceIndex = 0
        units = emptyList()
        paused = false
        pausedByAudioFocus = false
        persistPosition()
        loadPageAndSpeak()
    }

    private fun stopReading() {
        tts?.stop()
        pageLoadJob?.cancel()
        releasePlaybackResources()
        _state.value = PlaybackState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishBook(message: String) {
        tts?.stop()
        pageLoadJob?.cancel()
        releasePlaybackResources()
        updateState(active = false, playing = false, loading = false, message = message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSpeechError() {
        releaseWakeLock()
        updateState(playing = false, loading = false, message = "Voice playback error")
        updateNotification("Voice playback error")
    }

    private fun configureVoice(locale: Locale) {
        val engine = tts ?: return
        engine.language = locale
        engine.voices?.firstOrNull { it.name == voiceName }?.let {
            engine.voice = it
            return
        }

        val candidates = engine.voices
            ?.asSequence()
            ?.filter { it.locale.language == locale.language }
            ?.sortedWith(
                compareByDescending<android.speech.tts.Voice> { it.locale.country == "IN" }
                    .thenByDescending { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
            )
            ?.toList()
            .orEmpty()

        candidates.firstOrNull()?.let { engine.voice = it }
    }

    private fun persistPosition() {
        getSharedPreferences(PREF_PLAYBACK, MODE_PRIVATE)
            .edit()
            .putString("uri", currentUri?.toString())
            .putInt("page", currentPage)
            .putInt("sentence", sentenceIndex)
            .apply()
        updateState()
    }

    private fun updateState(
        active: Boolean = _state.value.active,
        playing: Boolean = _state.value.playing,
        loading: Boolean = _state.value.loading,
        message: String = _state.value.message
    ) {
        _state.value = PlaybackState(
            active = active,
            playing = playing,
            loading = loading,
            bookTitle = currentTitle,
            bookUri = currentUri?.toString().orEmpty(),
            pageIndex = currentPage,
            totalPages = totalPages,
            sentenceIndex = sentenceIndex,
            sentenceCount = units.size,
            paragraphIndex = current?.paragraphIndex ?: 0,
            paragraphStartLine = inParagraph.minOfOrNull { it.lineIndex } ?: 0,
            paragraphEndLine = inParagraph.maxOfOrNull { it.lineIndex } ?: 0,
            message = message
        )
    }

    private fun configureAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        if (_state.value.playing) {
                            pausedByAudioFocus = true
                            pauseInternal("Paused for another app", abandonFocus = false)
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (pausedByAudioFocus && _state.value.active) {
                            pausedByAudioFocus = false
                            paused = false
                            speakCurrentSentence()
                        }
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        pausedByAudioFocus = false
                        if (_state.value.active) {
                            pauseInternal("Paused by another app", abandonFocus = false)
                        }
                    }
                }
            }
            .build()
    }

    private fun requestAudioFocus(): Boolean =
        audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonAudioFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
    }

    private fun configureWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ReadAloud").apply {
            setReferenceCounted(false)
        }
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) lock.acquire(10 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    private fun releasePlaybackResources() {
        releaseWakeLock()
        abandonAudioFocus()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Read aloud",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for LumaRead voice playback"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(line: String) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(line))
        }
    }

    private fun buildNotification(line: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResume = if (_state.value.playing) ACTION_PAUSE else ACTION_RESUME
        val pauseLabel = if (_state.value.playing) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(currentTitle)
            .setContentText(line)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(_state.value.active)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_headphones, "Skip line", servicePendingIntent(ACTION_SKIP_LINE, 2))
            .addAction(R.drawable.ic_headphones, pauseLabel, servicePendingIntent(pauseOrResume, 3))
            .addAction(R.drawable.ic_headphones, "Skip ¶", servicePendingIntent(ACTION_SKIP_PARAGRAPH, 4))
            .addAction(R.drawable.ic_headphones, "Stop", servicePendingIntent(ACTION_STOP, 5))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ReadAloudService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pageLoadJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        releasePlaybackResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lumaread_read_aloud"
        private const val NOTIFICATION_ID = 8107
        private const val PREF_PLAYBACK = "lumaread_playback"

        const val ACTION_START = "com.lumaread.START"
        const val ACTION_PAUSE = "com.lumaread.PAUSE"
        const val ACTION_RESUME = "com.lumaread.RESUME"
        const val ACTION_STOP = "com.lumaread.STOP"
        const val ACTION_NEXT_PAGE = "com.lumaread.NEXT_PAGE"
        const val ACTION_PREVIOUS_PAGE = "com.lumaread.PREVIOUS_PAGE"
        const val ACTION_SET_SPEED = "com.lumaread.SET_SPEED"
        const val ACTION_SKIP_LINE = "com.lumaread.SKIP_LINE"
        const val ACTION_SKIP_PARAGRAPH = "com.lumaread.SKIP_PARAGRAPH"

        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PAGE = "page"
        const val EXTRA_LINE = "line"
        const val EXTRA_TOTAL_PAGES = "total_pages"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_VOICE_NAME = "voice_name"
        const val EXTRA_SKIP_RULES = "skip_rules"

        @Deprecated("Use an explicit installed voice")
        const val EXTRA_VOICE_MODE = "voice_mode"

        const val VOICE_NATURAL = "natural"
        const val VOICE_OFFLINE = "offline"
        const val VOICE_SYSTEM = "system"

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()
    }
}
