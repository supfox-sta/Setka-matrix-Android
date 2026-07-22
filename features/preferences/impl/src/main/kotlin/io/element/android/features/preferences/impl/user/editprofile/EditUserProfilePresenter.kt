/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.user.editprofile

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.threepid.ThreePidMedium
import io.element.android.features.preferences.impl.threepid.ThreePidService
import io.element.android.libraries.androidutils.file.TemporaryUriDeleter
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.media.AvatarAction
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

@AssistedInject
class EditUserProfilePresenter(
    @Assisted private val matrixUser: MatrixUser,
    @Assisted private val navigator: EditUserProfileNavigator,
    private val matrixClient: MatrixClient,
    private val threePidService: ThreePidService,
    private val mediaPickerProvider: PickerProvider,
    private val mediaPreProcessor: MediaPreProcessor,
    private val temporaryUriDeleter: TemporaryUriDeleter,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
) : Presenter<EditUserProfileState> {
    private val cameraPermissionPresenter: PermissionsPresenter =
        permissionsPresenterFactory.create(android.Manifest.permission.CAMERA)
    private var pendingPermissionRequest = false

    @AssistedFactory
    interface Factory {
        fun create(
            matrixUser: MatrixUser,
            navigator: EditUserProfileNavigator,
        ): EditUserProfilePresenter
    }

    @Composable
    override fun present(): EditUserProfileState {
        val cameraPermissionState = cameraPermissionPresenter.present()
        val lifecycleOwner = LocalLifecycleOwner.current
        var userAvatarUri by rememberSaveable { mutableStateOf(matrixUser.avatarUrl) }
        var userDisplayName by rememberSaveable { mutableStateOf(matrixUser.displayName) }
        var bio by rememberSaveable { mutableStateOf("") }
        var phoneNumber by remember { mutableStateOf<String?>(null) }
        var emailAddress by remember { mutableStateOf<String?>(null) }
        var hasVerifiedEmail by remember { mutableStateOf(false) }
        var loadedSetkaBio by remember { mutableStateOf<String?>(null) }
        var profileColorHex by rememberSaveable { mutableStateOf("") }
        var profileBackgroundUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var loadedSetkaProfile by remember { mutableStateOf<JSONObject?>(null) }

        val setkaProfile by produceState<JSONObject?>(initialValue = null, matrixUser.userId) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
                ).getOrThrow().decodeToString().let(::JSONObject)
            }.getOrNull()
        }

        val isSetkaPlusActive by produceState(initialValue = false, matrixUser.userId) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                val raw = matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_plus/subscription",
                ).getOrThrow().decodeToString()
                val json = JSONObject(raw)
                json.optBoolean("is_active", false) || json.optBoolean("active", false)
            }.getOrDefault(false)
        }

        LaunchedEffect(setkaProfile) {
            val incoming = setkaProfile ?: return@LaunchedEffect
            if (loadedSetkaProfile == null) {
                loadedSetkaProfile = incoming
                loadedSetkaBio = incoming.optString("bio", "")
                bio = incoming.optString("bio", "")
                profileColorHex = incoming.optString("color", "")
                profileBackgroundUrl = incoming.optString("background_mxc", "").takeIf { it.isNotBlank() }
            }
        }

        fun refreshContactInfo() {
            sessionCoroutineScope.launch {
                threePidService.getThreePids()
                    .onSuccess { threePids ->
                        phoneNumber = threePids
                            .firstOrNull { it.medium == ThreePidMedium.Msisdn }
                            ?.address
                    }
                threePidService.resolveUserEmail()
                    .onSuccess { status ->
                        emailAddress = status.address
                        hasVerifiedEmail = status.isVerified
                    }
            }
        }

        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                refreshContactInfo()
            }
        }

        val cameraPhotoPicker = mediaPickerProvider.registerCameraPhotoPicker(
            onResult = { uri ->
                if (uri != null) {
                    temporaryUriDeleter.delete(userAvatarUri?.toUri())
                    userAvatarUri = uri.toString()
                }
            }
        )
        val galleryImagePicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    temporaryUriDeleter.delete(userAvatarUri?.toUri())
                    userAvatarUri = uri.toString()
                }
            }
        )
        val backgroundImagePicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    profileBackgroundUrl = uri.toString()
                }
            }
        )

        val avatarActions by remember(userAvatarUri) {
            derivedStateOf {
                listOfNotNull(
                    AvatarAction.TakePhoto,
                    AvatarAction.ChoosePhoto,
                    AvatarAction.Remove.takeIf { userAvatarUri != null },
                ).toImmutableList()
            }
        }

        LaunchedEffect(cameraPermissionState.permissionGranted) {
            if (cameraPermissionState.permissionGranted && pendingPermissionRequest) {
                pendingPermissionRequest = false
                cameraPhotoPicker.launch()
            }
        }

        val saveAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        val localCoroutineScope = rememberCoroutineScope()

        val canSave = remember(
            userDisplayName,
            userAvatarUri,
            bio,
            loadedSetkaBio,
            profileBackgroundUrl,
            loadedSetkaProfile,
        ) {
            val hasProfileChanged = hasDisplayNameChanged(userDisplayName, matrixUser) ||
                hasAvatarUrlChanged(userAvatarUri, matrixUser) ||
                (loadedSetkaBio?.trim() ?: "") != bio.trim() ||
                hasBackgroundChanged(loadedSetkaProfile, profileBackgroundUrl)
            !userDisplayName.isNullOrBlank() && hasProfileChanged
        }

        fun handleEvent(event: EditUserProfileEvent) {
            when (event) {
                is EditUserProfileEvent.Save -> localCoroutineScope.saveChanges(
                    name = userDisplayName,
                    avatarUri = userAvatarUri?.toUri(),
                    bio = bio,
                    loadedSetkaBio = loadedSetkaBio,
                    loadedSetkaProfile = loadedSetkaProfile,
                    profileBackgroundUrl = profileBackgroundUrl,
                    currentUser = matrixUser,
                    action = saveAction,
                )
                is EditUserProfileEvent.HandleAvatarAction -> {
                    when (event.action) {
                        AvatarAction.ChoosePhoto -> galleryImagePicker.launch()
                        AvatarAction.TakePhoto -> if (cameraPermissionState.permissionGranted) {
                            cameraPhotoPicker.launch()
                        } else {
                            pendingPermissionRequest = true
                            cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                        }
                        AvatarAction.Remove -> {
                            temporaryUriDeleter.delete(userAvatarUri?.toUri())
                            userAvatarUri = null
                        }
                    }
                }
                is EditUserProfileEvent.UpdateDisplayName -> userDisplayName = event.name
                is EditUserProfileEvent.UpdateBio -> bio = event.bio
                EditUserProfileEvent.OpenChangePhone -> {
                    if (hasVerifiedEmail) {
                        navigator.openChangePhone()
                    } else {
                        navigator.openChangeEmail()
                    }
                }
                EditUserProfileEvent.OpenChangeEmail -> navigator.openChangeEmail()
                EditUserProfileEvent.OpenProfileCustomization -> navigator.openProfileCustomization()
                EditUserProfileEvent.PickProfileBackground -> {
                    if (isSetkaPlusActive) {
                        backgroundImagePicker.launch()
                    }
                }
                EditUserProfileEvent.RemoveProfileBackground -> profileBackgroundUrl = null
                EditUserProfileEvent.SignOut -> navigator.startSignOut()
                EditUserProfileEvent.Exit -> {
                    when (saveAction.value) {
                        is AsyncAction.Confirming -> {
                            saveAction.value = AsyncAction.Uninitialized
                            navigator.close()
                        }
                        AsyncAction.Loading -> navigator.close()
                        is AsyncAction.Failure,
                        is AsyncAction.Success -> Unit
                        AsyncAction.Uninitialized -> {
                            if (canSave) {
                                saveAction.value = AsyncAction.ConfirmingCancellation
                            } else {
                                navigator.close()
                            }
                        }
                    }
                }
                EditUserProfileEvent.DiscardChanges -> {
                    saveAction.value = AsyncAction.Uninitialized
                    navigator.close()
                }
                EditUserProfileEvent.CloseDialog -> saveAction.value = AsyncAction.Uninitialized
            }
        }

        return EditUserProfileState(
            userId = matrixUser.userId,
            displayName = userDisplayName.orEmpty(),
            userAvatarUrl = userAvatarUri,
            bio = bio,
            profileColorHex = profileColorHex,
            profileBackgroundUrl = profileBackgroundUrl,
            isSetkaPlusActive = isSetkaPlusActive,
            emailAddress = emailAddress,
            hasVerifiedEmail = hasVerifiedEmail,
            phoneNumber = phoneNumber,
            avatarActions = avatarActions,
            saveButtonEnabled = canSave && saveAction.value !is AsyncAction.Loading,
            saveAction = saveAction.value,
            cameraPermissionState = cameraPermissionState,
            eventSink = ::handleEvent,
        )
    }

    private fun hasDisplayNameChanged(name: String?, currentUser: MatrixUser) =
        name?.trim() != currentUser.displayName?.trim()

    private fun hasAvatarUrlChanged(avatarUri: String?, currentUser: MatrixUser) =
        avatarUri?.trim() != currentUser.avatarUrl?.trim()

    private fun CoroutineScope.saveChanges(
        name: String?,
        avatarUri: Uri?,
        bio: String,
        loadedSetkaBio: String?,
        loadedSetkaProfile: JSONObject?,
        profileBackgroundUrl: String?,
        currentUser: MatrixUser,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        suspend {
            val results = mutableListOf<Result<Unit>>()
            if (!name.isNullOrEmpty() && name.trim() != currentUser.displayName.orEmpty().trim()) {
                results.add(matrixClient.setDisplayName(name).onFailure {
                    Timber.e(it, "Failed to set user's display name")
                })
            }
            if (avatarUri?.toString()?.trim() != currentUser.avatarUrl?.trim()) {
                results.add(updateAvatar(avatarUri).onFailure {
                    Timber.e(it, "Failed to update user's avatar")
                })
            }
            if (loadedSetkaBio != null && loadedSetkaBio.trim() != bio.trim()) {
                results.add(updateBio(bio).onFailure {
                    Timber.e(it, "Failed to update bio")
                })
            }
            if (hasBackgroundChanged(loadedSetkaProfile, profileBackgroundUrl)) {
                results.add(updateProfileBackground(loadedSetkaProfile, profileBackgroundUrl).onFailure {
                    Timber.e(it, "Failed to update profile background")
                })
            }
            if (results.all { it.isSuccess }) Unit else results.first { it.isFailure }.getOrThrow()
        }.runCatchingUpdatingState(action)
    }

    private fun hasBackgroundChanged(loaded: JSONObject?, profileBackgroundUrl: String?): Boolean {
        if (loaded == null) return false
        val loadedBackground = loaded.optString("background_mxc", "").takeIf { it.isNotBlank() }
        val normalized = profileBackgroundUrl?.trim().orEmpty()
        return (loadedBackground?.trim() ?: "") != normalized
    }

    private suspend fun updateProfileBackground(
        loadedSetkaProfile: JSONObject?,
        profileBackgroundUrl: String?,
    ): Result<Unit> {
        if (loadedSetkaProfile == null) return Result.success(Unit)
        val backgroundMxcUrl = uploadProfileBackgroundIfNeeded(profileBackgroundUrl).getOrThrow()
        val body = JSONObject().put("background_mxc", backgroundMxcUrl?.trim().orEmpty())
        val encodedUser = Uri.encode(matrixUser.userId.value)
        return matrixClient.executeAuthenticatedRequest(
            method = "PUT",
            path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
            body = body.toString().encodeToByteArray(),
        ).map { Unit }
    }

    private suspend fun uploadProfileBackgroundIfNeeded(profileBackgroundUrl: String?): Result<String?> {
        if (profileBackgroundUrl.isNullOrBlank()) return Result.success(null)
        if (profileBackgroundUrl.startsWith("mxc://")) return Result.success(profileBackgroundUrl)
        return runCatchingExceptions {
            val preprocessed = mediaPreProcessor.process(
                uri = profileBackgroundUrl.toUri(),
                mimeType = MimeTypes.Jpeg,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            ).getOrThrow()
            matrixClient.uploadMedia(MimeTypes.Jpeg, preprocessed.file.readBytes()).getOrThrow()
        }.onFailure { Timber.e(it, "Unable to upload profile background") }
    }

    private suspend fun updateBio(bio: String): Result<Unit> {
        val encodedUser = Uri.encode(matrixUser.userId.value)
        val body = JSONObject().put("bio", bio).toString().encodeToByteArray()
        return matrixClient.executeAuthenticatedRequest(
            method = "PUT",
            path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
            body = body,
        ).map { Unit }
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
                matrixClient.uploadAvatar(MimeTypes.Jpeg, preprocessed.file.readBytes()).getOrThrow()
            } else {
                matrixClient.removeAvatar().getOrThrow()
            }
        }.onFailure { Timber.e(it, "Unable to update avatar") }
    }
}
