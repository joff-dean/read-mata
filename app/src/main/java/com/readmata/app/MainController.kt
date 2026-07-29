package com.readmata.app

import android.content.Context
import com.readmata.app.feed.FeedItem
import com.readmata.app.feed.HttpFeedSource
import com.readmata.app.feed.SourceAdapter
import com.readmata.app.playback.PlaybackUiState
import com.readmata.app.playback.TtsPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val feedUrl: String = MainController.SAMPLE_FEED_URL,
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class MainController(
    context: Context,
    private val source: SourceAdapter = HttpFeedSource(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback = TtsPlaybackController(context)
    private val _state = MutableStateFlow(MainUiState())

    val state: StateFlow<MainUiState> = _state.asStateFlow()
    val playbackState: StateFlow<PlaybackUiState> = playback.state

    fun updateFeedUrl(value: String) {
        _state.value = _state.value.copy(feedUrl = value, errorMessage = null)
    }

    fun loadFeed() {
        val url = _state.value.feedUrl.trim()
        if (url.isEmpty() || _state.value.isLoading) return

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            runCatching { source.load(url) }
                .onSuccess { loaded ->
                    val items = loaded
                        .distinctBy(FeedItem::id)
                        .filter { it.title.isNotBlank() }
                        .take(MAX_QUEUE_ITEMS)
                    _state.value = _state.value.copy(
                        items = items,
                        isLoading = false,
                        errorMessage = if (items.isEmpty()) "피드에서 읽을 글을 찾지 못했습니다." else null,
                    )
                    playback.load(items)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "피드를 불러오지 못했습니다.",
                    )
                }
        }
    }

    fun play() = playback.play()

    fun pause() = playback.pause()

    fun nextItem() = playback.nextItem()

    fun pauseForBackground() = playback.pause()

    override fun close() {
        scope.cancel()
        playback.close()
    }

    companion object {
        const val SAMPLE_FEED_URL = "https://developer.android.com/feeds/androidx-release-notes.xml"
        private const val MAX_QUEUE_ITEMS = 20
    }
}
