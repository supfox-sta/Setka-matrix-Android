/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.call.api.CallType
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.CallAudioBackgroundStyles
import io.element.android.libraries.preferences.api.store.RoomWallpaperStyles
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import timber.log.Timber
import java.util.UUID
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callType: CallType,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    private val appPreferencesStore: AppPreferencesStore,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callType: CallType, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val isInWidgetMode = callType is CallType.RoomCall
    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var widgetDriverStarted by rememberSaveable { mutableStateOf(false) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        var isCallEnded by rememberSaveable { mutableStateOf(false) }
        var hasLocalParticipant by rememberSaveable { mutableStateOf(false) }
        var hasRemoteParticipant by remember { mutableStateOf(false) }
        var isMicrophoneEnabled by rememberSaveable { mutableStateOf(true) }
        var isVideoEnabled by rememberSaveable { mutableStateOf((callType as? CallType.RoomCall)?.isAudioOnly != true) }
        var shouldAutoJoin by rememberSaveable { mutableStateOf(false) }
        var autoJoinInFlight by rememberSaveable { mutableStateOf(false) }
        var autoJoinAttempted by rememberSaveable { mutableStateOf(false) }
        var callPeerId by remember { mutableStateOf<String?>(null) }
        var callPeerName by remember { mutableStateOf<String?>(null) }
        var callPeerAvatarUrl by remember { mutableStateOf<String?>(null) }
        val callConnectionState by remember(isInWidgetMode, isWidgetLoaded, hasRemoteParticipant, isCallEnded) {
            derivedStateOf {
                when {
                    isCallEnded -> CallConnectionState.Ended
                    !isInWidgetMode -> if (isWidgetLoaded) CallConnectionState.Connected else CallConnectionState.Connecting
                    !isWidgetLoaded -> CallConnectionState.Connecting
                    hasRemoteParticipant -> CallConnectionState.Connected
                    else -> CallConnectionState.Calling
                }
            }
        }
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = if (ElementTheme.isLightTheme) "light" else "dark"
        val accentColorHex by remember { appPreferencesStore.getCustomizationAccentColorHexFlow() }.collectAsState(initial = null)
        val topBarBgColorHex by remember { appPreferencesStore.getCustomizationTopBarBackgroundColorHexFlow() }.collectAsState(initial = null)
        val topBarTextColorHex by remember { appPreferencesStore.getCustomizationTopBarTextColorHexFlow() }.collectAsState(initial = null)
        val blurEnabled by remember { appPreferencesStore.getCustomizationEnableBlurEffectsFlow() }.collectAsState(initial = true)
        val animationsEnabled by remember { appPreferencesStore.getCustomizationEnableChatAnimationsFlow() }.collectAsState(initial = true)
        val callAudioBackgroundStyle by remember {
            appPreferencesStore.getCustomizationCallAudioBackgroundStyleFlow()
        }.collectAsState(initial = CallAudioBackgroundStyles.GRADIENT)
        val callPreferEarpieceByDefault by remember {
            appPreferencesStore.getCallPreferEarpieceByDefaultFlow()
        }.collectAsState(initial = true)
        val callEnableProximitySensor by remember {
            appPreferencesStore.getCallEnableProximitySensorFlow()
        }.collectAsState(initial = true)
        val defaultRoomWallpaperStyle by remember {
            appPreferencesStore.getCustomizationDefaultRoomWallpaperStyleFlow()
        }.collectAsState(initial = RoomWallpaperStyles.AUTO)

        val callAudioBackgroundImageUrl = remember(callAudioBackgroundStyle, defaultRoomWallpaperStyle, theme) {
            if (callAudioBackgroundStyle != CallAudioBackgroundStyles.WALLPAPER) return@remember null
            val customWallpaperUri = RoomWallpaperStyles.customUri(defaultRoomWallpaperStyle)
            if (!customWallpaperUri.isNullOrBlank()) return@remember customWallpaperUri
            when (defaultRoomWallpaperStyle) {
                RoomWallpaperStyles.LIGHT -> LIGHT_WALLPAPER_URL
                RoomWallpaperStyles.DARK -> DARK_WALLPAPER_URL
                else -> if (theme == "light") LIGHT_WALLPAPER_URL else DARK_WALLPAPER_URL
            }
        }

        LaunchedEffect(callType) {
            val roomCallType = callType as? CallType.RoomCall ?: return@LaunchedEffect
            val client = matrixClientsProvider.getOrNull(roomCallType.sessionId) ?: return@LaunchedEffect
            val room = client.getRoom(roomCallType.roomId) ?: return@LaunchedEffect
            val directMember = room.getDirectRoomMember()
            room.roomInfoFlow.collect { roomInfo ->
                callPeerId = directMember?.userId?.value ?: roomInfo.id.value
                callPeerName = when {
                    roomInfo.isDm -> directMember?.displayName ?: directMember?.userId?.value ?: roomInfo.name
                    else -> roomInfo.name
                }
                callPeerAvatarUrl = directMember?.avatarUrl ?: roomInfo.avatarUrl
                hasRemoteParticipant = roomInfo.activeRoomCallParticipants.any { it != room.sessionId }
            }
        }

        DisposableEffect(Unit) {
            coroutineScope.launch {
                // Sets the call as joined
                activeCallManager.joinedCall(callType)
                fetchRoomCallUrl(
                    inputs = callType,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    shouldAutoJoinState = { shouldAutoJoin = it },
                    languageTag = languageTag,
                    theme = theme,
                )
            }
            onDispose {
                appCoroutineScope.launch { activeCallManager.hangUpCall(callType) }
            }
        }

        when (callType) {
            is CallType.ExternalUrl -> {
                // No analytics yet for external calls
            }
            is CallType.RoomCall -> {
                screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
            }
        }

        HandleMatrixClientSyncState()

        LaunchedEffect(callWidgetDriver.value, messageInterceptor.value) {
            val driver = callWidgetDriver.value ?: return@LaunchedEffect
            val interceptor = messageInterceptor.value ?: return@LaunchedEffect
            if (widgetDriverStarted) return@LaunchedEffect
            widgetDriverStarted = true

            driver.incomingMessages
                .onEach { message ->
                    val currentInterceptor = messageInterceptor.value
                    if (currentInterceptor == null) {
                        Timber.w("Dropping widget message because interceptor is not ready")
                    } else {
                        currentInterceptor.sendMessage(message)
                    }
                }
                .launchIn(this)

            // Start the driver only when JS message channels are ready.
            driver.run()
        }

        messageInterceptor.value?.let { interceptor ->
            LaunchedEffect(Unit) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        // Relay message to Widget Driver
                        callWidgetDriver.value?.send(it)

                        val parsedMessage = parseMessage(it)
                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                isCallEnded = true
                                close(callWidgetDriver.value, navigator)
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                                if (shouldAutoJoin && !autoJoinAttempted) {
                                    val widgetId = callWidgetDriver.value?.id
                                    if (widgetId != null) {
                                        autoJoinAttempted = true
                                        autoJoinInFlight = true
                                        sendJoinMessage(widgetId, interceptor)
                                        launchJoinFallbackTimer(
                                            hasLocalParticipant = { hasLocalParticipant },
                                            onJoinAttemptExpired = { autoJoinInFlight = false },
                                        )
                                    }
                                }
                            } else if (parsedMessage.action == WidgetMessage.Action.Join) {
                                hasLocalParticipant = true
                                autoJoinInFlight = false
                            } else if (parsedMessage.action == WidgetMessage.Action.DeviceMute) {
                                parsedMessage.data.asJsonObjectOrNull()?.let { payload ->
                                    payload.boolean("audio_enabled")?.let { isMicrophoneEnabled = it }
                                    payload.boolean("video_enabled")?.let { isVideoEnabled = it }
                                }
                            }
                        }
                    }
                    .launchIn(this)
            }

            if (callType is CallType.RoomCall) {
                // Note: For external calls isWidgetLoaded will always be false
                LaunchedEffect(Unit) {
                    // Wait for the call to be joined, if it takes too long, we display an error
                    delay(30.seconds)

                    if (!isWidgetLoaded) {
                        Timber.w("The call took too long to load (30s). Displaying an error before exiting.")

                        // This will display a simple 'Sorry, an error occurred' dialog and force the user to exit the call
                        webViewError = ""
                    }
                }
            }
        }

        fun handleEvent(event: CallScreenEvents) {
            when (event) {
                is CallScreenEvents.Hangup -> {
                    isCallEnded = true
                    hasLocalParticipant = false
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        // If the call was joined, we need to hang up first. Then the UI will be dismissed automatically.
                        sendHangupMessage(widgetId, interceptor)
                        isWidgetLoaded = false

                        coroutineScope.launch {
                            // Wait for a couple of seconds to receive the hangup message
                            // If we don't get it in time, we close the screen anyway
                            delay(2.seconds)
                            close(callWidgetDriver.value, navigator)
                        }
                    } else {
                        coroutineScope.launch {
                            close(callWidgetDriver.value, navigator)
                        }
                    }
                }
                is CallScreenEvents.JoinCall -> {
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        autoJoinInFlight = true
                        sendJoinMessage(widgetId, interceptor)
                        coroutineScope.launchJoinFallbackTimer(
                            hasLocalParticipant = { hasLocalParticipant },
                            onJoinAttemptExpired = { autoJoinInFlight = false },
                        )
                    }
                }
                is CallScreenEvents.SetMicrophoneEnabled -> {
                    isMicrophoneEnabled = event.enabled
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        sendDeviceMuteMessage(
                            widgetId = widgetId,
                            messageInterceptor = interceptor,
                            audioEnabled = event.enabled,
                        )
                    }
                }
                is CallScreenEvents.SetVideoEnabled -> {
                    isVideoEnabled = event.enabled
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        sendDeviceMuteMessage(
                            widgetId = widgetId,
                            messageInterceptor = interceptor,
                            videoEnabled = event.enabled,
                        )
                    }
                }
                is CallScreenEvents.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvents.OnWebViewError -> {
                    if (!ignoreWebViewError) {
                        webViewError = event.description.orEmpty()
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            webViewError = webViewError,
            userAgent = userAgent,
            isCallActive = isWidgetLoaded,
            isInWidgetMode = isInWidgetMode,
            isAudioOnly = (callType as? CallType.RoomCall)?.isAudioOnly == true,
            isMicrophoneEnabled = isMicrophoneEnabled,
            isVideoEnabled = isVideoEnabled,
            showJoinCallAction = isInWidgetMode && isWidgetLoaded && !hasLocalParticipant && !isCallEnded && !autoJoinInFlight,
            callConnectionState = callConnectionState,
            callPeerId = callPeerId,
            callPeerName = callPeerName,
            callPeerAvatarUrl = callPeerAvatarUrl,
            callAccentColorHex = accentColorHex,
            callTopBarBackgroundColorHex = topBarBgColorHex,
            callTopBarTextColorHex = topBarTextColorHex,
            callBlurEnabled = blurEnabled,
            callAnimationsEnabled = animationsEnabled,
            callAudioBackgroundImageUrl = callAudioBackgroundImageUrl,
            callPreferEarpieceByDefault = callPreferEarpieceByDefault,
            callProximitySensorEnabled = callEnableProximitySensor,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        inputs: CallType,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        shouldAutoJoinState: (Boolean) -> Unit,
        languageTag: String?,
        theme: String?,
    ) {
        urlState.runCatchingUpdatingState {
            when (inputs) {
                is CallType.ExternalUrl -> {
                    inputs.url
                }
                is CallType.RoomCall -> {
                    val result = callWidgetProvider.getWidget(
                        sessionId = inputs.sessionId,
                        roomId = inputs.roomId,
                        isAudioOnly = inputs.isAudioOnly,
                        clientId = UUID.randomUUID().toString(),
                        languageTag = languageTag,
                        theme = theme,
                    ).getOrThrow()
                    callWidgetDriver.value = result.driver
                    shouldAutoJoinState(result.shouldAutoJoin)
                    Timber.d("Call widget driver initialized for sessionId: ${inputs.sessionId}, roomId: ${inputs.roomId}")
                    result.url
                        .withEmbeddedCallParam(name = "header", value = "none")
                        .withEmbeddedCallParam(name = "showControls", value = "false")
                        .withEmbeddedCallParam(name = "preload", value = "true")
                        .withEmbeddedCallParam(name = "skipLobby", value = "true")
                }
            }
        }
    }

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val roomCallType = callType as? CallType.RoomCall ?: return@DisposableEffect onDispose {}
            val client = matrixClientsProvider.getOrNull(roomCallType.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${roomCallType.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun sendJoinMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.Join,
            data = buildJsonObject {
                // Embedded Element Call expects a JoinCallData object, even when we do not
                // override specific devices from the native shell.
                put("audioInput", JsonNull)
                put("videoInput", JsonNull)
            },
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun sendDeviceMuteMessage(
        widgetId: String,
        messageInterceptor: WidgetMessageInterceptor,
        audioEnabled: Boolean? = null,
        videoEnabled: Boolean? = null,
    ) {
        val payload = buildJsonObject {
            audioEnabled?.let { put("audio_enabled", JsonPrimitive(it)) }
            videoEnabled?.let { put("video_enabled", JsonPrimitive(it)) }
        }
        if (payload.isEmpty()) return
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.DeviceMute,
            data = payload,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun CoroutineScope.close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) = launch(dispatchers.io) {
        navigator.close()
        widgetDriver?.close()
    }

    private fun CoroutineScope.launchJoinFallbackTimer(
        hasLocalParticipant: () -> Boolean,
        onJoinAttemptExpired: () -> Unit,
    ) = launch {
        delay(4.seconds)
        if (!hasLocalParticipant()) {
            onJoinAttemptExpired()
        }
    }
}

private fun kotlinx.serialization.json.JsonElement?.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.boolean(key: String): Boolean? {
    return (get(key) as? JsonPrimitive)?.booleanOrNull
}

private fun String.withEmbeddedCallParam(name: String, value: String): String {
    val hashIndex = indexOf('#')
    if (hashIndex == -1) {
        val questionIndex = indexOf('?')
        val path = if (questionIndex == -1) this else substring(0, questionIndex)
        val rawQuery = if (questionIndex == -1) "" else substring(questionIndex + 1)
        val encoded = rawQuery
            .toMutableParamMap()
            .apply { put(name, value) }
            .toEncodedQuery()
        return "$path?$encoded"
    }
    val prefix = substring(0, hashIndex + 1)
    val fragment = substring(hashIndex + 1)
    val questionIndex = fragment.indexOf('?')
    val path = if (questionIndex == -1) fragment else fragment.substring(0, questionIndex)
    val rawQuery = if (questionIndex == -1) "" else fragment.substring(questionIndex + 1)
    val encoded = rawQuery
        .toMutableParamMap()
        .apply { put(name, value) }
        .toEncodedQuery()
    return "$prefix$path?$encoded"
}

private fun String.toMutableParamMap(): LinkedHashMap<String, String> {
    val params = linkedMapOf<String, String>()
    if (isBlank()) return params
    split('&')
        .filter { it.isNotBlank() }
        .forEach { entry ->
            val parts = entry.split('=', limit = 2)
            val key = URLDecoder.decode(parts[0], Charsets.UTF_8)
            val value = URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
            params[key] = value
        }
    return params
}

private fun Map<String, String>.toEncodedQuery(): String {
    return entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
    }
}

private const val LIGHT_WALLPAPER_URL = "https://web.setka-matrix.ru/themes/element/img/backgrounds/light_bg.png"
private const val DARK_WALLPAPER_URL = "https://web.setka-matrix.ru/themes/element/img/backgrounds/dark_bg.png"
