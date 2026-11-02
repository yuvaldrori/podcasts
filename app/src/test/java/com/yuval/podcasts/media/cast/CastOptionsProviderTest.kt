package com.yuval.podcasts.media.cast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.cast.CastMediaControlIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CastOptionsProviderTest {

    @Test
    fun getCastOptions_disablesCastMediaSessionAndEnablesRemoteToLocal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = CastOptionsProvider()

        val options = provider.getCastOptions(context)

        assertEquals(
            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            options.receiverApplicationId
        )
        assertTrue(options.stopReceiverApplicationWhenEndingSession)
        assertTrue(options.showSystemOutputSwitcherOnCastIconClick)
        val mediaOptions = options.castMediaOptions
        assertNotNull(mediaOptions)
        assertFalse(mediaOptions.mediaSessionEnabled)
        assertNull(mediaOptions.notificationOptions)
    }
}
