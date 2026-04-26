/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.messages

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import io.element.android.libraries.matrix.test.permalink.FakePermalinkParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToHtmlDocumentTest {
    @Test
    fun `toHtmlDocument - returns null if format is not HTML`() {
        val body = FormattedBody(
            format = MessageFormat.UNKNOWN,
            body = "Hello world"
        )

        val document = body.toHtmlDocument(permalinkParser = FakePermalinkParser())

        assertThat(document).isNull()
    }

    @Test
    fun `toHtmlDocument - returns a Document if the format is HTML`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "<p>Hello world</p>"
        )

        val document = body.toHtmlDocument(permalinkParser = FakePermalinkParser())
        assertThat(document).isNotNull()
        assertThat(document?.text()).isEqualTo("Hello world")
    }

    @Test
    fun `toHtmlDocument - returns a Document with a prefix if provided`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "<p>Hello world</p>"
        )

        val document = body.toHtmlDocument(
            permalinkParser = FakePermalinkParser(),
            prefix = "@Jorge:"
        )
        assertThat(document).isNotNull()
        assertThat(document?.text()).isEqualTo("@Jorge: Hello world")
    }

    @Test
    fun `toHtmlDocument - if a mention is found without an '@' prefix, it will be added`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "Hey <a href='https://matrix.to/#/@alice:matrix.org'>Alice</a>!"
        )

        val document = body.toHtmlDocument(permalinkParser = object : PermalinkParser {
            override fun parse(uriString: String): PermalinkData {
                return PermalinkData.UserLink(UserId("@alice:matrix.org"))
            }
        })
        assertThat(document?.text()).isEqualTo("Hey @Alice!")
    }

    @Test
    fun `toHtmlDocument - if a mention is found with an '@' prefix, nothing will be done`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "Hey <a href='https://matrix.to/#/@alice:matrix.org'>@Alice</a>!"
        )

        val document = body.toHtmlDocument(permalinkParser = object : PermalinkParser {
            override fun parse(uriString: String): PermalinkData {
                return PermalinkData.UserLink(UserId("@alice:matrix.org"))
            }
        })
        assertThat(document?.text()).isEqualTo("Hey @Alice!")
    }

    @Test
    fun `toHtmlDocument - if a link is not a mention, nothing will be done for it`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "Hey <a href='https://matrix.org'>Alice</a>!"
        )

        val document = body.toHtmlDocument(permalinkParser = object : PermalinkParser {
            override fun parse(uriString: String): PermalinkData {
                return PermalinkData.FallbackLink(uri = Uri.parse("https://matrix.org"))
            }
        })
        assertThat(document?.text()).isEqualTo("Hey Alice!")
    }

    @Test
    fun `toHtmlDocument - preserves Matrix custom emoji img tags`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = "<img data-mx-emoticon src=\"mxc://example.org/abc\" alt=\":element_logo:\" title=\"element_logo\" width=\"50\" height=\"50\" />",
        )

        val document = body.toHtmlDocument(permalinkParser = FakePermalinkParser())
        assertThat(document).isNotNull()
        val imgs = document!!.getElementsByTag("img")
        assertThat(imgs.size).isEqualTo(1)
        assertThat(imgs.first()!!.attr("src")).contains("mxc://")
        assertThat(imgs.first()!!.hasAttr("data-mx-emoticon")).isTrue()
    }

    @Test
    fun `toHtmlDocument - preserves custom emoji when mxc src contains whitespace and line breaks`() {
        val body = FormattedBody(
            format = MessageFormat.HTML,
            body = """
                <img data-mx-emoticon
                     src="mxc://
                         example.org/
                         abcDEF123"
                     alt=":element_logo:"
                     title="element_logo"
                     width="50"
                     height="50" />
            """.trimIndent(),
        )

        val document = body.toHtmlDocument(permalinkParser = FakePermalinkParser())
        assertThat(document).isNotNull()
        val imgs = document!!.getElementsByTag("img")
        assertThat(imgs.size).isEqualTo(1)
        // The parsed document should keep the src attribute (we normalize whitespace later when rendering).
        assertThat(imgs.first()!!.hasAttr("src")).isTrue()
        assertThat(imgs.first()!!.hasAttr("data-mx-emoticon")).isTrue()
    }

    @Test
    fun `toHtmlDocument - parses custom emoji even when format is UNKNOWN`() {
        val body = FormattedBody(
            format = MessageFormat.UNKNOWN,
            body = "<img data-mx-emoticon src=\"mxc://example.org/abc\" alt=\":element_logo:\" />",
        )

        val document = body.toHtmlDocument(permalinkParser = FakePermalinkParser())
        assertThat(document).isNotNull()
        val imgs = document!!.getElementsByTag("img")
        assertThat(imgs.size).isEqualTo(1)
        assertThat(imgs.first()!!.hasAttr("data-mx-emoticon")).isTrue()
    }
}
