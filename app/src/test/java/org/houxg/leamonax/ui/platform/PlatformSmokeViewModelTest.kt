package org.houxg.leamonax.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSmokeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun runCheckUpdatesImmutableUiState() = runTest(dispatcher) {
        val viewModel = PlatformSmokeViewModel()

        viewModel.onEvent(PlatformSmokeEvent.RunCheck)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.checks)
    }
}
