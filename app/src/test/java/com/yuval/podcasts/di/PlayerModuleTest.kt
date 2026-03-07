package com.yuval.podcasts.di

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerModuleTest {

    @Test
    fun testProvideExoPlayer_returnsNewInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player1 = PlayerModule.provideExoPlayer(context)
        val player2 = PlayerModule.provideExoPlayer(context)
        
        // This test will prove that ExoPlayer is NOT a singleton anymore!
        assertNotSame(player1, player2)
    }
}
