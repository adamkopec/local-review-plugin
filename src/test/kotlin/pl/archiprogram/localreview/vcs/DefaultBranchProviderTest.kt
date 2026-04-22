package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.state.Key

class DefaultBranchProviderTest {

    private val project: Project = mockk(relaxed = true)
    private val mgr: GitRepositoryManager = mockk()
    private val provider = DefaultBranchProvider()

    @BeforeEach
    fun setUp() {
        every { project.isDisposed } returns false
        mockkStatic(GitRepositoryManager::class)
        every { GitRepositoryManager.getInstance(project) } returns mgr
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test fun currentBranch_repoFoundByPath_returnsBranchName() {
        val repoRoot = mockk<VirtualFile> { every { path } returns "/repo" }
        val repo = fakeRepo(rootPath = "/repo", branchName = "main")
        every { mgr.repositories } returns listOf(repo)

        assertEquals("main", provider.currentBranch(project, repoRoot))
    }

    @Test fun currentBranch_detachedHead_returnsDetachedSentinel() {
        val repoRoot = mockk<VirtualFile> { every { path } returns "/repo" }
        val repo = fakeRepo(rootPath = "/repo", branchName = null)
        every { mgr.repositories } returns listOf(repo)

        assertEquals(Key.DETACHED, provider.currentBranch(project, repoRoot))
    }

    @Test fun currentBranch_noRepoMatchesPath_returnsNoBranchSentinel() {
        val repoRoot = mockk<VirtualFile> { every { path } returns "/other" }
        val repo = fakeRepo(rootPath = "/repo", branchName = "main")
        every { mgr.repositories } returns listOf(repo)

        assertEquals(Key.NO_BRANCH, provider.currentBranch(project, repoRoot))
    }

    @Test fun currentBranch_pathStringMatch_ignoresVirtualFileIdentity() {
        // Two different VirtualFile instances with the same path string must still match.
        val callerRoot = mockk<VirtualFile> { every { path } returns "/repo" }
        val cachedRoot = mockk<VirtualFile> { every { path } returns "/repo" }
        val repo = mockk<GitRepository> {
            every { root } returns cachedRoot
            every { currentBranch } returns fakeBranch("main")
        }
        every { mgr.repositories } returns listOf(repo)

        assertEquals("main", provider.currentBranch(project, callerRoot))
    }

    @Test fun currentBranch_disposedProject_returnsNoBranchSentinel() {
        every { project.isDisposed } returns true

        assertEquals(
            Key.NO_BRANCH,
            provider.currentBranch(project, mockk(relaxed = true)),
        )
    }

    @Test fun currentBranch_managerThrows_returnsNoBranchSentinel() {
        every { mgr.repositories } throws RuntimeException("simulated Git4Idea failure")
        val repoRoot = mockk<VirtualFile> { every { path } returns "/repo" }

        assertEquals(Key.NO_BRANCH, provider.currentBranch(project, repoRoot))
    }

    private fun fakeRepo(rootPath: String, branchName: String?): GitRepository {
        val root = mockk<VirtualFile> { every { path } returns rootPath }
        return mockk {
            every { this@mockk.root } returns root
            every { currentBranch } returns branchName?.let { fakeBranch(it) }
        }
    }

    private fun fakeBranch(name: String): GitLocalBranch =
        mockk { every { this@mockk.name } returns name }
}
