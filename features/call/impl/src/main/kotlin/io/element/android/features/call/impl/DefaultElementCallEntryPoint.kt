/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.SetkaCallPrivacy
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.features.call.api.CallType
import io.element.android.features.call.api.ElementCallEntryPoint
import io.element.android.features.call.impl.notifications.CallNotificationData
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.IntentProvider
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import org.json.JSONObject
import timber.log.Timber

@ContributesBinding(AppScope::class)
class DefaultElementCallEntryPoint(
    @ApplicationContext private val context: Context,
    private val activeCallManager: ActiveCallManager,
    private val matrixClientProvider: MatrixClientProvider,
) : ElementCallEntryPoint {
    companion object {
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"
        const val REQUEST_CODE = 2255
    }

    override fun startCall(callType: CallType) {
        context.startActivity(IntentProvider.createIntent(context, callType))
    }

    override suspend fun handleIncomingCall(
        callType: CallType.RoomCall,
        eventId: EventId,
        senderId: UserId,
        roomName: String?,
        senderName: String?,
        avatarUrl: String?,
        timestamp: Long,
        expirationTimestamp: Long,
        notificationChannelId: String,
        textContent: String?,
    ) {
        if (!isIncomingCallAllowed(callType.sessionId, senderId)) {
            Timber.i("Incoming call blocked by Setka call privacy from $senderId")
            return
        }
        val incomingCallNotificationData = CallNotificationData(
            sessionId = callType.sessionId,
            roomId = callType.roomId,
            eventId = eventId,
            senderId = senderId,
            roomName = roomName,
            senderName = senderName,
            avatarUrl = avatarUrl,
            timestamp = timestamp,
            expirationTimestamp = expirationTimestamp,
            notificationChannelId = notificationChannelId,
            textContent = textContent,
        )
        activeCallManager.registerIncomingCall(notificationData = incomingCallNotificationData)
    }

    private suspend fun isIncomingCallAllowed(
        sessionId: io.element.android.libraries.matrix.api.core.SessionId,
        senderId: UserId,
    ): Boolean {
        val client = matrixClientProvider.getOrRestore(sessionId).getOrNull() ?: return true
        val serverName = client.sessionId.value.substringAfter(':', "")
        if (!SetkaMatrixConfig.isSetkaHomeserver(serverName)) {
            return true
        }
        val encodedUserId = Uri.encode(client.sessionId.value)
        val callVisibilityRaw = client.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/user/$encodedUserId/setka_privacy",
        ).getOrNull()
            ?.decodeToString()
            ?.let { JSONObject(it).optString("call_visibility") }
        val senderHasDirectChat = client.findDM(senderId).getOrNull() != null
        return SetkaCallPrivacy.canCallerReachUser(
            callerHasDirectChat = senderHasDirectChat,
            callVisibilityRaw = callVisibilityRaw,
        )
    }
}
