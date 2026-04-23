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

    @Test fun default_state_has_mcp_tools_enabled() {
        assertTrue(
            "MCP tools must default to ON; changing this requires a conscious user decision.",
            LocalReviewSettings.State().enableMcpTools,
        )
    }

    @Test fun disabled_toggle_round_trips_through_persistence() {
        val settings = LocalReviewSettings()
        val modified = LocalReviewSettings.State(enableMcpTools = false)

        settings.loadState(modified)

        assertFalse(settings.current().enableMcpTools)
        assertEquals(false, settings.getState().enableMcpTools)
    }

    @Test fun re_enabled_toggle_round_trips() {
        val settings = LocalReviewSettings()
        settings.loadState(LocalReviewSettings.State(enableMcpTools = false))
        settings.loadState(LocalReviewSettings.State(enableMcpTools = true))

        assertTrue(settings.current().enableMcpTools)
    }
}
