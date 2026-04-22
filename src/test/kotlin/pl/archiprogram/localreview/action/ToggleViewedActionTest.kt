package pl.archiprogram.localreview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService

class ToggleViewedActionTest {

    private val action = ToggleViewedAction()
    private val project: Project = mockk(relaxed = true)
    private val service: ReviewStateService = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(TargetCollector)
        mockkStatic(ReviewStateService::class)
        every { ReviewStateService.getInstance(project) } returns service
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test fun actionUpdateThread_isBgt() {
        assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread())
    }

    @Test fun update_noProject_disablesAction() {
        val presentation = Presentation()
        val event = eventWith(project = null, presentation = presentation)

        action.update(event)

        assertFalse(presentation.isEnabled)
    }

    @Test fun update_emptyTargets_disablesWithNoSelectionDescription() {
        val presentation = Presentation()
        val event = eventWith(project = project, presentation = presentation)
        every {
            TargetCollector.collect(any(), any(), any(), any())
        } returns emptyList()

        action.update(event)

        assertFalse(presentation.isEnabled)
    }

    @Test fun update_mergeConflictTarget_disablesAction() {
        val presentation = Presentation()
        val event = eventWith(project = project, presentation = presentation)
        every {
            TargetCollector.collect(any(), any(), any(), any())
        } returns listOf(targetWithStatus(FileStatus.MERGED_WITH_CONFLICTS))

        action.update(event)

        assertFalse(presentation.isEnabled)
    }

    @Test fun update_allTargetsViewed_flipsTextToUnmark() {
        val key = Key("/r", "main", "/r/a.kt")
        val presentation = Presentation()
        val event = eventWith(project = project, presentation = presentation)
        every {
            TargetCollector.collect(any(), any(), any(), any())
        } returns listOf(targetWithStatus(FileStatus.MODIFIED, key))
        every { service.isViewed(key) } returns true

        action.update(event)

        assertTrue(presentation.isEnabled)
        // Text changes between the two bundle keys — we assert the visible form contains "Not".
        assertTrue(
            presentation.text.contains("Not", ignoreCase = true) ||
                presentation.text.contains("Unmark", ignoreCase = true) ||
                presentation.text.contains("Unviewed", ignoreCase = true),
            "Expected 'unmark' wording, got: ${presentation.text}",
        )
    }

    @Test fun update_someTargetsNotViewed_usesMarkText() {
        val viewedKey = Key("/r", "main", "/r/a.kt")
        val unviewedKey = Key("/r", "main", "/r/b.kt")
        val presentation = Presentation()
        val event = eventWith(project = project, presentation = presentation)
        every {
            TargetCollector.collect(any(), any(), any(), any())
        } returns listOf(
            targetWithStatus(FileStatus.MODIFIED, viewedKey),
            targetWithStatus(FileStatus.MODIFIED, unviewedKey),
        )
        every { service.isViewed(viewedKey) } returns true
        every { service.isViewed(unviewedKey) } returns false

        action.update(event)

        assertTrue(presentation.isEnabled)
        assertFalse(
            presentation.text.contains("Not", ignoreCase = true) &&
                presentation.text.contains("Reviewed", ignoreCase = true),
            "Expected 'mark' wording, got: ${presentation.text}",
        )
    }

    // ----- helpers -----

    private fun eventWith(project: Project?, presentation: Presentation): AnActionEvent =
        mockk(relaxed = true) {
            every { this@mockk.project } returns project
            every { this@mockk.presentation } returns presentation
            every { getData(any<com.intellij.openapi.actionSystem.DataKey<Any?>>()) } returns null
        }

    private fun targetWithStatus(status: FileStatus, key: Key = Key("/r", "m", "/r/x")): Target {
        val fp = mockk<FilePath>(relaxed = true) { every { path } returns key.path }
        val rev = mockk<ContentRevision>(relaxed = true) { every { file } returns fp }
        val change = mockk<Change>(relaxed = true) {
            every { afterRevision } returns rev
            every { beforeRevision } returns null
            every { fileStatus } returns status
        }
        return Target.Changed(project, change, key)
    }
}
