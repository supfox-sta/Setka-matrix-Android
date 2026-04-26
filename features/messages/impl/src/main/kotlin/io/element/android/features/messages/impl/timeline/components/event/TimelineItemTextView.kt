/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.text.SpannedString
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContentProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.utils.containsOnlyEmojis
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.designsystem.components.LINK_TAG
import io.element.android.libraries.designsystem.components.linkify
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.jsoup.nodes.Document
import io.element.android.features.messages.impl.setka.LocalSetkaSharedPackImporter
import io.element.android.features.messages.impl.setka.parseSetkaSharedPackLink
import io.element.android.features.messages.impl.setka.SetkaSharedPackLink
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

// Element Web allows broader emoji shortcodes than our original sanitizer (notably '-').
// If we don't match them, the HTML renderer may drop <img> tags and the message bubble becomes empty.
private val setkaEmoticonTokenRegex = Regex(":[A-Za-z0-9_\\-]{1,64}:")
private val setkaEmoticonTokenExactRegex = Regex("^:[A-Za-z0-9_\\-]{1,64}:$")

@Composable
fun TimelineItemTextView(
    content: TimelineItemTextBasedContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    val emojiOnly = content.formattedBody.toString() == content.body &&
        content.body.replace(" ", "").containsOnlyEmojis()
    val textStyle = when {
        emojiOnly -> ElementTheme.typography.fontHeadingXlRegular
        else -> ElementTheme.typography.fontBodyLgRegular
    }
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides textStyle
    ) {
        val sharedPackLink = remember(content.plainText) { parseSetkaSharedPackLink(content.plainText) }
        if (sharedPackLink != null && content.plainText.trim() == sharedPackLink.url) {
            SetkaSharedPackCard(link = sharedPackLink, modifier = modifier)
            return@CompositionLocalProvider
        }

        val emoticons = remember(content.htmlDocument, content.rawHtmlBody) {
            val rawEmoticons = extractSetkaEmoticons(content.rawHtmlBody)
            if (rawEmoticons.isNotEmpty()) {
                rawEmoticons
            } else {
                extractSetkaEmoticons(content.htmlDocument)
            }
        }
        if (emoticons.isEmpty()) {
            val resolved = getTextWithResolvedMentions(content)
            val text = resolved.takeUnless { it.toString().isBlank() } ?: content.body
            Box(modifier.semantics { contentDescription = content.plainText }) {
                EditorStyledText(
                    text = text,
                    onLinkClickedListener = onLinkClick,
                    onLinkLongClickedListener = onLinkLongClick,
                    style = ElementRichTextEditorStyle.textStyle(),
                    onTextLayout = ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange),
                    releaseOnDetach = false,
                )
            }
        } else {
            val plainText = content.plainText
            val baseText = plainText.takeIf {
                it.isNotBlank() && emoticons.keys.any { token -> plainText.contains(token) }
            }
                ?: emoticons.keys.joinToString(separator = " ")
            val emojiFontSize = LocalTextStyle.current.fontSize.takeIf(TextUnit::isSpecified) ?: 18.sp
            val shouldRenderAsEmojiOnly = remember(baseText) { isSetkaEmojiOnlyText(baseText) }
            val (annotatedText, inlineContent) = remember(baseText, emoticons, emojiFontSize, shouldRenderAsEmojiOnly) {
                if (shouldRenderAsEmojiOnly) {
                    AnnotatedString("") to emptyMap<String, InlineTextContent>().toImmutableMap()
                } else {
                    buildSetkaInlineEmojiText(
                        text = baseText,
                        emoticons = emoticons,
                        emojiSize = emojiFontSize * 1.15f,
                    )
                }
            }
            if (shouldRenderAsEmojiOnly || inlineContent.isEmpty() || annotatedText.text.isBlank()) {
                SetkaInlineEmojiOnlyRow(
                    emoticons = emoticons,
                    emojiSize = emojiFontSize * 1.15f,
                    modifier = modifier.semantics { contentDescription = content.plainText },
                    onContentLayoutChange = onContentLayoutChange,
                )
            } else {
                SetkaInlineEmojiText(
                    text = annotatedText,
                    inlineContent = inlineContent,
                    modifier = modifier.semantics { contentDescription = content.plainText },
                    onContentLayoutChange = onContentLayoutChange,
                )
            }
        }
    }
}

@Composable
private fun SetkaSharedPackCard(
    link: SetkaSharedPackLink,
    modifier: Modifier = Modifier,
) {
    val importer = LocalSetkaSharedPackImporter.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ElementTheme.colors.bgSubtleSecondary),
            contentAlignment = Alignment.Center,
        ) {
            if (link.iconMxcUrl != null) {
                AsyncImage(
                    model = MediaRequestData(MediaSource(link.iconMxcUrl), MediaRequestData.Kind.Content),
                    contentDescription = link.name,
                    modifier = Modifier.size(46.dp),
                )
            } else {
                Text(
                    text = "S",
                    style = ElementTheme.typography.fontHeadingMdBold,
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.name ?: stringResource(io.element.android.features.messages.impl.R.string.screen_room_setka_shared_pack_title),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
            Text(
                text = stringResource(
                    if (link.kind == "emoji") {
                        io.element.android.features.messages.impl.R.string.screen_room_setka_shared_pack_kind_emoji
                    } else {
                        io.element.android.features.messages.impl.R.string.screen_room_setka_shared_pack_kind_sticker
                    }
                ),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        FilledTonalButton(onClick = { importer(link.token) }) {
            Text(text = stringResource(io.element.android.features.messages.impl.R.string.screen_room_setka_shared_pack_add))
        }
    }
}

@Composable
private fun SetkaInlineEmojiText(
    text: AnnotatedString,
    inlineContent: ImmutableMap<String, InlineTextContent>,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val measureLastLine = ContentAvoidingLayout.measureLastTextLine(onContentLayoutChange = onContentLayoutChange)
    // Do not linkify here: the linkifier rebuilds the AnnotatedString and can drop inline placeholders
    // which makes custom-emoji-only messages render as empty bubbles on some builds.
    val linkified = text
    var layoutResult: TextLayoutResult? = remember { null }
    Text(
        text = linkified,
        inlineContent = inlineContent,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset: Offset ->
                        // Best-effort: open the link on long press as well (no Link type available here).
                        val result = layoutResult ?: return@detectTapGestures
                        val position = result.getOffsetForPosition(offset)
                        val annotations = linkified.getStringAnnotations(LINK_TAG, position, position)
                        annotations.firstOrNull()?.let { uriHandler.openUri(it.item) }
                    },
                    onTap = { offset: Offset ->
                        val result = layoutResult ?: return@detectTapGestures
                        val position = result.getOffsetForPosition(offset)
                        val annotations = linkified.getStringAnnotations(LINK_TAG, position, position)
                        annotations.firstOrNull()?.let { uriHandler.openUri(it.item) }
                    },
                )
            },
        onTextLayout = { result ->
            layoutResult = result
            measureLastLine(result)
        },
        style = LocalTextStyle.current,
        color = Color.Unspecified,
    )
}

@Composable
private fun SetkaInlineEmojiOnlyRow(
    emoticons: Map<String, String>,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    val sizeDp = with(LocalDensity.current) { emojiSize.toDp() }
    FlowRow(
        modifier = modifier.onSizeChanged { size ->
            onContentLayoutChange(
                ContentAvoidingLayoutData(
                    contentWidth = size.width,
                    contentHeight = size.height,
                    nonOverlappingContentWidth = size.width,
                    nonOverlappingContentHeight = size.height,
                )
            )
        },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        emoticons.forEach { (token, src) ->
            SetkaInlineEmojiImage(
                token = token,
                src = src,
                modifier = Modifier.size(sizeDp),
            )
        }
    }
}

@Composable
private fun SetkaInlineEmojiImage(
    token: String,
    src: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = MediaRequestData(MediaSource(src), MediaRequestData.Kind.Content),
        contentDescription = token,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun extractSetkaEmoticons(document: Document?): Map<String, String> {
    if (document == null) return emptyMap()
    extractSetkaEmoticons(document.outerHtml())
        .takeIf { it.isNotEmpty() }
        ?.let { return it }
    val map = LinkedHashMap<String, String>()
    // Element Web / other clients sometimes omit `alt` or store the shortcode in `title`
    // or even in the `data-mx-emoticon` attribute. Be permissive to avoid rendering empty messages.
    val nodes = document.select("img")
    for (node in nodes) {
        fun normalizeUrl(value: String): String {
            // Some servers/clients wrap attributes across lines; keep the URL usable.
            return value.trim().replace(Regex("\\s+"), "")
        }

        val dataMxUrl = normalizeUrl(node.attr("data-mx-url"))
        val dataMxSrc = normalizeUrl(node.attr("data-mx-src"))
        val rawSrc = normalizeUrl(node.attr("src"))
        // Prefer a canonical `mxc://` URL if any attribute provides it, otherwise fall back to the
        // first non-blank URL. This mirrors Element Web's behavior and avoids "empty" emoji bubbles.
        val src = sequenceOf(dataMxUrl, dataMxSrc, rawSrc)
            .firstOrNull { it.startsWith("mxc://", ignoreCase = true) }
            ?: sequenceOf(dataMxUrl, dataMxSrc, rawSrc).firstOrNull { it.isNotBlank() }.orEmpty()
        if (src.isBlank()) continue

        val alt = node.attr("alt").trim().takeIf { it.isNotBlank() }
        val title = node.attr("title").trim().takeIf { it.isNotBlank() }
        val dataMxEmoticon = node.attr("data-mx-emoticon").trim()
            .takeIf { it.isNotBlank() && it != "true" && it != "1" }

        val tokenCandidate = (alt ?: title ?: dataMxEmoticon)?.trim()
        val token = tokenCandidate
            ?.takeIf { setkaEmoticonTokenExactRegex.matches(it) }
            ?: if (node.hasAttr("data-mx-emoticon")) {
                // Some clients use data-mx-emoticon without a usable alt/title. Still render it so the
                // message doesn't collapse into an empty bubble.
                ":emoji_${map.size + 1}:"
            } else {
                continue
            }

        // Keep only emoticon-like images (either explicitly marked, or with a valid :token:).
        if (!node.hasAttr("data-mx-emoticon") && !setkaEmoticonTokenExactRegex.matches(token)) continue

        map[token] = src
    }
    return map
}

private fun extractSetkaEmoticons(htmlBody: String?): Map<String, String> {
    if (htmlBody.isNullOrBlank()) return emptyMap()
    val normalizedHtmlBody = htmlBody.replace("\\/", "/")

    val result = LinkedHashMap<String, String>()
    val tagRegex = Regex(
        pattern = "<img[^>]*data-mx-emoticon[^>]*>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val attrRegex = Regex(
        pattern = """([A-Za-z0-9:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    fun normalizeUrl(value: String): String = value.trim().replace(Regex("\\s+"), "")

    for (tagMatch in tagRegex.findAll(normalizedHtmlBody)) {
        val attributes = LinkedHashMap<String, String>()
        for (attributeMatch in attrRegex.findAll(tagMatch.value)) {
            val key = attributeMatch.groupValues[1].lowercase()
            val value = listOf(
                attributeMatch.groupValues[2],
                attributeMatch.groupValues[3],
                attributeMatch.groupValues[4],
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            attributes[key] = value
        }

        val src = sequenceOf(
            normalizeUrl(attributes["data-mx-url"].orEmpty()),
            normalizeUrl(attributes["data-mx-src"].orEmpty()),
            normalizeUrl(attributes["src"].orEmpty()),
        ).firstOrNull { it.startsWith("mxc://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?: sequenceOf(
                normalizeUrl(attributes["data-mx-url"].orEmpty()),
                normalizeUrl(attributes["data-mx-src"].orEmpty()),
                normalizeUrl(attributes["src"].orEmpty()),
            ).firstOrNull { it.isNotBlank() }
            ?: continue

        val rawToken = attributes["alt"]
            ?.takeIf { it.isNotBlank() }
            ?: attributes["title"]?.takeIf { it.isNotBlank() }
            ?: attributes["data-mx-emoticon"]?.takeIf { it.isNotBlank() && it != "true" && it != "1" }

        val token = rawToken
            ?.trim()
            ?.takeIf { setkaEmoticonTokenExactRegex.matches(it) }
            ?: ":emoji_${result.size + 1}:"

        result[token] = src
    }

    return result
}

private fun buildSetkaInlineEmojiText(
    text: String,
    emoticons: Map<String, String>,
    emojiSize: TextUnit,
): Pair<AnnotatedString, ImmutableMap<String, InlineTextContent>> {
    if (text.isBlank() || emoticons.isEmpty()) {
        return AnnotatedString(text) to emptyMap<String, InlineTextContent>().toImmutableMap()
    }

    val builder = AnnotatedString.Builder()
    val inlineMap = LinkedHashMap<String, InlineTextContent>()
    var cursor = 0
    var idx = 0

    for (match in setkaEmoticonTokenRegex.findAll(text)) {
        val token = match.value
        val src = emoticons[token] ?: continue
        val start = match.range.first
        val endExclusive = match.range.last + 1
        if (start > cursor) {
            builder.append(text.substring(cursor, start))
        }

        val key = "setka_emoji_$idx"
        idx += 1
        builder.appendInlineContent(key, alternateText = token)
        inlineMap[key] = InlineTextContent(
            placeholder = Placeholder(
                width = emojiSize,
                height = emojiSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            )
        ) {
            SetkaInlineEmojiImage(
                token = token,
                src = src,
                modifier = Modifier.fillMaxSize(),
            )
        }
        cursor = endExclusive
    }

    if (cursor < text.length) {
        builder.append(text.substring(cursor))
    }

    return builder.toAnnotatedString() to inlineMap.toImmutableMap()
}

private fun isSetkaEmojiOnlyText(text: String): Boolean {
    if (text.isBlank()) return true
    val withoutTokens = setkaEmoticonTokenRegex.replace(text, "")
    return withoutTokens.all { it.isWhitespace() || it in setOf(':', '-', '_', '.', ',', '!', '?', '(', ')') }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun getTextWithResolvedMentions(content: TimelineItemTextBasedContent): CharSequence {
    val mentionSpanUpdater = LocalMentionSpanUpdater.current
    val bodyWithResolvedMentions = mentionSpanUpdater.rememberMentionSpans(content.formattedBody)
    return SpannedString.valueOf(bodyWithResolvedMentions)
}

@PreviewsDayNight
@Composable
internal fun TimelineItemTextViewPreview(
    @PreviewParameter(TimelineItemTextBasedContentProvider::class) content: TimelineItemTextBasedContent
) = ElementPreview {
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the first '?' (url: github.com/element-hq/element-x-android/README?)?.")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlAndNestedParenthesisPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the '(ME)' ((url: github.com/element-hq/element-x-android/READ(ME)))!")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}
