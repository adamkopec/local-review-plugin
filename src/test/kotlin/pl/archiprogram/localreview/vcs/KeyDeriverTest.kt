package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pl.archiprogram.localreview.state.Key

class KeyDeriverTest {
    private val project: Project = mockk(relaxed = true)
    private val vcsManager: ProjectLevelVcsManager = mockk()
    private val fakeProvider = ScriptedBranchProvider()

    @Before
    fun setUp() {
        mockkStatic(ProjectLevelVcsManager::class)
        every { ProjectLevelVcsManager.getInstance(project) } returns vcsManager
        mockkObject(BranchProvider.Companion)
        every { BranchProvider.getInstance() } returns fakeProvider
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun keyForVirtualFileUnderVcsRootReturnsKeyWithRepoRootBranchPath() {
        val file = mockk<VirtualFile> { every { path } returns "/repo/src/Foo.kt" }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(file) } returns root
        fakeProvider.branch = "main"

        val key = KeyDeriver.keyFor(project, file)!!

        assertEquals("/repo", key.repoRoot)
        assertEquals("main", key.branch)
        assertEquals("/repo/src/Foo.kt", key.path)
    }

    @Test fun keyForVirtualFileOutsideVcsReturnsNoVcsSentinelKey() {
        val file = mockk<VirtualFile> { every { path } returns "/tmp/loose.kt" }
        every { vcsManager.getVcsRootFor(file) } returns null

        val key = KeyDeriver.keyFor(project, file)!!

        assertEquals(Key.NO_VCS, key.repoRoot)
        assertEquals(Key.NO_BRANCH, key.branch)
        assertEquals("/tmp/loose.kt", key.path)
    }

    @Test fun keyForBranchProviderThrowsBranchFallsBackToNoBranch() {
        val file = mockk<VirtualFile> { every { path } returns "/repo/a.kt" }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(file) } returns root
        fakeProvider.throwOnCall = true

        val key = KeyDeriver.keyFor(project, file)!!

        assertEquals("/repo", key.repoRoot)
        assertEquals(Key.NO_BRANCH, key.branch)
    }

    @Test fun keyForBranchProviderReturnsDetachedKeyCarriesDetachedSentinel() {
        val file = mockk<VirtualFile> { every { path } returns "/repo/a.kt" }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(file) } returns root
        fakeProvider.branch = Key.DETACHED

        val key = KeyDeriver.keyFor(project, file)!!

        assertEquals(Key.DETACHED, key.branch)
    }

    @Test fun keyForFilePathWithVirtualFileDelegatesToVirtualFileOverload() {
        val vf = mockk<VirtualFile> { every { path } returns "/repo/a.kt" }
        val filePath = mockk<FilePath> { every { virtualFile } returns vf }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(vf) } returns root
        fakeProvider.branch = "main"

        val key = KeyDeriver.keyFor(project, filePath)!!

        // VirtualFile path is used — proves the delegation path.
        assertEquals("/repo/a.kt", key.path)
        assertEquals("main", key.branch)
    }

    @Test fun keyForFilePathWithoutVirtualFileUnderVcsUsesFilePathValues() {
        val filePath =
            mockk<FilePath> {
                every { virtualFile } returns null
                every { path } returns "/repo/deleted.kt"
            }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(filePath) } returns root
        fakeProvider.branch = "feature"

        val key = KeyDeriver.keyFor(project, filePath)!!

        assertEquals("/repo", key.repoRoot)
        assertEquals("feature", key.branch)
        assertEquals("/repo/deleted.kt", key.path)
    }

    @Test fun keyForFilePathWithoutVirtualFileOutsideVcsReturnsNull() {
        val filePath =
            mockk<FilePath> {
                every { virtualFile } returns null
                every { path } returns "/tmp/loose.kt"
            }
        every { vcsManager.getVcsRootFor(filePath) } returns null

        val key = KeyDeriver.keyFor(project, filePath)

        assertNull(key)
    }

    @Test fun keyForFilePathBranchProviderThrowsBranchFallsBackToNoBranch() {
        val filePath =
            mockk<FilePath> {
                every { virtualFile } returns null
                every { path } returns "/repo/deleted.kt"
            }
        val root = mockk<VirtualFile> { every { path } returns "/repo" }
        every { vcsManager.getVcsRootFor(filePath) } returns root
        fakeProvider.throwOnCall = true

        val key = KeyDeriver.keyFor(project, filePath)!!

        assertEquals(Key.NO_BRANCH, key.branch)
    }
}

private class ScriptedBranchProvider : BranchProvider {
    var branch: String = Key.NO_BRANCH
    var throwOnCall: Boolean = false

    override fun currentBranch(
        project: Project,
        repoRoot: VirtualFile,
    ): String {
        if (throwOnCall) throw IllegalStateException("scripted")
        return branch
    }
}
