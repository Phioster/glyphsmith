package org.phioster.glyphsmith

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.glyphsmith.render.RenderMode

/**
 * The state the app opens with.
 *
 * [org.phioster.glyphsmith.render.RenderSettings.newSession] says which mode new work starts in;
 * this is the one place that has to actually use it. A fresh [UiState] is what the first frame
 * is drawn from, and it is free to build its own params — so the value it holds is worth
 * pinning separately from the factory that is supposed to supply it.
 *
 * Kept apart from `SessionDefaultTest` on purpose: this reaches into the view model's package
 * and only runs where the Android sources compile.
 */
class UiStateDefaultTest {

    @Test
    fun `a fresh project state is in pixel dither`() {
        assertEquals(RenderMode.PurePixel, UiState().params.renderMode)
    }
}
