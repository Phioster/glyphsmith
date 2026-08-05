package org.phioster.glyphsmith

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One walk through the whole application, on a real Android.
 *
 * Deliberately not a broad test suite. Breadth belongs with the JVM tests, which run in seconds
 * and can afford to be many; what an emulator buys is the class of fault none of them can see —
 * a tab that crashes the moment it is opened, a panel that will not compose, an activity that
 * does not survive being started. Those do not fail an assertion. They fail by the app not being
 * there any more.
 *
 * So the shape is: stand the real app up, give it a real image, open every tab in turn, and
 * check afterwards that it is still running. Anything that throws while composing takes the test
 * with it, which is the point.
 */
@RunWith(AndroidJUnit4::class)
class AppWalkthroughTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Every tab the app offers, by the label the row draws. */
    private val tabs = listOf("SET", "MAP", "COLOUR", "FX", "LYR", "ANIM", "OUT", "PRE")

    /**
     * A picture written to the app's own cache and handed over as a `file://` Uri.
     *
     * The panels are mostly empty without a source, and an empty panel cannot crash in the ways
     * worth catching. Going through `loadImage` rather than reaching into the view model keeps
     * the test on the same path a user takes — the decode included.
     */
    private fun loadAnImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "walkthrough-source.png")
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // A gradient with a disc in it: something for every algorithm to bite on.
                    val disc = if ((x - 160) * (x - 160) + (y - 120) * (y - 120) < 3000) 90 else 0
                    val v = (x * 200 / width + disc).coerceAtMost(255)
                    setPixel(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
                }
            }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        // The same instance the composable holds: `viewModel()` inside `setContent` resolves
        // against the activity's own store, so asking the provider for it here hands back that
        // one rather than a second. Reaching it this way keeps `MainActivity` free of a property
        // that exists only for a test.
        rule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[GlyphsmithViewModel::class.java].loadImage(Uri.fromFile(file))
        }
        rule.waitForIdle()
    }

    @Test
    fun everyTabOpensWithAnImageLoaded() {
        loadAnImage()

        tabs.forEach { label ->
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
            // Still there after the panel composed: the tab row is the thing that would be gone
            // if the panel had thrown.
            rule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun everyTabOpensWithNoImageAtAll() {
        // The state the app starts in, and the one hand testing skips because the first thing
        // anybody does is load a picture.
        tabs.forEach { label ->
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
            rule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /**
     * And the one interaction that reaches the whole pipeline: applying a preset re-renders
     * everything at once — sampling, dither, effects, layers.
     */
    @Test
    fun applyingAPresetRendersWithoutFalling() {
        loadAnImage()

        rule.onNodeWithText("PRE").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("one bit").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("PRE").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { activity ->
            val state = ViewModelProvider(activity)[GlyphsmithViewModel::class.java].state.value
            assertTrue("the render produced nothing", state.preview != null)
            assertTrue("the source was lost", state.hasImage)
        }
    }
}
