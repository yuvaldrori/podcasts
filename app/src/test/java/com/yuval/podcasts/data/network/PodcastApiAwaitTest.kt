package com.yuval.podcasts.data.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.junit.Test

class PodcastApiAwaitTest {

    @Test
    fun await_whenCancelledBeforeResponseArrives_closesResponseToAvoidLeak() = runTest {
        val call = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        every { call.enqueue(capture(callbackSlot)) } returns Unit

        val response = mockk<Response>(relaxed = true)

        // UNDISPATCHED runs await() up to its suspension point synchronously, registering
        // the OkHttp callback before we cancel.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            call.await()
        }

        // Cancel the awaiting coroutine, then have the network deliver the response late.
        job.cancel()
        callbackSlot.captured.onResponse(call, response)

        // The late response is unreachable by the (cancelled) caller, so await() must close
        // it itself rather than leaking the OkHttp connection.
        verify { response.close() }
    }
}
