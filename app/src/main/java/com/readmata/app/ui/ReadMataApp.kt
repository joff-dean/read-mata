package com.readmata.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.readmata.app.MainController
import com.readmata.app.MainUiState
import com.readmata.app.feed.FeedItem
import com.readmata.app.playback.PlaybackUiState
import com.readmata.app.playback.PlaybackStatus
import com.readmata.app.playback.TtsEngineState

@Composable
fun ReadMataApp(controller: MainController) {
    val state by controller.state.collectAsState()
    val playback by controller.playbackState.collectAsState()

    ReadMataTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScreen(
                state = state,
                playback = playback,
                onFeedUrlChanged = controller::updateFeedUrl,
                onLoadFeed = controller::loadFeed,
                onPlay = controller::play,
                onPause = controller::pause,
                onNext = controller::nextItem,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainScreen(
    state: MainUiState,
    playback: PlaybackUiState,
    onFeedUrlChanged: (String) -> Unit,
    onLoadFeed: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Read Mata", fontWeight = FontWeight.Bold)
                        Text(
                            "최신 글을 골라 읽어주는 작은 라디오",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FeedInput(
                    url = state.feedUrl,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onUrlChanged = onFeedUrlChanged,
                    onLoad = onLoadFeed,
                )
            }

            item {
                PlaybackCard(
                    state = playback,
                    onPlay = onPlay,
                    onPause = onPause,
                    onNext = onNext,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "읽기 큐 ${state.items.size}개",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "현재 프로토타입은 앱이 화면에서 사라지면 현재 문단에서 멈춥니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.items, key = FeedItem::id) { item ->
                FeedItemCard(item)
            }
        }
    }
}

@Composable
private fun FeedInput(
    url: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUrlChanged: (String) -> Unit,
    onLoad: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("RSS 또는 Atom 주소", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                label = { Text("HTTPS 피드 URL") },
            )
            Button(
                onClick = onLoad,
                enabled = !isLoading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("불러오는 중")
                } else {
                    Text("최신 글 불러오기")
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlaybackCard(
    state: PlaybackUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("재생", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = when (state.engineState) {
                    TtsEngineState.INITIALIZING -> "TTS 엔진: 초기화 중"
                    TtsEngineState.READY -> "TTS 엔진: 준비됨"
                    TtsEngineState.UNAVAILABLE -> "TTS 엔진: 사용 불가"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.engineState == TtsEngineState.UNAVAILABLE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = state.currentTitle ?: "아직 선택된 글이 없습니다.",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.hasQueue) {
                Text(
                    text = "글 ${state.currentItemIndex + 1}/${state.totalItems} · 문단 ${state.currentChunkIndex + 1}/${state.totalChunks}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = if (state.isPlaying) onPause else onPlay,
                    enabled = state.canTogglePlayback,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            state.isPlaying -> "일시정지"
                            state.status == PlaybackStatus.ERROR &&
                                state.engineState == TtsEngineState.READY -> "다시 시도"
                            else -> "재생"
                        },
                    )
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = state.hasQueue,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("다음 글")
                }
            }
        }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.publishedAt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            if (item.summary.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
