package org.phioster.glyphsmith

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every control on every tab, moved once, with a picture taken after each screen.
 *
 * The walkthrough next door proves the app stands up. This one asks a different question: does
 * anything break when a control is actually *used*. It finds the sliders by their semantics
 * rather than by name — `TerminalSlider` wraps a Material `Slider`, so every one of them carries
 * `SetProgress` — and the buttons by their click action, so a control added next month is
 * exercised without this file being touched.
 *
 * The screenshots are the real output. Nothing here asserts what a screen should look like,
 * because no assertion can say "the preview went blank" or "that label is cut in half". They are
 * pulled off the device afterwards and looked at — by a person, or by a model asked to say which
 * ones look wrong. What the test itself guarantees is narrower and still worth having: none of
 * this made the app fall over.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseEverythingTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val tabs = listOf("SET", "MAP", "COLOUR", "FX", "LYR", "ANIM", "OUT", "PRE")

    /**
     * Controls that hand the screen to something outside the app, or that would spend the run
     * writing files.
     *
     * Matched on the label rather than by a test tag, because a tag on every export button is
     * production code carrying the weight of a test. The list is short, explicit, and the cost
     * of it being wrong is a run that stalls in a share sheet rather than a bug getting through.
     */
    private val avoid = listOf(
        "share", "save", "copy", "pick", "load", "import", "export",
        "svg", "txt", "html", "ansi", "gif", "mp4", "camera", "capture",
    )

    private val shots: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "shots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun shot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(shots, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun loadAnImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "exercise-source.png")
        val bitmap = Bitmap.createBitmap(360, 280, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val disc = if ((x - 180) * (x - 180) + (y - 140) * (y - 140) < 4000) 100 else 0
                    val v = (x * 190 / width + y * 40 / height + disc).coerceAtMost(255)
                    setPixel(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
                }
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        rule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[GlyphsmithViewModel::class.java].loadImage(Uri.fromFile(file))
        }
        rule.waitForIdle()
    }

    /**
     * Pushes every slider on the current screen to a value it was not at.
     *
     * Through the semantics action rather than by dragging: a drag depends on where the thumb
     * happens to be and on the track being fully on screen, and neither is true for a panel that
     * scrolls. `SetProgress` is what a screen reader would use, so it is also the path that has
     * to keep working.
     */
    private fun moveEverySlider(): Int {
        // Matched on the action itself rather than on a named matcher, which is both the plainer
        // statement of what a slider is here and one fewer API name to be wrong about.
        val isSlider = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        var index = 0
        while (true) {
            // Re-fetched every time: setting one can add or remove others — the scanline spacing
            // slider only exists once scanlines are above zero.
            val nodes = rule.onAllNodes(isSlider).fetchSemanticsNodes()
            if (index >= nodes.size) break

            val info = nodes[index].config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            if (info != null) {
                val from = info.range.start
                val to = info.range.endInclusive
                // Away from wherever it is, so a slider already at one end still moves.
                val target = if (info.current > (from + to) / 2f) {
                    from + (to - from) * 0.25f
                } else {
                    from + (to - from) * 0.75f
                }
                runCatching {
                    rule.onAllNodes(isSlider)[index]
                        .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(target) }
                    rule.waitForIdle()
                }
            }
            index++
        }
        return index
    }

    /** Clicks every clickable that is not on the avoid list, one at a time, top to bottom. */
    private fun clickEverySafeControl(provider: SemanticsNodeInteractionsProvider): Int {
        var clicked = 0
        var index = 0
        while (true) {
            val nodes = provider.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            if (index >= nodes.size) break
            val label = nodes[index].config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
                ?.lowercase()
                .orEmpty()
            if (label.isNotEmpty() && avoid.none { it in label } && label !in tabs.map { it.lowercase() }) {
                runCatching {
                    provider.onAllNodes(hasClickAction())[index].performScrollTo().performClick()
                    rule.waitForIdle()
                    clicked++
                }
            }
            index++
        }
        return clicked
    }

    @Test
    fun everyControlOnEveryTab() {
        loadAnImage()
        shot("00-start")

        tabs.forEachIndexed { i, tab ->
            rule.onNodeWithText(tab).performScrollTo().performClick()
            rule.waitForIdle()
            shot("%02d-%s-opened".format(i + 1, tab.lowercase()))

            moveEverySlider()
            shot("%02d-%s-sliders".format(i + 1, tab.lowercase()))

            clickEverySafeControl(rule)
            shot("%02d-%s-controls".format(i + 1, tab.lowercase()))
        }

        // Still alive after all of that, which is the only thing this file asserts.
        rule.onNodeWithText("SET").assertExists()
    }
}
