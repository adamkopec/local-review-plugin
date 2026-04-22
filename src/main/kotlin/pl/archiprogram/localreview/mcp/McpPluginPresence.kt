package pl.archiprogram.localreview.mcp

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

private val MCP_SERVER_ID = PluginId.getId("com.intellij.mcpServer")

/**
 * True when the JetBrains MCP Server plugin is installed and enabled in this IDE. Used by the
 * settings UI to grey out the "Expose MCP tools" checkbox when there's nothing for it to do.
 *
 * Safe on IDEs that don't have the MCP plugin — [PluginManagerCore.getPlugin] returns null and
 * we report false. No MCP classes are referenced here.
 */
fun isMcpServerPluginAvailable(): Boolean {
    val descriptor = PluginManagerCore.getPlugin(MCP_SERVER_ID) ?: return false
    return !PluginManagerCore.isDisabled(MCP_SERVER_ID) && descriptor.isEnabled
}
