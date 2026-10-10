package com.personalticktick.app.ui

import com.personalticktick.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun roomSnapshotPowersUiAndCompletingTaskUsesRepository() = runTest(dispatcher) {
        val remote = Task("server-task", title = "服务端任务", createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        val gateway = FakeProductionGateway(listOf(remote))
        val viewModel = TickTickViewModel(gateway)
        advanceUntilIdle()
        assertEquals(listOf("server-task"), viewModel.state.value.tasks.map { it.id })

        viewModel.toggleComplete("server-task")
        advanceUntilIdle()
        assertEquals(listOf("server-task"), gateway.completed)
    }
}

private class FakeProductionGateway(initial: List<Task>) : TaskUiGateway {
    private var tasks = initial
    val completed = mutableListOf<String>()
    override fun observeAll() = flowOf(tasks)
    override suspend fun save(task: Task) { tasks = tasks.filterNot { it.id == task.id } + task }
    override suspend fun complete(id: String) { completed += id }
    override suspend fun restore(id: String) = Unit
    override suspend fun delete(id: String, futureSeries: Boolean) = Unit
}
