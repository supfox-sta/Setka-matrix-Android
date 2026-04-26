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
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.androidutils.file.TemporaryUriDeleter
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.user.MatrixUser
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
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

@AssistedInject
class EditUserProfilePresenter(
    @Assisted private val matrixUser: MatrixUser,
    @Assisted private val navigator: EditUserProfileNavigator,
    private val matrixClient: MatrixClient,
    private val mediaPickerProvider: PickerProvider,
    private val mediaPreProcessor: MediaPreProcessor,
    private val temporaryUriDeleter: TemporaryUriDeleter,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
) : Presenter<EditUserProfileState> {
    private val cameraPermissionPresenter: PermissionsPresenter = permissionsPresenterFactory.create(android.Manifest.permission.CAMERA)
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
        var userAvatarUri by rememberSaveable { mutableStateOf(matrixUser.avatarUrl) }
        var userDisplayName by rememberSaveable { mutableStateOf(matrixUser.displayName) }
        var bio by rememberSaveable { mutableStateOf("") }
        var profileColorHex by rememberSaveable { mutableStateOf("") }
        var badgeEmojiMxcUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var statusEmojiMxcUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var profileBackgroundUrl by rememberSaveable { mutableStateOf<String?>(null) }

        // Keep the last server-loaded value to compute dirty state for Setka extras.
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

        val badgeEmojiPacks by produceState<ImmutableList<SetkaEmojiPack>>(initialValue = persistentListOf()) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                val raw = matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_plus/sticker_packs",
                ).getOrThrow().decodeToString()
                parseEmojiPacksFromStickerPacksResponse(JSONObject(raw))
            }.getOrDefault(persistentListOf())
        }

        LaunchedEffect(setkaProfile) {
            val incoming = setkaProfile ?: return@LaunchedEffect
            if (loadedSetkaProfile == null) {
                loadedSetkaProfile = incoming
                bio = incoming.optString("bio", "")
                profileColorHex = incoming.optString("color", "")
                badgeEmojiMxcUrl = incoming.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
                statusEmojiMxcUrl = incoming.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
                profileBackgroundUrl = incoming.optString("background_mxc", "").takeIf { it.isNotBlank() }
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
            profileColorHex,
            badgeEmojiMxcUrl,
            statusEmojiMxcUrl,
            profileBackgroundUrl,
            loadedSetkaProfile,
        ) {
            val hasProfileChanged = hasDisplayNameChanged(userDisplayName, matrixUser) ||
                hasAvatarUrlChanged(userAvatarUri, matrixUser) ||
                hasSetkaProfileChanged(
                    loaded = loadedSetkaProfile,
                    bio = bio,
                    color = profileColorHex,
                    badge = badgeEmojiMxcUrl,
                    statusEmoji = statusEmojiMxcUrl,
                    background = profileBackgroundUrl,
                )
            !userDisplayName.isNullOrBlank() && hasProfileChanged
        }

        fun handleEvent(event: EditUserProfileEvent) {
            when (event) {
                is EditUserProfileEvent.Save -> localCoroutineScope.saveChanges(
                    name = userDisplayName,
                    avatarUri = userAvatarUri?.toUri(),
                    bio = bio,
                    color = profileColorHex,
                    badgeEmojiMxcUrl = badgeEmojiMxcUrl,
                    statusEmojiMxcUrl = statusEmojiMxcUrl,
                    profileBackgroundUrl = profileBackgroundUrl,
                    currentUser = matrixUser,
                    loadedSetkaProfile = loadedSetkaProfile,
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
                EditUserProfileEvent.PickProfileBackground -> {
                    if (isSetkaPlusActive) {
                        backgroundImagePicker.launch()
                    }
                }
                EditUserProfileEvent.RemoveProfileBackground -> {
                    profileBackgroundUrl = null
                }
                is EditUserProfileEvent.UpdateDisplayName -> userDisplayName = event.name
                is EditUserProfileEvent.UpdateBio -> bio = event.bio
                is EditUserProfileEvent.UpdateProfileColorHex -> profileColorHex = event.color
                is EditUserProfileEvent.SetBadgeEmoji -> badgeEmojiMxcUrl = event.mxcUrl
                is EditUserProfileEvent.SetStatusEmoji -> statusEmojiMxcUrl = event.mxcUrl
                EditUserProfileEvent.Exit -> {
                    when (saveAction.value) {
                        is AsyncAction.Confirming -> {
                            // Close the dialog right now
                            saveAction.value = AsyncAction.Uninitialized
                            navigator.close()
                        }
                        AsyncAction.Loading -> Unit
                        is AsyncAction.Failure,
                        is AsyncAction.Success -> {
                            // Should not happen
                        }
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
            badgeEmojiMxcUrl = badgeEmojiMxcUrl,
            statusEmojiMxcUrl = statusEmojiMxcUrl,
            profileBackgroundUrl = profileBackgroundUrl,
            isSetkaPlusActive = isSetkaPlusActive,
            badgeEmojiPacks = badgeEmojiPacks,
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
        color: String,
        badgeEmojiMxcUrl: String?,
        statusEmojiMxcUrl: String?,
        profileBackgroundUrl: String?,
        currentUser: MatrixUser,
        loadedSetkaProfile: JSONObject?,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        val results = mutableListOf<Result<Unit>>()
        suspend {
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
            val setkaUpdate = updateSetkaProfileIfNeeded(
                loaded = loadedSetkaProfile,
                bio = bio,
                color = color,
                badgeEmojiMxcUrl = badgeEmojiMxcUrl,
                statusEmojiMxcUrl = statusEmojiMxcUrl,
                backgroundMxcUrl = uploadProfileBackgroundIfNeeded(profileBackgroundUrl).getOrThrow(),
            )
            if (setkaUpdate != null) {
                results.add(setkaUpdate.onFailure { Timber.e(it, "Failed to update Setka profile extras") })
            }
            if (results.all { it.isSuccess }) Unit else results.first { it.isFailure }.getOrThrow()
        }.runCatchingUpdatingState(action)
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

    private fun hasSetkaProfileChanged(
        loaded: JSONObject?,
        bio: String,
        color: String,
        badge: String?,
        statusEmoji: String?,
        background: String?,
    ): Boolean {
        if (loaded == null) return false
        val loadedBio = loaded.optString("bio", "")
        val loadedColor = loaded.optString("color", "")
        val loadedBadge = loaded.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedStatusEmoji = loaded.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedBackground = loaded.optString("background_mxc", "").takeIf { it.isNotBlank() }
        return loadedBio.trim() != bio.trim() ||
            loadedColor.trim() != color.trim() ||
            (loadedBadge?.trim() ?: "") != (badge?.trim() ?: "") ||
            (loadedStatusEmoji?.trim() ?: "") != (statusEmoji?.trim() ?: "") ||
            (loadedBackground?.trim() ?: "") != (background?.trim() ?: "")
    }

    private suspend fun updateSetkaProfileIfNeeded(
        loaded: JSONObject?,
        bio: String,
        color: String,
        badgeEmojiMxcUrl: String?,
        statusEmojiMxcUrl: String?,
        backgroundMxcUrl: String?,
    ): Result<Unit>? {
        if (loaded == null) return null
        val loadedBio = loaded.optString("bio", "")
        val loadedColor = loaded.optString("color", "")
        val loadedBadge = loaded.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedStatusEmoji = loaded.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedBackground = loaded.optString("background_mxc", "").takeIf { it.isNotBlank() }
        val normalizedColor = color.trim()
        val normalizedBadge = badgeEmojiMxcUrl?.trim().orEmpty()
        val normalizedStatusEmoji = statusEmojiMxcUrl?.trim().orEmpty()
        val normalizedBackground = backgroundMxcUrl?.trim().orEmpty()
        val body = JSONObject()
        var changed = false
        if (loadedBio.trim() != bio.trim()) {
            body.put("bio", bio)
            changed = true
        }
        if (loadedColor.trim() != normalizedColor) {
            body.put("color", normalizedColor)
            changed = true
        }
        if ((loadedBadge?.trim() ?: "") != normalizedBadge) {
            body.put("badge_emoji_mxc", normalizedBadge)
            changed = true
        }
        if ((loadedStatusEmoji?.trim() ?: "") != normalizedStatusEmoji) {
            body.put("status_emoji_mxc", normalizedStatusEmoji)
            changed = true
        }
        if ((loadedBackground?.trim() ?: "") != normalizedBackground) {
            body.put("background_mxc", normalizedBackground)
            changed = true
        }
        if (!changed) return null
        val encodedUser = Uri.encode(matrixUser.userId.value)
        return matrixClient.executeAuthenticatedRequest(
            method = "PUT",
            path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
            body = body.toString().encodeToByteArray(),
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

    private fun parseEmojiPacksFromStickerPacksResponse(root: JSONObject): ImmutableList<SetkaEmojiPack> {
        val packsJson = root.optJSONArray("packs")
            ?: root.optJSONArray("sticker_packs")
            ?: JSONArray()
        val out = ArrayList<SetkaEmojiPack>()
        for (i in 0 until packsJson.length()) {
            val pack = packsJson.optJSONObject(i) ?: continue
            val kind = pack.optString("kind")
            val id = pack.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = pack.optString("name").takeIf { it.isNotBlank() } ?: id
            val stickersJson = pack.optJSONArray("stickers") ?: JSONArray()
            val stickers = ArrayList<SetkaEmojiSticker>()
            for (j in 0 until stickersJson.length()) {
                val sticker = stickersJson.optJSONObject(j) ?: continue
                val mxc = sticker.optString("mxc_url").takeIf { it.isNotBlank() }
                    ?: sticker.optString("mxcUrl").takeIf { it.isNotBlank() }
                    ?: continue
                val stickerId = sticker.optString("id").takeIf { it.isNotBlank() } ?: mxc
                val stickerName = sticker.optString("name").takeIf { it.isNotBlank() }
                stickers.add(SetkaEmojiSticker(id = stickerId, name = stickerName, mxcUrl = mxc))
            }
            val isEmojiPack = kind.lowercase(Locale.ROOT) == "emoji" ||
                stickers.any { sticker ->
                    val normalized = sticker.name.orEmpty()
                    normalized.startsWith(":") && normalized.endsWith(":")
                }
            if (!isEmojiPack) continue
            val icon = pack.optString("icon_mxc").takeIf { it.isNotBlank() }
                ?: pack.optString("iconMxcUrl").takeIf { it.isNotBlank() }
                ?: stickers.firstOrNull()?.mxcUrl
            out.add(
                SetkaEmojiPack(
                    id = id,
                    name = name,
                    iconMxcUrl = icon,
                    stickers = stickers.toImmutableList(),
                )
            )
        }
        return out.toImmutableList()
    }
}
