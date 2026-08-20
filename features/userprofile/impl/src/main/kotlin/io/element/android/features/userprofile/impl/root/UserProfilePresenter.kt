/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.impl.root

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.SetkaCallPrivacy
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.features.userprofile.api.UserProfileEvents
import io.element.android.features.userprofile.api.UserProfileState
import io.element.android.features.userprofile.api.UserProfileState.ConfirmationDialog
import io.element.android.features.userprofile.api.UserProfileVerificationState
import io.element.android.libraries.androidutils.clipboard.ClipboardHelper
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.powerlevels.canCall
import io.element.android.libraries.matrix.api.room.powerlevels.use
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject

@AssistedInject
class UserProfilePresenter(
    @Assisted private val userId: UserId,
    private val client: MatrixClient,
    private val startDMAction: StartDMAction,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val clipboardHelper: ClipboardHelper,
) : Presenter<UserProfileState> {
    @AssistedFactory
    interface Factory {
        fun create(userId: UserId): UserProfilePresenter
    }

    @Composable
    private fun getDmRoomId(): State<RoomId?> {
        return produceState(initialValue = null) {
            value = client.findDM(userId).getOrNull()
        }
    }

    @Composable
    private fun getCanCall(roomId: RoomId?, setkaProfile: JSONObject?): State<Boolean> {
        val isElementCallAvailable by produceState(initialValue = false, roomId) {
            value = sessionEnterpriseService.isElementCallAvailable()
        }
        return produceState(initialValue = false, isElementCallAvailable, roomId, setkaProfile) {
            value = when {
                isElementCallAvailable.not() -> false
                client.isMe(userId) -> false
                !SetkaCallPrivacy.canCallerReachUser(
                    callerHasDirectChat = roomId != null,
                    callVisibilityRaw = SetkaCallPrivacy.callVisibilityFromProfile(setkaProfile),
                ) -> false
                else -> roomId
                    ?.let { client.getRoom(it) }
                    ?.use { room ->
                        room.roomPermissions().use(false) { perms -> perms.canCall() }
                    }
                    .orFalse()
            }
        }
    }

    @Composable
    override fun present(): UserProfileState {
        val coroutineScope = rememberCoroutineScope()
        val snackbarDispatcher = LocalSnackbarDispatcher.current
        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        val isCurrentUser = remember { client.isMe(userId) }
        var confirmationDialog by remember { mutableStateOf<ConfirmationDialog?>(null) }
        val startDmActionState: MutableState<AsyncAction<RoomId>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        val isBlocked: MutableState<AsyncData<Boolean>> = remember { mutableStateOf(AsyncData.Uninitialized) }
        val dmRoomId by getDmRoomId()

        LaunchedEffect(Unit) {
            client.ignoredUsersFlow
                .map { ignoredUsers -> userId in ignoredUsers }
                .distinctUntilChanged()
                .onEach { isBlocked.value = AsyncData.Success(it) }
                .launchIn(this)
        }

        val userProfile by produceState<MatrixUser?>(null) {
            value = client.getProfile(userId).getOrNull()
        }
        val setkaProfile by produceState<JSONObject?>(null, userId, isCurrentUser) {
            val encoded = Uri.encode(userId.value)
            val candidatePaths = if (isCurrentUser) {
                listOf(
                    "/_matrix/client/v3/setka/users/$encoded/profile",
                    "/_matrix/client/r0/setka/users/$encoded/profile",
                    "/_synapse/admin/v1/setka/users/$encoded/profile",
                    "/_matrix/client/v3/user/$encoded/setka_profile",
                    "/_matrix/client/r0/user/$encoded/setka_profile",
                )
            } else {
                listOf(
                    "/_matrix/client/v3/setka/users/$encoded/profile",
                    "/_matrix/client/r0/setka/users/$encoded/profile",
                    "/_synapse/admin/v1/setka/users/$encoded/profile",
                    "/_synapse/admin/v1/setka_plus/users/$encoded",
                    "/_matrix/client/v3/profile/$encoded/setka_profile",
                    "/_matrix/client/v3/profile/${userId.value}/setka_profile",
                    "/_matrix/client/r0/profile/$encoded/setka_profile",
                    "/_matrix/client/r0/profile/${userId.value}/setka_profile",
                )
            }
            val payloads = candidatePaths.mapNotNull { path ->
                client.executeAuthenticatedRequest(
                    method = "GET",
                    path = path,
                ).getOrNull()
                    ?.decodeToString()
                    ?.let(::parseSetkaProfileResponse)
            }
            value = payloads.firstOrNull { it.hasUsefulSetkaProfilePayload() } ?: payloads.firstOrNull()
        }
        val canCall by getCanCall(dmRoomId, setkaProfile)

        fun handleEvent(event: UserProfileEvents) {
            when (event) {
                is UserProfileEvents.BlockUser -> {
                    if (event.needsConfirmation) {
                        confirmationDialog = ConfirmationDialog.Block
                    } else {
                        confirmationDialog = null
                        coroutineScope.blockUser(isBlocked)
                    }
                }

                is UserProfileEvents.UnblockUser -> {
                    if (event.needsConfirmation) {
                        confirmationDialog = ConfirmationDialog.Unblock
                    } else {
                        confirmationDialog = null
                        coroutineScope.unblockUser(isBlocked)
                    }
                }

                UserProfileEvents.ClearConfirmationDialog -> confirmationDialog = null
                UserProfileEvents.ClearBlockUserError -> {
                    isBlocked.value = AsyncData.Success(isBlocked.value.dataOrNull().orFalse())
                }

                UserProfileEvents.StartDM -> {
                    coroutineScope.launch {
                        startDMAction.execute(
                            matrixUser = userProfile ?: MatrixUser(userId),
                            createIfDmDoesNotExist = startDmActionState.value is AsyncAction.Confirming,
                            actionState = startDmActionState,
                        )
                    }
                }

                UserProfileEvents.ClearStartDMState -> {
                    startDmActionState.value = AsyncAction.Uninitialized
                }

                UserProfileEvents.WithdrawVerification -> Unit

                is UserProfileEvents.CopyToClipboard -> {
                    clipboardHelper.copyPlainText(event.text)
                    snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_copied_to_clipboard))
                }
            }
        }

        return UserProfileState(
            userId = userId,
            userName = userProfile?.displayName,
            avatarUrl = userProfile?.avatarUrl,
            bio = setkaProfile?.optString("bio")?.takeIf { it.isNotBlank() },
            profileColorHex = setkaProfile?.optString("color")?.takeIf { it.isNotBlank() },
            badgeEmojiMxcUrl = setkaProfile?.optString("badge_emoji_mxc")?.takeIf { it.isNotBlank() },
            statusEmojiMxcUrl = setkaProfile?.optString("status_emoji_mxc")?.takeIf { it.isNotBlank() },
            profileBackgroundMxcUrl = setkaProfile?.optString("background_mxc")?.takeIf { it.isNotBlank() },
            email = setkaProfile?.optString("email")?.takeIf { it.isNotBlank() },
            phone = setkaProfile?.optString("phone")?.takeIf { it.isNotBlank() },
            lastSeenText = setkaProfile.toSetkaPresenceText(),
            isBlocked = isBlocked.value,
            verificationState = UserProfileVerificationState.UNKNOWN,
            startDmActionState = startDmActionState.value,
            displayConfirmationDialog = confirmationDialog,
            isCurrentUser = isCurrentUser,
            dmRoomId = dmRoomId,
            canCall = canCall,
            snackbarMessage = snackbarMessage,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.blockUser(
        isBlockedState: MutableState<AsyncData<Boolean>>,
    ) = launch {
        isBlockedState.value = AsyncData.Loading(false)
        client.ignoreUser(userId)
            .onFailure {
                isBlockedState.value = AsyncData.Failure(it, false)
            }
    }

    private fun CoroutineScope.unblockUser(
        isBlockedState: MutableState<AsyncData<Boolean>>,
    ) = launch {
        isBlockedState.value = AsyncData.Loading(true)
        client.unignoreUser(userId)
            .onFailure {
                isBlockedState.value = AsyncData.Failure(it, true)
            }
    }
}

private fun parseSetkaProfileResponse(raw: String): JSONObject? {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val nestedProfile = json.optJSONObject("profile")
    if (nestedProfile != null) return nestedProfile
    val nestedUserPayload = json.optJSONObject("user")
        ?.optJSONObject("profile")
    if (nestedUserPayload != null) return nestedUserPayload
    return json
}

private fun JSONObject.hasUsefulSetkaProfilePayload(): Boolean {
    return listOf(
        "bio",
        "color",
        "badge_emoji_mxc",
        "status_emoji_mxc",
        "background_mxc",
        "email",
        "phone",
        "presence_state",
        "last_seen_ts",
        "call_visibility",
    ).any { key ->
        when (val value = opt(key)) {
            is String -> value.isNotBlank()
            is Number -> true
            else -> value != null && value != JSONObject.NULL
        }
    }
}

private fun JSONObject?.toSetkaPresenceText(): String? {
    val json = this ?: return null
    return when (json.optString("presence_state").trim()) {
        "online" -> "В сети"
        "recently" -> "Был(-а) недавно"
        "this_week" -> "Был(-а) на этой неделе"
        "long_ago" -> "Был(-а) давно"
        else -> null
    }
}
