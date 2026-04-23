package pl.archiprogram.localreview.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.vcs.KeyDeriver

class TargetCollectorTest {
    private val project: Project = mockk(relaxed = true)
    private val clm: ChangeListManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(ChangeListManager::class)
        every { ChangeListManager.getInstance(project) } returns clm
        mockkObject(KeyDeriver)
        mockkStatic(VcsUtil::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun collectFromVcsChangesProducesChangedTargets() {
        val file = filePath("/repo/a.kt")
        val change = change(afterFile = file)
        val key = Key("/repo", "main", "/repo/a.kt")
        every { KeyDeriver.keyFor(project, file) } returns key

        val out = TargetCollector.collect(project, arrayOf(change), null, emptyList())

        assertEquals(1, out.size)
        assertTrue(out[0] is Target.Changed)
        assertEquals(key, out[0].key)
    }

    @Test fun collectFromUnversionedPathsProducesUnversionedTargets() {
        val fp = filePath("/repo/new.kt")
        val key = Key("/repo", "main", "/repo/new.kt")
        every { KeyDeriver.keyFor(project, fp) } returns key

        val out = TargetCollector.collect(project, null, listOf(fp), emptyList())

        assertEquals(1, out.size)
        assertTrue(out[0] is Target.Unversioned)
    }

    @Test fun collectDedupsByKeyWhenSameFileAppearsInMultipleSources() {
        val file = filePath("/repo/a.kt")
        val change = change(afterFile = file)
        val key = Key("/repo", "main", "/repo/a.kt")
        every { KeyDeriver.keyFor(project, file) } returns key
        every { KeyDeriver.keyFor(project, file as FilePath) } returns key

        val out = TargetCollector.collect(project, arrayOf(change), listOf(file), emptyList())

        assertEquals(1, out.size)
    }

    @Test fun collectFromVirtualFileSelectionChangeFoundProducesChangedTarget() {
        val vf = virtualFile("/repo/a.kt")
        val fp = filePath("/repo/a.kt")
        val change = change(afterFile = fp)
        val key = Key("/repo", "main", "/repo/a.kt")
        every { clm.getChange(vf) } returns change
        every { clm.isUnversioned(vf) } returns false
        every { VcsUtil.getFilePath(vf) } returns fp
        every { KeyDeriver.keyFor(project, fp) } returns key

        val out = TargetCollector.collect(project, null, null, listOf(vf))

        assertEquals(1, out.size)
        assertTrue(out[0] is Target.Changed)
    }

    @Test fun collectFromVirtualFileSelectionUnversionedProducesUnversionedTarget() {
        val vf = virtualFile("/repo/new.kt")
        val fp = filePath("/repo/new.kt")
        val key = Key("/repo", "main", "/repo/new.kt")
        every { clm.getChange(vf) } returns null
        every { clm.isUnversioned(vf) } returns true
        every { VcsUtil.getFilePath(vf) } returns fp
        every { KeyDeriver.keyFor(project, fp) } returns key

        val out = TargetCollector.collect(project, null, null, listOf(vf))

        assertEquals(1, out.size)
        assertTrue(out[0] is Target.Unversioned)
    }

    @Test fun collectFromVirtualFileSelectionNotAChangeFilteredOut() {
        val vf = virtualFile("/repo/committed.kt")
        every { clm.getChange(vf) } returns null
        every { clm.isUnversioned(vf) } returns false

        val out = TargetCollector.collect(project, null, null, listOf(vf))

        assertTrue(out.isEmpty())
    }

    @Test fun collectDirectoryVirtualFileSkipped() {
        val dir =
            mockk<VirtualFile> {
                every { isDirectory } returns true
                every { isValid } returns true
            }

        val out = TargetCollector.collect(project, null, null, listOf(dir))

        assertTrue(out.isEmpty())
    }

    @Test fun collectInvalidVirtualFileSkipped() {
        val stale =
            mockk<VirtualFile> {
                every { isDirectory } returns false
                every { isValid } returns false
            }

        val out = TargetCollector.collect(project, null, null, listOf(stale))

        assertTrue(out.isEmpty())
    }

    @Test fun collectChangeWithNullKeySkipped() {
        val file = filePath("/repo/weird.kt")
        val change = change(afterFile = file)
        every { KeyDeriver.keyFor(project, file) } returns null

        val out = TargetCollector.collect(project, arrayOf(change), null, emptyList())

        assertTrue(out.isEmpty())
    }

    // ----- helpers -----

    private fun filePath(p: String): FilePath =
        mockk(relaxed = true) {
            every { path } returns p
            every { virtualFile } returns null
        }

    private fun virtualFile(p: String): VirtualFile =
        mockk(relaxed = true) {
            every { path } returns p
            every { isDirectory } returns false
            every { isValid } returns true
        }

    private fun change(afterFile: FilePath): Change {
        val afterRev = mockk<ContentRevision>(relaxed = true) { every { file } returns afterFile }
        return mockk(relaxed = true) {
            every { afterRevision } returns afterRev
            every { beforeRevision } returns null
        }
    }
}
