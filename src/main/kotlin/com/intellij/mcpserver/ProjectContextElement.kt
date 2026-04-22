// Compile-time stub for com.intellij.mcpserver.ProjectContextElement. See package note in
// McpToolset.kt for why these stubs exist.
package com.intellij.mcpserver

import com.intellij.openapi.project.Project
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class ProjectContextElement(val project: Project) :
    AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<ProjectContextElement>
}
