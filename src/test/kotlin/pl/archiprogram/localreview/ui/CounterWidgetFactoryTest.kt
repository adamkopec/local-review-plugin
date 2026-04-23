package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterWidgetFactoryTest {

    private val factory = CounterWidgetFactory()

    @Test fun isAvailable_disposedProject_returnsFalse() {
        val project: Project = mockk { every { isDisposed } returns true }
        assertFalse(factory.isAvailable(project))
    }

    @Test fun isAvailable_activeProject_returnsTrue() {
        // Must not gate on VCS here: StatusBarWidgetsManager populates widgets before
        // VCS mappings settle and does not re-evaluate on VCS_CONFIGURATION_CHANGED,
        // so any VCS-based gate would lose the race and the widget would never appear.
        val project: Project = mockk { every { isDisposed } returns false }
        assertTrue(factory.isAvailable(project))
    }
}