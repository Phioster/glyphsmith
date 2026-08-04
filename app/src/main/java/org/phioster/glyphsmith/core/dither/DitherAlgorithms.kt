package org.phioster.glyphsmith.core.dither

/**
 * Which algorithm each style is, in one table.
 *
 * This is the only place a `when (DitherMode)` is meant to survive. Something has to map a
 * constant to the thing behind it, and doing it once — exhaustively, so that the compiler
 * refuses a style that was added without saying how it works — is what lets every other
 * dispatch go away. The rest of the app asks the provider, not the enum.
 *
 * Read as a list, it is also the catalogue: eighty styles, each on one line, saying what kind of
 * thing it is and what its controls are called.
 */
internal object DitherAlgorithms {

    /**
     * Built once, indexed by ordinal.
     *
     * The lookup is on the per-cell path — the threshold of every cell of every frame goes
     * through it — so it is an array rather than a map, and the declarations are shared rather
     * than rebuilt. Nothing here is stateful: two renders that ask for the same style get the
     * same object.
     */
    private val byMode: Array<DitherAlgorithm> =
        Array(DitherMode.entries.size) { declare(DitherMode.entries[it]) }

    fun of(mode: DitherMode): DitherAlgorithm = byMode[mode.ordinal]

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun declare(mode: DitherMode): DitherAlgorithm = when (mode) {
        DitherMode.NONE -> NoDither

        // --- error diffusion ----------------------------------------------------------------
        DitherMode.FLOYD_STEINBERG -> DiffusionKernels.FLOYD_STEINBERG
        DitherMode.ATKINSON -> DiffusionKernels.ATKINSON
        DitherMode.JARVIS -> DiffusionKernels.JARVIS
        DitherMode.SIERRA_LITE -> DiffusionKernels.SIERRA_LITE
        DitherMode.STUCKI -> DiffusionKernels.STUCKI
        DitherMode.BURKES -> DiffusionKernels.BURKES
        DitherMode.SIERRA -> DiffusionKernels.SIERRA
        DitherMode.SIERRA_TWO_ROW -> DiffusionKernels.SIERRA_TWO_ROW
        DitherMode.FALSE_FLOYD -> DiffusionKernels.FALSE_FLOYD
        DitherMode.STEVENSON_ARCE -> DiffusionKernels.STEVENSON_ARCE
        DitherMode.SMOOTH_DIFFUSE -> DiffusionKernels.SMOOTH_DIFFUSE
        DitherMode.DIFFUSE_Y -> DiffusionKernels.DIFFUSE_Y
        DitherMode.DIFFUSE_X -> DiffusionKernels.DIFFUSE_X
        DitherMode.OSTROMOUKHOV -> DiffusionKernels.OSTROMOUKHOV
        DitherMode.SHIAU_FAN -> DiffusionKernels.SHIAU_FAN
        DitherMode.GAUSSIAN -> DiffusionKernels.GAUSSIAN
        DitherMode.ARTIFACT_MODULATION -> DiffusionKernels.ARTIFACT_MODULATION
        DitherMode.VARIABLE_ERROR -> DiffusionKernels.VARIABLE_ERROR
        DitherMode.CRACKED_DIFFUSE -> DiffusionKernels.CRACKED_DIFFUSE
        DitherMode.ARTIFACT_GLITCH -> DiffusionKernels.ARTIFACT_GLITCH
        DitherMode.ATKINSON_VHS -> DiffusionKernels.ATKINSON_VHS
        DitherMode.ATKINSON_LINE_MOD -> DiffusionKernels.ATKINSON_LINE_MOD
        DitherMode.STUCKI_LINES -> DiffusionKernels.STUCKI_LINES

        // --- ordered matrices ---------------------------------------------------------------
        DitherMode.BAYER_2, DitherMode.BAYER_4, DitherMode.BAYER_8, DitherMode.BAYER_16,
        DitherMode.CLUSTER_4, DitherMode.CLUSTER_8,
        DitherMode.BLUE_NOISE_16, DitherMode.BLUE_NOISE_32,
        -> OrderedMatrix()

        // --- modulation ---------------------------------------------------------------------
        DitherMode.MOD_LINES -> Modulation()
        DitherMode.MOD_WAVE -> Modulation()
        DitherMode.MOD_RINGS -> Modulation()
        DitherMode.MOD_ORB -> Modulation()
        DitherMode.BEEHIVE -> Modulation()
        DitherMode.UNIFORM_MODULATION -> Modulation()
        DitherMode.HEART_GRID -> Modulation()
        DitherMode.POP_TONE -> Modulation()
        DitherMode.CHECKERS -> Modulation(periodLabel = "block size")
        DitherMode.DIAMOND -> Modulation()
        DitherMode.CROSSHATCH -> Modulation(
            periodLabel = "line spacing",
            densityLabel = "line weight",
            readsContent = true,
        )

        DitherMode.STIPPLING -> Modulation(
            periodLabel = "dot spacing",
            densityLabel = "dot size",
            readsContent = true,
        )

        DitherMode.BIT_TONE -> Modulation(periodLabel = "dot size")
        DitherMode.BLOCK_TONE -> Modulation(periodLabel = "dot size")
        DitherMode.PRINT_PATTERN -> Modulation(periodLabel = "dot scale")
        DitherMode.GRIDLOCK -> Modulation(periodLabel = "grid size")
        DitherMode.RANDOM_ORDERED -> Modulation(periodLabel = "block size")
        DitherMode.NOISE -> Modulation(periodLabel = "grain")
        DitherMode.WAVE -> Modulation(periodLabel = "frequency")
        DitherMode.RADIAL_BURST -> Modulation(
            periodLabel = "ring spacing",
            densityLabel = "spokes",
        )

        DitherMode.SINE_DISTORT -> Modulation(
            periodLabel = "frequency",
            densityLabel = "second frequency",
        )

        DitherMode.WAVEFORM -> Modulation(
            periodLabel = "wavelength",
            densityLabel = "frequency range",
            readsContent = true,
        )

        DitherMode.WAVEFORM_ALT -> Modulation(
            periodLabel = "wavelength",
            densityLabel = "bend",
            readsContent = true,
        )

        DitherMode.THRESHOLDER -> Modulation(
            densityLabel = "content weight",
            readsContent = true,
        )

        DitherMode.SINE_WAVE_MOD -> Modulation(periodLabel = "wavelength", readsContent = true)
        DitherMode.TOPOGRAPHY -> Modulation(
            periodLabel = "contour spacing",
            densityLabel = "warp",
            readsContent = true,
        )

        DitherMode.VORTEX -> Modulation(periodLabel = "ring spacing", densityLabel = "twist")
        DitherMode.RADIAL_PEAKS -> Modulation(
            periodLabel = "ring spacing",
            densityLabel = "interference",
            readsContent = true,
        )

        DitherMode.DOTTED_LINES -> Modulation(
            periodLabel = "line spacing",
            densityLabel = "dotting",
        )

        DitherMode.DISPLACE_CONTOUR -> Modulation(
            periodLabel = "contour spacing",
            densityLabel = "displacement",
            readsContent = true,
        )

        DitherMode.ORB_MATRIX -> Modulation()
        DitherMode.ORDERED_MODULATION -> Modulation(periodLabel = "sequence length")
        DitherMode.GLITCH -> Modulation(periodLabel = "band height")

        /*
         * --- resolved up front, by walking an order of their own -----------------------------
         *
         * These carry no kernel even where they plainly diffuse. Knuth's dot diffusion decides
         * its own order and its own neighbours, Riemersma and Fractal Diffuse follow a curve,
         * and Contrast Aware measures a neighbourhood a per-cell kernel cannot see. All of them
         * hand back a finished grid, so there are no taps for the row loop to read.
         */
        DitherMode.RIEMERSMA, DitherMode.DOT_DIFFUSION, DitherMode.FRACTAL_DIFFUSE,
        DitherMode.CONTRAST_AWARE_X, DitherMode.CONTRAST_AWARE_Y, DitherMode.VORTEX_DIFFUSION,
        -> Precomputed()

        // --- resolved up front, by flattening areas ------------------------------------------
        DitherMode.MOSAIC -> Precomputed(periodLabel = "tile size", flattensRegions = true)
        DitherMode.SQUARE_MOSAIC -> Precomputed(
            periodLabel = "tile size",
            densityLabel = "grout",
            flattensRegions = true,
        )

        DitherMode.CIRCLE_GRID -> Precomputed(
            periodLabel = "grid size",
            densityLabel = "fill",
            flattensRegions = true,
        )

        DitherMode.DIAMOND_GRID -> Precomputed(
            periodLabel = "grid size",
            densityLabel = "fill",
            flattensRegions = true,
        )

        DitherMode.TRI_POLY -> Precomputed(periodLabel = "triangle size", flattensRegions = true)
        DitherMode.HEXA_POLY -> Precomputed(periodLabel = "hex size", flattensRegions = true)
        DitherMode.PENTA_POLY -> Precomputed(
            periodLabel = "hex size",
            densityLabel = "split direction",
            flattensRegions = true,
        )

        DitherMode.LOW_POLY -> Precomputed(
            periodLabel = "triangle size",
            densityLabel = "triangle type",
            flattensRegions = true,
        )

        DitherMode.CAMO -> Precomputed(periodLabel = "cell size", flattensRegions = true)
    }
}
