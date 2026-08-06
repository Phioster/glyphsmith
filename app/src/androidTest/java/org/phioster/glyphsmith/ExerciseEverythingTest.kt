package org.phioster.glyphsmith

import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
// `onAllNodes` is a member of SemanticsNodeInteractionsProvider, not a free function — importing
// it is what the second run rejected. `onRoot` and `onNodeWithText` really are extensions.
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
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
        "svg", "txt", "html", "ansi", "gif", "mp4", "camera",
        // "capture" was here for the camera and the only thing it ever blocked was
        // "[capture current settings as a layer]" — the whole of the LYR tab, which the notes
        // duly reported as nought controls clicked. The camera button is labelled "[cam]".
        // Added after the first successful run: the preset shelf reported "preset deleted" and
        // it was this walk that did it. Everything else here is undone by the next run; a
        // deleted user preset is not.
        "del]", "delete",
    )

    /**
     * The directory Gradle collects after the run, if it named one.
     *
     * Anywhere the test can obviously write is somewhere the pictures cannot be fetched from,
     * and it took three runs to see why: `connectedAndroidTest` **uninstalls the app** when it
     * finishes, so by the time anything on the host reaches for them, the app's storage — private
     * and external alike — is gone with the package. `adb pull` said the directory did not exist
     * and `run-as` said the package did not exist, and both were telling the truth.
     *
     * `additionalTestOutputDir` is the instrumentation argument the Android Gradle plugin passes
     * for exactly this, and it copies that directory off the device *before* the uninstall, into
     * `app/build/outputs/connected_android_test_additional_output/`. Falling back to private
     * storage when the argument is absent keeps the test runnable straight from an IDE, where the
     * pictures then stay on the device and nothing has taken them away.
     */
    private val shots: File by lazy {
        val given = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val base = given?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(base, "shots").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** Shots that could not be taken at all, reported together at the end of the walk. */
    private val missed = mutableListOf<String>()

    /** What the walk did on each tab, written out beside the pictures. */
    private val notes = StringBuilder()

    /**
     * A picture of the screen, through UiAutomation rather than Compose.
     *
     * `captureToImage()` was the obvious call and it does not work here: it reads the pixels back
     * out of the composition's own surface, and on a software-rendered emulator that read fails —
     * the first run of this test died on "Failed to capture a node to bitmap", with the graphics
     * layer logging `Failed to find ColorBuffer` underneath it. UiAutomation takes the screenshot
     * the system takes, which is both robust to that and the more honest answer to the question
     * being asked: this is the screen as the device would show it, dialogs and system bars and
     * all, not a redraw of one composable.
     *
     * A failure here is recorded rather than thrown, so one unlucky frame does not cost the whole
     * walk; [everyControlOnEveryTab] fails at the end if anything was missed.
     */
    private fun shot(name: String) {
        // The screenshot comes from the system, not from the composition, so "Compose is idle"
        // is not the same as "the screen has stopped moving". A short settle in front of every
        // capture is the difference between a picture of a panel and a picture of an animation.
        rule.waitForIdle()
        Thread.sleep(SETTLE)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (bitmap == null) {
            missed += name
            return
        }
        File(shots, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
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

    /**
     * Clicks every clickable that is not on the avoid list, one at a time, top to bottom.
     *
     * Bounded, and the bound is not decoration. The list is re-read each time round because a
     * click can reveal controls that were not there before — which is the point — but some of
     * them *add* controls: `[+ segment]` on the ANIM tab appends a segment with its own row of
     * them, so the walk clicks it, finds more to click, and clicks it again. That pass took
     * thirteen minutes on its own before this budget existed. A run that never ends is not a
     * thorough run.
     */
    private fun clickEverySafeControl(provider: SemanticsNodeInteractionsProvider): Int {
        var clicked = 0
        var index = 0
        while (clicked < CLICK_BUDGET) {
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

    /**
     * Closes a menu the walk left standing open.
     *
     * The ANIM tab's curve menu was open when its screenshot was taken, so the picture recorded
     * the menu instead of the panel it was meant to show. Only when there is actually a popup:
     * a back press on a screen with nothing over it would leave the activity, and the walk would
     * end early with everything apparently fine.
     *
     * Through UiAutomation rather than Espresso, so this stays on the one dependency the file
     * already has.
     */
    private fun popups() = rule.onAllNodes(isPopup()).fetchSemanticsNodes().size

    /**
     * Closes menus the walk left standing open, and stops the moment a press achieves nothing.
     *
     * Two lessons are built into this. Menus stack — the ANIM tab carries a curve menu and a
     * property menu per segment — so one press is not enough. And the press has to reach the
     * menu's own window: `UiAutomation.performGlobalAction` goes through the accessibility
     * service and left this menu exactly where it was, twelve times in a row, while the counter
     * happily reported twelve closures. `sendKeyDownUpSync` injects the key into the focused
     * window, which is the popup.
     *
     * Counting what actually closed rather than what was attempted is the other half of that: a
     * count of presses reads as success and is a lie. If a press changes nothing the loop stops,
     * and whatever is left is reported by the caller.
     */
    private fun dismissAnyPopup(): Int {
        var closed = 0
        repeat(POPUP_LIMIT) {
            val before = popups()
            if (before == 0) return closed
            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            // Waiting for the menu to be gone, not merely for the composition to settle: an
            // earlier attempt did dismiss one and the picture still had it in it, torn across two
            // frames, because the capture went out while it was animating away.
            runCatching { rule.waitUntil(TWO_SECONDS) { popups() < before } }
            if (popups() >= before) return closed
            closed++
        }
        return closed
    }

    @Test
    fun everyControlOnEveryTab() {
        loadAnImage()
        shot("00-start")

        tabs.forEachIndexed { i, tab ->
            rule.onNodeWithText(tab).performScrollTo().performClick()
            rule.waitForIdle()
            shot("%02d-%s-opened".format(i + 1, tab.lowercase()))

            val moved = moveEverySlider()
            shot("%02d-%s-sliders".format(i + 1, tab.lowercase()))

            val clicked = clickEverySafeControl(rule)
            // A second pass, and the notes are why it exists: FX, COLOUR, ANIM and LYR all
            // reported nought sliders moved, and they are full of them. A dither's parameters,
            // an effect's amount, an animation's depth — none of those exist until the thing
            // they belong to is switched on, and switching on happens here. One pass moved the
            // sliders that were there at rest and never came back for the ones it had revealed.
            val revealed = moveEverySlider()
            val closed = dismissAnyPopup()
            val left = popups()
            shot("%02d-%s-controls".format(i + 1, tab.lowercase()))

            notes.appendLine(
                "$tab: $moved sliders, $clicked controls, $revealed sliders after" +
                    (if (clicked >= CLICK_BUDGET) ", budget reached" else "") +
                    (if (closed > 0) ", closed $closed menus" else "") +
                    // Said out loud, because a menu in the picture with nothing in the notes is
                    // what sent me looking for a bug in the dismissal three times over.
                    (if (left > 0) ", $left STILL OPEN" else ""),
            )
        }

        // Written beside the pictures, because the pictures do not say how they came about. When
        // a menu was still standing open in one of them, the question was whether the walk had
        // failed to close it or had closed it too late to matter — and there was nothing to read.
        File(shots, "notes.txt").writeText(notes.toString())

        // Still alive after all of that, which is the thing the walk itself asserts.
        rule.onNodeWithText("SET").assertExists()

        // And the pictures actually landed. Checked at the end rather than at each capture so a
        // run that loses one frame still leaves the other twenty-four to look at.
        assertTrue("no screenshots were written at all", shots.listFiles().orEmpty().isNotEmpty())
        assertTrue("screens not captured: $missed", missed.isEmpty())
    }

    private companion object {
        /**
         * How many clicks one tab is worth.
         *
         * Forty was a guess and the notes showed it was too low: FX, COLOUR, ANIM and PRE all
         * reached it, so four tabs were being cut short rather than only the one that breeds
         * controls. Eighty still bounds `[+ segment]`, which is the whole reason for a budget,
         * and the notes now say when a tab hits the limit — so the next wrong guess is visible
         * rather than silent.
         */
        const val CLICK_BUDGET = 80

        /** How long to let the screen stop moving before photographing it. */
        const val SETTLE = 250L

        const val TWO_SECONDS = 2_000L

        /**
         * How many stacked menus to close before giving up and photographing what is there.
         *
         * Four was not enough — the notes said "closed 4 menus" and the picture still had one in
         * it. The ANIM tab carries a curve menu and a property menu per segment, and the walk
         * adds segments, so the stack is as deep as the panel is long. The loop stops as soon as
         * none is left; this only bounds the pathological case.
         */
        const val POPUP_LIMIT = 12
    }
}
