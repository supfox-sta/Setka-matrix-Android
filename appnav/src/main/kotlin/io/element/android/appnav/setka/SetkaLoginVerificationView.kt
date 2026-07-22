/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.appnav.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaLoginVerificationView(
    state: SetkaLoginVerificationState,
    onUseEmojiVerification: () -> Unit,
    onUseRecoveryKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = {})
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconTitleSubtitleMolecule(
                    modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp),
                    iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
                    title = stringResource(R.string.screen_setka_login_verification_title),
                    subTitle = stringResource(R.string.screen_setka_login_verification_subtitle),
                )
                if (state.ip.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.screen_setka_login_verification_ip, state.ip),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.region.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.screen_setka_login_verification_region, state.region),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (state.statusLoadError) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.screen_setka_login_verification_status_error),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!state.isLoading) {
                    ButtonColumnMolecule(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    ) {
                        Button(
                            text = stringResource(R.string.screen_setka_login_verification_fallback_emoji),
                            onClick = onUseEmojiVerification,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            text = stringResource(R.string.screen_setka_login_verification_fallback_recovery),
                            onClick = onUseRecoveryKey,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.screen_setka_login_verification_loading),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
