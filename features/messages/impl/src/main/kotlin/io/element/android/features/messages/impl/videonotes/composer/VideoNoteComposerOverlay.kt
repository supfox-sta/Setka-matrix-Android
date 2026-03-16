/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.videonotes.composer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.api.timeline.videonotes.composer.VideoNoteComposerEvent
import io.element.android.features.messages.api.timeline.videonotes.composer.VideoNoteComposerState
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.permissions.api.R as PermissionsR
import io.element.android.libraries.textcomposer.model.VideoNoteRecorderEvent
import io.element.android.libraries.textcomposer.model.VideoNoteState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.io.File

@Composable
internal fun VideoNoteComposerOverlay(
    state: VideoNoteComposerState,
    modifier: Modifier = Modifier,
) {
    val videoNoteState = state.videoNoteState
    if (videoNoteState is VideoNoteState.Idle) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val ringStrokeWidth = 6.dp
    val ringPadding = 10.dp
    val ringStrokePx = with(density) { ringStrokeWidth.toPx() }
    val ringPaddingPx = with(density) { ringPadding.toPx() }

    val maxDuration = 60.seconds

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewUseCase = remember { Preview.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var outputFile by remember { mutableStateOf<java.io.File?>(null) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var recordingStartUptimeMillis by remember { mutableLongStateOf(0L) }

    val coroutineScope = rememberCoroutineScope()

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var flashEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val segments = remember { mutableStateListOf<File>() }
    var pendingLensFacing by remember { mutableStateOf<Int?>(null) }
    var isSwitchingCamera by remember { mutableStateOf(false) }
    var isMergingSegments by remember { mutableStateOf(false) }
    val circleFlipDegrees = remember { Animatable(0f) }
    var preventAutoStart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cameraProvider = context.getCameraProvider()
    }

    // Bind camera only when needed (provider/view/facing changes), otherwise torch will flicker.
    LaunchedEffect(cameraProvider, previewViewRef, lensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val previewView = previewViewRef ?: return@LaunchedEffect
        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        try {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                previewUseCase,
                videoCapture,
            )
        } catch (_: Throwable) {
            boundCamera = null
        }
    }

    // Apply "flash" state to current camera: back = torch; front = display flash (handled by UI overlay).
    LaunchedEffect(boundCamera, lensFacing, flashEnabled) {
        val cam = boundCamera ?: return@LaunchedEffect
        val enableTorch = lensFacing == CameraSelector.LENS_FACING_BACK &&
            flashEnabled &&
            cam.cameraInfo.hasFlashUnit()
        try {
            cam.cameraControl.enableTorch(enableTorch)
        } catch (_: Throwable) {
            // Best-effort; if it fails, keep UI state but don't crash.
        }
    }

    suspend fun concatVideoSegments(context: Context, inputFiles: List<File>): File {
        val tmpFile = java.io.File(context.cacheDir, "video-note-merged-${System.currentTimeMillis()}.mp4")
        val editedItems = inputFiles.map { file ->
            EditedMediaItem.Builder(MediaItem.fromUri(file.toUri())).build()
        }
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)
        val composition = Composition.Builder(sequence).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        return suspendCancellableCoroutine { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer.removeListener(this)
                    if (cont.isActive) cont.resume(tmpFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer.removeListener(this)
                    tmpFile.safeDelete()
                    if (cont.isActive) cont.resume(inputFiles.last())
                }
            }
            transformer.addListener(listener)
            cont.invokeOnCancellation {
                transformer.removeListener(listener)
                tmpFile.safeDelete()
            }
            transformer.start(composition, tmpFile.path)
        }
    }

    fun stopRecording() {
        recording?.stop()
    }

    LaunchedEffect(videoNoteState, recordingStartUptimeMillis, recording) {
        if (videoNoteState !is VideoNoteState.Recording) return@LaunchedEffect
        if (recording == null || recordingStartUptimeMillis <= 0L) return@LaunchedEffect
        // Keep the timer in this UI layer, to avoid pushing ticks through the presenter.
        while (recording != null) {
            delay(50.milliseconds)
            val now = SystemClock.uptimeMillis()
            elapsedMillis = (now - recordingStartUptimeMillis).coerceAtMost(maxDuration.inWholeMilliseconds)
            if (elapsedMillis >= maxDuration.inWholeMilliseconds) {
                state.eventSink(VideoNoteComposerEvent.RecorderEvent(VideoNoteRecorderEvent.Stop))
                stopRecording()
            }
        }
    }

    // Respond to stop requests coming from the composer (press & release).
    if (videoNoteState is VideoNoteState.Recording) {
        val stopRequestId = videoNoteState.stopRequestId
        LaunchedEffect(stopRequestId) {
            if (stopRequestId > 0) {
                preventAutoStart = true
                stopRecording()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        val circleSize = 260.dp
        val ringSize = circleSize + ringPadding * 2
        val flashHoleRadiusPx = with(density) { (ringSize * 0.5f + 4.dp).toPx() }

        if (videoNoteState is VideoNoteState.Recording && lensFacing == CameraSelector.LENS_FACING_FRONT && flashEnabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .semantics { hideFromAccessibility() }
            ) {
                drawRect(Color.White.copy(alpha = 0.92f))
                drawCircle(
                    color = Color.Transparent,
                    radius = flashHoleRadiusPx,
                    center = center,
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // Center circle
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center,
        ) {
            val ringBgColor = ElementTheme.colors.iconQuaternary
            val ringProgressColor = ElementTheme.colors.iconAccentPrimary
            val progress = when (videoNoteState) {
                is VideoNoteState.Recording -> (elapsedMillis.toFloat() / maxDuration.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .semantics { hideFromAccessibility() },
            ) {
                val stroke = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
                val inset = ringPaddingPx + ringStrokePx / 2f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = ringBgColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = ringProgressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }

            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgSubtleSecondary),
                contentAlignment = Alignment.Center,
            ) {
                when (videoNoteState) {
                    is VideoNoteState.Recording -> {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = circleFlipDegrees.value },
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            update = { previewView ->
                                previewViewRef = previewView
                                if (recording == null && boundCamera != null && !isMergingSegments && !isSwitchingCamera && !preventAutoStart) {
                                     if (recordingStartUptimeMillis <= 0L) {
                                         elapsedMillis = 0L
                                         recordingStartUptimeMillis = SystemClock.uptimeMillis()
                                     }
                                     preventAutoStart = false
                                     val file = java.io.File(context.cacheDir, "video-note-${System.currentTimeMillis()}.mp4")
                                     outputFile = file
                                     val options = FileOutputOptions.Builder(file).build()
                                     val pending: PendingRecording = videoCapture.output
                                         .prepareRecording(context, options)
                                         .withAudioEnabled()
                                     recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                         when (event) {
                                             is VideoRecordEvent.Finalize -> {
                                                 val out = outputFile
                                                 recording?.close()
                                                 recording = null
                                                 if (event.hasError() || out == null) {
                                                     out?.safeDelete()
                                                     state.eventSink(VideoNoteComposerEvent.RecorderEvent(VideoNoteRecorderEvent.Cancel))
                                                     return@start
                                                 }
                                                 if (isSwitchingCamera) {
                                                     segments.add(out)
                                                     outputFile = null
                                                     val nextFacing = pendingLensFacing
                                                     pendingLensFacing = null
                                                     if (nextFacing != null) {
                                                         coroutineScope.launch {
                                                             lensFacing = nextFacing
                                                             circleFlipDegrees.snapTo(-90f)
                                                             circleFlipDegrees.animateTo(0f, tween(durationMillis = 110))
                                                         }
                                                     }
                                                     coroutineScope.launch {
                                                         // Prevent auto-start for a short moment until the new camera bind settles.
                                                         delay(80.milliseconds)
                                                         isSwitchingCamera = false
                                                     }
                                                     return@start
                                                 }

                                                 val durationMs = out.videoDurationMillis(context)
                                                 val fullDurationMs = elapsedMillis.coerceAtLeast(durationMs)
                                                 val allSegments = buildList {
                                                     addAll(segments)
                                                     add(out)
                                                 }

                                                 if (segments.isNotEmpty()) {
                                                     isMergingSegments = true
                                                     coroutineScope.launch {
                                                         val merged = concatVideoSegments(context, allSegments)
                                                         // Clean up intermediate segments if merge succeeded.
                                                         if (merged != out) {
                                                             allSegments.forEach { if (it != merged) it.safeDelete() }
                                                         }
                                                         segments.clear()
                                                         isMergingSegments = false
                                                         state.eventSink(
                                                             VideoNoteComposerEvent.RecordingFinished(
                                                                 file = merged,
                                                                 mimeType = "video/mp4",
                                                                 durationMillis = fullDurationMs,
                                                             )
                                                         )
                                                     }
                                                 } else {
                                                     state.eventSink(
                                                         VideoNoteComposerEvent.RecordingFinished(
                                                             file = out,
                                                             mimeType = "video/mp4",
                                                             durationMillis = fullDurationMs,
                                                         )
                                                     )
                                                 }
                                             }
                                             else -> Unit
                                         }
                                     }
                                 }
                             },
                             onRelease = {
                                 previewViewRef = null
                                 cameraProvider?.unbindAll()
                             }
                        )
                    }
                    is VideoNoteState.Preview -> {
                        VideoNotePreview(
                            file = videoNoteState.file,
                            durationMillis = max(1L, videoNoteState.duration.inWholeMilliseconds),
                        )
                    }
                    VideoNoteState.Idle -> Unit
                }
            }
        }

        // Footer controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (videoNoteState is VideoNoteState.Recording) {
                    IconButton(
                        onClick = {
                            // Switch camera mid-record without "starting over":
                            // We stop the current segment, switch camera, and later concatenate segments into a single mp4.
                            if (recording == null || isMergingSegments) return@IconButton
                            if (isSwitchingCamera) return@IconButton

                            pendingLensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                CameraSelector.LENS_FACING_BACK
                            } else {
                                CameraSelector.LENS_FACING_FRONT
                            }
                            isSwitchingCamera = true
                            preventAutoStart = false

                            // Flip animation: rotate to 90 degrees and hold until the camera is swapped.
                            coroutineScope.launch {
                                circleFlipDegrees.animateTo(90f, tween(durationMillis = 110))
                            }
                            stopRecording()
                        }
                    ) {
                        Icon(
                            imageVector = CompoundIcons.SwitchCameraSolid(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val flashTint = when {
                        flashEnabled -> ElementTheme.colors.iconAccentPrimary
                        else -> ElementTheme.colors.iconSecondary
                    }
                    IconButton(
                        onClick = {
                            // Toggle persistent flash state.
                            // Back camera: torch (if available). Front camera: display flash (white overlay around circle).
                            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                val cam = boundCamera
                                if (cam?.cameraInfo?.hasFlashUnit() == true) {
                                    flashEnabled = !flashEnabled
                                }
                            } else {
                                flashEnabled = !flashEnabled
                            }
                        }
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Spotlight(),
                            contentDescription = null,
                            tint = flashTint,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                IconButton(
                    onClick = {
                        flashEnabled = false
                        try {
                            boundCamera?.cameraControl?.enableTorch(false)
                        } catch (_: Throwable) {
                            // no-op
                        }
                        segments.forEach { it.safeDelete() }
                        segments.clear()
                        recordingStartUptimeMillis = 0L
                        elapsedMillis = 0L
                        preventAutoStart = false
                        outputFile?.safeDelete()
                        state.eventSink(VideoNoteComposerEvent.DeleteVideoNote)
                    }
                ) {
                    Icon(imageVector = CompoundIcons.Close(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val timerText = formatTimer(elapsedMillis)
            Text(
                text = timerText,
                color = ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodyLgRegular,
            )

            Spacer(modifier = Modifier.weight(1f))

            when (videoNoteState) {
                is VideoNoteState.Recording -> {
                    IconButton(
                        onClick = {
                            state.eventSink(VideoNoteComposerEvent.RecorderEvent(VideoNoteRecorderEvent.Stop))
                            stopRecording()
                        }
                    ) {
                        Icon(resourceId = CommonDrawables.ic_stop, contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary)
                    }
                }
                is VideoNoteState.Preview -> {
                    IconButton(
                        enabled = !videoNoteState.isSending,
                        onClick = { state.eventSink(VideoNoteComposerEvent.SendVideoNote) }
                    ) {
                        Icon(imageVector = CompoundIcons.SendSolid(), contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary)
                    }
                }
                VideoNoteState.Idle -> Unit
            }
        }

        if (state.showPermissionRationaleDialog) {
            io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog(
                content = context.getString(PermissionsR.string.dialog_permission_camera),
                onSubmitClick = { state.eventSink(VideoNoteComposerEvent.AcceptPermissionRationale) },
                onDismiss = { state.eventSink(VideoNoteComposerEvent.DismissPermissionsRationale) },
                submitText = context.getString(io.element.android.libraries.ui.strings.CommonStrings.action_continue),
                cancelText = context.getString(io.element.android.libraries.ui.strings.CommonStrings.action_cancel),
            )
        }
    }
}

@Composable
private fun VideoNotePreview(
    file: java.io.File,
    durationMillis: Long,
) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            setMediaItem(MediaItem.fromUri(file.toUri()))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(file) {
        player.play()
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    val stopLatest by rememberUpdatedState {
        player.pause()
        player.seekTo(0)
        progress = 0f
        isPlaying = false
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopLatest()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val dur = player.duration.coerceAtLeast(1)
            progress = (player.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            delay(50.milliseconds)
        }
    }

    val ringStrokeWidth = 6.dp
    val ringPadding = 10.dp
    val density = LocalDensity.current
    val ringStrokePx = with(density) { ringStrokeWidth.toPx() }
    val ringPaddingPx = with(density) { ringPadding.toPx() }

    var scrubJustUntilMs by remember { mutableLongStateOf(0L) }
    val ringBgColor = ElementTheme.colors.iconQuaternary
    val ringProgressColor = ElementTheme.colors.iconAccentPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(player) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = down.position.x - center.x
                        val dy = down.position.y - center.y
                        val dist = hypot(dx, dy)
                        val outer = min(size.width, size.height) / 2f
                        val inner = outer - (ringPaddingPx + ringStrokePx * 3f)
                        val onRing = dist in inner..outer
                        if (!onRing) continue
                        scrubJustUntilMs = SystemClock.uptimeMillis() + 250L
                        val angle = atan2(dy, dx) + (PI / 2.0)
                        var fraction = (angle / (2.0 * PI)).toFloat()
                        if (fraction < 0f) fraction += 1f
                        val seekTo = (fraction * player.duration).toLong()
                        if (player.duration > 0) player.seekTo(seekTo)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val mdx = change.position.x - center.x
                            val mdy = change.position.y - center.y
                            val mAngle = atan2(mdy, mdx) + (PI / 2.0)
                            var mFraction = (mAngle / (2.0 * PI)).toFloat()
                            if (mFraction < 0f) mFraction += 1f
                            if (player.duration > 0) player.seekTo((mFraction * player.duration).toLong())
                            change.consume()
                        }
                    }
                }
            }
            .background(ElementTheme.colors.bgSubtleSecondary),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { view -> view.player = player }
        )

        // Simple click-to-play/pause.
        IconButton(
            modifier = Modifier.align(Alignment.Center),
            onClick = {
                if (SystemClock.uptimeMillis() < scrubJustUntilMs) return@IconButton
                if (isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        ) {
            Icon(imageVector = if (isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(), contentDescription = null)
        }

        // Scrubbable ring overlay.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { hideFromAccessibility() },
        ) {
            val stroke = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
            val inset = ringPaddingPx + ringStrokePx / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = ringBgColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = ringProgressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
    }
}

private fun formatTimer(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this),
            )
        }
    }

private fun java.io.File.videoDurationMillis(context: Context): Long {
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, this.toUri())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}
