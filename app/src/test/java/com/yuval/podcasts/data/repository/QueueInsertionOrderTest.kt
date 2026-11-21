package com.yuval.podcasts.data.repository

import com.yuval.podcasts.data.db.entity.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueInsertionOrderTest {

    private fun ep(id: String, feed: String, pubDate: Long): Episode = Episode(
        id = id,
        podcastFeedUrl = feed,
        title = "Title-$id",
        description = "Desc",
        audioUrl = "http://example.com/$id.mp3",
        imageUrl = null,
        episodeWebLink = null,
        pubDate = pubDate,
        duration = 0L,
        downloadStatus = 0,
        localFilePath = null,
        isPlayed = false,
        lastPlayedPosition = 0L
    )

    private fun insert(
        queue: List<Episode>,
        episode: Episode,
        playingId: String? = null
    ): List<Episode> {
        val index = findQueueInsertionIndex(queue, episode, playingId)
        return queue.toMutableList().apply { add(index, episode) }
    }

    private fun assertInsertOrder(
        initial: List<Episode>,
        toAdd: Episode,
        playingId: String? = null,
        expectedIds: List<String>
    ) {
        assertEquals(expectedIds, insert(initial, toAdd, playingId).map { it.id })
    }

    @Test
    fun emptyQueue_insertsAtTop_withOrWithoutPlayingId() {
        val newEp = ep("ep1", "feedA", 1000L)
        assertInsertOrder(emptyList(), newEp, playingId = null, expectedIds = listOf("ep1"))
        assertInsertOrder(emptyList(), newEp, playingId = "otherPlayingEp", expectedIds = listOf("ep1"))
    }

    @Test
    fun noSameSubscription_insertsChronologically() {
        val ep1 = ep("ep1", "feedA", 1000L)
        val ep3 = ep("ep3", "feedC", 3000L)

        // Older than all -> top
        assertInsertOrder(listOf(ep1, ep3), ep("ep0", "feedD", 500L), expectedIds = listOf("ep0", "ep1", "ep3"))
        // Between older and newer -> middle
        assertInsertOrder(listOf(ep1, ep3), ep("ep2", "feedB", 2000L), expectedIds = listOf("ep1", "ep2", "ep3"))
        // Newer than all -> bottom
        assertInsertOrder(listOf(ep1, ep3), ep("ep4", "feedD", 4000L), expectedIds = listOf("ep1", "ep3", "ep4"))
    }

    @Test
    fun sameSubscription_multipleSequentialAdds_ordersByPubDateNewestLast() {
        var queue = listOf(ep("epB1", "feedB", 4000L))

        queue = insert(queue, ep("epA3", "feedA", 3000L))
        assertEquals(listOf("epA3", "epB1"), queue.map { it.id })

        queue = insert(queue, ep("epA1", "feedA", 1000L))
        assertEquals(listOf("epA1", "epA3", "epB1"), queue.map { it.id })

        queue = insert(queue, ep("epA2", "feedA", 2000L))
        assertEquals(listOf("epA1", "epA2", "epA3", "epB1"), queue.map { it.id })

        // Newer than epB1, but still stays grouped at the bottom of feedA.
        queue = insert(queue, ep("epA4", "feedA", 5000L))
        assertEquals(listOf("epA1", "epA2", "epA3", "epA4", "epB1"), queue.map { it.id })
    }

    @Test
    fun sameSubscriptionNonContiguous_insertsChronologicallyRelativeToSubscription() {
        val epB0 = ep("epB0", "feedB", 500L)
        val epA1 = ep("epA1", "feedA", 1000L)
        val epB1 = ep("epB1", "feedB", 2000L)
        val epA2 = ep("epA2", "feedA", 3000L)
        val epC1 = ep("epC1", "feedC", 4000L)
        val queue = listOf(epB0, epA1, epB1, epA2, epC1)

        // Older than all feedA (and older than epB0) -> before first feedA, preserving epB0
        assertInsertOrder(queue, ep("epA0", "feedA", 200L), expectedIds = listOf("epB0", "epA0", "epA1", "epB1", "epA2", "epC1"))
        // Between epA1 and epA2 -> before epA2
        assertInsertOrder(queue, ep("epAMid", "feedA", 2500L), expectedIds = listOf("epB0", "epA1", "epB1", "epAMid", "epA2", "epC1"))
        // Newer than all feedA -> right after last feedA without disturbing epB1
        assertInsertOrder(queue, ep("epA3", "feedA", 5000L), expectedIds = listOf("epB0", "epA1", "epB1", "epA2", "epA3", "epC1"))
    }

    @Test
    fun equalPubDate_preservesStableOrderForSameAndDifferentSubscriptions() {
        var queue = listOf(ep("epA1", "feedA", 2000L), ep("epC1", "feedC", 3000L))

        queue = insert(queue, ep("epA2", "feedA", 2000L))
        assertEquals(listOf("epA1", "epA2", "epC1"), queue.map { it.id })

        queue = insert(queue, ep("epB1", "feedB", 2000L))
        assertEquals(listOf("epA1", "epA2", "epB1", "epC1"), queue.map { it.id })
    }

    @Test
    fun whenPlayingAtTop_keepsOlderDifferentFeedEpisodesBelowPlayingAndOrdered() {
        var queue = listOf(ep("epB1", "feedB", 3000L), ep("epC1", "feedC", 4000L))

        queue = insert(queue, ep("epA1", "feedA", 1000L), playingId = "epB1")
        assertEquals(listOf("epB1", "epA1", "epC1"), queue.map { it.id })

        queue = insert(queue, ep("epD1", "feedD", 2000L), playingId = "epB1")
        assertEquals(listOf("epB1", "epA1", "epD1", "epC1"), queue.map { it.id })
    }

    @Test
    fun whenPlayingIsSameSubscription_keepsNewEpisodesBelowPlayingAndOrderedByDate() {
        var queue = listOf(ep("epA3", "feedA", 3000L), ep("epB1", "feedB", 4000L))

        // Newer than epB1 added first to pin the lastSameSubscriptionIndex >= playingIndex boundary.
        queue = insert(queue, ep("epA4", "feedA", 5000L), playingId = "epA3")
        assertEquals(listOf("epA3", "epA4", "epB1"), queue.map { it.id })

        queue = insert(queue, ep("epA2", "feedA", 2000L), playingId = "epA3")
        assertEquals(listOf("epA3", "epA2", "epA4", "epB1"), queue.map { it.id })

        queue = insert(queue, ep("epA1", "feedA", 1000L), playingId = "epA3")
        assertEquals(listOf("epA3", "epA1", "epA2", "epA4", "epB1"), queue.map { it.id })
    }

    @Test
    fun whenPlayingIsLastOfSameSubscriptionGroup_groupsNewerEpisodeImmediatelyAfterPlaying() {
        val queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 2000L),
            ep("epC1", "feedC", 3000L)
        )

        assertInsertOrder(
            queue,
            ep("epA3", "feedA", 5000L),
            playingId = "epA2",
            expectedIds = listOf("epA1", "epA2", "epA3", "epC1")
        )
    }

    @Test
    fun whenPlayingInMiddleOfSameSubscriptionGroup_insertsBelowPlayingInDateOrder() {
        var queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 2000L),
            ep("epA4", "feedA", 4000L),
            ep("epB1", "feedB", 5000L)
        )

        queue = insert(queue, ep("epA3", "feedA", 3000L), playingId = "epA2")
        assertEquals(listOf("epA1", "epA2", "epA3", "epA4", "epB1"), queue.map { it.id })

        queue = insert(queue, ep("epA0", "feedA", 500L), playingId = "epA2")
        assertEquals(listOf("epA1", "epA2", "epA0", "epA3", "epA4", "epB1"), queue.map { it.id })
    }

    @Test
    fun whenPlayingInMiddle_keepsOlderUnrelatedEpisodeBelowPlaying() {
        val queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epB1", "feedB", 2000L),
            ep("epC1", "feedC", 3000L)
        )

        assertInsertOrder(
            queue,
            ep("epD1", "feedD", 500L),
            playingId = "epB1",
            expectedIds = listOf("epA1", "epB1", "epD1", "epC1")
        )
    }

    @Test
    fun whenSameSubscriptionOnlyAbovePlaying_insertsChronologicallyBelowPlayingGroup() {
        val queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epB1", "feedB", 2000L),
            ep("epB2", "feedB", 3000L),
            ep("epC1", "feedC", 4000L),
            ep("epD1", "feedD", 5000L)
        )

        // Newer than everything below -> appends at bottom rather than jumping ahead of epC1/epD1.
        assertInsertOrder(
            queue,
            ep("epA2", "feedA", 6000L),
            playingId = "epB1",
            expectedIds = listOf("epA1", "epB1", "epB2", "epC1", "epD1", "epA2")
        )
        // Older than epC1 -> lands immediately after the playing group without splitting it.
        assertInsertOrder(
            queue,
            ep("epA0", "feedA", 500L),
            playingId = "epB1",
            expectedIds = listOf("epA1", "epB1", "epB2", "epA0", "epC1", "epD1")
        )
    }

    @Test
    fun whenSameSubscriptionAboveAndBelowPlaying_groupsWithSubscriptionBelowPlaying() {
        val queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epB1", "feedB", 2000L),
            ep("epB2", "feedB", 3000L),
            ep("epC1", "feedC", 3500L),
            ep("epA2", "feedA", 4000L)
        )

        // epC1 separates the playing group and epA2, proving grouping beats chronological fallback.
        assertInsertOrder(
            queue,
            ep("epA3", "feedA", 500L),
            playingId = "epB1",
            expectedIds = listOf("epA1", "epB1", "epB2", "epC1", "epA3", "epA2")
        )
    }

    @Test
    fun whenPlayingAtEnd_appendsAfterPlaying() {
        val queue = listOf(ep("epA1", "feedA", 1000L), ep("epB1", "feedB", 2000L))

        assertInsertOrder(
            queue,
            ep("epC1", "feedC", 500L),
            playingId = "epB1",
            expectedIds = listOf("epA1", "epB1", "epC1")
        )
    }

    @Test
    fun whenPlayingNotInQueue_fallsBackToNormalOrdering() {
        val queue = listOf(ep("epB1", "feedB", 2000L), ep("epC1", "feedC", 3000L))

        assertInsertOrder(
            queue,
            ep("epA1", "feedA", 1000L),
            playingId = "singleEpisodeNotInQueue",
            expectedIds = listOf("epA1", "epB1", "epC1")
        )
    }

    @Test
    fun differentSubscription_doesNotSplitExistingGroup() {
        val queue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 3000L),
            ep("epC1", "feedC", 4000L)
        )

        // Between epA1 and epA2 by date -> placed after feedA's group, not inside it.
        assertInsertOrder(queue, ep("epB1", "feedB", 2000L), expectedIds = listOf("epA1", "epA2", "epB1", "epC1"))
        // Older than the whole group -> placed before feedA's group.
        assertInsertOrder(queue, ep("epB0", "feedB", 500L), expectedIds = listOf("epB0", "epA1", "epA2", "epC1"))
    }

    @Test
    fun multipleConsecutiveGroups_insertsAtGroupBoundaryByGroupStartDate() {
        val queue1 = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 5000L),
            ep("epB1", "feedB", 2000L),
            ep("epB2", "feedB", 6000L)
        )
        assertInsertOrder(queue1, ep("epC1", "feedC", 3000L), expectedIds = listOf("epA1", "epA2", "epB1", "epB2", "epC1"))

        val queue2 = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 5000L),
            ep("epB1", "feedB", 4000L),
            ep("epB2", "feedB", 6000L)
        )
        assertInsertOrder(queue2, ep("epC1", "feedC", 3000L), expectedIds = listOf("epA1", "epA2", "epC1", "epB1", "epB2"))
    }

    @Test
    fun whenPlayingInGroup_differentSubscriptionDoesNotSplitPlayingGroup() {
        val midQueue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 3000L),
            ep("epC1", "feedC", 4000L)
        )
        assertInsertOrder(
            midQueue,
            ep("epB1", "feedB", 2000L),
            playingId = "epA1",
            expectedIds = listOf("epA1", "epA2", "epB1", "epC1")
        )

        val endQueue = listOf(
            ep("epA1", "feedA", 1000L),
            ep("epA2", "feedA", 2000L),
            ep("epA3", "feedA", 3000L)
        )
        assertInsertOrder(
            endQueue,
            ep("epB1", "feedB", 500L),
            playingId = "epA2",
            expectedIds = listOf("epA1", "epA2", "epA3", "epB1")
        )
    }

    @Test
    fun manuallyReorderedOutOfOrderGroup_doesNotSplitGroup() {
        val queue = listOf(
            ep("epA1", "feedA", 5000L),
            ep("epA2", "feedA", 1000L),
            ep("epC1", "feedC", 6000L)
        )

        assertInsertOrder(queue, ep("epB1", "feedB", 3000L), expectedIds = listOf("epB1", "epA1", "epA2", "epC1"))
    }

    @Test
    fun preservesExactRelativeOrderOfManuallyReorderedExistingQueue() {
        val initial = listOf(
            ep("epC", "feedC", 5000L),
            ep("epAHigh", "feedA", 4000L),
            ep("epB", "feedB", 1000L),
            ep("epALow", "feedA", 2000L),
            ep("epD", "feedD", 3000L)
        )
        val originalOrder = initial.map { it.id }

        var queue = insert(initial, ep("epE", "feedE", 2500L))
        assertEquals(originalOrder, queue.map { it.id }.filter { it in originalOrder })
        assertEquals(listOf("epE", "epC", "epAHigh", "epB", "epALow", "epD"), queue.map { it.id })

        queue = insert(queue, ep("epANew", "feedA", 3500L))
        assertEquals(originalOrder, queue.map { it.id }.filter { it in originalOrder })
        assertEquals(listOf("epE", "epC", "epANew", "epAHigh", "epB", "epALow", "epD"), queue.map { it.id })

        queue = insert(queue, ep("epF", "feedF", 500L), playingId = "epB")
        assertEquals(originalOrder, queue.map { it.id }.filter { it in originalOrder })
        assertEquals(listOf("epE", "epC", "epANew", "epAHigh", "epB", "epF", "epALow", "epD"), queue.map { it.id })
    }
}
