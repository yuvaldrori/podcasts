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

    @Test
    fun stripHtml_handlesNullAndEmpty() {
        assertEquals("", HtmlUtils.stripHtml(null))
        assertEquals("", HtmlUtils.stripHtml(""))
        assertEquals("", HtmlUtils.stripHtml("   "))
    }

    @Test
    fun stripHtml_returnsPlainTextUnmodified() {
        val plain = "This is a simple plain text description."
        assertEquals(plain, HtmlUtils.stripHtml(plain))
    }

    @Test
    fun stripHtml_stripsHtmlTagsAndDecodesEntities() {
        val html = """<p dir="rtl">מגזין סוף השבוע של The-Marker<br>גיא רולניק עם מבט מעמיק</p>
<p dir="rtl">&nbsp;</p>"""
        val result = HtmlUtils.stripHtml(html)
        assertTrue(!result.contains("<p"))
        assertTrue(!result.contains("<br"))
        assertTrue(!result.contains("&nbsp;"))
        assertTrue(result.contains("מגזין סוף השבוע של The-Marker"))
        assertTrue(result.contains("גיא רולניק עם מבט מעמיק"))
    }

    @Test
    fun stripHtml_stripsNestedTagsAndAttributes() {
        val html = """<p dir="rtl" data-pm-slice="0 0 []"><strong>"הולכים לבחירות" פודאסט חדש</strong></p>"""
        val result = HtmlUtils.stripHtml(html)
        assertEquals("\"הולכים לבחירות\" פודאסט חדש", result)
    }

    @Test
    fun stripHtml_decodesStandardEntities() {
        val html = "Rock &amp; Roll &gt; Pop &lt; Jazz &quot;Music&quot;"
        val result = HtmlUtils.stripHtml(html)
        assertEquals("Rock & Roll > Pop < Jazz \"Music\"", result)
    }

    @Test
    fun stripHtml_handlesHorizontalRuleAndAnchors() {
        val html = "יוסי מרשק מארח בכל פרק אנשים שמעוררים בו תקווה.<hr /><p> Hosted on Acast. See <a target=\"_blank\" href=\"https://acast.com/privacy\">acast.com/privacy</a> for more information.</p>"
        val result = HtmlUtils.stripHtml(html)
        assertTrue(!result.contains("<hr"))
        assertTrue(!result.contains("<a"))
        assertTrue(!result.contains("</a>"))
        assertTrue(!result.contains("<p>"))
        assertTrue(result.contains("יוסי מרשק מארח בכל פרק אנשים שמעוררים בו תקווה."))
        assertTrue(result.contains("Hosted on Acast. See acast.com/privacy for more information."))
    }

    @Test
    fun stripHtml_handlesUnicodeNonBreakingSpacesAndLineBreaks() {
        val html = "<p dir=\"rtl\">״אבא תרחם״, מבקשת דניאלה לונדון דקל מאביה. \u00A0<br>״בתי״, עונה לה ירון לונדון.\u00A0</p>\n<p dir=\"rtl\">בכל פרק שאלה.</p>"
        val result = HtmlUtils.stripHtml(html)
        assertTrue(!result.contains("<p"))
        assertTrue(!result.contains("<br"))
        assertTrue(!result.contains("\u00A0"))
        assertTrue(result.contains("״אבא תרחם״, מבקשת דניאלה לונדון דקל מאביה."))
        assertTrue(result.contains("״בתי״, עונה לה ירון לונדון."))
        assertTrue(result.contains("בכל פרק שאלה."))
    }

    @Test
    fun stripHtml_handlesConsecutiveNonBreakingSpacesAndTrailingParagraphs() {
        val html = "<p dir=\"rtl\">סיפור אחד ביום. ראשון עד חמישי, אצלכם על הבוקר.&nbsp;</p>\n<p>&nbsp;&nbsp;</p>"
        val result = HtmlUtils.stripHtml(html)
        assertTrue(!result.contains("<p"))
        assertTrue(!result.contains("&nbsp;"))
        assertEquals("סיפור אחד ביום. ראשון עד חמישי, אצלכם על הבוקר.", result)
    }

    @Test
    fun stripHtml_handlesStandardParagraphsWithoutAttributes() {
        val html = "<p>\"השבוע\" - הפודקאסט השבועי של \"הארץ\". מדי יום שלישי וחמישי יארח ליאור קודנר את מיטב המומחים.</p>"
        val result = HtmlUtils.stripHtml(html)
        assertEquals("\"השבוע\" - הפודקאסט השבועי של \"הארץ\". מדי יום שלישי וחמישי יארח ליאור קודנר את מיטב המומחים.", result)
    }
}
