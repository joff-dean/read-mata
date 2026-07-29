package com.readmata.app.playback

enum class PlaybackStatus {
    INITIALIZING,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
}

enum class TtsEngineState {
    INITIALIZING,
    READY,
    UNAVAILABLE,
}

data class PlaybackUiState(
    val status: PlaybackStatus = PlaybackStatus.INITIALIZING,
    val engineState: TtsEngineState = TtsEngineState.INITIALIZING,
    val currentTitle: String? = null,
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val message: String = "음성 엔진을 준비하는 중입니다.",
) {
    val hasQueue: Boolean get() = totalItems > 0
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val canTogglePlayback: Boolean get() = hasQueue && engineState == TtsEngineState.READY
}
