/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.setka

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

@Composable
fun SetkaStatusEmojiInline(
    mxcUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (mxcUrl.isNullOrBlank()) return
    AsyncImage(
        model = MediaRequestData(MediaSource(mxcUrl), MediaRequestData.Kind.Content),
        contentDescription = null,
        modifier = modifier
            .padding(start = 4.dp)
            .size(18.dp),
        contentScale = ContentScale.Fit,
    )
}
