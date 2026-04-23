package pl.archiprogram.localreview.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.vcs.KeyDeriver
import java.io.File
import java.nio.file.Files

class PathResolverTest {
    private val project: Project = mockk(relaxed = true)
    private val lfs: LocalFileSystem = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(LocalFileSystem::class)
        every { LocalFileSystem.getInstance() } returns lfs
        mockkObject(KeyDeriver)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun blankPathReturnsBlankPath() {
        val outcome = PathResolver.resolve(project, "   ")
        assertSame(PathResolver.Outcome.BlankPath, outcome)
    }

    @Test fun absolutePathThatDoesntExistReturnsNotFound() {
        every { lfs.findFileByIoFile(any()) } returns null

        val outcome = PathResolver.resolve(project, "/does/not/exist.kt")

        assertSame(PathResolver.Outcome.NotFound, outcome)
    }

    @Test fun directoryReturnsIsDirectory() {
        val vf = virtualFile(isDirectory = true, isValid = true)
        every { lfs.findFileByIoFile(any()) } returns vf

        val outcome = PathResolver.resolve(project, "/some/dir")

        assertSame(PathResolver.Outcome.IsDirectory, outcome)
    }

    @Test fun invalidVirtualFileReturnsNotFound() {
        val vf = virtualFile(isDirectory = false, isValid = false)
        every { lfs.findFileByIoFile(any()) } returns vf

        val outcome = PathResolver.resolve(project, "/some/file.kt")

        assertSame(PathResolver.Outcome.NotFound, outcome)
    }

    @Test fun fileOutsideVcsReturnsOutsideVcs() {
        val vf = virtualFile(isDirectory = false, isValid = true)
        every { lfs.findFileByIoFile(any()) } returns vf
        every { KeyDeriver.keyFor(project, vf) } returns null

        val outcome = PathResolver.resolve(project, "/some/file.kt")

        assertSame(PathResolver.Outcome.OutsideVcs, outcome)
    }

    @Test fun absolutePathResolvesToKey() {
        val vf = virtualFile(isDirectory = false, isValid = true)
        val key = Key("/repo", "main", "/repo/src/x.kt")
        every { lfs.findFileByIoFile(any()) } returns vf
        every { KeyDeriver.keyFor(project, vf) } returns key

        val outcome = PathResolver.resolve(project, "/repo/src/x.kt")

        assertTrue(outcome is PathResolver.Outcome.Resolved)
        assertEquals(key, (outcome as PathResolver.Outcome.Resolved).key)
        assertSame(vf, outcome.file)
    }

    @Test fun projectRelativePathResolvesAgainstBasePath() {
        // Use a real temp directory so the canonicalization step has something concrete to chew on.
        val baseDir = Files.createTempDirectory("localreview-path-test").toFile()
        val target =
            File(baseDir, "src/x.kt").also {
                it.parentFile.mkdirs()
                it.writeText("hello")
            }
        every { project.basePath } returns baseDir.absolutePath

        val vf = virtualFile(isDirectory = false, isValid = true)
        val key = Key("/repo", "main", target.absolutePath)
        // LFS must be called with the canonicalized absolute form of `src/x.kt` under baseDir.
        every { lfs.findFileByIoFile(match { it.canonicalPath == target.canonicalPath }) } returns vf
        every { KeyDeriver.keyFor(project, vf) } returns key

        val outcome = PathResolver.resolve(project, "src/x.kt")

        assertTrue(outcome is PathResolver.Outcome.Resolved)
        assertEquals(key, (outcome as PathResolver.Outcome.Resolved).key)

        target.delete()
        baseDir.deleteRecursively()
    }

    @Test fun projectRelativePathWithNullBasePathReturnsNotFound() {
        every { project.basePath } returns null
        every { lfs.findFileByIoFile(any()) } returns null

        val outcome = PathResolver.resolve(project, "some/relative/path.kt")

        assertSame(PathResolver.Outcome.NotFound, outcome)
    }

    private fun virtualFile(
        isDirectory: Boolean,
        isValid: Boolean,
    ): VirtualFile =
        mockk(relaxed = true) {
            every { this@mockk.isDirectory } returns isDirectory
            every { this@mockk.isValid } returns isValid
        }
}
