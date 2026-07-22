/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import androidx.compose.runtime.compositionLocalOf
import io.element.android.libraries.matrix.api.core.RoomId

val LocalRoomMediaPlaybackCoordinator = compositionLocalOf<RoomMediaPlaybackCoordinator?> { null }

val LocalRoomId = compositionLocalOf<RoomId?> { null }
