/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl

import android.content.Intent
import androidx.core.net.toUri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.deeplink.api.DeeplinkData
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnOutcome
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.tests.testutils.assertThrowsInDebug
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultDeeplinkParserTest {
    companion object {
        const val A_URI =
            "elementx://open/@alice:server.org"
        const val A_URI_WITH_ROOM =
            "elementx://open/@alice:server.org/!aRoomId:domain"
        const val A_URI_WITH_ROOM_WITH_THREAD =
            "elementx://open/@alice:server.org/!aRoomId:domain/\$aThreadId"
        const val A_URI_WITH_ROOM_WITH_THREAD_AND_EVENT =
            "elementx://open/@alice:server.org/!aRoomId:domain/\$aThreadId/\$anEventId"
        const val A_URI_WITH_ROOM_WITH_EVENT_AND_NO_THREAD =
            "elementx://open/@alice:server.org/!aRoomId:domain//\$anEventId"
        const val A_URI_WITH_ROOM_WITH_THREAD_AND_EVENT_AND_INVALID_CHARACTERS =
            "elementx://open/@a%2Flice:server.org/!a%2FRoomId:domain/\$a%2FThreadId/\$an%2FEventId"
        const val A_URI_SETKA_PLUS_PAYMENT_SUCCESS =
            "elementx://open/@alice:server.org/setka-plus/payment/success?payment_id=pay-123"
        const val A_URI_SETKA_PLUS_PAYMENT_FAIL =
            "elementx://open/@alice:server.org/setka-plus/payment/fail?payment_id=pay-456"
        const val A_HTTPS_SETKA_PAYMENT_SUCCESS =
            "https://matrix.setka-matrix.ru/setka/payanyway/return/success?user_id=%40alice%3Aserver.org&payment_id=pay-789"
        const val A_HTTPS_SETKA_PAYMENT_SUCCESS_LEGACY_ROBOKASSA =
            "https://matrix.setka-matrix.ru/setka/robokassa/return/success?user_id=%40alice%3Aserver.org&payment_id=pay-legacy"
    }

    @Test
    fun `nominal cases`() {
        val sut = DefaultDeeplinkParser()
        assertThat(sut.getFromIntent(createIntent(A_URI)))
            .isEqualTo(DeeplinkData.Root(A_SESSION_ID))
        assertThat(sut.getFromIntent(createIntent(A_URI_WITH_ROOM)))
            .isEqualTo(DeeplinkData.Room(A_SESSION_ID, A_ROOM_ID, null, null))
        assertThat(sut.getFromIntent(createIntent(A_URI_WITH_ROOM_WITH_THREAD)))
            .isEqualTo(DeeplinkData.Room(A_SESSION_ID, A_ROOM_ID, A_THREAD_ID, null))
        assertThat(sut.getFromIntent(createIntent(A_URI_WITH_ROOM_WITH_THREAD_AND_EVENT)))
            .isEqualTo(DeeplinkData.Room(A_SESSION_ID, A_ROOM_ID, A_THREAD_ID, AN_EVENT_ID))
        assertThat(sut.getFromIntent(createIntent(A_URI_WITH_ROOM_WITH_EVENT_AND_NO_THREAD)))
            .isEqualTo(DeeplinkData.Room(A_SESSION_ID, A_ROOM_ID, null, AN_EVENT_ID))
        assertThat(sut.getFromIntent(createIntent(A_URI_WITH_ROOM_WITH_THREAD_AND_EVENT_AND_INVALID_CHARACTERS)))
            .isEqualTo(
                DeeplinkData.Room(
                    sessionId = SessionId("@a/lice:server.org"),
                    roomId = RoomId("!a/RoomId:domain"),
                    threadId = ThreadId("\$a/ThreadId"),
                    eventId = EventId("\$an/EventId"),
                )
            )
        assertThat(sut.getFromIntent(createIntent(A_URI_SETKA_PLUS_PAYMENT_SUCCESS)))
            .isEqualTo(
                DeeplinkData.SetkaPlusPaymentReturn(
                    sessionId = A_SESSION_ID,
                    paymentId = "pay-123",
                    outcome = SetkaPlusPaymentReturnOutcome.SUCCESS,
                )
            )
        assertThat(sut.getFromIntent(createIntent(A_URI_SETKA_PLUS_PAYMENT_FAIL)))
            .isEqualTo(
                DeeplinkData.SetkaPlusPaymentReturn(
                    sessionId = A_SESSION_ID,
                    paymentId = "pay-456",
                    outcome = SetkaPlusPaymentReturnOutcome.FAIL,
                )
            )
        assertThat(sut.getFromIntent(createIntent(A_HTTPS_SETKA_PAYMENT_SUCCESS)))
            .isEqualTo(
                DeeplinkData.SetkaPlusPaymentReturn(
                    sessionId = A_SESSION_ID,
                    paymentId = "pay-789",
                    outcome = SetkaPlusPaymentReturnOutcome.SUCCESS,
                )
            )
        assertThat(sut.getFromIntent(createIntent(A_HTTPS_SETKA_PAYMENT_SUCCESS_LEGACY_ROBOKASSA)))
            .isEqualTo(
                DeeplinkData.SetkaPlusPaymentReturn(
                    sessionId = A_SESSION_ID,
                    paymentId = "pay-legacy",
                    outcome = SetkaPlusPaymentReturnOutcome.SUCCESS,
                )
            )
    }

    @Test
    fun `error cases`() {
        val sut = DefaultDeeplinkParser()
        // Bad scheme
        assertThat(sut.getFromIntent(createIntent("x://open/@alice:server.org"))).isNull()
        // Bad host
        assertThat(sut.getFromIntent(createIntent("elementx://close/@alice:server.org"))).isNull()
        // No session Id
        assertThat(sut.getFromIntent(createIntent("elementx://open"))).isNull()

        assertThrowsInDebug {
            // Invalid sessionId
            sut.getFromIntent(createIntent("elementx://open/alice:server.org"))
        }
        assertThrowsInDebug {
            // Empty sessionId
            sut.getFromIntent(createIntent("elementx://open//"))
        }
    }

    private fun createIntent(uri: String): Intent {
        return Intent().apply {
            action = Intent.ACTION_VIEW
            data = uri.toUri()
        }
    }
}
