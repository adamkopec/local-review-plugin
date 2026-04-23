package pl.archiprogram.localreview.hash

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * SHA-256 hashing with a size-cap fallback.
 *
 * Files larger than [SIZE_CAP] hash `SHA-256(length || modificationStamp)` instead of the full
 * content, so memory and latency are bounded for review-irrelevant artefacts (binaries, generated
 * sources, etc.).
 */
@Service(Service.Level.APP)
class ContentHasher {
    private val digestLocal: ThreadLocal<MessageDigest> =
        ThreadLocal.withInitial {
            MessageDigest.getInstance("SHA-256")
        }

    /** Returns lowercase-hex SHA-256 of [file]'s content, or a fallback digest for oversize files.
     *  Returns `null` when the file cannot be read (e.g. deleted, unreadable). */
    fun hash(file: VirtualFile): String? {
        if (!file.isValid || file.isDirectory) return null
        return try {
            if (file.length > SIZE_CAP) {
                fallbackHash(file.length, file.modificationStamp)
            } else {
                // cacheContent = false: avoid populating the VFS content cache from hashing passes.
                val bytes = file.contentsToByteArray(false)
                digestHex(bytes)
            }
        } catch (e: Exception) {
            LOG.debug("Failed to hash ${file.path}: ${e.message}")
            null
        }
    }

    /** For test use: hash arbitrary bytes. */
    fun hashBytes(bytes: ByteArray): String = digestHex(bytes)

    /** For test/benchmark: produce the fallback digest for a given (length, stamp) pair. */
    fun fallbackHash(
        length: Long,
        modificationStamp: Long,
    ): String {
        val buf =
            ByteBuffer.allocate(Long.SIZE_BYTES * 2)
                .putLong(length)
                .putLong(modificationStamp)
                .array()
        return digestHex(buf)
    }

    private fun digestHex(bytes: ByteArray): String {
        val digest = digestLocal.get()
        digest.reset()
        val hashed = digest.digest(bytes)
        return toHex(hashed)
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            sb.append(HEX[i ushr 4])
            sb.append(HEX[i and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        const val SIZE_CAP: Long = 10L * 1024 * 1024
        private val HEX = "0123456789abcdef".toCharArray()
        private val LOG = Logger.getInstance(ContentHasher::class.java)

        fun getInstance(): ContentHasher = com.intellij.openapi.application.ApplicationManager.getApplication().service()
    }
}
