package me.kpavlov.kt.schema.integration.type

import me.kpavlov.kt.schema.Schema
import me.kpavlov.kt.schema.test.fixtures.WorkspacePath
import me.kpavlov.kt.schema.test.fixtures.Wrapper

@Schema
data class WorkspacePathTrip(
    val workspace: WorkspacePath,
    val count: Wrapper<Int>,
)
