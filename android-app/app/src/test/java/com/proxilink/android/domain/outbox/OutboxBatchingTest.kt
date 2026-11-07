package com.proxilink.android.domain.outbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class OutboxBatchingTest {

    private val baseTime: Instant = Instant.parse("2026-11-07T12:00:00Z")

    @Test
    fun `accepted and duplicate rows are removed and counted`() = runTest {
        val store = FakeOutboxStore(proximityRows(3))
        val sender = FakeBatchSender { _, _ -> BatchResult(accepted = 2, duplicates = 1) }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        val summary = syncer.sync()

        assertEquals(3, summary.accepted + summary.duplicates)
        assertEquals(2, summary.accepted)
        assertEquals(1, summary.duplicates)
        assertEquals(0, summary.rejected)
        assertEquals(1, summary.batches)
        assertTrue(store.rows.isEmpty())
        assertTrue(store.rejectedReasons.isEmpty())
        assertEquals(1, sender.batches.size)
    }

    @Test
    fun `rejected rows stay with reason while acknowledged rows are deleted`() = runTest {
        val store = FakeOutboxStore(proximityRows(3))
        val sender = FakeBatchSender { _, _ ->
            BatchResult(accepted = 2, duplicates = 0, rejected = listOf(RejectedEvent("event-1", "device_not_accessible")))
        }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        val summary = syncer.sync()

        assertEquals(1, summary.rejected)
        assertEquals(listOf("event-1"), store.rows.map { it.eventId })
        assertEquals("device_not_accessible", store.rejectedReasons["event-1"])
    }

    @Test
    fun `full batches are drained until fewer than batchSize rows remain`() = runTest {
        val store = FakeOutboxStore(proximityRows(5))
        val sender = FakeBatchSender { _, _ -> BatchResult(accepted = 0, duplicates = 2) }
        val syncer = OutboxSyncer(store, sender, batchSize = 2, now = { baseTime })

        val summary = syncer.sync()

        assertEquals(3, summary.batches)
        assertEquals(6, summary.duplicates)
        assertTrue(store.rows.isEmpty())
        assertTrue(sender.batches.all { it.first == OutboxKind.PROXIMITY })
    }

    @Test
    fun `transport failure propagates and keeps rows pending`() = runTest {
        val store = FakeOutboxStore(proximityRows(2))
        val sender = FakeBatchSender { _, _ -> throw IllegalStateException("network down") }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        try {
            syncer.sync()
            fail("Expected the transport failure to propagate for WorkManager retry")
        } catch (expected: IllegalStateException) {
            assertEquals("network down", expected.message)
        }

        assertEquals(2, store.rows.size)
        assertEquals(0, store.attemptsRecorded)
    }

    @Test
    fun `server acknowledging nothing keeps rows and flags failure`() = runTest {
        val store = FakeOutboxStore(proximityRows(2))
        val sender = FakeBatchSender { _, _ -> BatchResult(accepted = 0, duplicates = 0) }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        val summary = syncer.sync()

        assertTrue(summary.failed)
        assertEquals(2, store.rows.size)
        assertEquals(2, store.attemptsRecorded)
    }

    @Test
    fun `rejection ids outside the batch are ignored`() = runTest {
        val store = FakeOutboxStore(proximityRows(2))
        val sender = FakeBatchSender { _, _ ->
            BatchResult(
                accepted = 2,
                duplicates = 0,
                rejected = listOf(RejectedEvent("not-in-this-batch", "unknown")),
            )
        }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        val summary = syncer.sync()

        assertEquals(0, summary.rejected)
        assertEquals(2, summary.accepted)
        assertTrue(store.rows.isEmpty())
        assertNull(store.rejectedReasons["not-in-this-batch"])
    }

    @Test
    fun `location and proximity rows are posted to their own endpoints`() = runTest {
        val store = FakeOutboxStore(
            proximityRows(1) + listOf(
                OutboxRecord(
                    eventId = "loc-1",
                    kind = OutboxKind.LOCATION,
                    payloadJson = """{"eventId":"loc-1","latitude":1.0,"longitude":2.0}""",
                    recordedAt = baseTime,
                    state = OutboxState.PENDING,
                    attempts = 0,
                    rejectionReason = null,
                ),
            ),
        )
        val sender = FakeBatchSender { _, _ -> BatchResult(accepted = 1, duplicates = 0) }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        syncer.sync()

        assertEquals(
            listOf(OutboxKind.PROXIMITY, OutboxKind.LOCATION),
            sender.batches.map { it.first },
        )
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `batch json contains an events array with every payload`() = runTest {
        val store = FakeOutboxStore(proximityRows(2))
        val sender = FakeBatchSender { _, _ -> BatchResult(accepted = 2, duplicates = 0) }
        val syncer = OutboxSyncer(store, sender, now = { baseTime })

        syncer.sync()

        val body = sender.batches.single().second
        assertTrue(body.contains("\"events\""))
        assertTrue(body.contains("\"eventId\":\"event-0\""))
        assertTrue(body.contains("\"eventId\":\"event-1\""))
    }

    private fun proximityRows(count: Int): List<OutboxRecord> = (0 until count).map { index ->
        OutboxRecord(
            eventId = "event-$index",
            kind = OutboxKind.PROXIMITY,
            payloadJson = """{"eventId":"event-$index","rssi":-65}""",
            recordedAt = baseTime.plusSeconds(index.toLong()),
            state = OutboxState.PENDING,
            attempts = 0,
            rejectionReason = null,
        )
    }

    private class FakeOutboxStore(initial: List<OutboxRecord>) : OutboxStore {
        val rows = initial.toMutableList()
        val rejectedReasons = mutableMapOf<String, String>()
        var attemptsRecorded = 0

        override suspend fun pending(kind: OutboxKind, limit: Int): List<OutboxRecord> =
            rows.filter { it.kind == kind }.sortedBy { it.recordedAt }.take(limit)

        override suspend fun markAcknowledged(eventIds: List<String>) {
            rows.removeAll { it.eventId in eventIds }
        }

        override suspend fun markRejected(eventId: String, reason: String) {
            rejectedReasons[eventId] = reason
        }

        override suspend fun markAttempt(eventIds: List<String>) {
            attemptsRecorded += eventIds.size
        }

        override suspend fun counts(): OutboxCounts =
            OutboxCounts(pending = rows.size, rejected = rejectedReasons.size)

        override suspend fun recentRejected(limit: Int): List<OutboxRecord> =
            rows.filter { it.eventId in rejectedReasons }.take(limit)
    }

    private class FakeBatchSender(
        private val responder: (OutboxKind, String) -> BatchResult,
    ) : BatchSender {
        val batches = mutableListOf<Pair<OutboxKind, String>>()

        override suspend fun send(kind: OutboxKind, batchJson: String): BatchResult {
            batches += kind to batchJson
            return responder(kind, batchJson)
        }
    }
}
