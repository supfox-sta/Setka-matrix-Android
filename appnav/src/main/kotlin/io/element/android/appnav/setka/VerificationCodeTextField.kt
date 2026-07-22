/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import kotlinx.coroutines.delay

enum class VerificationDigitState {
    Empty,
    Filled,
    Success,
}

data class VerificationCodeEntry(
    val digits: List<VerificationDigitState> = List(CODE_LENGTH) { VerificationDigitState.Empty },
) {
    fun fillWith(text: String): VerificationCodeEntry {
        val sanitized = text.filter { it.isDigit() }.take(CODE_LENGTH)
        val updated = MutableList(CODE_LENGTH) { VerificationDigitState.Empty }
        sanitized.forEachIndexed { index, _ ->
            updated[index] = VerificationDigitState.Filled
        }
        return copy(digits = updated)
    }

    fun withSuccessUpTo(indexInclusive: Int, rawInput: String): VerificationCodeEntry {
        val base = fillWith(rawInput)
        return base.copy(
            digits = base.digits.mapIndexed { index, _ ->
                if (index <= indexInclusive) VerificationDigitState.Success else VerificationDigitState.Filled
            }
        )
    }

    companion object {
        const val CODE_LENGTH = 6
        fun empty() = VerificationCodeEntry()
    }
}

@Composable
fun VerificationCodeTextField(
    code: VerificationCodeEntry,
    rawInput: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    BasicTextField(
        modifier = modifier,
        value = rawInput,
        onValueChange = { value ->
            onValueChange(value.filter { it.isDigit() }.take(VerificationCodeEntry.CODE_LENGTH))
        },
        interactionSource = interactionSource,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                code.digits.forEachIndexed { index, digitState ->
                    VerificationDigitBox(
                        digitState = digitState,
                        char = rawInput.getOrNull(index),
                        isCurrent = index == rawInput.length && isFocused,
                        drawCursor = isFocused,
                    )
                }
            }
        }
    )
}

@Composable
fun rememberAnimatedVerificationCode(
    successTrigger: Boolean,
    rawInput: String,
    onFinished: () -> Unit,
): VerificationCodeEntry {
    var animatedIndex by remember { mutableIntStateOf(-1) }
    var displayCode by remember(rawInput, successTrigger) {
        mutableStateOf(VerificationCodeEntry.empty().fillWith(rawInput))
    }

    LaunchedEffect(successTrigger) {
        if (!successTrigger) return@LaunchedEffect
        repeat(VerificationCodeEntry.CODE_LENGTH) { index ->
            animatedIndex = index
            displayCode = VerificationCodeEntry.empty()
                .fillWith(rawInput)
                .withSuccessUpTo(index, rawInput)
            delay(120)
        }
        delay(250)
        onFinished()
    }

    return if (successTrigger && animatedIndex >= 0) displayCode else VerificationCodeEntry.empty().fillWith(rawInput)
}

@Composable
private fun VerificationDigitBox(
    digitState: VerificationDigitState,
    char: Char?,
    isCurrent: Boolean,
    drawCursor: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    val successColor = Color(0xFF1B8755)
    val targetBorder = when (digitState) {
        VerificationDigitState.Success -> successColor
        VerificationDigitState.Filled -> ElementTheme.colors.textPrimary
        VerificationDigitState.Empty -> if (isCurrent) {
            ElementTheme.colors.textPrimary
        } else {
            ElementTheme.colors.borderInteractiveSecondary
        }
    }
    val targetBackground = when (digitState) {
        VerificationDigitState.Success -> successColor.copy(alpha = 0.15f)
        VerificationDigitState.Filled -> ElementTheme.colors.bgActionSecondaryPressed
        VerificationDigitState.Empty -> Color.Transparent
    }
    val borderColor by animateColorAsState(targetBorder, label = "digitBorder")
    val backgroundColor by animateColorAsState(targetBackground, label = "digitBackground")

    Box(
        modifier = Modifier
            .size(44.dp, 56.dp)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null && char.isDigit()) {
            Text(
                text = char.toString(),
                style = ElementTheme.typography.fontHeadingLgBold,
                color = if (digitState == VerificationDigitState.Success) {
                    successColor
                } else {
                    ElementTheme.colors.textPrimary
                },
            )
        } else if (drawCursor && isCurrent) {
            Spacer(
                modifier = Modifier
                    .size(2.dp, 24.dp)
                    .offset(x = (-4).dp)
                    .background(ElementTheme.colors.textPrimary, RoundedCornerShape(1.dp))
            )
        }
    }
}
