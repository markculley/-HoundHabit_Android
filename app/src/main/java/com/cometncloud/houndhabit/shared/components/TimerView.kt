package com.cometncloud.houndhabit.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cometncloud.houndhabit.shared.util.HapticManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Self-contained countdown timer with haptics. Mirrors iOS TimerView:
 * preset durations (5s / 30s / 1m / 2m / 5m), circular progress ring,
 * play-pause-reset controls, haptic on each interaction and at completion.
 *
 * The container [TrainingTimerSection] adds an iOS-style expandable
 * "Training Timer" header that hosts this view. Use it directly in a
 * form, or use the inner [TimerView] if you want to host it differently.
 */

enum class TimerStatus { IDLE, RUNNING, PAUSED, COMPLETE }

data class TimerUiState(
    val durationSeconds: Int = 60,
    val remainingSeconds: Float = 60f,
    val status: TimerStatus = TimerStatus.IDLE,
)

class TimerViewModel : ViewModel() {
    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    private var tickJob: Job? = null

    fun selectDuration(seconds: Int) {
        if (_state.value.status == TimerStatus.RUNNING) return
        tickJob?.cancel()
        _state.value = TimerUiState(
            durationSeconds = seconds,
            remainingSeconds = seconds.toFloat(),
            status = TimerStatus.IDLE,
        )
    }

    fun start() {
        if (_state.value.status == TimerStatus.RUNNING) return
        if (_state.value.status == TimerStatus.COMPLETE) reset()
        HapticManager.medium()
        _state.update { it.copy(status = TimerStatus.RUNNING) }
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val current = _state.value
                if (current.status != TimerStatus.RUNNING) return@launch
                val next = current.remainingSeconds - 0.1f
                if (next <= 0f) {
                    _state.update {
                        it.copy(remainingSeconds = 0f, status = TimerStatus.COMPLETE)
                    }
                    HapticManager.timerComplete()
                    return@launch
                }
                _state.update { it.copy(remainingSeconds = next) }
            }
        }
    }

    fun pause() {
        if (_state.value.status != TimerStatus.RUNNING) return
        HapticManager.medium()
        tickJob?.cancel()
        _state.update { it.copy(status = TimerStatus.PAUSED) }
    }

    fun reset() {
        HapticManager.light()
        tickJob?.cancel()
        _state.update {
            it.copy(remainingSeconds = it.durationSeconds.toFloat(), status = TimerStatus.IDLE)
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}

private val PRESETS = listOf(
    5 to "5s",
    30 to "30s",
    60 to "1m",
    120 to "2m",
    300 to "5m",
)

/**
 * Expandable "Training Timer" header with the timer body collapsed below.
 * Drop into a form between sections.
 */
@Composable
fun TrainingTimerSection(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(),
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null)
            Text(
                "Training Timer",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
            )
        }

        AnimatedVisibility(visible = expanded) {
            TimerView(
                viewModel = viewModel,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerView(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PRESETS.forEachIndexed { idx, (seconds, label) ->
                SegmentedButton(
                    selected = state.durationSeconds == seconds,
                    onClick = { viewModel.selectDuration(seconds) },
                    enabled = state.status != TimerStatus.RUNNING,
                    shape = SegmentedButtonDefaults.itemShape(idx, PRESETS.size),
                ) { Text(label) }
            }
        }

        ProgressRing(state = state)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledIconButton(
                onClick = {
                    if (state.status == TimerStatus.RUNNING) viewModel.pause()
                    else viewModel.start()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (state.status == TimerStatus.RUNNING) Icons.Filled.Pause
                    else Icons.Filled.PlayArrow,
                    contentDescription = if (state.status == TimerStatus.RUNNING) "Pause" else "Start",
                )
            }
            IconButton(
                onClick = { viewModel.reset() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset")
            }
        }
    }
}

@Composable
private fun ProgressRing(state: TimerUiState) {
    val progress = if (state.durationSeconds > 0)
        state.remainingSeconds / state.durationSeconds.toFloat()
    else 0f
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        Text(
            formatTime(state.remainingSeconds),
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

private fun formatTime(seconds: Float): String {
    val total = kotlin.math.ceil(seconds).toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "%d:%02d".format(m, s) else ":%02d".format(s)
}
