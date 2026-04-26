/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.Composer
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.features.location.api.LocationService
import io.element.android.features.messages.impl.MessagesNavigator
import io.element.android.features.messages.impl.attachments.Attachment
import io.element.android.features.messages.impl.attachments.preview.error.sendAttachmentError
import io.element.android.features.messages.impl.draft.ComposerDraftService
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryFilter
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryItem
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryMediaProvider
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryPickerEvent
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryPickerState
import io.element.android.features.messages.impl.messagecomposer.legacygallery.matches
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaComposerState
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaDeleteConfirmation
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaPackEditorState
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaPackKind
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaService
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaStickerPack
import io.element.android.features.messages.impl.messagecomposer.suggestions.RoomAliasSuggestionsDataSource
import io.element.android.features.messages.impl.messagecomposer.suggestions.SuggestionsProcessor
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.room.IntentionalMention
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.draft.ComposerDraft
import io.element.android.libraries.matrix.api.room.draft.ComposerDraftType
import io.element.android.libraries.matrix.api.room.getDirectRoomMember
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.room.powerlevels.use
import io.element.android.libraries.matrix.api.timeline.TimelineException
import io.element.android.libraries.matrix.api.timeline.item.event.toEventOrTransactionId
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.matrix.ui.messages.reply.map
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.push.api.notifications.conversations.NotificationConversationService
import io.element.android.libraries.textcomposer.mentions.MentionSpanProvider
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MarkdownTextEditorState
import io.element.android.libraries.textcomposer.model.Message
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.Suggestion
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.textcomposer.model.rememberMarkdownTextEditorState
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import io.element.android.services.toolbox.api.intent.ExternalIntentLauncher
import io.element.android.services.toolbox.api.sdk.BuildVersionSdkIntProvider
import io.element.android.wysiwyg.compose.RichTextEditorState
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds
import io.element.android.libraries.core.mimetype.MimeTypes.Any as AnyMimeTypes

private data class PendingSetkaUpload(
    val packId: String,
    val kind: SetkaPackKind,
)

@Suppress("LargeClass")
@AssistedInject
class MessageComposerPresenter(
    @Assisted private val navigator: MessagesNavigator,
    @Assisted private val timelineController: TimelineController,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val room: JoinedRoom,
    private val mediaPickerProvider: PickerProvider,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val localMediaFactory: LocalMediaFactory,
    mediaSenderFactory: MediaSenderFactory,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val analyticsService: AnalyticsService,
    private val locationService: LocationService,
    private val messageComposerContext: DefaultMessageComposerContext,
    private val richTextEditorStateFactory: RichTextEditorStateFactory,
    private val roomAliasSuggestionsDataSource: RoomAliasSuggestionsDataSource,
    private val legacyGalleryMediaProvider: LegacyGalleryMediaProvider,
    private val buildVersionSdkIntProvider: BuildVersionSdkIntProvider,
    private val permalinkParser: PermalinkParser,
    private val permalinkBuilder: PermalinkBuilder,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val draftService: ComposerDraftService,
    private val mentionSpanProvider: MentionSpanProvider,
    private val pillificationHelper: TextPillificationHelper,
    private val suggestionsProcessor: SuggestionsProcessor,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    private val notificationConversationService: NotificationConversationService,
    private val setkaService: SetkaService,
    private val externalIntentLauncher: ExternalIntentLauncher,
) : Presenter<MessageComposerState> {
    @AssistedFactory
    interface Factory {
        fun create(timelineController: TimelineController, navigator: MessagesNavigator): MessageComposerPresenter
    }

    private val mediaSender = mediaSenderFactory.create(timelineMode = timelineController.mainTimelineMode())

    private val cameraPermissionPresenter = permissionsPresenterFactory.create(Manifest.permission.CAMERA)
    private val legacyGalleryPermissionPresenter = permissionsPresenterFactory.create(Manifest.permission.READ_EXTERNAL_STORAGE)
    private var pendingEvent: MessageComposerEvent? = null
    private val suggestionSearchTrigger = MutableStateFlow<Suggestion?>(null)

    // Used to disable some UI related elements in tests
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var isTesting: Boolean = false

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var showTextFormatting: Boolean by mutableStateOf(false)

    @SuppressLint("UnsafeOptInUsageError")
    @Composable
    override fun present(): MessageComposerState {
        val localCoroutineScope = rememberCoroutineScope()

        val roomInfo by room.roomInfoFlow.collectAsState()

        val richTextEditorState = richTextEditorStateFactory.remember()
        if (isTesting) {
            richTextEditorState.isReadyToProcessActions = true
        }
        val markdownTextEditorState = rememberMarkdownTextEditorState(initialText = null, initialFocus = false)

        val cameraPermissionState = cameraPermissionPresenter.present()
        val legacyGalleryPermissionState = legacyGalleryPermissionPresenter.present()

        val canShareLocation = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            canShareLocation.value = locationService.isServiceAvailable()
        }

        val galleryMediaPicker = mediaPickerProvider.registerGalleryPicker { uri, mimeType ->
            handlePickedMedia(uri, mimeType)
        }
        val filesPicker = mediaPickerProvider.registerFilePicker(AnyMimeTypes) { uri, mimeType ->
            handlePickedMedia(uri, mimeType ?: MimeTypes.OctetStream)
        }
        val cameraPhotoPicker = mediaPickerProvider.registerCameraPhotoPicker { uri ->
            handlePickedMedia(uri, MimeTypes.Jpeg)
        }
        val cameraVideoPicker = mediaPickerProvider.registerCameraVideoPicker { uri ->
            handlePickedMedia(uri, MimeTypes.Mp4)
        }
        val isFullScreen = rememberSaveable {
            mutableStateOf(false)
        }
        var showAttachmentSourcePicker: Boolean by remember { mutableStateOf(false) }
        var showLegacyGalleryPicker: Boolean by remember { mutableStateOf(false) }
        var legacyGalleryFilter by rememberSaveable { mutableStateOf(LegacyGalleryFilter.All) }
        var legacyGalleryItems by remember { mutableStateOf(emptyList<LegacyGalleryItem>()) }
        val selectedLegacyGalleryItems = remember { mutableStateListOf<LegacyGalleryItem>() }
        var isLegacyGalleryLoading by remember { mutableStateOf(false) }
        var pendingSetkaUpload by remember { mutableStateOf<PendingSetkaUpload?>(null) }
        var suspendedSetkaPackEditor by remember { mutableStateOf<SetkaPackEditorState?>(null) }
        var setkaState by remember { mutableStateOf(SetkaComposerState()) }

        val sendTypingNotifications by remember {
            sessionPreferencesStore.isSendTypingNotificationsEnabled()
        }.collectAsState(initial = true)

        LaunchedEffect(cameraPermissionState.permissionGranted) {
            if (cameraPermissionState.permissionGranted) {
                when (pendingEvent) {
                    is MessageComposerEvent.PickAttachmentSource.PhotoFromCamera -> cameraPhotoPicker.launch()
                    is MessageComposerEvent.PickAttachmentSource.VideoFromCamera -> cameraVideoPicker.launch()
                    else -> Unit
                }
                pendingEvent = null
            }
        }

        LaunchedEffect(
            showLegacyGalleryPicker,
            legacyGalleryPermissionState.permissionGranted,
            legacyGalleryPermissionState.permissionAlreadyAsked,
        ) {
            if (!showLegacyGalleryPicker || !shouldUseLegacyGalleryPicker()) return@LaunchedEffect
            if (!legacyGalleryPermissionState.permissionGranted) {
                if (!legacyGalleryPermissionState.permissionAlreadyAsked) {
                    legacyGalleryPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                }
                return@LaunchedEffect
            }
            isLegacyGalleryLoading = true
            legacyGalleryItems = runCatching {
                legacyGalleryMediaProvider.getRecentMedia(limit = 120)
            }.getOrElse { throwable ->
                Timber.e(throwable, "Failed to load media for legacy gallery picker")
                emptyList()
            }
            isLegacyGalleryLoading = false
        }

        val suggestions = remember { mutableStateListOf<ResolvedSuggestion>() }
        ResolveSuggestionsEffect(suggestions)

        DisposableEffect(Unit) {
            // Declare that the user is not typing anymore when the composer is disposed
            onDispose {
                sessionCoroutineScope.launch {
                    if (sendTypingNotifications) {
                        room.typingNotice(false)
                    }
                }
            }
        }

        val textEditorState by rememberUpdatedState(
            if (showTextFormatting) {
                TextEditorState.Rich(richTextEditorState, roomInfo.isEncrypted == true)
            } else {
                TextEditorState.Markdown(markdownTextEditorState, roomInfo.isEncrypted == true)
            }
        )

        LaunchedEffect(Unit) {
            val draft = draftService.loadDraft(
                roomId = room.roomId,
                // TODO support threads in composer
                threadRoot = null,
                isVolatile = false
            )
            if (draft != null) {
                applyDraft(draft, markdownTextEditorState, richTextEditorState)
            }
        }

        fun applySetkaBootstrap(showLoading: Boolean, errorMessage: String? = null) {
            if (showLoading) {
                setkaState = setkaState.copy(isLoading = true)
            }
            localCoroutineScope.launch {
                setkaService.bootstrap().fold(
                    onSuccess = { bootstrap ->
                        setkaState = setkaState.copy(
                            isLoading = false,
                            subscription = bootstrap.subscription,
                            plans = bootstrap.plans.toImmutableList(),
                            stickerPacks = bootstrap.stickerPacks.toImmutableList(),
                            errorMessage = errorMessage,
                        )
                    },
                    onFailure = { failure ->
                        setkaState = setkaState.copy(
                            isLoading = false,
                            errorMessage = failure.message ?: "Не удалось загрузить Setka",
                        )
                    }
                )
            }
        }

        fun refreshSetka(showLoading: Boolean = false) {
            applySetkaBootstrap(showLoading = showLoading)
        }

        fun replaceSetkaPack(updatedPack: SetkaStickerPack) {
            setkaState = setkaState.copy(
                stickerPacks = setkaState.stickerPacks
                    .map { pack -> if (pack.id == updatedPack.id) updatedPack else pack }
                    .sortedBy { it.name.lowercase() }
                    .toImmutableList()
            )
        }

        fun findInstalledSharedPackId(pack: SetkaStickerPack): String? {
            return setkaState.stickerPacks.firstOrNull { installedPack ->
                installedPack.id == pack.id || (
                    installedPack.kind == pack.kind &&
                        installedPack.name == pack.name &&
                        installedPack.stickers.map { it.mxcUrl }.toSet() == pack.stickers.map { it.mxcUrl }.toSet()
                    )
            }?.id
        }

        fun handleSetkaPickedMedia(uri: Uri?) {
            val uploadTarget = pendingSetkaUpload ?: return
            pendingSetkaUpload = null
            val editorToRestore = suspendedSetkaPackEditor
            suspendedSetkaPackEditor = null
            uri ?: return
            localCoroutineScope.launch {
                val pack = setkaState.stickerPacks.firstOrNull { it.id == uploadTarget.packId }
                if (pack == null) {
                    setkaState = setkaState.copy(
                        errorMessage = "Пак не найден",
                        packEditorState = editorToRestore ?: setkaState.packEditorState,
                    )
                    return@launch
                }
                setkaState = setkaState.copy(uploadingPackId = uploadTarget.packId, errorMessage = null)
                setkaService.uploadPackMedia(uri, uploadTarget.kind)
                    .mapCatching { sticker ->
                        setkaService.addStickerToPack(pack, sticker).getOrThrow()
                    }
                    .fold(
                        onSuccess = { updatedPack ->
                            replaceSetkaPack(updatedPack)
                            setkaState = setkaState.copy(
                                uploadingPackId = null,
                                packEditorState = editorToRestore ?: setkaState.packEditorState,
                            )
                        },
                        onFailure = { failure ->
                            setkaState = setkaState.copy(
                                uploadingPackId = null,
                                packEditorState = editorToRestore ?: setkaState.packEditorState,
                                errorMessage = failure.message ?: "Не удалось загрузить медиа",
                            )
                        }
                    )
            }
        }

        val setkaGalleryImagePicker = mediaPickerProvider.registerGalleryImagePicker { uri ->
            handleSetkaPickedMedia(uri)
        }

        fun openSetkaUploadPicker(packId: String, kind: SetkaPackKind) {
            pendingSetkaUpload = PendingSetkaUpload(packId = packId, kind = kind)
            if (shouldUseLegacyGalleryPicker()) {
                suspendedSetkaPackEditor = setkaState.packEditorState
                setkaState = setkaState.copy(packEditorState = null)
                legacyGalleryFilter = LegacyGalleryFilter.Photos
                selectedLegacyGalleryItems.clear()
                showLegacyGalleryPicker = true
            } else {
                setkaGalleryImagePicker.launch()
            }
        }

        fun openSetkaPayment(url: String?) {
            if (url.isNullOrBlank()) return
            externalIntentLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        LaunchedEffect(Unit) {
            refreshSetka(showLoading = true)
        }

        fun handleEvent(event: MessageComposerEvent) {
            when (event) {
                MessageComposerEvent.ToggleFullScreenState -> isFullScreen.value = !isFullScreen.value
                MessageComposerEvent.CloseSpecialMode -> {
                    if (messageComposerContext.composerMode.isEditing) {
                        localCoroutineScope.launch {
                            resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = true)
                        }
                    } else {
                        messageComposerContext.composerMode = MessageComposerMode.Normal
                    }
                }
                is MessageComposerEvent.SendMessage -> {
                    sessionCoroutineScope.sendMessage(
                        markdownTextEditorState = markdownTextEditorState,
                        richTextEditorState = richTextEditorState,
                        stickerPacks = setkaState.stickerPacks,
                        isSetkaPlusActive = setkaState.isPlusActive,
                    )
                }
                is MessageComposerEvent.SendUri -> {
                    val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId
                    sessionCoroutineScope.sendAttachment(
                        attachment = Attachment.Media(
                            localMedia = localMediaFactory.createFromUri(
                                uri = event.uri,
                                mimeType = null,
                                name = null,
                                formattedFileSize = null
                            ),
                        ),
                        inReplyToEventId = inReplyToEventId,
                    )

                    // Reset composer since the attachment has been sent
                    messageComposerContext.composerMode = MessageComposerMode.Normal
                }
                is MessageComposerEvent.SetMode -> {
                    localCoroutineScope.setMode(event.composerMode, markdownTextEditorState, richTextEditorState)
                }
                MessageComposerEvent.AddAttachment -> localCoroutineScope.launch {
                    val shouldShowAttachmentPicker = !showAttachmentSourcePicker
                    showAttachmentSourcePicker = shouldShowAttachmentPicker
                    if (shouldShowAttachmentPicker) {
                        setkaState = setkaState.copy(isStickerPickerVisible = false)
                    }
                }
                MessageComposerEvent.DismissAttachmentMenu -> showAttachmentSourcePicker = false
                MessageComposerEvent.PickAttachmentSource.FromGallery -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    if (shouldUseLegacyGalleryPicker()) {
                        legacyGalleryFilter = LegacyGalleryFilter.All
                        selectedLegacyGalleryItems.clear()
                        showLegacyGalleryPicker = true
                    } else {
                        galleryMediaPicker.launch()
                    }
                }
                MessageComposerEvent.PickAttachmentSource.FromFiles -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    filesPicker.launch()
                }
                MessageComposerEvent.PickAttachmentSource.PhotoFromCamera -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    if (cameraPermissionState.permissionGranted) {
                        cameraPhotoPicker.launch()
                    } else {
                        pendingEvent = event
                        cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                    }
                }
                MessageComposerEvent.PickAttachmentSource.VideoFromCamera -> localCoroutineScope.launch {
                    showAttachmentSourcePicker = false
                    if (cameraPermissionState.permissionGranted) {
                        cameraVideoPicker.launch()
                    } else {
                        pendingEvent = event
                        cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                    }
                }
                MessageComposerEvent.PickAttachmentSource.SetkaStickers -> {
                    showAttachmentSourcePicker = false
                    setkaState = setkaState.copy(
                        isStickerPickerVisible = true,
                        errorMessage = null,
                    )
                    if (setkaState.stickerPacks.isEmpty()) {
                        refreshSetka(showLoading = true)
                    }
                }
                MessageComposerEvent.PickAttachmentSource.SetkaPlus -> {
                    showAttachmentSourcePicker = false
                    setkaState = setkaState.copy(
                        isSubscriptionDialogVisible = true,
                        errorMessage = null,
                    )
                    refreshSetka(showLoading = setkaState.plans.isEmpty())
                }
                MessageComposerEvent.PickAttachmentSource.Location -> {
                    showAttachmentSourcePicker = false
                    // Navigation to the location picker screen is done at the view layer
                }
                MessageComposerEvent.PickAttachmentSource.Poll -> {
                    showAttachmentSourcePicker = false
                    // Navigation to the create poll screen is done at the view layer
                }
                MessageComposerEvent.RefreshSetka -> refreshSetka(showLoading = setkaState.stickerPacks.isEmpty())
                MessageComposerEvent.ShowSetkaStickerPicker -> {
                    showAttachmentSourcePicker = false
                    setkaState = setkaState.copy(isStickerPickerVisible = true, errorMessage = null)
                    if (setkaState.stickerPacks.isEmpty()) {
                        refreshSetka(showLoading = true)
                    }
                }
                MessageComposerEvent.HideSetkaStickerPicker -> {
                    setkaState = setkaState.copy(isStickerPickerVisible = false)
                }
                is MessageComposerEvent.InsertInlineText -> {
                    localCoroutineScope.launch {
                        insertInlineText(
                            text = event.text,
                            markdownTextEditorState = markdownTextEditorState,
                            richTextEditorState = richTextEditorState,
                        )
                    }
                }
                MessageComposerEvent.ShowSetkaPlusDialog -> {
                    setkaState = setkaState.copy(isSubscriptionDialogVisible = true, errorMessage = null)
                    refreshSetka(showLoading = setkaState.plans.isEmpty())
                }
                MessageComposerEvent.HideSetkaPlusDialog -> {
                    setkaState = setkaState.copy(isSubscriptionDialogVisible = false)
                }
                is MessageComposerEvent.SendSetkaSticker -> {
                    localCoroutineScope.launch {
                        timelineController.invokeOnCurrentTimeline {
                            setkaService.sendSticker(this, event.sticker)
                                .onSuccess {
                                    setkaState = setkaState.copy(
                                        isStickerPickerVisible = false,
                                        errorMessage = null,
                                    )
                                }
                                .onFailure { failure ->
                                    setkaState = setkaState.copy(
                                        errorMessage = failure.message ?: "Не удалось отправить стикер",
                                    )
                                }
                        }
                    }
                }
                is MessageComposerEvent.BuySetkaPlan -> {
                    localCoroutineScope.launch {
                        if (setkaState.subscription?.isActive == true) {
                            setkaState = setkaState.copy(errorMessage = "Подписка уже активна")
                            return@launch
                        }
                        setkaState = setkaState.copy(busyPlanId = event.plan.id, errorMessage = null)
                        setkaService.createPlusPayment(event.plan)
                            .onSuccess { payment ->
                                openSetkaPayment(payment.checkoutUrl)
                                refreshSetka()
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    errorMessage = failure.message ?: "Не удалось создать платеж",
                                )
                            }
                        setkaState = setkaState.copy(busyPlanId = null)
                    }
                }
                is MessageComposerEvent.OpenSetkaPackEditor -> {
                    if (!setkaState.isPlusActive) {
                        setkaState = setkaState.copy(errorMessage = "Для управления паками нужен Setka Plus")
                        return
                    }
                    val existingPack = setkaState.stickerPacks.firstOrNull { it.id == event.packId }
                    setkaState = setkaState.copy(
                        packEditorState = SetkaPackEditorState(
                            kind = event.kind,
                            packId = event.packId,
                            initialName = existingPack?.name.orEmpty(),
                        ),
                        errorMessage = null,
                    )
                }
                MessageComposerEvent.CloseSetkaPackEditor -> {
                    setkaState = setkaState.copy(packEditorState = null)
                }
                is MessageComposerEvent.SaveSetkaPack -> {
                    localCoroutineScope.launch {
                        if (!setkaState.isPlusActive) {
                            setkaState = setkaState.copy(errorMessage = "Для управления паками нужен Setka Plus")
                            return@launch
                        }
                        val trimmedName = event.name.trim()
                        if (trimmedName.isBlank()) {
                            setkaState = setkaState.copy(errorMessage = "Нужно название пака")
                            return@launch
                        }
                        setkaState = setkaState.copy(uploadingPackId = event.packId ?: "new-pack", errorMessage = null)
                        val result = if (event.packId == null) {
                            setkaService.createStickerPack(trimmedName, event.kind)
                        } else {
                            val currentPack = setkaState.stickerPacks.firstOrNull { it.id == event.packId }
                            if (currentPack == null) {
                                Result.failure(IllegalStateException("Sticker pack not found"))
                            } else {
                                setkaService.saveStickerPack(
                                    currentPack.copy(
                                        name = trimmedName,
                                        kind = event.kind,
                                    )
                                )
                            }
                        }
                        result.fold(
                            onSuccess = { _ ->
                                setkaState = setkaState.copy(
                                    uploadingPackId = null,
                                    packEditorState = null,
                                )
                                refreshSetka()
                            },
                            onFailure = { failure ->
                                setkaState = setkaState.copy(
                                    uploadingPackId = null,
                                    errorMessage = failure.message ?: "Не удалось сохранить пак",
                                )
                            }
                        )
                    }
                }
                is MessageComposerEvent.ConfirmDeleteSetkaPack -> {
                    setkaState = setkaState.copy(
                        deleteConfirmation = SetkaDeleteConfirmation(
                            packId = event.packId,
                            packName = event.packName,
                        ),
                        packEditorState = null,
                    )
                }
                MessageComposerEvent.DismissDeleteSetkaPack -> {
                    setkaState = setkaState.copy(deleteConfirmation = null)
                }
                is MessageComposerEvent.DeleteSetkaPack -> {
                    localCoroutineScope.launch {
                        if (!setkaState.isPlusActive) {
                            setkaState = setkaState.copy(errorMessage = "Для управления паками нужен Setka Plus")
                            return@launch
                        }
                        setkaState = setkaState.copy(
                            deletingPackId = event.packId,
                            deleteConfirmation = null,
                            errorMessage = null,
                        )
                        setkaService.deleteStickerPack(event.packId)
                            .onSuccess {
                                refreshSetka()
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    errorMessage = failure.message ?: "Не удалось удалить пак",
                                )
                        }
                        setkaState = setkaState.copy(deletingPackId = null)
                    }
                }
                is MessageComposerEvent.ShareSetkaPack -> {
                    val pack = setkaState.stickerPacks.firstOrNull { it.id == event.packId }
                    if (pack == null) {
                        setkaState = setkaState.copy(errorMessage = "Пак не найден")
                        return
                    }
                    localCoroutineScope.launch {
                        setkaService.createPackShareLink(pack.id)
                            .onSuccess { url ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, pack.name)
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                externalIntentLauncher.launch(Intent.createChooser(shareIntent, null))
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    errorMessage = failure.message ?: "Не удалось создать ссылку",
                                )
                            }
                    }
                }
                is MessageComposerEvent.PreviewSetkaSharedPack -> {
                    localCoroutineScope.launch {
                        setkaState = setkaState.copy(
                            sharedPackPreview = io.element.android.features.messages.impl.messagecomposer.setka.SetkaSharedPackPreviewState(
                                token = event.token,
                                isLoading = true,
                            ),
                            errorMessage = null,
                        )
                        setkaService.resolveSharedPack(event.token)
                            .onSuccess { pack ->
                                setkaState = setkaState.copy(
                                    sharedPackPreview = io.element.android.features.messages.impl.messagecomposer.setka.SetkaSharedPackPreviewState(
                                        token = event.token,
                                        pack = pack,
                                        installedPackId = findInstalledSharedPackId(pack),
                                        isLoading = false,
                                    )
                                )
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    sharedPackPreview = null,
                                    errorMessage = failure.message ?: "РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ РїР°Рє",
                                )
                            }
                    }
                }
                MessageComposerEvent.DismissSetkaSharedPackPreview -> {
                    setkaState = setkaState.copy(sharedPackPreview = null)
                }
                MessageComposerEvent.ApplySetkaSharedPackPreview -> {
                    localCoroutineScope.launch {
                        val preview = setkaState.sharedPackPreview ?: return@launch
                        if (!setkaState.isPlusActive) {
                            setkaState = setkaState.copy(
                                errorMessage = "Р§С‚РѕР±С‹ РґРѕР±Р°РІР»СЏС‚СЊ РїР°РєРё, РЅСѓР¶РµРЅ Setka Plus",
                                isSubscriptionDialogVisible = true,
                            )
                            refreshSetka(showLoading = setkaState.plans.isEmpty())
                            return@launch
                        }
                        setkaState = setkaState.copy(
                            sharedPackPreview = preview.copy(isLoading = true),
                            errorMessage = null,
                        )
                        val result = if (preview.installedPackId != null) {
                            setkaService.deleteStickerPack(preview.installedPackId).map { preview.pack }
                        } else {
                            setkaService.importSharedPack(preview.token).map { it as SetkaStickerPack? }
                        }
                        result
                            .onSuccess {
                                if (preview.installedPackId == null) {
                                    snackbarDispatcher.post(
                                        SnackbarMessage(
                                            messageResId = io.element.android.features.messages.impl.R.string.screen_room_setka_pack_added
                                        )
                                    )
                                }
                                setkaState = setkaState.copy(sharedPackPreview = null)
                                refreshSetka(showLoading = false)
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    sharedPackPreview = preview.copy(isLoading = false),
                                    errorMessage = failure.message ?: if (preview.installedPackId == null) {
                                        "РќРµ СѓРґР°Р»РѕСЃСЊ РёРјРїРѕСЂС‚РёСЂРѕРІР°С‚СЊ РїР°Рє"
                                    } else {
                                        "РќРµ СѓРґР°Р»РѕСЃСЊ СѓРґР°Р»РёС‚СЊ РїР°Рє"
                                    },
                                )
                            }
                    }
                }
                is MessageComposerEvent.ImportSetkaSharedPack -> {
                    localCoroutineScope.launch {
                        if (!setkaState.isPlusActive) {
                            setkaState = setkaState.copy(
                                errorMessage = "Чтобы добавлять паки, нужен Setka Plus",
                                isSubscriptionDialogVisible = true,
                            )
                            refreshSetka(showLoading = setkaState.plans.isEmpty())
                            return@launch
                        }
                        setkaService.importSharedPack(event.token)
                            .onSuccess {
                                snackbarDispatcher.post(
                                    SnackbarMessage(
                                        messageResId = io.element.android.features.messages.impl.R.string.screen_room_setka_pack_added
                                    )
                                )
                                refreshSetka(showLoading = false)
                            }
                            .onFailure { failure ->
                                setkaState = setkaState.copy(
                                    errorMessage = failure.message ?: "Не удалось импортировать пак",
                                )
                            }
                    }
                }
                is MessageComposerEvent.DeleteSetkaSticker -> {
                    localCoroutineScope.launch {
                        if (!setkaState.isPlusActive) {
                            setkaState = setkaState.copy(errorMessage = "Для управления паками нужен Setka Plus")
                            return@launch
                        }
                        val pack = setkaState.stickerPacks.firstOrNull { it.id == event.packId }
                        if (pack == null) {
                            setkaState = setkaState.copy(errorMessage = "Пак не найден")
                            return@launch
                        }
                        val stickerKey = "${event.packId}:${event.stickerId}"
                        setkaState = setkaState.copy(deletingStickerKey = stickerKey, errorMessage = null)
                        setkaService.removeStickerFromPack(pack, event.stickerId)
                            .fold(
                                onSuccess = { updatedPack ->
                                    replaceSetkaPack(updatedPack)
                                    setkaState = setkaState.copy(deletingStickerKey = null)
                                },
                                onFailure = { failure ->
                                    setkaState = setkaState.copy(
                                        deletingStickerKey = null,
                                        errorMessage = failure.message ?: "Не удалось удалить стикер",
                                    )
                                }
                            )
                    }
                }
                is MessageComposerEvent.UploadSetkaMedia -> {
                    if (!setkaState.isPlusActive) {
                        setkaState = setkaState.copy(errorMessage = "Для управления паками нужен Setka Plus")
                        return
                    }
                    openSetkaUploadPicker(packId = event.packId, kind = event.kind)
                }
                MessageComposerEvent.ClearSetkaError -> {
                    setkaState = setkaState.copy(errorMessage = null)
                }
                is MessageComposerEvent.ToggleTextFormatting -> {
                    showAttachmentSourcePicker = false
                    localCoroutineScope.toggleTextFormatting(event.enabled, markdownTextEditorState, richTextEditorState)
                }
                is MessageComposerEvent.Error -> {
                    analyticsService.trackError(event.error)
                }
                is MessageComposerEvent.TypingNotice -> {
                    if (sendTypingNotifications) {
                        localCoroutineScope.launch {
                            room.typingNotice(event.isTyping)
                        }
                    }
                }
                is MessageComposerEvent.SuggestionReceived -> {
                    suggestionSearchTrigger.value = event.suggestion
                }
                is MessageComposerEvent.InsertSuggestion -> {
                    localCoroutineScope.launch {
                        if (showTextFormatting) {
                            when (val suggestion = event.resolvedSuggestion) {
                                is ResolvedSuggestion.AtRoom -> {
                                    richTextEditorState.insertAtRoomMentionAtSuggestion()
                                }
                                is ResolvedSuggestion.Member -> {
                                    val text = suggestion.roomMember.userId.value
                                    val link = permalinkBuilder.permalinkForUser(suggestion.roomMember.userId).getOrNull() ?: return@launch
                                    richTextEditorState.insertMentionAtSuggestion(text = text, link = link)
                                }
                                is ResolvedSuggestion.Alias -> {
                                    val text = suggestion.roomAlias.value
                                    val link = permalinkBuilder.permalinkForRoomAlias(suggestion.roomAlias).getOrNull() ?: return@launch
                                    richTextEditorState.insertMentionAtSuggestion(text = text, link = link)
                                }
                            }
                        } else if (markdownTextEditorState.currentSuggestion != null) {
                            markdownTextEditorState.insertSuggestion(
                                resolvedSuggestion = event.resolvedSuggestion,
                                mentionSpanProvider = mentionSpanProvider,
                            )
                            suggestionSearchTrigger.value = null
                        }
                    }
                }
                MessageComposerEvent.SaveDraft -> {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    sessionCoroutineScope.updateDraft(draft, isVolatile = false)
                }
            }
        }

        fun handleLegacyGalleryEvent(event: LegacyGalleryPickerEvent) {
            when (event) {
                LegacyGalleryPickerEvent.Dismiss -> {
                    pendingSetkaUpload = null
                    if (suspendedSetkaPackEditor != null) {
                        setkaState = setkaState.copy(packEditorState = suspendedSetkaPackEditor)
                        suspendedSetkaPackEditor = null
                    }
                    selectedLegacyGalleryItems.clear()
                    showLegacyGalleryPicker = false
                }
                LegacyGalleryPickerEvent.RequestPermissions -> legacyGalleryPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                is LegacyGalleryPickerEvent.SelectFilter -> legacyGalleryFilter = event.filter
                is LegacyGalleryPickerEvent.ToggleMediaSelection -> {
                    val existingIndex = selectedLegacyGalleryItems.indexOfFirst { it.id == event.item.id }
                    when {
                        existingIndex >= 0 -> selectedLegacyGalleryItems.removeAt(existingIndex)
                        pendingSetkaUpload != null -> {
                            selectedLegacyGalleryItems.clear()
                            selectedLegacyGalleryItems.add(event.item)
                        }
                        else -> selectedLegacyGalleryItems.add(event.item)
                    }
                }
                LegacyGalleryPickerEvent.ConfirmSelection -> {
                    if (selectedLegacyGalleryItems.isEmpty()) return
                    showLegacyGalleryPicker = false
                    if (pendingSetkaUpload != null) {
                        handleSetkaPickedMedia(selectedLegacyGalleryItems.firstOrNull()?.uri)
                    } else {
                        handlePickedMediaItems(
                            items = selectedLegacyGalleryItems.map { item ->
                                item.uri to item.mimeType
                            }
                        )
                    }
                    selectedLegacyGalleryItems.clear()
                }
            }
        }

        val resolveMentionDisplay = remember {
            { text: String, url: String ->
                val mentionSpan = mentionSpanProvider.getMentionSpanFor(text, url)
                if (mentionSpan != null) {
                    TextDisplay.Custom(mentionSpan)
                } else {
                    TextDisplay.Plain
                }
            }
        }

        val resolveAtRoomMentionDisplay = remember {
            {
                val mentionSpan = mentionSpanProvider.createEveryoneMentionSpan()
                TextDisplay.Custom(mentionSpan)
            }
        }

        return MessageComposerState(
            textEditorState = textEditorState,
            isFullScreen = isFullScreen.value,
            mode = messageComposerContext.composerMode,
            showAttachmentSourcePicker = showAttachmentSourcePicker,
            showTextFormatting = showTextFormatting,
            canShareLocation = canShareLocation.value,
            suggestions = suggestions.toImmutableList(),
            legacyGalleryPickerState = if (showLegacyGalleryPicker && shouldUseLegacyGalleryPicker()) {
                LegacyGalleryPickerState(
                    permissionState = legacyGalleryPermissionState,
                    isLoading = isLegacyGalleryLoading,
                    selectedFilter = legacyGalleryFilter,
                    mediaItems = legacyGalleryItems
                        .filter { it.matches(legacyGalleryFilter) }
                        .toImmutableList(),
                    selectedMediaIds = selectedLegacyGalleryItems
                        .map { it.id }
                        .toImmutableSet(),
                    maxSelectionCount = if (pendingSetkaUpload != null) 1 else Int.MAX_VALUE,
                    eventSink = ::handleLegacyGalleryEvent,
                )
            } else {
                null
            },
            setkaState = setkaState,
            resolveMentionDisplay = resolveMentionDisplay,
            resolveAtRoomMentionDisplay = resolveAtRoomMentionDisplay,
            eventSink = ::handleEvent,
        )
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun ResolveSuggestionsEffect(
        suggestions: SnapshotStateList<ResolvedSuggestion>,
    ) {
        LaunchedEffect(Unit) {
            val currentUserId = room.sessionId

            suspend fun canSendRoomMention(): Boolean {
                val userCanSendAtRoom = room.roomPermissions().use(false) { perms ->
                    perms.canOwnUserTriggerRoomNotification()
                }
                return !room.isDm() && userCanSendAtRoom
            }

            // This will trigger a search immediately when `@` is typed
            val mentionStartTrigger = suggestionSearchTrigger.filter { it?.text.isNullOrEmpty() }
            // This will start a search when the user changes the text after the `@` with a debounce to prevent too much wasted work
            val mentionCompletionTrigger = suggestionSearchTrigger.debounce(0.3.seconds).filter { !it?.text.isNullOrEmpty() }

            val mentionTriggerFlow = merge(mentionStartTrigger, mentionCompletionTrigger)

            val roomAliasSuggestionsFlow = roomAliasSuggestionsDataSource
                .getAllRoomAliasSuggestions()
                .stateIn(this, SharingStarted.Lazily, emptyList())

            combine(mentionTriggerFlow, room.membersStateFlow, roomAliasSuggestionsFlow) { suggestion, roomMembersState, roomAliasSuggestions ->
                val result = suggestionsProcessor.process(
                    suggestion = suggestion,
                    roomMembersState = roomMembersState,
                    roomAliasSuggestions = roomAliasSuggestions,
                    currentUserId = currentUserId,
                    canSendRoomMention = ::canSendRoomMention,
                )
                suggestions.clear()
                suggestions.addAll(result)
            }
                .collect()
        }
    }

    private fun CoroutineScope.sendMessage(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        stickerPacks: List<SetkaStickerPack>,
        isSetkaPlusActive: Boolean,
    ) = launch {
        val message = currentComposerMessage(markdownTextEditorState, richTextEditorState, withMentions = true)
            .withCustomEmoji(setkaService, stickerPacks)

        val fallbackBody = if (message.markdown.isBlank()) {
            setkaService.customEmojiFallbackBodyFromHtml(message.html)
        } else {
            null
        }
        val normalizedMessage = if (!fallbackBody.isNullOrBlank()) {
            message.copy(markdown = fallbackBody)
        } else {
            message
        }

        if (!isSetkaPlusActive && (
                setkaService.containsCustomEmoji(normalizedMessage.markdown, stickerPacks) ||
                    normalizedMessage.html?.contains("data-mx-emoticon") == true
                )
        ) {
            snackbarDispatcher.post(SnackbarMessage(messageResId = io.element.android.features.messages.impl.R.string.screen_room_setka_custom_emoji_requires_plus))
            return@launch
        }
        val capturedMode = messageComposerContext.composerMode
        // Reset composer right away
        resetComposer(markdownTextEditorState, richTextEditorState, fromEdit = capturedMode is MessageComposerMode.Edit)
        when (capturedMode) {
            is MessageComposerMode.Attachment,
            is MessageComposerMode.Normal -> timelineController.invokeOnCurrentTimeline {
                sendMessage(
                    body = normalizedMessage.markdown,
                    htmlBody = normalizedMessage.html,
                    intentionalMentions = normalizedMessage.intentionalMentions
                )
            }
            is MessageComposerMode.Edit -> {
                timelineController.invokeOnCurrentTimeline {
                    // First try to edit the message in the current timeline
                    editMessage(capturedMode.eventOrTransactionId, normalizedMessage.markdown, normalizedMessage.html, normalizedMessage.intentionalMentions)
                        .onFailure { cause ->
                            val eventId = capturedMode.eventOrTransactionId.eventId
                            if (cause is TimelineException.EventNotFound && eventId != null) {
                                // if the event is not found in the timeline, try to edit the message directly
                                room.editMessage(eventId, normalizedMessage.markdown, normalizedMessage.html, normalizedMessage.intentionalMentions)
                            }
                        }
                }
            }
            is MessageComposerMode.EditCaption -> {
                timelineController.invokeOnCurrentTimeline {
                    editCaption(
                        capturedMode.eventOrTransactionId,
                        caption = normalizedMessage.markdown,
                        formattedCaption = normalizedMessage.html
                    )
                }
            }
            is MessageComposerMode.Reply -> {
                timelineController.invokeOnCurrentTimeline {
                    with(capturedMode) {
                        replyMessage(
                            body = normalizedMessage.markdown,
                            htmlBody = normalizedMessage.html,
                            intentionalMentions = normalizedMessage.intentionalMentions,
                            repliedToEventId = eventId,
                        )
                    }
                }
            }
        }

        val roomInfo = room.info()
        val roomMembers = room.membersStateFlow.value

        notificationConversationService.onSendMessage(
            sessionId = room.sessionId,
            roomId = roomInfo.id,
            roomName = roomInfo.name ?: roomInfo.id.value,
            roomIsDirect = roomInfo.isDm,
            roomAvatarUrl = roomInfo.avatarUrl ?: roomMembers.getDirectRoomMember(roomInfo = roomInfo, sessionId = room.sessionId)?.avatarUrl,
        )

        analyticsService.capture(
            Composer(
                inThread = capturedMode.inThread,
                isEditing = capturedMode.isEditing,
                isReply = capturedMode.isReply,
                // Set proper type when we'll be sending other types of messages.
                messageType = Composer.MessageType.Text,
            )
        )
    }

    private fun CoroutineScope.sendAttachment(
        attachment: Attachment,
        inReplyToEventId: EventId?,
    ) = when (attachment) {
        is Attachment.Media -> {
            launch {
                sendMedia(
                    uri = attachment.localMedia.uri,
                    mimeType = attachment.localMedia.info.mimeType,
                    inReplyToEventId = inReplyToEventId,
                )
            }
        }
    }

    private fun handlePickedMedia(
        uri: Uri?,
        mimeType: String? = null,
    ) {
        handlePickedMediaItems(
            items = uri?.let { listOf(it to mimeType) }.orEmpty(),
        )
    }

    private fun handlePickedMediaItems(
        items: List<Pair<Uri, String?>>,
    ) {
        if (items.isEmpty()) return
        val mediaAttachments = items.map { (uri, mimeType) ->
            Attachment.Media(
                localMediaFactory.createFromUri(
                    uri = uri,
                    mimeType = mimeType,
                    name = null,
                    formattedFileSize = null,
                )
            )
        }.toImmutableList()
        val inReplyToEventId = (messageComposerContext.composerMode as? MessageComposerMode.Reply)?.eventId
        navigator.navigateToPreviewAttachments(mediaAttachments, inReplyToEventId)

        // Reset composer since the attachment will be sent in a separate flow
        messageComposerContext.composerMode = MessageComposerMode.Normal
    }

    private suspend fun sendMedia(
        uri: Uri,
        mimeType: String,
        inReplyToEventId: EventId?,
    ) = runCatchingExceptions {
        mediaSender.sendMedia(
            uri = uri,
            mimeType = mimeType,
            mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            inReplyToEventId = inReplyToEventId,
        ).getOrThrow()
    }
        .onFailure { cause ->
            Timber.e(cause, "Failed to send attachment")
            if (cause is CancellationException) {
                throw cause
            } else {
                val snackbarMessage = SnackbarMessage(sendAttachmentError(cause))
                snackbarDispatcher.post(snackbarMessage)
            }
        }

    private fun CoroutineScope.updateDraft(
        draft: ComposerDraft?,
        isVolatile: Boolean,
    ) = launch {
        draftService.updateDraft(
            roomId = room.roomId,
            draft = draft,
            isVolatile = isVolatile,
            // TODO support threads in composer
            threadRoot = null,
        )
    }

    private suspend fun applyDraft(
        draft: ComposerDraft,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ) {
        val htmlText = draft.htmlText
        val markdownText = draft.plainText
        if (htmlText != null) {
            showTextFormatting = true
            setText(htmlText, markdownTextEditorState, richTextEditorState, requestFocus = true)
        } else {
            showTextFormatting = false
            setText(markdownText, markdownTextEditorState, richTextEditorState, requestFocus = true)
        }
        when (val draftType = draft.draftType) {
            ComposerDraftType.NewMessage -> messageComposerContext.composerMode = MessageComposerMode.Normal
            is ComposerDraftType.Edit -> messageComposerContext.composerMode = MessageComposerMode.Edit(
                eventOrTransactionId = draftType.eventId.toEventOrTransactionId(),
                content = htmlText ?: markdownText
            )
            is ComposerDraftType.Reply -> {
                messageComposerContext.composerMode = MessageComposerMode.Reply(
                    replyToDetails = InReplyToDetails.Loading(draftType.eventId),
                    // I guess it's fine to always render the image when restoring a draft
                    hideImage = false
                )
                timelineController.invokeOnCurrentTimeline {
                    val replyToDetails = loadReplyDetails(draftType.eventId).map(permalinkParser)
                    messageComposerContext.composerMode = MessageComposerMode.Reply(
                        replyToDetails = replyToDetails,
                        // I guess it's fine to always render the image when restoring a draft
                        hideImage = false
                    )
                }
            }
        }
    }

    private fun createDraftFromState(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ): ComposerDraft? {
        val message = currentComposerMessage(markdownTextEditorState, richTextEditorState, withMentions = false)
        val draftType = when (val mode = messageComposerContext.composerMode) {
            is MessageComposerMode.Attachment,
            is MessageComposerMode.Normal -> ComposerDraftType.NewMessage
            is MessageComposerMode.Edit -> {
                mode.eventOrTransactionId.eventId?.let { eventId -> ComposerDraftType.Edit(eventId) }
            }
            is MessageComposerMode.Reply -> ComposerDraftType.Reply(mode.eventId)
            is MessageComposerMode.EditCaption -> {
                // TODO Need a new type to save caption in the SDK
                null
            }
        }
        return if (draftType == null || message.markdown.isBlank()) {
            null
        } else {
            ComposerDraft(
                draftType = draftType,
                htmlText = message.html,
                plainText = message.markdown,
            )
        }
    }

    private fun currentComposerMessage(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        withMentions: Boolean,
    ): Message {
        return if (showTextFormatting) {
            val html = richTextEditorState.messageHtml
            val markdown = richTextEditorState.messageMarkdown
            val mentions = richTextEditorState.mentionsState
                .takeIf { withMentions }
                ?.let { state ->
                    buildList {
                        if (state.hasAtRoomMention) {
                            add(IntentionalMention.Room)
                        }
                        for (userId in state.userIds) {
                            add(IntentionalMention.User(UserId(userId)))
                        }
                    }
                }
                .orEmpty()
            Message(html = html, markdown = markdown, intentionalMentions = mentions)
        } else {
            val markdown = markdownTextEditorState.getMessageMarkdown(permalinkBuilder)
            val mentions = if (withMentions) {
                markdownTextEditorState.getMentions()
            } else {
                emptyList()
            }
            Message(html = null, markdown = markdown, intentionalMentions = mentions)
        }
    }

    private fun CoroutineScope.toggleTextFormatting(
        enabled: Boolean,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState
    ) = launch {
        showTextFormatting = enabled
        if (showTextFormatting) {
            val markdown = markdownTextEditorState.getMessageMarkdown(permalinkBuilder)
            richTextEditorState.setMarkdown(markdown)
            richTextEditorState.requestFocus()
            analyticsService.captureInteraction(Interaction.Name.MobileRoomComposerFormattingEnabled)
        } else {
            val markdown = richTextEditorState.messageMarkdown
            val markdownWithMentions = pillificationHelper.pillify(markdown, false)
            markdownTextEditorState.text.update(markdownWithMentions, true)
            // Give some time for the focus of the previous editor to be cleared
            delay(100)
            markdownTextEditorState.requestFocusAction()
        }
    }

    private fun CoroutineScope.setMode(
        newComposerMode: MessageComposerMode,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ) = launch {
        val currentComposerMode = messageComposerContext.composerMode
        when (newComposerMode) {
            is MessageComposerMode.Edit -> {
                if (currentComposerMode.isEditing.not()) {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    updateDraft(draft, isVolatile = true).join()
                }
                setText(newComposerMode.content, markdownTextEditorState, richTextEditorState)
            }
            is MessageComposerMode.EditCaption -> {
                if (currentComposerMode.isEditing.not()) {
                    val draft = createDraftFromState(markdownTextEditorState, richTextEditorState)
                    updateDraft(draft, isVolatile = true).join()
                }
                setText(newComposerMode.content, markdownTextEditorState, richTextEditorState)
            }
            else -> {
                // When coming from edit, just clear the composer as it'd be weird to reset a volatile draft in this scenario.
                if (currentComposerMode.isEditing) {
                    setText("", markdownTextEditorState, richTextEditorState)
                }
            }
        }
        messageComposerContext.composerMode = newComposerMode
    }

    private suspend fun resetComposer(
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        fromEdit: Boolean,
    ) {
        // Use the volatile draft only when coming from edit mode otherwise.
        val draft = draftService.loadDraft(
            roomId = room.roomId,
            // TODO support threads in composer
            threadRoot = null,
            isVolatile = true
        ).takeIf { fromEdit }
        if (draft != null) {
            applyDraft(draft, markdownTextEditorState, richTextEditorState)
        } else {
            setText("", markdownTextEditorState, richTextEditorState)
            messageComposerContext.composerMode = MessageComposerMode.Normal
        }
    }

    private suspend fun setText(
        content: String,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
        requestFocus: Boolean = false,
    ) {
        if (showTextFormatting) {
            richTextEditorState.setHtml(content)
            if (requestFocus) {
                richTextEditorState.requestFocus()
            }
        } else {
            if (content.isEmpty()) {
                markdownTextEditorState.selection = IntRange.EMPTY
            }
            val pillifiedContent = pillificationHelper.pillify(content, false)
            markdownTextEditorState.text.update(pillifiedContent, true)
            if (requestFocus) {
                markdownTextEditorState.requestFocusAction()
            }
        }
    }

    private suspend fun insertInlineText(
        text: String,
        markdownTextEditorState: MarkdownTextEditorState,
        richTextEditorState: RichTextEditorState,
    ) {
        if (text.isEmpty()) return
        if (showTextFormatting) {
            richTextEditorState.setMarkdown(richTextEditorState.messageMarkdown + text)
            richTextEditorState.requestFocus()
        } else {
            val currentText = SpannableStringBuilder(markdownTextEditorState.text.value())
            val selection = markdownTextEditorState.selection
            val start = selection.first.coerceAtLeast(0).coerceAtMost(currentText.length)
            val end = selection.last.coerceAtLeast(start).coerceAtMost(currentText.length)
            currentText.replace(start, end, text)
            markdownTextEditorState.text.update(currentText, true)
            val cursor = start + text.length
            markdownTextEditorState.selection = cursor..cursor
            markdownTextEditorState.requestFocusAction()
        }
    }

    private fun shouldUseLegacyGalleryPicker(): Boolean {
        return buildVersionSdkIntProvider.get() < Build.VERSION_CODES.Q
    }
}

private fun Message.withCustomEmoji(
    setkaService: SetkaService,
    packs: List<SetkaStickerPack>,
): Message {
    val (body, formattedHtml) = setkaService.applyCustomEmojiFormatting(
        body = markdown,
        htmlBody = html,
        packs = packs,
    )
    return copy(
        markdown = body,
        html = formattedHtml,
    )
}
