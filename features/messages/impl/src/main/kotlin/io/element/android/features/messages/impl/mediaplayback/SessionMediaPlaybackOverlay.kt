/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.features.messages.impl.mediaplayback



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import androidx.compose.ui.zIndex

import io.element.android.libraries.architecture.Presenter

import io.element.android.libraries.matrix.api.core.EventId

import io.element.android.libraries.matrix.api.core.RoomId



@Composable

fun SessionMediaPlaybackOverlay(

    barPresenter: Presenter<RoomMediaPlaybackBarState>,

    coordinator: RoomMediaPlaybackCoordinator,

    onOpenPlayingMessage: (RoomId, EventId) -> Unit,

    modifier: Modifier = Modifier,

) {

    val barState = barPresenter.present()

    val barVisible = barState is RoomMediaPlaybackBarState.Visible

    Box(

        modifier = modifier

            .fillMaxSize()

            .zIndex(10f),

    ) {

        if (barVisible) {

            RoomMediaPlaybackBarView(

                state = barState,

                modifier = Modifier

                    .fillMaxWidth()

                    .statusBarsPadding()

                    .padding(top = 56.dp),

            )

        }

        RoomVideoNoteFloatingPreview(

            coordinator = coordinator,

            onOpenPlayingMessage = onOpenPlayingMessage,

            modifier = Modifier.fillMaxSize(),

        )

    }

}

