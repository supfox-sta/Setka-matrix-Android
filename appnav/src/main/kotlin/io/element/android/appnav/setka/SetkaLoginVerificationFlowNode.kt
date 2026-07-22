/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.securebackup.api.SecureBackupEntryPoint
import io.element.android.features.verifysession.api.OutgoingVerificationEntryPoint
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationFallbackMethod
import io.element.android.libraries.matrix.api.verification.VerificationRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@ContributesNode(SessionScope::class)
@AssistedInject
class SetkaLoginVerificationFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val setkaLoginRecoverySync: SetkaLoginRecoverySync,
    private val outgoingVerificationEntryPoint: OutgoingVerificationEntryPoint,
    private val secureBackupEntryPoint: SecureBackupEntryPoint,
) : BaseFlowNode<SetkaLoginVerificationFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.MethodSelection,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object MethodSelection : NavTarget

        @Parcelize
        data object EmojiVerification : NavTarget

        @Parcelize
        data object RecoveryKey : NavTarget
    }

    interface Callback : Plugin {
        fun onDone()
    }

    private val callback: Callback = callback()

    private fun finishWithFallback(method: SetkaLoginVerificationFallbackMethod) {
        callback.onDone()
        lifecycleScope.launch {
            runCatching {
                withTimeout(FALLBACK_COMPLETE_TIMEOUT) {
                    setkaLoginRecoverySync.completeFallbackAndSync(method).getOrThrow()
                }
            }.onFailure { error ->
                Timber.w(error, "Setka login fallback sync failed")
            }
        }
    }

    private companion object {
        val FALLBACK_COMPLETE_TIMEOUT = 30.seconds
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.MethodSelection -> {
                val nodeCallback = object : SetkaLoginVerificationNode.Callback {
                    override fun onVerified() {
                        callback.onDone()
                    }

                    override fun onSkipVerification() {
                        callback.onDone()
                    }

                    override fun onUseEmojiVerification() {
                        backstack.push(NavTarget.EmojiVerification)
                    }

                    override fun onUseRecoveryKey() {
                        backstack.push(NavTarget.RecoveryKey)
                    }
                }
                createNode<SetkaLoginVerificationNode>(buildContext, plugins = listOf(nodeCallback))
            }
            NavTarget.EmojiVerification -> {
                outgoingVerificationEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = OutgoingVerificationEntryPoint.Params(
                        showDeviceVerifiedScreen = true,
                        verificationRequest = VerificationRequest.Outgoing.CurrentSession,
                    ),
                    callback = object : OutgoingVerificationEntryPoint.Callback {
                        override fun onDone() {
                            finishWithFallback(SetkaLoginVerificationFallbackMethod.EMOJI)
                        }

                        override fun onBack() {
                            backstack.pop()
                        }

                        override fun navigateToLearnMoreAboutEncryption() = Unit
                    },
                )
            }
            NavTarget.RecoveryKey -> {
                secureBackupEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = SecureBackupEntryPoint.Params(
                        SecureBackupEntryPoint.InitialTarget.EnterRecoveryKey
                    ),
                    callback = object : SecureBackupEntryPoint.Callback {
                        override fun onDone() {
                            finishWithFallback(SetkaLoginVerificationFallbackMethod.RECOVERY_KEY)
                        }
                    },
                )
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier = modifier)
    }
}
