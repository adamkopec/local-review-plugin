package pl.archiprogram.localreview.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ContentHasherTest {
    private val hasher = ContentHasher()

    @Test fun identicalContentProducesEqualDigests() {
        val bytes = "hello world".toByteArray()
        assertEquals(hasher.hashBytes(bytes), hasher.hashBytes(bytes))
    }

    @Test fun singleByteFlipProducesDifferentDigest() {
        val a = byteArrayOf(0, 1, 2, 3, 4)
        val b = byteArrayOf(0, 1, 2, 3, 5)
        assertNotEquals(hasher.hashBytes(a), hasher.hashBytes(b))
    }

    @Test fun whitespaceSensitiveLFvsCRLFdiffer() {
        assertNotEquals(
            hasher.hashBytes("a\n".toByteArray()),
            hasher.hashBytes("a\r\n".toByteArray()),
        )
    }

    @Test fun emptyInputProducesKnownSha256() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hasher.hashBytes(ByteArray(0)),
        )
    }

    @Test fun digestIsLowercaseHex64chars() {
        val d = hasher.hashBytes("x".toByteArray())
        assertEquals(64, d.length)
        assertTrue("got: $d", d.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun fallbackHashStableWhenLengthAndStampUnchanged() {
        val a = hasher.fallbackHash(1_000_000L, 12345L)
        val b = hasher.fallbackHash(1_000_000L, 12345L)
        assertEquals(a, b)
    }

    @Test fun fallbackHashDiffersWhenStampChanges() {
        assertNotEquals(
            hasher.fallbackHash(1_000_000L, 12345L),
            hasher.fallbackHash(1_000_000L, 12346L),
        )
    }

    @Test fun fallbackHashDiffersWhenLengthChanges() {
        assertNotEquals(
            hasher.fallbackHash(1_000_000L, 12345L),
            hasher.fallbackHash(1_000_001L, 12345L),
        )
    }

    @Test fun binaryContentHashedAsRawBytes() {
        val a = byteArrayOf(0x00, 0x01.toByte(), 0xFF.toByte(), 0x80.toByte())
        val b = byteArrayOf(0x00, 0x01.toByte(), 0xFF.toByte(), 0x80.toByte())
        assertEquals(hasher.hashBytes(a), hasher.hashBytes(b))
    }

    @Test fun threadLocalDigestConcurrentHashingNoCrossTalk() {
        val pool = Executors.newFixedThreadPool(16)
        val tasks =
            (0 until 256).map { i ->
                pool.submit<Pair<Int, String>> {
                    // Mix thread-local reuse with varying inputs
                    i to hasher.hashBytes("content-$i".toByteArray())
                }
            }
        val results = tasks.map { it.get(10, TimeUnit.SECONDS) }.toMap()
        // Each input should produce the same digest as a single-threaded recomputation
        for ((i, digest) in results) {
            assertEquals(hasher.hashBytes("content-$i".toByteArray()), digest)
        }
        pool.shutdownNow()
    }

    @Test fun threadLocalDigestResetsBetweenCallsSameResultAsSingleCall() {
        val a = "first".toByteArray()
        val b = "second".toByteArray()
        val h1 = hasher.hashBytes(a)
        val h2 = hasher.hashBytes(b)
        // Re-hash in opposite order on same thread; must match previous digests
        assertEquals(h2, hasher.hashBytes(b))
        assertEquals(h1, hasher.hashBytes(a))
    }

    @Test fun sizeCapConstantIs10MiB() {
        assertEquals(10L * 1024 * 1024, ContentHasher.SIZE_CAP)
    }
}
