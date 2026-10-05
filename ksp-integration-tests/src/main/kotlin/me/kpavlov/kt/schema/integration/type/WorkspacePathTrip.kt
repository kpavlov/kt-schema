package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.testfixtures.WorkspacePath
import me.kpavlov.kt.schema.testfixtures.Wrapper

@Schema
data class WorkspacePathTrip(
    val workspace: WorkspacePath,
    val count: Wrapper<Int>,
)
