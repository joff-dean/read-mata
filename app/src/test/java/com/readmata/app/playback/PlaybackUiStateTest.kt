package com.readmata.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUiStateTest {
    @Test
    fun `playback toggle requires both a queue and a ready engine`() {
        assertFalse(PlaybackUiState().canTogglePlayback)
        assertFalse(
            PlaybackUiState(
                engineState = TtsEngineState.UNAVAILABLE,
                totalItems = 1,
            ).canTogglePlayback,
        )
        assertTrue(
            PlaybackUiState(
                status = PlaybackStatus.ERROR,
                engineState = TtsEngineState.READY,
                totalItems = 1,
            ).canTogglePlayback,
        )
    }
}
