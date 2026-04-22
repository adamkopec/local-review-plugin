package pl.archiprogram.localreview.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyTest {

    @Test fun equals_identicalTriples_areEqual() {
        val a = Key("/repo", "main", "/repo/a.kt")
        val b = Key("/repo", "main", "/repo/a.kt")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun equals_differingRepoRoot_notEqual() {
        assertNotEquals(
            Key("/r1", "main", "/r1/a.kt"),
            Key("/r2", "main", "/r1/a.kt"),
        )
    }

    @Test fun equals_differingBranch_notEqual() {
        assertNotEquals(
            Key("/r", "main", "/r/a.kt"),
            Key("/r", "feature", "/r/a.kt"),
        )
    }

    @Test fun equals_differingPath_notEqual() {
        assertNotEquals(
            Key("/r", "main", "/r/a.kt"),
            Key("/r", "main", "/r/b.kt"),
        )
    }

    @Test fun sentinels_areDistinctAndNonEmpty() {
        assertNotNull(Key.NO_VCS)
        assertNotNull(Key.NO_BRANCH)
        assertNotNull(Key.DETACHED)
        assertFalse(Key.NO_VCS.isEmpty())
        assertFalse(Key.NO_BRANCH.isEmpty())
        assertFalse(Key.DETACHED.isEmpty())
        assertNotEquals(Key.NO_VCS, Key.NO_BRANCH)
        assertNotEquals(Key.NO_VCS, Key.DETACHED)
        assertNotEquals(Key.NO_BRANCH, Key.DETACHED)
    }

    @Test fun sentinels_doNotCollideWithRealBranchNames() {
        // Angle-bracketed sentinels are illegal Git branch names (`git check-ref-format` rejects them).
        assertTrue(Key.NO_BRANCH.startsWith("<") && Key.NO_BRANCH.endsWith(">"))
        assertTrue(Key.NO_VCS.startsWith("<") && Key.NO_VCS.endsWith(">"))
        assertTrue(Key.DETACHED.startsWith("<") && Key.DETACHED.endsWith(">"))
    }

    @Test fun toString_containsAllThreeFields_forLogging() {
        val s = Key("/r", "main", "/r/foo.kt").toString()
        assertTrue(s.contains("/r"))
        assertTrue(s.contains("main"))
        assertTrue(s.contains("/r/foo.kt"))
    }

    @Test fun hashCode_consistentAcrossInvocations() {
        val k = Key("/r", "main", "/r/a.kt")
        assertEquals(k.hashCode(), k.hashCode())
    }

    @Test fun equality_symmetricAndReflexive() {
        val a = Key("/r", "main", "/r/a.kt")
        val b = a.copy()
        assertEquals(a, a)
        assertEquals(a, b)
        assertEquals(b, a)
    }
}
