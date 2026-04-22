package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.ui.SafeRefresh

class GitBranchListenerTest {

    private val project: Project = mockk(relaxed = true)
    private lateinit var listener: GitBranchListener

    @BeforeEach
    fun setUp() {
        mockkObject(SafeRefresh)
        every { SafeRefresh.scheduleChangesViewRefresh(any()) } returns Unit
        listener = GitBranchListener(project)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test fun firstRepositoryChanged_cachesBranch_doesNotTriggerRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))

        verify(exactly = 0) { SafeRefresh.scheduleChangesViewRefresh(any()) }
    }

    @Test fun sameBranchOnSubsequentCall_doesNotTriggerRefresh() {
        val repo = fakeRepo(rootPath = "/r", branchName = "main")

        listener.repositoryChanged(repo)
        listener.repositoryChanged(repo)

        verify(exactly = 0) { SafeRefresh.scheduleChangesViewRefresh(any()) }
    }

    @Test fun branchChangeOnSecondCall_triggersRefreshExactlyOnce() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "feature"))

        verify(exactly = 1) { SafeRefresh.scheduleChangesViewRefresh(project) }
    }

    @Test fun detachedHeadTransition_triggersRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = null))

        verify(exactly = 1) { SafeRefresh.scheduleChangesViewRefresh(project) }
    }

    @Test fun separateReposTrackedIndependently_noRefreshCrossTalk() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r1", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r2", branchName = "main"))

        verify(exactly = 0) { SafeRefresh.scheduleChangesViewRefresh(any()) }
    }

    private fun fakeRepo(rootPath: String, branchName: String?): GitRepository {
        val root = mockk<VirtualFile> { every { path } returns rootPath }
        return mockk {
            every { this@mockk.root } returns root
            every { currentBranch } returns branchName?.let {
                mockk<GitLocalBranch> { every { name } returns it }
            }
        }
    }
}
