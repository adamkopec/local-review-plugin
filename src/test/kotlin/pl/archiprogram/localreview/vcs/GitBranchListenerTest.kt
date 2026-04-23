package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GitBranchListenerTest {

    private val project: Project = mockk(relaxed = true)
    private val refreshCount = AtomicInteger(0)
    private val listener = GitBranchListener(project) { refreshCount.incrementAndGet() }

    @Test fun firstRepositoryChanged_cachesBranch_doesNotTriggerRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))

        assertEquals(0, refreshCount.get())
    }

    @Test fun sameBranchOnSubsequentCall_doesNotTriggerRefresh() {
        val repo = fakeRepo(rootPath = "/r", branchName = "main")

        listener.repositoryChanged(repo)
        listener.repositoryChanged(repo)

        assertEquals(0, refreshCount.get())
    }

    @Test fun branchChangeOnSecondCall_triggersRefreshExactlyOnce() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "feature"))

        assertEquals(1, refreshCount.get())
    }

    @Test fun detachedHeadTransition_triggersRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = null))

        assertEquals(1, refreshCount.get())
    }

    @Test fun separateReposTrackedIndependently_noRefreshCrossTalk() {
        listener.repositoryChanged(fakeRepo(rootPath = "/a", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/b", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/b", branchName = "feature"))

        assertEquals(1, refreshCount.get())
    }

    private fun fakeRepo(rootPath: String, branchName: String?): GitRepository =
        mockk(relaxed = true) {
            every { root.path } returns rootPath
            every { currentBranch } returns branchName?.let {
                mockk(relaxed = true) { every { name } returns it }
            }
        }
}
