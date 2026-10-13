package com.auxo.pos.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Records everything [flow] emits from now until the test ends. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.record(flow: Flow<T>): List<T> {
    val list = mutableListOf<T>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { list += it } }
    return list
}

/** Keeps a StateFlow built with stateIn(WhileSubscribed) active for the rest of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepActive(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect() }
}

/**
 * Waits in real time (not the test clock) until [condition] holds. For code that works on database threads, which
 * the test scheduler cannot wait for.
 */
suspend fun awaitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) kotlinx.coroutines.delay(20)
    }
}
