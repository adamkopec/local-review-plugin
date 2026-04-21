package com.localreview.state

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.localreview.settings.LocalReviewSettings

/**
 * Proves that [ReviewStateService] can be looked up via `@Service`, that mark/unmark semantics
 * hold under the real platform wiring, and that [getState]/[loadState] round-trip losslessly.
 */
class ReviewStateServicePersistenceTest : BasePlatformTestCase() {

    private lateinit var service: ReviewStateService

    override fun setUp() {
        super.setUp()
        service = ReviewStateService.getInstance(project)
        service.clearAll()
    }

    fun test_mark_addsEntry_isViewedReturnsTrue() {
        val key = Key("/repo", "main", "/repo/a.kt")
        service.mark(key, "deadbeef", now = 1_000L)

        assertTrue(service.isViewed(key))
        assertEquals(1, service.size())
        val entry = service.getEntry(key)!!
        assertEquals("deadbeef", entry.hashHex)
        assertEquals(1_000L, entry.markedAt)
    }

    fun test_unmark_removesEntry() {
        val key = Key("/repo", "main", "/repo/a.kt")
        service.mark(key, "h", now = 1L)
        val removed = service.unmark(key)
        assertTrue(removed)
        assertFalse(service.isViewed(key))
        assertEquals(0, service.size())
    }

    fun test_unmark_unknownKey_returnsFalse() {
        val key = Key("/repo", "main", "/repo/nonexistent")
        assertFalse(service.unmark(key))
    }

    fun test_toggle_unmarked_marks() {
        val key = Key("/r", "b", "/r/a.kt")
        val marked = service.toggle(key, hashHexIfMarking = "h")
        assertTrue(marked)
        assertTrue(service.isViewed(key))
    }

    fun test_toggle_marked_unmarks() {
        val key = Key("/r", "b", "/r/a.kt")
        service.mark(key, "h", 1L)
        val marked = service.toggle(key, hashHexIfMarking = "h")
        assertFalse(marked)
        assertFalse(service.isViewed(key))
    }

    fun test_toggle_nullHashForMark_leavesStateUnchanged() {
        val key = Key("/r", "b", "/r/a.kt")
        service.toggle(key, hashHexIfMarking = null)
        assertFalse(service.isViewed(key))
    }

    fun test_clearAll_removesEverything_returnsCount() {
        service.mark(Key("/r", "b", "/r/a"), "h1", 1L)
        service.mark(Key("/r", "b", "/r/b"), "h2", 2L)
        val removed = service.clearAll()
        assertEquals(2, removed)
        assertEquals(0, service.size())
    }

    fun test_persistenceRoundTrip_nonEmpty_matchesOriginal() {
        val k1 = Key("/r", "main", "/r/a")
        val k2 = Key("/r", "main", "/r/b")
        service.mark(k1, "h1", 1_000L)
        service.mark(k2, "h2", 2_000L)

        val captured = service.state

        // Create a fresh service instance and load the same State
        service.clearAll()
        service.loadState(captured)

        assertTrue(service.isViewed(k1))
        assertTrue(service.isViewed(k2))
        assertEquals("h1", service.getEntry(k1)!!.hashHex)
        assertEquals(1_000L, service.getEntry(k1)!!.markedAt)
        assertEquals(2, service.size())
    }

    fun test_getState_containsAllEntries_withVersionField() {
        service.mark(Key("/r", "m", "/r/a"), "h", 1L)
        val state = service.state
        assertEquals(State.CURRENT_VERSION, state.version)
        assertEquals(1, state.entries.size)
        assertEquals("h", state.entries[0].hashHex)
    }

    fun test_reconcile_dropsEntries_notBackedByCurrentChanges() {
        val keep = Key("/r", "m", "/r/keep")
        val drop = Key("/r", "m", "/r/drop")
        service.mark(keep, "h1", 1L)
        service.mark(drop, "h2", 2L)

        service.reconcile(
            currentChanges = setOf(keep),
            renames = emptyMap(),
            rehashedContent = emptyMap(),
            settings = LocalReviewSettings.State(),
            now = 1_000_000L,
        )

        assertTrue(service.isViewed(keep))
        assertFalse(service.isViewed(drop))
    }

    fun test_reconcile_reKeysRenames() {
        val oldKey = Key("/r", "m", "/r/old.kt")
        val newKey = Key("/r", "m", "/r/new.kt")
        service.mark(oldKey, "h", 1L)

        service.reconcile(
            currentChanges = setOf(newKey),
            renames = mapOf(oldKey to newKey),
            rehashedContent = emptyMap(),
            settings = LocalReviewSettings.State(),
            now = 1_000_000L,
        )

        assertFalse(service.isViewed(oldKey))
        assertTrue(service.isViewed(newKey))
        assertEquals("h", service.getEntry(newKey)!!.hashHex)
    }

    fun test_reconcile_dropsEntry_onHashMismatch() {
        val key = Key("/r", "m", "/r/a")
        service.mark(key, "oldHash", 1L)

        service.reconcile(
            currentChanges = setOf(key),
            renames = emptyMap(),
            rehashedContent = mapOf(key to "newHash"),
            settings = LocalReviewSettings.State(),
            now = 1_000_000L,
        )

        assertFalse(service.isViewed(key))
    }

    fun test_reconcile_keepsEntry_whenHashMatches() {
        val key = Key("/r", "m", "/r/a")
        service.mark(key, "sameHash", 1L)

        service.reconcile(
            currentChanges = setOf(key),
            renames = emptyMap(),
            rehashedContent = mapOf(key to "sameHash"),
            settings = LocalReviewSettings.State(),
            now = 1_000_000L,
        )

        assertTrue(service.isViewed(key))
    }

    fun test_reconcile_TTLeviction_dropsOldEntries() {
        val key = Key("/r", "m", "/r/a")
        val day = 24L * 60 * 60 * 1000
        service.mark(key, "h", now = 0L)

        service.reconcile(
            currentChanges = setOf(key),
            renames = emptyMap(),
            rehashedContent = emptyMap(),
            settings = LocalReviewSettings.State(ttlDays = 30),
            now = 40 * day,
        )

        assertFalse(service.isViewed(key))
    }

    fun test_reconcile_perBranchCap_evictsOldestByMarkedAt() {
        for (i in 0 until 10) {
            service.mark(Key("/r", "m", "/r/$i"), "h$i", now = i.toLong())
        }

        service.reconcile(
            currentChanges = (0 until 10).map { Key("/r", "m", "/r/$it") }.toSet(),
            renames = emptyMap(),
            rehashedContent = emptyMap(),
            settings = LocalReviewSettings.State(ttlDays = 0, perBranchCap = 5),
            now = 100L,
        )

        assertEquals(5, service.size())
        // Oldest (lowest markedAt) are dropped; newest 5 are kept
        for (i in 0 until 5) {
            assertFalse(service.isViewed(Key("/r", "m", "/r/$i")))
        }
        for (i in 5 until 10) {
            assertTrue(service.isViewed(Key("/r", "m", "/r/$i")))
        }
    }

    fun test_clearBranch_scopedToRepoAndBranch() {
        service.mark(Key("/r", "main", "/r/a"), "h", 1L)
        service.mark(Key("/r", "feature", "/r/b"), "h", 2L)
        service.mark(Key("/other", "main", "/other/c"), "h", 3L)

        val removed = service.clearBranch("/r", "main")

        assertEquals(1, removed)
        assertEquals(2, service.size())
        assertFalse(service.isViewed(Key("/r", "main", "/r/a")))
        assertTrue(service.isViewed(Key("/r", "feature", "/r/b")))
        assertTrue(service.isViewed(Key("/other", "main", "/other/c")))
    }
}
