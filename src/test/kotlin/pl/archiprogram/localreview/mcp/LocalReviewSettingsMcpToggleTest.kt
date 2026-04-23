package pl.archiprogram.localreview.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.archiprogram.localreview.settings.LocalReviewSettings

/**
 * Guards the user-facing decision that MCP tools default to **on**. If a future refactor silently
 * flips the default, this test trips before the change ships.
 */
class LocalReviewSettingsMcpToggleTest {
    @Test fun defaultStateHasMcpToolsEnabled() {
        assertTrue(
            "MCP tools must default to ON; changing this requires a conscious user decision.",
            LocalReviewSettings.State().enableMcpTools,
        )
    }

    @Test fun disabledToggleRoundTripsThroughPersistence() {
        val settings = LocalReviewSettings()
        val modified = LocalReviewSettings.State(enableMcpTools = false)

        settings.loadState(modified)

        assertFalse(settings.current().enableMcpTools)
        assertEquals(false, settings.getState().enableMcpTools)
    }

    @Test fun reEnabledToggleRoundTrips() {
        val settings = LocalReviewSettings()
        settings.loadState(LocalReviewSettings.State(enableMcpTools = false))
        settings.loadState(LocalReviewSettings.State(enableMcpTools = true))

        assertTrue(settings.current().enableMcpTools)
    }
}
