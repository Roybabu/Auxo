package com.auxo.pos.ui

import com.auxo.pos.ui.screens.onboarding.sproutStage
import org.junit.Assert.assertEquals
import org.junit.Test

class SproutStageTest {
    @Test
    fun threeSteps_growSmallMediumLarge() {
        assertEquals(listOf(0, 1, 2), (0..2).map { sproutStage(it, 3) })
    }

    @Test
    fun fiveSteps_growEvenly() {
        assertEquals(0, sproutStage(0, 5))
        assertEquals(1, sproutStage(2, 5))
        assertEquals(2, sproutStage(4, 5))
    }

    @Test
    fun outOfRangeSteps_areClamped() {
        assertEquals(0, sproutStage(-3, 3))
        assertEquals(2, sproutStage(9, 3))
        assertEquals(2, sproutStage(0, 1))
    }
}
