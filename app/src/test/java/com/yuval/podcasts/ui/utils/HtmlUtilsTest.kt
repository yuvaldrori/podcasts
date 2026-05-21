package com.yuval.podcasts.ui.utils

import android.text.Html
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HtmlUtilsTest {

    @Test
    fun toAnnotatedString_validatesUrlSchemes() {
        val html = """
            <a href="https://example.com">Safe Link</a>
            <a href="http://example.com">Insecure but allowed</a>
            <a href="mailto:test@example.com">Mailto allowed</a>
            <a href="javascript:alert('evil')">Javascript BLOCKED</a>
            <a href="content://malicious">Content BLOCKED</a>
        """.trimIndent()
        
        val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        val annotated = spanned.toAnnotatedString()
        
        val urlAnnotations = annotated.getStringAnnotations("URL", 0, annotated.length)
        
        // Should only have 3 annotations (https, http, mailto)
        assertEquals(3, urlAnnotations.size)
        
        assertTrue(urlAnnotations.any { it.item == "https://example.com" })
        assertTrue(urlAnnotations.any { it.item == "http://example.com" })
        assertTrue(urlAnnotations.any { it.item == "mailto:test@example.com" })
        
        // Javascript and Content should not be present
        assertTrue(urlAnnotations.none { it.item.startsWith("javascript") })
        assertTrue(urlAnnotations.none { it.item.startsWith("content") })
    }
}
