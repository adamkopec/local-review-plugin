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

    @Test fun firstRepositoryChangedCachesBranchDoesNotTriggerRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))

        assertEquals(0, refreshCount.get())
    }

    @Test fun sameBranchOnSubsequentCallDoesNotTriggerRefresh() {
        val repo = fakeRepo(rootPath = "/r", branchName = "main")

        listener.repositoryChanged(repo)
        listener.repositoryChanged(repo)

        assertEquals(0, refreshCount.get())
    }

    @Test fun branchChangeOnSecondCallTriggersRefreshExactlyOnce() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "feature"))

        assertEquals(1, refreshCount.get())
    }

    @Test fun detachedHeadTransitionTriggersRefresh() {
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/r", branchName = null))

        assertEquals(1, refreshCount.get())
    }

    @Test fun separateReposTrackedIndependentlyNoRefreshCrossTalk() {
        listener.repositoryChanged(fakeRepo(rootPath = "/a", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/b", branchName = "main"))
        listener.repositoryChanged(fakeRepo(rootPath = "/b", branchName = "feature"))

        assertEquals(1, refreshCount.get())
    }

    @Test fun exposesProjectOnlyConstructorSoPlatformCanInstantiateTheListener() {
        // The IntelliJ platform's message-bus resolver instantiates `<listener>` classes via
        // reflection and only tries constructors with shapes (), (Project), (CoroutineScope),
        // or (Project, CoroutineScope). The Kotlin default-arg form emits a single
        // `(Project, Function1)` constructor, which the resolver rejects with:
        //   "Cannot find suitable constructor, expected (Project), ..."
        // @JvmOverloads on the primary constructor also emits a `(Project)` bridge. Guard that
        // shape here so we don't regress again.
        GitBranchListener::class.java.getDeclaredConstructor(Project::class.java)
    }

    private fun fakeRepo(
        rootPath: String,
        branchName: String?,
    ): GitRepository =
        mockk(relaxed = true) {
            every { root.path } returns rootPath
            every { currentBranch } returns
                branchName?.let {
                    mockk(relaxed = true) { every { name } returns it }
                }
        }
}
