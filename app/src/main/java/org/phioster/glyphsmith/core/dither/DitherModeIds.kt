package org.phioster.glyphsmith.core.dither

import org.phioster.glyphsmith.core.serial.WireIdSerializer

/**
 * What each dither algorithm is called in a saved preset.
 *
 * Named after the algorithm, not after the constant and not after the label: the constants are
 * abbreviated where the enum wanted them short (`MOD_WAVE`, `SINE_WAVE_MOD`) and the labels are
 * interface text that may be reworded or translated at any time. An id is neither — it is the
 * one spelling that is promised never to change.
 *
 * The order below follows the enum so the two can be read side by side. Exhaustive on purpose:
 * a style added without an id does not compile.
 */
object DitherModeIds : WireIdSerializer<DitherMode>(
    category = "dither",
    values = DitherMode.entries.toList(),
    idOf = { mode ->
        when (mode) {
            DitherMode.NONE -> "dither.none"

            // --- error diffusion, mostly by the name of whoever published the kernel ---
            DitherMode.FLOYD_STEINBERG -> "dither.floyd-steinberg"
            DitherMode.ATKINSON -> "dither.atkinson"
            DitherMode.JARVIS -> "dither.jarvis"
            DitherMode.SIERRA_LITE -> "dither.sierra-lite"
            DitherMode.STUCKI -> "dither.stucki"
            DitherMode.BURKES -> "dither.burkes"
            DitherMode.SIERRA -> "dither.sierra"
            DitherMode.SIERRA_TWO_ROW -> "dither.sierra-two-row"
            DitherMode.FALSE_FLOYD -> "dither.false-floyd-steinberg"
            DitherMode.STEVENSON_ARCE -> "dither.stevenson-arce"
            DitherMode.SMOOTH_DIFFUSE -> "dither.smooth-diffuse"
            DitherMode.RIEMERSMA -> "dither.riemersma"
            DitherMode.DIFFUSE_Y -> "dither.diffuse-y"
            DitherMode.DIFFUSE_X -> "dither.diffuse-x"

            // --- ordered matrices ---
            DitherMode.BAYER_2 -> "dither.bayer-2"
            DitherMode.BAYER_4 -> "dither.bayer-4"
            DitherMode.BAYER_8 -> "dither.bayer-8"
            DitherMode.BAYER_16 -> "dither.bayer-16"
            DitherMode.CLUSTER_4 -> "dither.clustered-dot-4"
            DitherMode.CLUSTER_8 -> "dither.clustered-dot-8"
            DitherMode.BLUE_NOISE_16 -> "dither.blue-noise-16"
            DitherMode.BLUE_NOISE_32 -> "dither.blue-noise-32"

            // --- modulation surfaces ---
            DitherMode.MOD_LINES -> "dither.modulation-lines"
            DitherMode.CUSTOM_SCREEN -> "dither.custom-screen"
            DitherMode.WAVE_DIFFUSE -> "dither.wave-diffuse"
            DitherMode.ORB_DIFFUSE -> "dither.orb-diffuse"
            DitherMode.MOD_WAVE -> "dither.modulation-wave"
            DitherMode.MOD_RINGS -> "dither.modulation-rings"
            DitherMode.MOD_ORB -> "dither.orb"
            DitherMode.BEEHIVE -> "dither.beehive"
            DitherMode.UNIFORM_MODULATION -> "dither.uniform-modulation"

            // --- patterns ---
            DitherMode.HEART_GRID -> "dither.heart-grid"
            DitherMode.POP_TONE -> "dither.pop-tone"
            DitherMode.CHECKERS -> "dither.checkers"
            DitherMode.DIAMOND -> "dither.diamond"
            DitherMode.CROSSHATCH -> "dither.crosshatch"
            DitherMode.STIPPLING -> "dither.stippling"
            DitherMode.BIT_TONE -> "dither.bit-tone"
            DitherMode.BLOCK_TONE -> "dither.block-tone"
            DitherMode.PRINT_PATTERN -> "dither.print-pattern"
            DitherMode.GRIDLOCK -> "dither.gridlock"
            DitherMode.RANDOM_ORDERED -> "dither.random-ordered"
            DitherMode.NOISE -> "dither.noise"
            DitherMode.WAVE -> "dither.wave"
            DitherMode.RADIAL_BURST -> "dither.radial-burst"
            DitherMode.SINE_DISTORT -> "dither.sine-distort"
            DitherMode.MOSAIC -> "dither.mosaic"
            DitherMode.SQUARE_MOSAIC -> "dither.square-mosaic"
            DitherMode.CIRCLE_GRID -> "dither.circle-grid"
            DitherMode.DIAMOND_GRID -> "dither.diamond-grid"

            // --- tessellations ---
            DitherMode.TRI_POLY -> "dither.tri-poly"
            DitherMode.HEXA_POLY -> "dither.hexa-poly"
            DitherMode.PENTA_POLY -> "dither.penta-poly"
            DitherMode.LOW_POLY -> "dither.low-poly"
            DitherMode.CAMO -> "dither.camo"

            // --- the literature ---
            DitherMode.OSTROMOUKHOV -> "dither.ostromoukhov"
            DitherMode.SHIAU_FAN -> "dither.shiau-fan"
            DitherMode.DOT_DIFFUSION -> "dither.dot-diffusion"
            DitherMode.GAUSSIAN -> "dither.gaussian"
            DitherMode.ARTIFACT_MODULATION -> "dither.artifact-modulation"
            DitherMode.VARIABLE_ERROR -> "dither.variable-error"
            DitherMode.FRACTAL_DIFFUSE -> "dither.fractal-diffuse"

            // --- signal surfaces ---
            DitherMode.WAVEFORM -> "dither.waveform"
            DitherMode.WAVEFORM_ALT -> "dither.waveform-alt"
            DitherMode.THRESHOLDER -> "dither.thresholder"
            DitherMode.SINE_WAVE_MOD -> "dither.sine-wave-modulation"
            DitherMode.TOPOGRAPHY -> "dither.topography"
            DitherMode.VORTEX -> "dither.vortex"
            DitherMode.RADIAL_PEAKS -> "dither.radial-peaks"
            DitherMode.DOTTED_LINES -> "dither.dotted-lines"
            DitherMode.DISPLACE_CONTOUR -> "dither.displace-contour"
            DitherMode.ORB_MATRIX -> "dither.orb-matrix"
            DitherMode.ORDERED_MODULATION -> "dither.ordered-modulation"

            // --- faults ---
            DitherMode.GLITCH -> "dither.glitch"
            DitherMode.CRACKED_DIFFUSE -> "dither.cracked-diffuse"
            DitherMode.ARTIFACT_GLITCH -> "dither.artifact-modulation-glitch"
            DitherMode.ATKINSON_VHS -> "dither.atkinson-vhs"
            DitherMode.ATKINSON_LINE_MOD -> "dither.atkinson-line-modulation"
            DitherMode.STUCKI_LINES -> "dither.stucki-lines"
            DitherMode.CONTRAST_AWARE_X -> "dither.contrast-aware-x"
            DitherMode.CONTRAST_AWARE_Y -> "dither.contrast-aware-y"
            DitherMode.VORTEX_DIFFUSION -> "dither.vortex-diffusion"
        }
    },
)
