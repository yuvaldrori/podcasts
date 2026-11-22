package com.yuval.podcasts.ui.utils

import android.content.Context
import com.yuval.podcasts.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {

    @Test
    fun `asString with DynamicString returns raw value`() {
        val uiText = UiText.DynamicString("Hello")
        val context = mockk<Context>()

        assertEquals("Hello", uiText.asString(context))
    }

    @Test
    fun `asString with StringResource returns formatted string from context`() {
        val context = mockk<Context>()
        every { context.getString(R.string.app_name) } returns "Podcasts"

        val uiText = UiText.StringResource(R.string.app_name)
        assertEquals("Podcasts", uiText.asString(context))
        verify(exactly = 1) { context.getString(R.string.app_name) }
    }

    @Test
    fun `asString with StringResource and args returns formatted string from context`() {
        val context = mockk<Context>()
        every { context.getString(R.string.app_version, "1.0", "today") } returns "Version 1.0 (today)"

        val uiText = UiText.StringResource(R.string.app_version, "1.0", "today")
        assertEquals("Version 1.0 (today)", uiText.asString(context))
        verify(exactly = 1) { context.getString(R.string.app_version, "1.0", "today") }
    }

    @Test
    fun `StringResource supports structural equality and hashCode`() {
        val a = UiText.StringResource(R.string.app_version, "1.0", "today")
        val b = UiText.StringResource(R.string.app_version, "1.0", "today")
        val c = UiText.StringResource(R.string.app_version, "2.0", "today")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        org.junit.Assert.assertNotEquals(a, c)
    }
}
