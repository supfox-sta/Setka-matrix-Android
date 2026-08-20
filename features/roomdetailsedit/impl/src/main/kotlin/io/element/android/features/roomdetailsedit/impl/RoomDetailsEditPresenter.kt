/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetailsedit.impl

import android.Manifest
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import io.element.android.features.roomdetailsedit.api.RoomDetailsEditPermissions
import io.element.android.features.roomdetailsedit.api.roomDetailsEditPermissions
import io.element.android.features.securityandprivacy.api.SecurityAndPrivacyPermissions
import io.element.android.features.securityandprivacy.api.securityAndPrivacyPermissions
import io.element.android.libraries.androidutils.file.TemporaryUriDeleter
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.api.setka.SetkaFeedCategory
import io.element.android.libraries.matrix.api.setka.loadSetkaFeedCategories
import io.element.android.libraries.matrix.api.setka.publishSetkaChannelWhenReady
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.ui.media.AvatarAction
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class RoomDetailsEditPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val mediaPickerProvider: PickerProvider,
    private val mediaPreProcessor: MediaPreProcessor,
    private val temporaryUriDeleter: TemporaryUriDeleter,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
) : Presenter<RoomDetailsEditState> {
    private val cameraPermissionPresenter = permissionsPresenterFactory.create(Manifest.permission.CAMERA)
    private var pendingPermissionRequest = false

    @Composable
    override fun present(): RoomDetailsEditState {
        val cameraPermissionState = cameraPermissionPresenter.present()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val roomAvatarUri = roomInfo.avatarUrl
        var roomAvatarUriEdited by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(roomAvatarUri) {
            // Every time the roomAvatar change (from sync), we can set the new avatar.
            temporaryUriDeleter.delete(roomAvatarUriEdited?.toUri())
            roomAvatarUriEdited = roomAvatarUri
        }

        val roomRawNameTrimmed = roomInfo.rawName.orEmpty().trim()
        var roomRawNameEdited by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(roomRawNameTrimmed) {
            // Every time the rawName change (from sync), we can set the new name.
            roomRawNameEdited = roomRawNameTrimmed
        }
        val isSetkaChannelRoom = SetkaChannelConfig.isChannelTopic(roomInfo.topic)
        val roomTopicForDisplay = SetkaChannelConfig.displayTopic(roomInfo.topic).orEmpty()
        val channelTagForDisplay = SetkaChannelConfig.parseChannelTag(roomInfo.topic).orEmpty()
        var roomTopicEdited by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(roomTopicForDisplay) {
            roomTopicEdited = roomTopicForDisplay
        }
        var channelTagEdited by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(channelTagForDisplay) {
            channelTagEdited = channelTagForDisplay
        }
        var channelCategoryIdEdited by rememberSaveable { mutableStateOf<String?>(null) }
        var feedCategories by remember { mutableStateOf<ImmutableList<SetkaFeedCategory>>(persistentListOf()) }
        val isPublicChannel = roomInfo.joinRule == JoinRule.Public
        LaunchedEffect(isSetkaChannelRoom) {
            if (!isSetkaChannelRoom) return@LaunchedEffect
            feedCategories = matrixClient.loadSetkaFeedCategories().toImmutableList()
        }

        val saveButtonEnabled by remember(
            roomRawNameTrimmed,
            roomTopicForDisplay,
            channelTagForDisplay,
            roomAvatarUri,
            channelCategoryIdEdited,
            isPublicChannel,
        ) {
            derivedStateOf {
                roomRawNameTrimmed != roomRawNameEdited.trim() ||
                    roomTopicForDisplay != roomTopicEdited.trim() ||
                    channelTagForDisplay != channelTagEdited.trim() ||
                    roomAvatarUri != roomAvatarUriEdited ||
                    (isPublicChannel && !channelCategoryIdEdited.isNullOrBlank())
            }
        }

        val permissions by room.permissionsAsState(RoomDetailsEditPermissions.DEFAULT) { perms ->
            perms.roomDetailsEditPermissions()
        }
        val securityAndPrivacyPermissions by room.permissionsAsState(SecurityAndPrivacyPermissions.DEFAULT) { perms ->
            perms.securityAndPrivacyPermissions()
        }
        val canShowPrivacySettings = isSetkaChannelRoom &&
            securityAndPrivacyPermissions.hasAny(isSpace = roomInfo.isSpace, joinRule = roomInfo.joinRule)

        val cameraPhotoPicker = mediaPickerProvider.registerCameraPhotoPicker(
            onResult = { uri ->
                if (uri != null) {
                    temporaryUriDeleter.delete(roomAvatarUriEdited?.toUri())
                    roomAvatarUriEdited = uri.toString()
                }
            }
        )
        val galleryImagePicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    temporaryUriDeleter.delete(roomAvatarUriEdited?.toUri())
                    roomAvatarUriEdited = uri.toString()
                }
            }
        )

        LaunchedEffect(cameraPermissionState.permissionGranted) {
            if (cameraPermissionState.permissionGranted && pendingPermissionRequest) {
                pendingPermissionRequest = false
                cameraPhotoPicker.launch()
            }
        }

        val avatarActions by remember(roomAvatarUriEdited) {
            derivedStateOf {
                listOfNotNull(
                    AvatarAction.TakePhoto,
                    AvatarAction.ChoosePhoto,
                    AvatarAction.Remove.takeIf { roomAvatarUriEdited != null },
                ).toImmutableList()
            }
        }

        val saveAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        val localCoroutineScope = rememberCoroutineScope()
        fun handleEvent(event: RoomDetailsEditEvent) {
            when (event) {
                is RoomDetailsEditEvent.Save -> localCoroutineScope.saveChanges(
                    currentNameTrimmed = roomRawNameTrimmed,
                    newNameTrimmed = roomRawNameEdited.trim(),
                    currentTopicTrimmed = roomTopicForDisplay,
                    newTopicTrimmed = roomTopicEdited.trim(),
                    currentChannelTag = channelTagForDisplay,
                    newChannelTag = channelTagEdited.trim(),
                    isSetkaChannelRoom = isSetkaChannelRoom,
                    isPublicChannel = isPublicChannel,
                    channelCategoryId = channelCategoryIdEdited,
                    currentAvatar = roomAvatarUri?.toUri(),
                    newAvatarUri = roomAvatarUriEdited?.toUri(),
                    action = saveAction,
                )
                is RoomDetailsEditEvent.HandleAvatarAction -> {
                    when (event.action) {
                        AvatarAction.ChoosePhoto -> galleryImagePicker.launch()
                        AvatarAction.TakePhoto -> if (cameraPermissionState.permissionGranted) {
                            cameraPhotoPicker.launch()
                        } else {
                            pendingPermissionRequest = true
                            cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                        }
                        AvatarAction.Remove -> {
                            temporaryUriDeleter.delete(roomAvatarUriEdited?.toUri())
                            roomAvatarUriEdited = null
                        }
                    }
                }

                is RoomDetailsEditEvent.UpdateRoomName -> roomRawNameEdited = event.name
                is RoomDetailsEditEvent.UpdateRoomTopic -> roomTopicEdited = event.topic
                is RoomDetailsEditEvent.UpdateChannelTag -> channelTagEdited = event.tag
                is RoomDetailsEditEvent.ChannelCategoryChanged -> {
                    channelCategoryIdEdited = event.categoryId.takeIf { it.isNotBlank() }
                }
                RoomDetailsEditEvent.CloseDialog -> saveAction.value = AsyncAction.Uninitialized
                RoomDetailsEditEvent.OnBackPress -> if (saveButtonEnabled.not() || saveAction.value == AsyncAction.ConfirmingCancellation) {
                    // No changes to save or already confirming exit without saving
                    saveAction.value = AsyncAction.Success(Unit)
                } else {
                    saveAction.value = AsyncAction.ConfirmingCancellation
                }
            }
        }

        val channelTagError = isSetkaChannelRoom &&
            channelTagEdited.isNotBlank() &&
            SetkaChannelConfig.normalizeChannelTag(channelTagEdited) == null

        val channelCategoryMissing = isSetkaChannelRoom &&
            isPublicChannel &&
            channelCategoryIdEdited.isNullOrBlank()

        return RoomDetailsEditState(
            roomId = room.roomId,
            roomRawName = roomRawNameEdited,
            canChangeName = permissions.canEditName,
            roomTopic = roomTopicEdited,
            canChangeTopic = permissions.canEditTopic,
            isSetkaChannel = isSetkaChannelRoom,
            isPublicChannel = isPublicChannel,
            channelTag = channelTagEdited,
            channelTagError = channelTagError,
            feedCategories = feedCategories,
            channelCategoryId = channelCategoryIdEdited,
            channelCategoryMissing = channelCategoryMissing,
            canShowPrivacySettings = canShowPrivacySettings,
            roomAvatarUrl = roomAvatarUriEdited,
            canChangeAvatar = permissions.canEditAvatar,
            avatarActions = avatarActions,
            saveButtonEnabled = saveButtonEnabled && !channelTagError,
            saveAction = saveAction.value,
            cameraPermissionState = cameraPermissionState,
            isSpace = roomInfo.isSpace,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.saveChanges(
        currentNameTrimmed: String,
        newNameTrimmed: String,
        currentTopicTrimmed: String,
        newTopicTrimmed: String,
        currentChannelTag: String,
        newChannelTag: String,
        isSetkaChannelRoom: Boolean,
        isPublicChannel: Boolean,
        channelCategoryId: String?,
        currentAvatar: Uri?,
        newAvatarUri: Uri?,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        val results = mutableListOf<Result<Unit>>()
        suspend {
            if (isSetkaChannelRoom && (newTopicTrimmed != currentTopicTrimmed || newChannelTag != currentChannelTag)) {
                val normalizedTag = SetkaChannelConfig.normalizeChannelTag(newChannelTag)
                if (newChannelTag.isNotBlank() && normalizedTag == null) {
                    error("Invalid channel tag")
                }
                val topicToSave = SetkaChannelConfig.formatChannelTopic(
                    userDescription = newTopicTrimmed,
                    tag = normalizedTag ?: currentChannelTag.takeIf { it.isNotBlank() },
                )
                results.add(room.setTopic(topicToSave).onFailure {
                    Timber.e(it, "Failed to set room topic")
                })
            } else if (newTopicTrimmed != currentTopicTrimmed) {
                results.add(room.setTopic(newTopicTrimmed).onFailure {
                    Timber.e(it, "Failed to set room topic")
                })
            }
            if (newNameTrimmed.isNotEmpty() && newNameTrimmed != currentNameTrimmed) {
                results.add(room.setName(newNameTrimmed).onFailure {
                    Timber.e(it, "Failed to set room name")
                })
            }
            if (newAvatarUri != currentAvatar) {
                results.add(updateAvatar(newAvatarUri).onFailure {
                    Timber.e(it, "Failed to update avatar")
                })
            }
            if (results.all { it.isSuccess }) {
                if (isSetkaChannelRoom && isPublicChannel && !channelCategoryId.isNullOrBlank()) {
                    matrixClient.publishSetkaChannelWhenReady(
                        roomId = room.roomId,
                        categoryId = channelCategoryId,
                    ).getOrThrow()
                }
                Unit
            } else {
                results.first { it.isFailure }.getOrThrow()
            }
        }.runCatchingUpdatingState(action)
    }

    private suspend fun updateAvatar(avatarUri: Uri?): Result<Unit> {
        return runCatchingExceptions {
            if (avatarUri != null) {
                val preprocessed = mediaPreProcessor.process(
                    uri = avatarUri,
                    mimeType = MimeTypes.Jpeg,
                    deleteOriginal = false,
                    mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
                ).getOrThrow()
                room.updateAvatar(MimeTypes.Jpeg, preprocessed.file.readBytes()).getOrThrow()
            } else {
                room.removeAvatar().getOrThrow()
            }
        }
    }
}
