/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bumble.appyx.core.modality.BuildContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.forward.test.FakeForwardEntryPoint
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import androidx.test.core.app.ApplicationProvider
import io.element.android.features.home.impl.recent.SetkaRecentRoomsStore
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultHomeEntryPointTest {
    @Test
    fun `test node builder`() = runTest {
        val entryPoint = DefaultHomeEntryPoint()
        val matrixClient = FakeMatrixClient()
        val parentNode = TestParentNode.create { buildContext, plugins ->
            HomeFlowNode(
                buildContext = buildContext,
                plugins = plugins,
                matrixClient = matrixClient,
                presenter = createHomePresenter(client = matrixClient),
                inviteFriendsUseCase = { lambdaError() },
                analyticsService = FakeAnalyticsService(),
                acceptDeclineInviteView = { _, _, _, _ -> lambdaError() },
                directLogoutView = { _ -> lambdaError() },
                reportRoomEntryPoint = { _, _, _ -> lambdaError() },
                declineInviteAndBlockUserEntryPoint = { _, _, _ -> lambdaError() },
                changeRoomMemberRolesEntryPoint = { _, _, _, _ -> lambdaError() },
                forwardEntryPoint = FakeForwardEntryPoint(),
                leaveRoomRenderer = { _, _, _ -> lambdaError() },
                setkaRecentRoomsStore = SetkaRecentRoomsStore(
                    context = ApplicationProvider.getApplicationContext(),
                    matrixClient = matrixClient,
                ),
                sessionCoroutineScope = backgroundScope,
            )
        }
        val callback = object : HomeEntryPoint.Callback {
            override fun navigateToRoom(
                roomId: RoomId,
                joinedRoom: JoinedRoom?,
                focusedEventId: EventId?,
                threadRootId: EventId?,
            ) = lambdaError()
            override fun navigateToCreateRoom() = lambdaError()
            override fun navigateToCreateChannel() = lambdaError()
            override fun navigateToCreateSpace() = lambdaError()
            override fun navigateToSettings() = lambdaError()
            override fun navigateToUserProfile(userId: UserId) = lambdaError()
            override fun navigateToSetUpRecovery() = lambdaError()
            override fun navigateToEnterRecoveryKey() = lambdaError()
            override fun navigateToRoomSettings(roomId: RoomId) = lambdaError()
            override fun navigateToBugReport() = lambdaError()
        }
        val result = entryPoint.createNode(
            parentNode = parentNode,
            buildContext = BuildContext.root(null),
            callback = callback,
        )
        assertThat(result).isInstanceOf(HomeFlowNode::class.java)
        assertThat(result.plugins).contains(callback)
    }
}
