package com.yuval.podcasts.ui.utils

import android.content.Context
import com.yuval.podcasts.R
import io.mockk.every
import io.mockk.mockk
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
    }

    @Test
    fun `asString with StringResource and args returns formatted string from context`() {
        val context = mockk<Context>()
        // Mocking getString with varargs is tricky in MockK, but we can mock the specific call
        every { context.getString(any(), any(), any()) } returns "Version 1.0"
        
        val uiText = UiText.StringResource(R.string.app_version, "1.0", "today")
        assertEquals("Version 1.0", uiText.asString(context))
    }
}
