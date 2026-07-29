package com.readmata.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.readmata.app.feed.FeedItem
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TtsPlaybackController(
    context: Context,
    private val locale: Locale = Locale.KOREAN,
) : AutoCloseable {
    private data class QueueItem(
        val title: String,
        val chunks: List<String>,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(::onAudioFocusChanged, mainHandler)
        .build()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var engine: TextToSpeech? = null
    private var engineState = TtsEngineState.INITIALIZING
    private var engineUnavailableMessage: String? = null
    private var queue: List<QueueItem> = emptyList()
    private var itemIndex = 0
    private var chunkIndex = 0
    private var sessionId = 0L
    private var activeUtteranceId: String? = null
    private var closed = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post { onEngineInitialized(status) }
        }
    }

    fun load(items: List<FeedItem>) {
        ensureMainThread()
        pauseInternal(updateState = false)

        val maxLength = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(32)
        queue = items.mapNotNull { item ->
            val title = item.title.trim()
            if (title.isEmpty()) return@mapNotNull null

            val chunks = buildList {
                addAll(TextChunker.split("제목. $title", maxLength))
                addAll(TextChunker.split(item.summary, maxLength))
            }
            QueueItem(title = title, chunks = chunks)
        }
        itemIndex = 0
        chunkIndex = 0

        when (engineState) {
            TtsEngineState.READY -> updateState(
                status = PlaybackStatus.READY,
                message = if (queue.isEmpty()) {
                    "읽을 글이 없습니다."
                } else {
                    "${queue.size}개의 글을 읽을 준비가 됐습니다."
                },
            )

            TtsEngineState.INITIALIZING -> updateState(
                status = PlaybackStatus.INITIALIZING,
                message = if (queue.isEmpty()) {
                    "읽을 글이 없습니다. 음성 엔진을 기다리는 중입니다."
                } else {
                    "글을 불러왔습니다. 음성 엔진을 기다리는 중입니다."
                },
            )

            TtsEngineState.UNAVAILABLE -> updateState(
                status = PlaybackStatus.ERROR,
                message = engineUnavailableMessage ?: "음성 엔진을 사용할 수 없습니다.",
            )
        }
    }

    fun play() {
        ensureMainThread()
        if (closed) return
        when (engineState) {
            TtsEngineState.INITIALIZING -> {
                updateState(PlaybackStatus.INITIALIZING, "음성 엔진이 아직 준비되지 않았습니다.")
                return
            }

            TtsEngineState.UNAVAILABLE -> {
                updateState(
                    PlaybackStatus.ERROR,
                    engineUnavailableMessage ?: "음성 엔진을 사용할 수 없습니다.",
                )
                return
            }

            TtsEngineState.READY -> Unit
        }
        if (queue.isEmpty()) {
            updateState(PlaybackStatus.READY, "먼저 RSS 피드를 불러오세요.")
            return
        }
        if (_state.value.status == PlaybackStatus.COMPLETED) {
            itemIndex = 0
            chunkIndex = 0
        }
        if (audioManager.requestAudioFocus(audioFocusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            updateState(PlaybackStatus.PAUSED, "다른 앱이 오디오를 사용 중이라 재생하지 못했습니다.")
            return
        }

        sessionId++
        speakCurrentChunk()
    }

    fun pause() {
        ensureMainThread()
        if (_state.value.status == PlaybackStatus.PLAYING) {
            pauseInternal(updateState = true)
        }
    }

    fun nextItem() {
        ensureMainThread()
        if (queue.isEmpty()) return

        val shouldContinue = _state.value.status == PlaybackStatus.PLAYING
        sessionId++
        activeUtteranceId = null
        engine?.stop()
        itemIndex++
        chunkIndex = 0

        if (itemIndex >= queue.size) {
            completePlayback()
        } else if (shouldContinue) {
            speakCurrentChunk()
        } else {
            updateState(PlaybackStatus.PAUSED, "다음 글부터 재생할 준비가 됐습니다.")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        sessionId++
        activeUtteranceId = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun onEngineInitialized(status: Int) {
        if (closed) return
        val currentEngine = engine ?: return

        if (status != TextToSpeech.SUCCESS) {
            markEngineUnavailable("음성 엔진을 초기화하지 못했습니다.")
            return
        }

        val languageResult = currentEngine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            markEngineUnavailable("한국어를 지원하는 TTS 음성 데이터가 필요합니다.")
            return
        }

        currentEngine.setAudioAttributes(audioAttributes)
        currentEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post { onUtteranceDone(utteranceId) }
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onUtteranceError(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onUtteranceError(utteranceId) }
            }
        })
        engineState = TtsEngineState.READY
        engineUnavailableMessage = null
        updateState(
            status = PlaybackStatus.READY,
            message = if (queue.isEmpty()) {
                "RSS 피드를 불러오면 읽어드릴게요."
            } else {
                "${queue.size}개의 글을 읽을 준비가 됐습니다."
            },
        )
    }

    private fun onUtteranceDone(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
        chunkIndex++

        if (chunkIndex >= queue[itemIndex].chunks.size) {
            itemIndex++
            chunkIndex = 0
        }

        if (itemIndex >= queue.size) {
            completePlayback()
        } else {
            speakCurrentChunk()
        }
    }

    private fun onUtteranceError(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        updateState(PlaybackStatus.ERROR, "음성을 재생하는 중 오류가 발생했습니다.")
    }

    private fun markEngineUnavailable(message: String) {
        engineState = TtsEngineState.UNAVAILABLE
        engineUnavailableMessage = message
        updateState(PlaybackStatus.ERROR, message)
    }

    private fun speakCurrentChunk() {
        val currentEngine = engine ?: return
        val item = queue.getOrNull(itemIndex) ?: run {
            completePlayback()
            return
        }
        val text = item.chunks.getOrNull(chunkIndex) ?: run {
            nextItem()
            return
        }

        val utteranceId = "$sessionId:$itemIndex:$chunkIndex"
        activeUtteranceId = utteranceId
        updateState(PlaybackStatus.PLAYING, "읽는 중입니다.")
        val result = currentEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            updateState(PlaybackStatus.ERROR, "TTS 엔진이 문장을 받아들이지 못했습니다.")
        }
    }

    private fun pauseInternal(updateState: Boolean) {
        sessionId++
        activeUtteranceId = null
        engine?.stop()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        if (updateState && queue.isNotEmpty()) {
            updateState(PlaybackStatus.PAUSED, "현재 문단의 처음에서 다시 시작합니다.")
        }
    }

    private fun completePlayback() {
        activeUtteranceId = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        itemIndex = queue.size.coerceAtLeast(1) - 1
        chunkIndex = queue.getOrNull(itemIndex)?.chunks?.size?.coerceAtLeast(1)?.minus(1) ?: 0
        updateState(PlaybackStatus.COMPLETED, "읽기 큐를 모두 재생했습니다.")
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> if (_state.value.status == PlaybackStatus.PLAYING) {
                pauseInternal(updateState = false)
                updateState(PlaybackStatus.PAUSED, "다른 오디오가 시작되어 일시정지했습니다.")
            }
        }
    }

    private fun updateState(status: PlaybackStatus, message: String) {
        val item = queue.getOrNull(itemIndex)
        val effectiveStatus = if (engineState == TtsEngineState.UNAVAILABLE) {
            PlaybackStatus.ERROR
        } else {
            status
        }
        val effectiveMessage = if (engineState == TtsEngineState.UNAVAILABLE) {
            engineUnavailableMessage ?: message
        } else {
            message
        }
        _state.value = PlaybackUiState(
            status = effectiveStatus,
            engineState = engineState,
            currentTitle = item?.title,
            currentItemIndex = if (queue.isEmpty()) 0 else itemIndex.coerceIn(queue.indices),
            totalItems = queue.size,
            currentChunkIndex = if (item == null) 0 else chunkIndex.coerceIn(item.chunks.indices),
            totalChunks = item?.chunks?.size ?: 0,
            message = effectiveMessage,
        )
    }

    private fun ensureMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "TtsPlaybackController must be used from the main thread"
        }
    }
}
