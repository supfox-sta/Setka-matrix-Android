/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class SetkaLoginVerificationNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: SetkaLoginVerificationPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onVerified()
        fun onSkipVerification()
        fun onUseEmojiVerification()
        fun onUseRecoveryKey()
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        LaunchedEffect(state.shouldSkip) {
            if (state.shouldSkip) {
                callback.onSkipVerification()
            }
        }
        SetkaLoginVerificationView(
            state = state,
            modifier = modifier,
            onUseEmojiVerification = { callback.onUseEmojiVerification() },
            onUseRecoveryKey = { callback.onUseRecoveryKey() },
        )
    }
}
