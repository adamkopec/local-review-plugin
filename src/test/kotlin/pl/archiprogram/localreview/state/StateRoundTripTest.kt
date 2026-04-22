package pl.archiprogram.localreview.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StateRoundTripTest {

    @Test fun entryDto_toKeyAndEntry_roundTrip() {
        val key = Key("/repo", "main", "/repo/a.kt")
        val entry = ReviewEntry("abc123", 1_700_000_000L)
        val dto = EntryDto(key, entry)
        assertEquals(key, dto.toKey())
        assertEquals(entry, dto.toEntry())
    }

    @Test fun state_initialVersion_isCurrent() {
        val s = State()
        assertEquals(State.CURRENT_VERSION, s.version)
    }

    @Test fun state_emptyEntries_isMutable() {
        val s = State()
        s.entries.add(EntryDto(Key("/r", "m", "/r/a"), ReviewEntry("h", 1)))
        assertEquals(1, s.entries.size)
    }

    @Test fun entryDto_defaultConstructor_producesDefaults() {
        val dto = EntryDto()
        assertEquals("", dto.repoRoot)
        assertEquals(Key.NO_BRANCH, dto.branch)
        assertEquals("", dto.path)
        assertEquals("", dto.hashHex)
        assertEquals(0L, dto.markedAt)
    }
}
