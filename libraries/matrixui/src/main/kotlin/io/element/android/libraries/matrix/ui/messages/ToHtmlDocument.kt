/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.messages

import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import io.element.android.wysiwyg.utils.HtmlToDomParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/**
 * Converts the HTML string [FormattedBody.body] to a [Document] by parsing it.
 * If the message is not formatted or the format is not [MessageFormat.HTML] we return `null`.
 *
 * This will also make sure mentions are prefixed with `@`.
 *
 * @param permalinkParser the parser to use to parse the mentions.
 * @param prefix if not null, the prefix will be inserted at the beginning of the message.
 */
fun FormattedBody.toHtmlDocument(
    permalinkParser: PermalinkParser,
    prefix: String? = null,
): Document? {
    // Most clients (Element Web included) send HTML formatted bodies with:
    //   "format": "org.matrix.custom.html"
    //
    // For robustness, if the SDK flags the format as UNKNOWN but the body clearly contains
    // Matrix custom-emoji HTML, still parse it with a restrictive JSoup safelist so the user
    // doesn't see empty message bubbles.
    val formattedBody = when (format) {
        MessageFormat.HTML -> body
        MessageFormat.UNKNOWN -> body.takeIf { raw ->
            raw.contains("data-mx-emoticon", ignoreCase = true) || raw.contains("<img", ignoreCase = true)
        }
    } ?: return null

    // Trim whitespace at the end to avoid having wrong rendering of the message.
    // We don't trim the start in case it's used as indentation.
    val trimmed = formattedBody.trimEnd()
    val input = if (prefix != null) "$prefix $trimmed" else trimmed

    var dom = HtmlToDomParser.document(input)

    // HtmlToDomParser is strict and may keep <img> but drop its attributes/protocols (notably
    // mxc://), which makes custom emoji extraction fail and can collapse the message into an
    // empty bubble. When we detect custom emoji markup, ensure at least one <img> keeps a usable
    // source URL, otherwise fall back to a JSoup Cleaner with an explicit allowlist.
    if (input.contains("data-mx-emoticon", ignoreCase = true)) {
        val imgs = dom.getElementsByTag("img")
        val hasUsableImg = imgs.any { img ->
            fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), "")
            val candidate = sequenceOf(
                normalize(img.attr("data-mx-url")),
                normalize(img.attr("data-mx-src")),
                normalize(img.attr("src")),
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            candidate.startsWith("mxc://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true) ||
                candidate.startsWith("http://", ignoreCase = true)
        }
        if (imgs.isEmpty() || !hasUsableImg) {
            dom = parseWithJsoupSafelist(input)
        }
    }

    // Prepend `@` to mentions
    fixMentions(dom, permalinkParser)

    return dom
}

private fun parseWithJsoupSafelist(html: String): Document {
    // Start from a relaxed safelist and expand it to support Matrix custom emoji.
    val safelist = Safelist.relaxed()
        .addTags("img")
        .addAttributes(
            "img",
            "src",
            "alt",
            "title",
            "width",
            "height",
            "data-mx-emoticon",
            "data-mx-src",
            "data-mx-url",
        )
        // Matrix media URLs for emoji are usually mxc://...
        .addProtocols("img", "src", "mxc", "https", "http")
        // Allow matrix.to permalinks and matrix scheme on links.
        .addProtocols("a", "href", "https", "http", "matrix")

    val cleaned = Cleaner(safelist).clean(Jsoup.parseBodyFragment(html))
    return cleaned
}

private fun fixMentions(
    dom: Document,
    permalinkParser: PermalinkParser,
) {
    val links = dom.getElementsByTag("a")
    links.forEach {
        if (it.hasAttr("href")) {
            val link = permalinkParser.parse(it.attr("href"))
            if (link is PermalinkData.UserLink && !it.text().startsWith("@")) {
                it.prependText("@")
            }
        }
    }
}
