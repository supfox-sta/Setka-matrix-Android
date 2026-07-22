/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api

data class ImportedRoomEventsResult(
    val importedCount: Int,
    val reachedTimelineStart: Boolean = false,
)

object MatrixClientImportEventsException : RuntimeException(
    "matrix-rust-sdk does not expose event import yet; see docs/setka-lite/matrix-rust-sdk-event-import.md",
)
