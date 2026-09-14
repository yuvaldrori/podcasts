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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration

object HtmlUtils {
    private val TAG_REGEX = Regex("<[^>]*>", RegexOption.DOT_MATCHES_ALL)
    private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);|&#x([0-9a-fA-F]+);")
    private val WHITESPACE_REGEX = Regex("[\\s\\u00A0]+")

    /**
     * Strips HTML tags and decodes HTML entities into clean plain text.
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        if (!html.contains('<') && !html.contains('&')) return html.trim()

        val withoutTags = html.replace(TAG_REGEX, " ")
        val decodedNamed = withoutTags
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

        val decodedNumeric = NUMERIC_ENTITY_REGEX.replace(decodedNamed) { matchResult ->
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

        return WHITESPACE_REGEX.replace(decodedNumeric, " ").trim()
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
