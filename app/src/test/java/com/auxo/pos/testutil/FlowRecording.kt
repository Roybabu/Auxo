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
