package com.xiaoxiong.weather.ui.settings

import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyLinkifyTest {
    private val style = SpanStyle()

    @Test
    fun trailingPunctuationAfterUrl_isPreservedAsPlainText() {
        val raw = "详情见 https://example.com。） 谢谢"
        val (annotated, links) = linkifyText(raw, style)

        // 整段原文（含 URL 后的标点）必须原样保留，标点不能被吞掉
        assertEquals(raw, annotated.text)
        assertEquals(1, links.size)
        assertEquals("https://example.com", links.single().url)
        val start = raw.indexOf("https://example.com")
        assertEquals(start, links.single().start)
        assertEquals(start + "https://example.com".length, links.single().end)
    }

    @Test
    fun emailIsLinkedWithMailto() {
        val raw = "联系 a@b.com 反馈"
        val (annotated, links) = linkifyText(raw, style)

        assertEquals(raw, annotated.text)
        assertEquals(1, links.size)
        assertEquals("mailto:a@b.com", links.single().url)
    }
}
