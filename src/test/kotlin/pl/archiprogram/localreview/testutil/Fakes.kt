package pl.archiprogram.localreview.testutil

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.vcs.BranchProvider
import java.util.concurrent.atomic.AtomicLong

/** Injectable clock. */
class FakeClock(initial: Long = 1_700_000_000_000L) {
    private val t = AtomicLong(initial)

    fun now(): Long = t.get()

    fun advance(millis: Long): Long = t.addAndGet(millis)

    fun set(millis: Long) {
        t.set(millis)
    }
}

/** Branch provider that returns scripted values; optionally throws. */
class FakeBranchProvider(
    var branch: String = Key.NO_BRANCH,
    var throwOnCall: Boolean = false,
) : BranchProvider {
    var callCount = 0
        private set

    override fun currentBranch(
        project: Project,
        repoRoot: VirtualFile,
    ): String {
        callCount++
        if (throwOnCall) throw IllegalStateException("scripted failure")
        return branch
    }
}
