package com.aritiq.calcnote.ui.home

import com.aritiq.calcnote.domain.NoteProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The welcome note demonstrates the calculator, so it is parsed by the real parser on every
 * keystroke the user makes in it. If a stray digit ever lands in the prose, the sample silently
 * starts summing the wrong thing and teaches the wrong grammar.
 */
class SampleNoteTest {

    @Test fun prose_does_not_leak_into_the_sum() {
        assertEquals(41.0, NoteProcessor.liveTotal(SAMPLE_CONTENT), 1e-9)
    }

    @Test fun title_comes_from_the_greeting() {
        assertEquals("Welcome to Aritiq", NoteProcessor.titleOf(SAMPLE_CONTENT))
    }

    @Test fun sample_closes_with_a_total_line() {
        // Without a trigger line the Σ bar stays empty and the grammar it teaches is invisible.
        assertTrue(NoteProcessor.isTotalTriggerLine(SAMPLE_CONTENT))
    }
}
