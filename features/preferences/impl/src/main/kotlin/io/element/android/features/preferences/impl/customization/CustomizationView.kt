/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.customization

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull
import io.element.android.libraries.preferences.api.store.CallAudioBackgroundStyles
import io.element.android.libraries.preferences.api.store.RoomWallpaperStyles
import io.element.android.libraries.ui.strings.CommonStrings
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType
import coil3.compose.AsyncImage
import org.json.JSONObject

private enum class CustomizationCategory(val title: String) {
    Themes("Темы чатов"),
    Chat("Дизайн чата"),
    Calls("Звонки"),
    Wallpapers("Обои чатов"),
    TextSize("Размер сообщений"),
    Optimization("Оптимизация"),
    ImportExport("Импорт / Экспорт"),
}

private enum class PreviewColorTarget(val title: String) {
    TopBar("Шапка чата"),
    TopBarText("Текст шапки"),
    Accent("Акцент / ссылка"),
    Composer("Композер"),
    ServiceBubble("Сервисный bubble"),
    ServiceText("Текст сервисных событий"),
    IncomingBubble("Входящие bubbles"),
    OutgoingBubble("Исходящие bubbles"),
    IncomingGradient("Градиент входящих"),
    OutgoingGradient("Градиент исходящих"),
}

private enum class StudioCategory(val title: String, val targets: List<PreviewColorTarget>) {
    Chat(
        title = "Чат",
        targets = listOf(
            PreviewColorTarget.TopBar,
            PreviewColorTarget.TopBarText,
            PreviewColorTarget.Composer,
            PreviewColorTarget.ServiceBubble,
            PreviewColorTarget.ServiceText,
        ),
    ),
    Messages(
        title = "Сообщения",
        targets = listOf(
            PreviewColorTarget.IncomingBubble,
            PreviewColorTarget.IncomingGradient,
            PreviewColorTarget.OutgoingBubble,
            PreviewColorTarget.OutgoingGradient,
            PreviewColorTarget.Accent,
        ),
    ),
}

private enum class OptimizationPreset(
    val title: String,
    val enableAnimations: Boolean,
    val enableBlur: Boolean,
    val initialMessages: Int,
) {
    Maximum("Максимум", enableAnimations = true, enableBlur = true, initialMessages = 20),
    Optimized("Оптимизация", enableAnimations = true, enableBlur = false, initialMessages = 10),
    PowerSave("Энергосбережение", enableAnimations = false, enableBlur = false, initialMessages = 5),
}

private sealed interface ColorPickerTarget {
    data object PreviewElement : ColorPickerTarget
    data object HomeBackground : ColorPickerTarget
    data object ChatWallpaper : ColorPickerTarget
}

private data class ThemePreset(
    val title: String,
    val themeMode: String,
    val accent: String,
    val topBarBg: String,
    val topBarText: String,
    val composerBg: String,
    val serviceBubble: String,
    val serviceText: String,
    val incomingBubble: String,
    val outgoingBubble: String,
    val incomingGradient: String?,
    val outgoingGradient: String?,
    val homeBackground: String,
    val wallpaperStyle: String,
)

private val themePresets = listOf(
    ThemePreset(
        title = "Арктика",
        themeMode = "Dark",
        accent = "#6EA7FF",
        topBarBg = "#1F2C3D",
        topBarText = "#FFFFFF",
        composerBg = "#1E2A39",
        serviceBubble = "#324357",
        serviceText = "#D7E3F3",
        incomingBubble = "#243447",
        outgoingBubble = "#305A83",
        incomingGradient = "#33506B",
        outgoingGradient = "#4A86BE",
        homeBackground = "#111A25",
        wallpaperStyle = RoomWallpaperStyles.DARK,
    ),
    ThemePreset(
        title = "Лайм",
        themeMode = "Light",
        accent = "#0A8F7A",
        topBarBg = "#F6F7F8",
        topBarText = "#111111",
        composerBg = "#FFFFFF",
        serviceBubble = "#DCE5EA",
        serviceText = "#2A2A2A",
        incomingBubble = "#FFFFFF",
        outgoingBubble = "#D9FDD3",
        incomingGradient = "#F1F6FB",
        outgoingGradient = "#BCEEB6",
        homeBackground = "#ECE5DD",
        wallpaperStyle = RoomWallpaperStyles.LIGHT,
    ),
    ThemePreset(
        title = "Полярная ночь",
        themeMode = "Dark",
        accent = "#88C0D0",
        topBarBg = "#2E3440",
        topBarText = "#ECEFF4",
        composerBg = "#3B4252",
        serviceBubble = "#434C5E",
        serviceText = "#E5E9F0",
        incomingBubble = "#3B4252",
        outgoingBubble = "#4C566A",
        incomingGradient = "#4B566A",
        outgoingGradient = "#6A7690",
        homeBackground = "#2B303B",
        wallpaperStyle = RoomWallpaperStyles.DARK,
    ),
    ThemePreset(
        title = "Закат",
        themeMode = "Light",
        accent = "#E65B4A",
        topBarBg = "#FFE6D8",
        topBarText = "#3F1C16",
        composerBg = "#FFF3EB",
        serviceBubble = "#FFD8C5",
        serviceText = "#4A261A",
        incomingBubble = "#FFFFFF",
        outgoingBubble = "#FFD1BA",
        incomingGradient = "#FFF0E6",
        outgoingGradient = "#FFB38F",
        homeBackground = "#FFF1E8",
        wallpaperStyle = RoomWallpaperStyles.LIGHT,
    ),
    ThemePreset(
        title = "Графит",
        themeMode = "Dark",
        accent = "#8FA3BF",
        topBarBg = "#202833",
        topBarText = "#F6F8FB",
        composerBg = "#1B222C",
        serviceBubble = "#2A3442",
        serviceText = "#D7DEE8",
        incomingBubble = "#273241",
        outgoingBubble = "#39506A",
        incomingGradient = "#38485C",
        outgoingGradient = "#56769A",
        homeBackground = "#11161D",
        wallpaperStyle = RoomWallpaperStyles.DARK,
    ),
    ThemePreset(
        title = "Лотос",
        themeMode = "Light",
        accent = "#C3478A",
        topBarBg = "#FFEAF4",
        topBarText = "#4E1836",
        composerBg = "#FFF7FB",
        serviceBubble = "#F9DDEB",
        serviceText = "#5B2140",
        incomingBubble = "#FFFFFF",
        outgoingBubble = "#FFD8EA",
        incomingGradient = "#FFEFF8",
        outgoingGradient = "#FFB7D9",
        homeBackground = "#FFF4FA",
        wallpaperStyle = RoomWallpaperStyles.LIGHT,
    ),
    ThemePreset(
        title = "Море",
        themeMode = "Dark",
        accent = "#3BC9DB",
        topBarBg = "#0F2A34",
        topBarText = "#E6FBFF",
        composerBg = "#12323E",
        serviceBubble = "#1A4453",
        serviceText = "#D6F4FA",
        incomingBubble = "#153946",
        outgoingBubble = "#1F5C70",
        incomingGradient = "#1F5364",
        outgoingGradient = "#3392B0",
        homeBackground = "#0B1D23",
        wallpaperStyle = RoomWallpaperStyles.DARK,
    ),
)

private val quickPalette = listOf(
    "#FFFFFF", "#000000", "#0A84FF", "#5E5CE6", "#30D158", "#FF9F0A", "#FF453A", "#64D2FF",
    "#BF5AF2", "#32D74B", "#FFD60A", "#FF375F", "#8E8E93", "#1C1C1E", "#E5E5EA", "#2C2C2E",
)

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun CustomizationView(
    state: CustomizationState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedCategory by remember { mutableStateOf(CustomizationCategory.Themes) }
    var selectedPreviewTarget by remember { mutableStateOf(PreviewColorTarget.Accent) }
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf<ColorPickerTarget>(ColorPickerTarget.PreviewElement) }

    val currentTheme = remember(state) {
        SetkaThemeFile(
            themeMode = state.themeMode,
            accentColorHex = state.accentColorHex,
            uiScale = state.uiScale,
            messageScale = state.messageScale,
            bubbleRadiusDp = state.bubbleRadiusDp,
            bubbleWidthPercent = state.bubbleWidthPercent,
            timelineOverlayOpacityPercent = state.timelineOverlayOpacityPercent,
            composerBackgroundOpacityPercent = state.composerBackgroundOpacityPercent,
            wallpaperBlurDp = state.wallpaperBlurDp,
            showEncryptionStatus = state.showEncryptionStatus,
            topBarBackgroundColorHex = state.topBarBackgroundColorHex,
            topBarTextColorHex = state.topBarTextColorHex,
            composerBackgroundColorHex = state.composerBackgroundColorHex,
            serviceBubbleColorHex = state.serviceBubbleColorHex,
            serviceTextColorHex = state.serviceTextColorHex,
            incomingBubbleColorHex = state.incomingBubbleColorHex,
            outgoingBubbleColorHex = state.outgoingBubbleColorHex,
            incomingBubbleGradientToColorHex = state.incomingBubbleGradientToColorHex,
            outgoingBubbleGradientToColorHex = state.outgoingBubbleGradientToColorHex,
            homeBackgroundColorHex = state.homeBackgroundColorHex,
            homeBackgroundImageUri = state.homeBackgroundImageUri,
            defaultRoomWallpaperStyle = state.defaultRoomWallpaperStyle,
            enableChatAnimations = state.enableChatAnimations,
            enableBlurEffects = state.enableBlurEffects,
            callAudioBackgroundStyle = state.callAudioBackgroundStyle,
            callPreferEarpieceByDefault = state.callPreferEarpieceByDefault,
            callProximitySensorEnabled = state.callProximitySensorEnabled,
            initialTimelineItemCount = state.initialTimelineItemCount,
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = encodeThemeFile(currentTheme)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@runCatching
            decodeThemeFile(content)?.let { theme ->
                state.eventSink(CustomizationEvents.ImportTheme(theme))
            }
        }
    }

    val homeImageLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        state.eventSink(CustomizationEvents.SetHomeBackgroundImageUri(uri.toString()))
    }

    val customWallpaperLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.toCustomStyle(uri.toString())))
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_preferences_customization_title),
    ) {
        ChatPreviewCard(
            state = state,
            selectedTarget = selectedPreviewTarget,
            onSelectTarget = { selectedPreviewTarget = it },
        )
        CategorySelector(
            selected = selectedCategory,
            onSelected = { selectedCategory = it },
        )

        when (selectedCategory) {
            CustomizationCategory.Themes -> {
                ThemeModeSection(state)
                ThemePresetsSection(state)
                ColorTargetSection(
                    state = state,
                    selectedTarget = selectedPreviewTarget,
                    onTargetSelected = { selectedPreviewTarget = it },
                    onOpenPicker = {
                        colorPickerTarget = ColorPickerTarget.PreviewElement
                        showColorPicker = true
                    },
                    onResetTarget = { state.eventSink(selectedPreviewTarget.toResetEvent()) },
                )
            }

            CustomizationCategory.Chat -> {
                AdvancedChatAppearanceSection(state)
            }

            CustomizationCategory.Calls -> {
                CallsAppearanceSection(state)
            }

            CustomizationCategory.Wallpapers -> {
                WallpaperSection(
                    state = state,
                    onPickCustomWallpaper = { customWallpaperLauncher.launch(arrayOf("image/*")) },
                    onPickHomeBackground = { homeImageLauncher.launch(arrayOf("image/*")) },
                    onPickHomeBackgroundColor = {
                        colorPickerTarget = ColorPickerTarget.HomeBackground
                        showColorPicker = true
                    },
                    onPickChatWallpaperColor = {
                        colorPickerTarget = ColorPickerTarget.ChatWallpaper
                        showColorPicker = true
                    },
                )
            }

            CustomizationCategory.TextSize -> MessageSizeSection(state)
            CustomizationCategory.Optimization -> OptimizationSection(state)
            CustomizationCategory.ImportExport -> {
                ImportExportSection(
                    onExport = { exportLauncher.launch("setka-theme-${System.currentTimeMillis()}.setkathemes") },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onReset = { state.eventSink(CustomizationEvents.ResetToDefault) },
                )
            }
        }
    }

    ColorPickerDialog(
        show = showColorPicker,
        type = ColorPickerType.Classic(showAlphaBar = false),
        onDismissRequest = { showColorPicker = false },
        onPickedColor = { color ->
            when (colorPickerTarget) {
                ColorPickerTarget.PreviewElement -> state.eventSink(selectedPreviewTarget.toSetEvent(color.toHexColorString()))
                ColorPickerTarget.HomeBackground -> state.eventSink(CustomizationEvents.SetHomeBackgroundColorHex(color.toHexColorString()))
                ColorPickerTarget.ChatWallpaper -> state.eventSink(
                    CustomizationEvents.SetDefaultRoomWallpaperStyle(
                        RoomWallpaperStyles.toColorStyle(color.toHexColorString())
                    )
                )
            }
            showColorPicker = false
        },
    )
}

@Composable
private fun CategorySelector(
    selected: CustomizationCategory,
    onSelected: (CustomizationCategory) -> Unit,
) {
    PreferenceCategory(title = "Категории") {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CustomizationCategory.entries) { category ->
                ChoiceTile(
                    modifier = Modifier.widthIn(min = 130.dp),
                    text = category.title,
                    selected = selected == category,
                    onClick = { onSelected(category) },
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSection(state: CustomizationState) {
    PreferenceCategory(title = "Режим темы") {
        SectionHint("Авто — следует системной теме устройства.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeButton(title = "Авто", mode = "System", state = state, current = state.themeMode)
            ThemeButton(title = "Светлая", mode = "Light", state = state, current = state.themeMode)
            ThemeButton(title = "Тёмная", mode = "Dark", state = state, current = state.themeMode)
        }
        SwitchPreference(
            title = "Ночной режим",
            checked = state.themeMode == "Dark",
            onCheckedChange = { checked ->
                state.eventSink(CustomizationEvents.SetThemeMode(if (checked) "Dark" else "Light"))
            },
        )
    }
}

@Composable
private fun RowScope.ThemeButton(
    title: String,
    mode: String,
    state: CustomizationState,
    current: String?,
) {
    ChoiceTile(
        modifier = Modifier.weight(1f),
        text = title,
        selected = (current ?: "System") == mode,
        onClick = { state.eventSink(CustomizationEvents.SetThemeMode(mode)) },
    )
}

@Composable
private fun ThemePresetsSection(state: CustomizationState) {
    PreferenceCategory(title = "Пресеты цветов") {
        SectionHint("Готовые сочетания меняют интерфейс целиком, не только акцент.")
        themePresets.forEach { preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(color = parseSetkaColorOrNull(preset.accent) ?: ElementTheme.colors.bgActionPrimaryRest)
                    Text(modifier = Modifier.padding(start = 10.dp), text = preset.title)
                }
                Button(
                    onClick = { applyPreset(state, preset) },
                    text = "Применить",
                )
            }
        }
    }
}

private fun applyPreset(state: CustomizationState, preset: ThemePreset) {
    state.eventSink(CustomizationEvents.SetThemeMode(preset.themeMode))
    state.eventSink(CustomizationEvents.SetAccentColorHex(preset.accent))
    state.eventSink(CustomizationEvents.SetTopBarBackgroundColorHex(preset.topBarBg))
    state.eventSink(CustomizationEvents.SetTopBarTextColorHex(preset.topBarText))
    state.eventSink(CustomizationEvents.SetComposerBackgroundColorHex(preset.composerBg))
    state.eventSink(CustomizationEvents.SetServiceBubbleColorHex(preset.serviceBubble))
    state.eventSink(CustomizationEvents.SetServiceTextColorHex(preset.serviceText))
    state.eventSink(CustomizationEvents.SetIncomingBubbleColorHex(preset.incomingBubble))
    state.eventSink(CustomizationEvents.SetOutgoingBubbleColorHex(preset.outgoingBubble))
    state.eventSink(CustomizationEvents.SetIncomingBubbleGradientToColorHex(preset.incomingGradient))
    state.eventSink(CustomizationEvents.SetOutgoingBubbleGradientToColorHex(preset.outgoingGradient))
    state.eventSink(CustomizationEvents.SetHomeBackgroundColorHex(preset.homeBackground))
    state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(preset.wallpaperStyle))
}

@Composable
private fun ColorTargetSection(
    state: CustomizationState,
    selectedTarget: PreviewColorTarget,
    onTargetSelected: (PreviewColorTarget) -> Unit,
    onOpenPicker: () -> Unit,
    onResetTarget: () -> Unit,
) {
    var showStudio by remember { mutableStateOf(false) }
    PreferenceCategory(title = "Редактор цветов") {
        SectionHint("Откройте отдельный экран, чтобы менять цвета по группам на большом превью.")
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = { showStudio = true },
            text = "Открыть студию цветов",
        )
    }

    if (showStudio) {
        ColorStudioView(
            state = state,
            selectedTarget = selectedTarget,
            onTargetSelected = onTargetSelected,
            onOpenPicker = onOpenPicker,
            onResetTarget = onResetTarget,
            onClose = { showStudio = false },
        )
    }
}

@Composable
private fun ColorStudioView(
    state: CustomizationState,
    selectedTarget: PreviewColorTarget,
    onTargetSelected: (PreviewColorTarget) -> Unit,
    onOpenPicker: () -> Unit,
    onResetTarget: () -> Unit,
    onClose: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(StudioCategory.Chat) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgCanvasDefault)
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Студия цветов",
                        style = ElementTheme.typography.fontHeadingSmMedium,
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_close),
                        )
                    }
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.35f),
                                            ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.15f),
                                            ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.35f),
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(8.dp),
                        ) {
                            ChatPreviewCard(
                                state = state,
                                selectedTarget = selectedTarget,
                                onSelectTarget = onTargetSelected,
                            )
                        }
                    }
                    item {
                        PreferenceCategory(title = "Категории") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(StudioCategory.entries) { category ->
                                    ChoiceTile(
                                        modifier = Modifier.widthIn(min = 120.dp),
                                        text = category.title,
                                        selected = category == selectedCategory,
                                        onClick = { selectedCategory = category },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        TargetGroup(
                            title = selectedCategory.title,
                            targets = selectedCategory.targets,
                            state = state,
                            selectedTarget = selectedTarget,
                            onTargetSelected = onTargetSelected,
                            onOpenPicker = onOpenPicker,
                            onResetTarget = onResetTarget,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetGroup(
    title: String,
    targets: List<PreviewColorTarget>,
    state: CustomizationState,
    selectedTarget: PreviewColorTarget,
    onTargetSelected: (PreviewColorTarget) -> Unit,
    onOpenPicker: () -> Unit,
    onResetTarget: () -> Unit,
) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        text = title,
        style = ElementTheme.typography.fontBodyMdMedium,
    )
    targets.forEach { target ->
        val isSelected = target == selectedTarget
        val color = target.resolveColor(state) ?: ElementTheme.colors.bgSubtleSecondary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(
                    color = if (isSelected) ElementTheme.colors.bgSubtlePrimary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onTargetSelected(target) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorSwatch(color = color)
            Text(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
                    .padding(end = 8.dp),
                text = target.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                modifier = Modifier.widthIn(min = 64.dp),
                onClick = {
                    onTargetSelected(target)
                    onOpenPicker()
                },
                text = "Цвет",
                size = ButtonSize.Small,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier = Modifier.widthIn(min = 64.dp),
                onClick = {
                    onTargetSelected(target)
                    onResetTarget()
                },
                text = "Сброс",
                destructive = true,
                size = ButtonSize.Small,
            )
        }
    }
    SectionHint("Быстрые оттенки")
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 8,
    ) {
        quickPalette.forEach { hex ->
            val color = parseSetkaColorOrNull(hex) ?: return@forEach
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color = color, shape = CircleShape)
                    .clickable { state.eventSink(selectedTarget.toSetEvent(hex)) },
            )
        }
    }
}

@Composable
private fun AdvancedChatAppearanceSection(state: CustomizationState) {
    PreferenceCategory(title = "Дополнительно") {
        SectionHint("Тонкая настройка параметров отображения чата.")
        SliderPreference(
            title = "Скругление bubble",
            valueText = "${state.bubbleRadiusDp}dp",
            value = state.bubbleRadiusDp.toFloat(),
            range = 4f..28f,
            onValueChange = { state.eventSink(CustomizationEvents.SetBubbleRadius(it.toInt())) },
        )
        SliderPreference(
            title = "Ширина bubble",
            valueText = "${state.bubbleWidthPercent}%",
            value = state.bubbleWidthPercent.toFloat(),
            range = 55f..95f,
            onValueChange = { state.eventSink(CustomizationEvents.SetBubbleWidthPercent(it.toInt())) },
        )
        SliderPreference(
            title = "Затемнение таймлайна",
            valueText = "${state.timelineOverlayOpacityPercent}%",
            value = state.timelineOverlayOpacityPercent.toFloat(),
            range = 0f..80f,
            onValueChange = { state.eventSink(CustomizationEvents.SetTimelineOverlayOpacityPercent(it.toInt())) },
        )
        SliderPreference(
            title = "Непрозрачность композера",
            valueText = "${state.composerBackgroundOpacityPercent}%",
            value = state.composerBackgroundOpacityPercent.toFloat(),
            range = 0f..100f,
            onValueChange = { state.eventSink(CustomizationEvents.SetComposerBackgroundOpacityPercent(it.toInt())) },
        )
        SwitchPreference(
            title = "Прозрачный композер",
            checked = state.composerBackgroundOpacityPercent == 0,
            onCheckedChange = { isTransparent ->
                state.eventSink(
                    CustomizationEvents.SetComposerBackgroundOpacityPercent(
                        if (isTransparent) 0 else 92
                    )
                )
            },
        )
        SwitchPreference(
            title = "Показывать статус шифрования",
            checked = state.showEncryptionStatus,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetShowEncryptionStatus(it)) },
        )
    }
}

@Composable
private fun WallpaperSection(
    state: CustomizationState,
    onPickCustomWallpaper: () -> Unit,
    onPickHomeBackground: () -> Unit,
    onPickHomeBackgroundColor: () -> Unit,
    onPickChatWallpaperColor: () -> Unit,
) {
    PreferenceCategory(title = "Обои чата") {
        SectionHint("Авто использует светлые/тёмные обои по текущей теме.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceTile(
                modifier = Modifier.weight(1f),
                text = "Авто",
                selected = state.defaultRoomWallpaperStyle == null || state.defaultRoomWallpaperStyle == RoomWallpaperStyles.AUTO,
                onClick = { state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.AUTO)) },
            )
            ChoiceTile(
                modifier = Modifier.weight(1f),
                text = "Светлые",
                selected = state.defaultRoomWallpaperStyle == RoomWallpaperStyles.LIGHT,
                onClick = { state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.LIGHT)) },
            )
            ChoiceTile(
                modifier = Modifier.weight(1f),
                text = "Тёмные",
                selected = state.defaultRoomWallpaperStyle == RoomWallpaperStyles.DARK,
                onClick = { state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.DARK)) },
            )
        }
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            onClick = onPickCustomWallpaper,
            text = "Загрузить кастомные обои",
        )
        ColorLine(
            title = "Или выбрать цвет фона чата",
            color = parseSetkaColorOrNull(RoomWallpaperStyles.customColor(state.defaultRoomWallpaperStyle)),
            onPick = onPickChatWallpaperColor,
            onReset = { state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.AUTO)) },
        )
        if (RoomWallpaperStyles.customUri(state.defaultRoomWallpaperStyle) != null) {
            Button(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                onClick = { state.eventSink(CustomizationEvents.SetDefaultRoomWallpaperStyle(RoomWallpaperStyles.AUTO)) },
                text = "Убрать кастомные обои",
                destructive = true,
            )
        }
        SliderPreference(
            title = stringResource(R.string.screen_preferences_customization_wallpaper_blur),
            valueText = "${state.wallpaperBlurDp}dp",
            value = state.wallpaperBlurDp.toFloat(),
            range = 0f..20f,
            onValueChange = { state.eventSink(CustomizationEvents.SetWallpaperBlurDp(it.toInt())) },
        )
        SwitchPreference(
            title = "Включить blur-эффекты",
            checked = state.enableBlurEffects,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetEnableBlurEffects(it)) },
        )
    }

    PreferenceCategory(title = "Фон главного экрана") {
        ColorLine(
            title = "Цвет фона",
            color = parseSetkaColorOrNull(state.homeBackgroundColorHex),
            onPick = onPickHomeBackgroundColor,
            onReset = { state.eventSink(CustomizationEvents.SetHomeBackgroundColorHex(null)) },
        )
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = onPickHomeBackground,
            text = if (state.homeBackgroundImageUri.isNullOrBlank()) "Выбрать картинку фона" else "Сменить картинку фона",
        )
        if (!state.homeBackgroundImageUri.isNullOrBlank()) {
            Button(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                onClick = { state.eventSink(CustomizationEvents.SetHomeBackgroundImageUri(null)) },
                text = "Удалить картинку",
                destructive = true,
            )
        }
    }
}

@Composable
private fun MessageSizeSection(state: CustomizationState) {
    PreferenceCategory(title = "Размер и масштаб") {
        SectionHint("Сначала настройте размер сообщений, затем общий масштаб UI.")
        SliderPreference(
            title = stringResource(R.string.screen_preferences_customization_message_size),
            valueText = "${(state.messageScale * 100).toInt()}%",
            value = state.messageScale,
            range = 0.85f..1.40f,
            onValueChange = { state.eventSink(CustomizationEvents.SetMessageScale(it)) },
        )
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = { state.eventSink(CustomizationEvents.SetMessageScale(1.0f)) },
            text = "Системный размер текста",
        )
        SliderPreference(
            title = stringResource(R.string.screen_preferences_customization_ui_scale),
            valueText = "${(state.uiScale * 100).toInt()}%",
            value = state.uiScale,
            range = 0.90f..1.15f,
            onValueChange = { state.eventSink(CustomizationEvents.SetUiScale(it)) },
        )
    }
}

@Composable
private fun OptimizationSection(state: CustomizationState) {
    PreferenceCategory(title = "Пресеты оптимизации") {
        SectionHint("Пресет меняет анимации, blur и количество сообщений при открытии.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptimizationPreset.entries.forEach { preset ->
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        state.eventSink(CustomizationEvents.SetEnableChatAnimations(preset.enableAnimations))
                        state.eventSink(CustomizationEvents.SetEnableBlurEffects(preset.enableBlur))
                        state.eventSink(CustomizationEvents.SetInitialTimelineItemCount(preset.initialMessages))
                    },
                    text = preset.title,
                )
            }
        }
        SwitchPreference(
            title = "Анимации в чате",
            checked = state.enableChatAnimations,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetEnableChatAnimations(it)) },
        )
        SwitchPreference(
            title = "Blur-эффекты",
            checked = state.enableBlurEffects,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetEnableBlurEffects(it)) },
        )
        SliderPreference(
            title = "Сообщений при открытии",
            valueText = state.initialTimelineItemCount.toString(),
            value = state.initialTimelineItemCount.toFloat(),
            range = 5f..50f,
            onValueChange = { state.eventSink(CustomizationEvents.SetInitialTimelineItemCount(it.toInt())) },
        )
    }
}

@Composable
private fun ImportExportSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.screen_preferences_customization_import_export_category)) {
        SectionHint("Экспортирует/импортирует полный набор кастомных параметров.")
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = onExport,
            text = stringResource(R.string.screen_preferences_customization_export),
        )
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            onClick = onImport,
            text = stringResource(R.string.screen_preferences_customization_import),
        )
        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = onReset,
            text = stringResource(CommonStrings.action_reset),
            destructive = true,
        )
    }
}

@Composable
private fun ChatPreviewCard(
    state: CustomizationState,
    selectedTarget: PreviewColorTarget,
    onSelectTarget: (PreviewColorTarget) -> Unit,
) {
    val topBarColor = parseSetkaColorOrNull(state.topBarBackgroundColorHex) ?: ElementTheme.colors.bgCanvasDefault
    val topBarTextColor = parseSetkaColorOrNull(state.topBarTextColorHex) ?: ElementTheme.colors.textPrimary
    val serviceBubbleColor = parseSetkaColorOrNull(state.serviceBubbleColorHex) ?: ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.42f)
    val serviceTextColor = parseSetkaColorOrNull(state.serviceTextColorHex) ?: ElementTheme.colors.textSecondary
    val incomingBubbleFrom = parseSetkaColorOrNull(state.incomingBubbleColorHex) ?: ElementTheme.colors.bgSubtleSecondary
    val outgoingBubbleFrom = parseSetkaColorOrNull(state.outgoingBubbleColorHex) ?: ElementTheme.colors.bgActionPrimaryRest
    val incomingBubbleTo = parseSetkaColorOrNull(state.incomingBubbleGradientToColorHex)
    val outgoingBubbleTo = parseSetkaColorOrNull(state.outgoingBubbleGradientToColorHex)
    val composerBase = parseSetkaColorOrNull(state.composerBackgroundColorHex) ?: ElementTheme.colors.bgSubtleSecondary
    val composerColor = composerBase.copy(alpha = (state.composerBackgroundOpacityPercent / 100f).coerceIn(0f, 1f))
    val accentColor = parseSetkaColorOrNull(state.accentColorHex) ?: ElementTheme.colors.textActionAccent
    val isLightWallpaper = when (state.defaultRoomWallpaperStyle) {
        RoomWallpaperStyles.LIGHT -> true
        RoomWallpaperStyles.DARK -> false
        else -> ElementTheme.isLightTheme
    }
    val chatWallpaperColor = parseSetkaColorOrNull(RoomWallpaperStyles.customColor(state.defaultRoomWallpaperStyle))
    val wallpaperGradient = if (isLightWallpaper) {
        Brush.verticalGradient(listOf(Color(0xFFF6F8FC), Color(0xFFE7ECF8)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF161B25), Color(0xFF0E1219)))
    }
    val bubbleWidth = (state.bubbleWidthPercent / 100f).coerceIn(0.5f, 0.95f)
    val previewTextStyle = ElementTheme.typography.fontBodyMdRegular.copy(
        fontSize = ElementTheme.typography.fontBodyMdRegular.fontSize * state.messageScale
    )
    val customWallpaperUri = RoomWallpaperStyles.customUri(state.defaultRoomWallpaperStyle)

    val previewBackgroundModifier = if (chatWallpaperColor != null) {
        Modifier.background(chatWallpaperColor, RoundedCornerShape(16.dp))
    } else {
        Modifier.background(wallpaperGradient, RoundedCornerShape(16.dp))
    }

    PreferenceCategory(title = "Превью") {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(420.dp)
                .then(previewBackgroundModifier),
        ) {
            if (customWallpaperUri != null) {
                AsyncImage(
                    model = customWallpaperUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color.Transparent),
                )
            }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTarget(PreviewColorTarget.TopBar) }
                        .background(topBarColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Kozlik",
                        color = topBarTextColor,
                        modifier = Modifier.clickable { onSelectTarget(PreviewColorTarget.TopBarText) },
                    )
                    Text(
                        text = if (selectedTarget == PreviewColorTarget.TopBar || selectedTarget == PreviewColorTarget.TopBarText) "●" else "",
                        color = topBarTextColor,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = serviceBubbleColor,
                        modifier = Modifier.clickable { onSelectTarget(PreviewColorTarget.ServiceBubble) },
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { onSelectTarget(PreviewColorTarget.ServiceText) },
                            text = "Сегодня • Сервисное событие",
                            color = serviceTextColor,
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(bubbleWidth),
                        color = incomingBubbleFrom,
                        shape = RoundedCornerShape(state.bubbleRadiusDp.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    brush = incomingBubbleTo?.let { Brush.verticalGradient(listOf(incomingBubbleFrom, it)) }
                                        ?: Brush.verticalGradient(listOf(incomingBubbleFrom, incomingBubbleFrom))
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .clickable { onSelectTarget(PreviewColorTarget.IncomingBubble) },
                        ) {
                            Text(text = "Пример сообщения с ссылкой:", style = previewTextStyle)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "https://setka-matrix.ru",
                                color = accentColor,
                                style = previewTextStyle,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { onSelectTarget(PreviewColorTarget.Accent) },
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Градиент",
                                style = ElementTheme.typography.fontBodyXsRegular,
                                color = ElementTheme.colors.textSecondary,
                                modifier = Modifier.clickable { onSelectTarget(PreviewColorTarget.IncomingGradient) },
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth((bubbleWidth - 0.06f).coerceAtLeast(0.5f)).align(Alignment.End),
                        color = outgoingBubbleFrom,
                        shape = RoundedCornerShape(state.bubbleRadiusDp.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    brush = outgoingBubbleTo?.let { Brush.verticalGradient(listOf(outgoingBubbleFrom, it)) }
                                        ?: Brush.verticalGradient(listOf(outgoingBubbleFrom, outgoingBubbleFrom))
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .clickable { onSelectTarget(PreviewColorTarget.OutgoingBubble) },
                        ) {
                            Text(
                                text = "Второе сообщение для проверки размеров",
                                style = previewTextStyle,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Градиент",
                                style = ElementTheme.typography.fontBodyXsRegular,
                                color = ElementTheme.colors.textSecondary,
                                modifier = Modifier.clickable { onSelectTarget(PreviewColorTarget.OutgoingGradient) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTarget(PreviewColorTarget.Composer) }
                        .background(composerColor)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(text = "Введите сообщение...", color = ElementTheme.colors.textSecondary, style = previewTextStyle)
                }
            }
        }
    }
}

@Composable
private fun CallsAppearanceSection(state: CustomizationState) {
    PreferenceCategory(title = "Звонки") {
        SectionHint("Настройки применяются к экрану звонка и embedded Element Call.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceTile(
                modifier = Modifier.weight(1f),
                text = "Градиент",
                selected = state.callAudioBackgroundStyle == CallAudioBackgroundStyles.GRADIENT,
                onClick = { state.eventSink(CustomizationEvents.SetCallAudioBackgroundStyle(CallAudioBackgroundStyles.GRADIENT)) },
            )
            ChoiceTile(
                modifier = Modifier.weight(1f),
                text = "Обои чата",
                selected = state.callAudioBackgroundStyle == CallAudioBackgroundStyles.WALLPAPER,
                onClick = { state.eventSink(CustomizationEvents.SetCallAudioBackgroundStyle(CallAudioBackgroundStyles.WALLPAPER)) },
            )
        }
        SwitchPreference(
            title = "Размытие панелей звонка",
            checked = state.enableBlurEffects,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetEnableBlurEffects(it)) },
        )
        SwitchPreference(
            title = "Анимации в звонке",
            checked = state.enableChatAnimations,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetEnableChatAnimations(it)) },
        )
        SwitchPreference(
            title = "Тихая связь по умолчанию",
            checked = state.callPreferEarpieceByDefault,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetCallPreferEarpieceByDefault(it)) },
        )
        SwitchPreference(
            title = "Датчик приближения",
            checked = state.callProximitySensorEnabled,
            onCheckedChange = { state.eventSink(CustomizationEvents.SetCallProximitySensorEnabled(it)) },
        )
        SectionHint("Кнопка громкой/тихой связи размещена в нижней панели управления звонком.")
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        text = text,
        color = ElementTheme.colors.textSecondary,
        style = ElementTheme.typography.fontBodySmRegular,
    )
}

@Composable
private fun ChoiceTile(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val background = if (selected) {
        ElementTheme.colors.bgSubtlePrimary
    } else {
        ElementTheme.colors.bgSubtleSecondary
    }
    val textColor = if (selected) {
        ElementTheme.colors.textPrimary
    } else {
        ElementTheme.colors.textSecondary
    }
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = shape,
        color = background,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            text = if (selected) "• $text" else text,
            color = textColor,
            style = ElementTheme.typography.fontBodySmRegular,
        )
    }
}

@Composable
private fun SliderPreference(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = title)
        Text(
            text = valueText,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Slider(
            modifier = Modifier.padding(top = 8.dp),
            value = value,
            valueRange = range,
            onValueChange = onValueChange,
        )
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorLine(
    title: String,
    color: Color?,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(color = color ?: ElementTheme.colors.bgSubtleSecondary)
            Text(modifier = Modifier.padding(start = 10.dp), text = title)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, text = "Set")
            Button(onClick = onReset, text = "Reset", destructive = true)
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color = color, shape = CircleShape),
    )
}

private fun PreviewColorTarget.resolveColor(state: CustomizationState): Color? {
    return when (this) {
        PreviewColorTarget.TopBar -> parseSetkaColorOrNull(state.topBarBackgroundColorHex)
        PreviewColorTarget.TopBarText -> parseSetkaColorOrNull(state.topBarTextColorHex)
        PreviewColorTarget.Accent -> parseSetkaColorOrNull(state.accentColorHex)
        PreviewColorTarget.Composer -> parseSetkaColorOrNull(state.composerBackgroundColorHex)
        PreviewColorTarget.ServiceBubble -> parseSetkaColorOrNull(state.serviceBubbleColorHex)
        PreviewColorTarget.ServiceText -> parseSetkaColorOrNull(state.serviceTextColorHex)
        PreviewColorTarget.IncomingBubble -> parseSetkaColorOrNull(state.incomingBubbleColorHex)
        PreviewColorTarget.OutgoingBubble -> parseSetkaColorOrNull(state.outgoingBubbleColorHex)
        PreviewColorTarget.IncomingGradient -> parseSetkaColorOrNull(state.incomingBubbleGradientToColorHex)
        PreviewColorTarget.OutgoingGradient -> parseSetkaColorOrNull(state.outgoingBubbleGradientToColorHex)
    }
}

private fun PreviewColorTarget.toSetEvent(colorHex: String?): CustomizationEvents {
    return when (this) {
        PreviewColorTarget.TopBar -> CustomizationEvents.SetTopBarBackgroundColorHex(colorHex)
        PreviewColorTarget.TopBarText -> CustomizationEvents.SetTopBarTextColorHex(colorHex)
        PreviewColorTarget.Accent -> CustomizationEvents.SetAccentColorHex(colorHex)
        PreviewColorTarget.Composer -> CustomizationEvents.SetComposerBackgroundColorHex(colorHex)
        PreviewColorTarget.ServiceBubble -> CustomizationEvents.SetServiceBubbleColorHex(colorHex)
        PreviewColorTarget.ServiceText -> CustomizationEvents.SetServiceTextColorHex(colorHex)
        PreviewColorTarget.IncomingBubble -> CustomizationEvents.SetIncomingBubbleColorHex(colorHex)
        PreviewColorTarget.OutgoingBubble -> CustomizationEvents.SetOutgoingBubbleColorHex(colorHex)
        PreviewColorTarget.IncomingGradient -> CustomizationEvents.SetIncomingBubbleGradientToColorHex(colorHex)
        PreviewColorTarget.OutgoingGradient -> CustomizationEvents.SetOutgoingBubbleGradientToColorHex(colorHex)
    }
}

private fun PreviewColorTarget.toResetEvent(): CustomizationEvents = toSetEvent(null)

private fun Color.toHexColorString(): String {
    val argb = toArgb()
    return String.format("#%02X%02X%02X", AndroidColor.red(argb), AndroidColor.green(argb), AndroidColor.blue(argb))
}

private fun encodeThemeFile(theme: SetkaThemeFile): String {
    val json = JSONObject()
    json.put("version", theme.version)
    json.put("themeMode", theme.themeMode)
    json.put("accentColorHex", theme.accentColorHex)
    json.put("uiScale", theme.uiScale)
    json.put("messageScale", theme.messageScale)
    json.put("bubbleRadiusDp", theme.bubbleRadiusDp)
    json.put("bubbleWidthPercent", theme.bubbleWidthPercent)
    json.put("timelineOverlayOpacityPercent", theme.timelineOverlayOpacityPercent)
    json.put("composerBackgroundOpacityPercent", theme.composerBackgroundOpacityPercent)
    json.put("wallpaperBlurDp", theme.wallpaperBlurDp)
    json.put("showEncryptionStatus", theme.showEncryptionStatus)
    json.put("topBarBackgroundColorHex", theme.topBarBackgroundColorHex)
    json.put("topBarTextColorHex", theme.topBarTextColorHex)
    json.put("composerBackgroundColorHex", theme.composerBackgroundColorHex)
    json.put("serviceBubbleColorHex", theme.serviceBubbleColorHex)
    json.put("serviceTextColorHex", theme.serviceTextColorHex)
    json.put("incomingBubbleColorHex", theme.incomingBubbleColorHex)
    json.put("outgoingBubbleColorHex", theme.outgoingBubbleColorHex)
    json.put("incomingBubbleGradientToColorHex", theme.incomingBubbleGradientToColorHex)
    json.put("outgoingBubbleGradientToColorHex", theme.outgoingBubbleGradientToColorHex)
    json.put("homeBackgroundColorHex", theme.homeBackgroundColorHex)
    json.put("homeBackgroundImageUri", theme.homeBackgroundImageUri)
    json.put("defaultRoomWallpaperStyle", theme.defaultRoomWallpaperStyle)
    json.put("enableChatAnimations", theme.enableChatAnimations)
    json.put("enableBlurEffects", theme.enableBlurEffects)
    json.put("callAudioBackgroundStyle", theme.callAudioBackgroundStyle)
    json.put("callPreferEarpieceByDefault", theme.callPreferEarpieceByDefault)
    json.put("callProximitySensorEnabled", theme.callProximitySensorEnabled)
    json.put("initialTimelineItemCount", theme.initialTimelineItemCount)
    return json.toString(2)
}

private fun decodeThemeFile(content: String): SetkaThemeFile? {
    return runCatching {
        val json = JSONObject(content)
        SetkaThemeFile(
            version = json.optInt("version", 6),
            themeMode = json.optString("themeMode").takeIf { it.isNotBlank() && it != "null" },
            accentColorHex = json.optString("accentColorHex").takeIf { it.isNotBlank() && it != "null" },
            uiScale = json.optDouble("uiScale", 1.0).toFloat(),
            messageScale = json.optDouble("messageScale", 1.0).toFloat(),
            bubbleRadiusDp = json.optInt("bubbleRadiusDp", 12),
            bubbleWidthPercent = json.optInt("bubbleWidthPercent", 78),
            timelineOverlayOpacityPercent = json.optInt("timelineOverlayOpacityPercent", 22),
            composerBackgroundOpacityPercent = json.optInt("composerBackgroundOpacityPercent", 0),
            wallpaperBlurDp = json.optInt("wallpaperBlurDp", 0),
            showEncryptionStatus = json.optBoolean("showEncryptionStatus", false),
            topBarBackgroundColorHex = json.optString("topBarBackgroundColorHex").takeIf { it.isNotBlank() && it != "null" },
            topBarTextColorHex = json.optString("topBarTextColorHex").takeIf { it.isNotBlank() && it != "null" },
            composerBackgroundColorHex = json.optString("composerBackgroundColorHex").takeIf { it.isNotBlank() && it != "null" },
            serviceBubbleColorHex = json.optString("serviceBubbleColorHex").takeIf { it.isNotBlank() && it != "null" },
            serviceTextColorHex = json.optString("serviceTextColorHex").takeIf { it.isNotBlank() && it != "null" },
            incomingBubbleColorHex = json.optString("incomingBubbleColorHex").takeIf { it.isNotBlank() && it != "null" },
            outgoingBubbleColorHex = json.optString("outgoingBubbleColorHex").takeIf { it.isNotBlank() && it != "null" },
            incomingBubbleGradientToColorHex = json.optString("incomingBubbleGradientToColorHex").takeIf { it.isNotBlank() && it != "null" },
            outgoingBubbleGradientToColorHex = json.optString("outgoingBubbleGradientToColorHex").takeIf { it.isNotBlank() && it != "null" },
            homeBackgroundColorHex = json.optString("homeBackgroundColorHex").takeIf { it.isNotBlank() && it != "null" },
            homeBackgroundImageUri = json.optString("homeBackgroundImageUri").takeIf { it.isNotBlank() && it != "null" },
            defaultRoomWallpaperStyle = json.optString("defaultRoomWallpaperStyle").takeIf { it.isNotBlank() && it != "null" },
            enableChatAnimations = json.optBoolean("enableChatAnimations", true),
            enableBlurEffects = json.optBoolean("enableBlurEffects", true),
            callAudioBackgroundStyle = json.optString("callAudioBackgroundStyle", CallAudioBackgroundStyles.GRADIENT),
            callPreferEarpieceByDefault = json.optBoolean("callPreferEarpieceByDefault", true),
            callProximitySensorEnabled = json.optBoolean("callProximitySensorEnabled", true),
            initialTimelineItemCount = json.optInt("initialTimelineItemCount", 12),
        )
    }.getOrNull()
}
