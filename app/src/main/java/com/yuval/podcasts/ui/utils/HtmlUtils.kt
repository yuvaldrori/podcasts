package com.yuval.podcasts.ui.utils

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object HtmlUtils {
    private val ANY_TAG_REGEX = Regex("<[^>]*>", RegexOption.DOT_MATCHES_ALL)
    private val KNOWN_HTML_TAG_REGEX = Regex(
        "</?(?:p|div|span|strong|b|em|i|u|a|br|hr|h[1-6]|ul|ol|li|img|blockquote|code|pre|table|tr|td|th|tbody|thead|font|center)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);|&#x([0-9a-fA-F]+);")
    private val WHITESPACE_REGEX = Regex("[\\s\\u00A0]+")
    private const val MAX_ENTITY_DECODE_PASSES = 3

    /**
     * Strips all HTML tags and decodes HTML entities into clean single-spaced plain text.
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        if (!html.contains('<') && !html.contains('&')) return html.trim()

        val withoutTags = html.replace(ANY_TAG_REGEX, " ")
        val decoded = decodeEntities(withoutTags)
        return WHITESPACE_REGEX.replace(decoded, " ").trim()
    }

    /**
     * Sanitizes RSS metadata text (titles, podcast descriptions, chapter titles):
     * strips known HTML tags while preserving non-HTML angle-bracket text (e.g. `<Special>`),
     * and decodes nested/double-encoded XML/HTML entities.
     */
    fun sanitizeRssText(input: String): String {
        if (input.isBlank()) return ""
        var text = stripKnownHtmlTags(input)

        var passes = 0
        while (text.contains('&') && text.contains(';') && passes < MAX_ENTITY_DECODE_PASSES) {
            val prev = text
            text = decodeEntities(text)
            text = stripKnownHtmlTags(text)
            if (prev == text) break
            passes++
        }

        return text.replace('\u00A0', ' ').trim()
    }

    private fun stripKnownHtmlTags(text: String): String =
        if (text.contains('<') && text.contains('>')) {
            text.replace(KNOWN_HTML_TAG_REGEX, "")
        } else {
            text
        }

    private fun decodeEntities(input: String): String {
        val decodedNamed = input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        if (!decodedNamed.contains("&#")) return decodedNamed

        return NUMERIC_ENTITY_REGEX.replace(decodedNamed) { matchResult ->
            val decimalStr = matchResult.groupValues[1]
            val hexStr = matchResult.groupValues[2]
            try {
                val codePoint = if (decimalStr.isNotEmpty()) {
                    decimalStr.toInt(10)
                } else {
                    hexStr.toInt(16)
                }
                if (Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    matchResult.value
                }
            } catch (_: Exception) {
                matchResult.value
            }
        }
    }
}

/**
 * Converts a [Spanned] (e.g. from Html.fromHtml) into a Compose [AnnotatedString].
 */
fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append(this@toAnnotatedString.toString())
    getSpans(0, length, Any::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        when (span) {
            is StyleSpan -> when (span.style) {
                android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is URLSpan -> {
                val url = span.url
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                    addStyle(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline), start, end)
                    addStringAnnotation("URL", url, start, end)
                }
            }
        }
    }
}
