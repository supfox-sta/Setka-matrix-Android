/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.videonotes

import android.content.Context
import android.view.LayoutInflater
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import io.element.android.features.messages.impl.R

internal object VideoNotePlayerViewFactory {
    fun create(context: Context): PlayerView {
        return LayoutInflater.from(context).inflate(
            R.layout.setka_video_note_player_view,
            null,
        ) as PlayerView
    }

    fun bindPlayer(view: PlayerView, player: Player?) {
        if (view.player != player) {
            view.player = player
        }
    }
}
