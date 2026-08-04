package org.phioster.glyphsmith.core.dither

/**
 * Where each diffusing style sends a cell's error.
 *
 * Weights sum to 1 for every kernel **except the Atkinson family**, which deliberately throws
 * away a quarter of the error — that loss is exactly what gives Atkinson its higher-contrast,
 * less muddy look, so it is not normalised away.
 *
 * One declaration per style, named after it. They were a single 300-line `when (mode)`, which
 * meant that adding a kernel and mistyping a neighbour's weight were the same edit to the same
 * expression; here the neighbours are not in scope.
 */
internal object DiffusionKernels {

    val FLOYD_STEINBERG = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 7 / 16f),
            DiffusionTap(-1, 1, 3 / 16f),
            DiffusionTap(0, 1, 5 / 16f),
            DiffusionTap(1, 1, 1 / 16f),
        ),
    )

    val ATKINSON = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 1 / 8f),
            DiffusionTap(2, 0, 1 / 8f),
            DiffusionTap(-1, 1, 1 / 8f),
            DiffusionTap(0, 1, 1 / 8f),
            DiffusionTap(1, 1, 1 / 8f),
            DiffusionTap(0, 2, 1 / 8f),
        ),
    )

    val JARVIS = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 7 / 48f),
            DiffusionTap(2, 0, 5 / 48f),
            DiffusionTap(-2, 1, 3 / 48f),
            DiffusionTap(-1, 1, 5 / 48f),
            DiffusionTap(0, 1, 7 / 48f),
            DiffusionTap(1, 1, 5 / 48f),
            DiffusionTap(2, 1, 3 / 48f),
            DiffusionTap(-2, 2, 1 / 48f),
            DiffusionTap(-1, 2, 3 / 48f),
            DiffusionTap(0, 2, 5 / 48f),
            DiffusionTap(1, 2, 3 / 48f),
            DiffusionTap(2, 2, 1 / 48f),
        ),
    )

    val SIERRA_LITE = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 2 / 4f),
            DiffusionTap(-1, 1, 1 / 4f),
            DiffusionTap(0, 1, 1 / 4f),
        ),
    )

    // The published kernels below are transcribed from the halftoning literature, where each is
    // named after whoever proposed it. Their weights are the whole algorithm — what differs
    // between them is only how far and how evenly the error is spread, and that is exactly what
    // makes their grain look different.
    val STUCKI = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 8 / 42f),
            DiffusionTap(2, 0, 4 / 42f),
            DiffusionTap(-2, 1, 2 / 42f),
            DiffusionTap(-1, 1, 4 / 42f),
            DiffusionTap(0, 1, 8 / 42f),
            DiffusionTap(1, 1, 4 / 42f),
            DiffusionTap(2, 1, 2 / 42f),
            DiffusionTap(-2, 2, 1 / 42f),
            DiffusionTap(-1, 2, 2 / 42f),
            DiffusionTap(0, 2, 4 / 42f),
            DiffusionTap(1, 2, 2 / 42f),
            DiffusionTap(2, 2, 1 / 42f),
        ),
    )

    /** Stucki with the bottom row dropped — faster, and a touch sharper for it. */
    val BURKES = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 8 / 32f),
            DiffusionTap(2, 0, 4 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 4 / 32f),
            DiffusionTap(0, 1, 8 / 32f),
            DiffusionTap(1, 1, 4 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
        ),
    )

    val SIERRA = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 5 / 32f),
            DiffusionTap(2, 0, 3 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 4 / 32f),
            DiffusionTap(0, 1, 5 / 32f),
            DiffusionTap(1, 1, 4 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
            DiffusionTap(-1, 2, 2 / 32f),
            DiffusionTap(0, 2, 3 / 32f),
            DiffusionTap(1, 2, 2 / 32f),
        ),
    )

    val SIERRA_TWO_ROW = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 4 / 16f),
            DiffusionTap(2, 0, 3 / 16f),
            DiffusionTap(-2, 1, 1 / 16f),
            DiffusionTap(-1, 1, 2 / 16f),
            DiffusionTap(0, 1, 3 / 16f),
            DiffusionTap(1, 1, 2 / 16f),
            DiffusionTap(2, 1, 1 / 16f),
        ),
    )

    /**
     * A widely copied mistranscription of Floyd-Steinberg that took on a life of its own.
     *
     * It is included because it genuinely looks different — coarser, more directional — not
     * because it is correct. The name says so.
     */
    val FALSE_FLOYD = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 3 / 8f),
            DiffusionTap(0, 1, 3 / 8f),
            DiffusionTap(1, 1, 2 / 8f),
        ),
    )

    /**
     * Stevenson-Arce, 1985. The weights sit on a hexagonal lattice — every second column of the
     * 7x4 window is empty — which is why it reaches three cells sideways yet costs about what a
     * 5x3 kernel does. The staggering is the point: a hexagonal neighbourhood has no axis for
     * artefacts to line up along.
     */
    val STEVENSON_ARCE = ErrorDiffusion(
        listOf(
            DiffusionTap(2, 0, 32 / 200f),
            DiffusionTap(-3, 1, 12 / 200f),
            DiffusionTap(-1, 1, 26 / 200f),
            DiffusionTap(1, 1, 30 / 200f),
            DiffusionTap(3, 1, 16 / 200f),
            DiffusionTap(-2, 2, 12 / 200f),
            DiffusionTap(0, 2, 26 / 200f),
            DiffusionTap(2, 2, 12 / 200f),
            DiffusionTap(-3, 3, 5 / 200f),
            DiffusionTap(-1, 3, 12 / 200f),
            DiffusionTap(1, 3, 12 / 200f),
            DiffusionTap(3, 3, 5 / 200f),
        ),
    )

    /**
     * Smooth Diffuse. Named in their tutorials; the weights are this app's own reading.
     *
     * The classic kernels concentrate most of the error on the two or three cells nearest the
     * current one, which is what gives their grain its bite. Spreading it thinly and evenly over
     * a wider neighbourhood instead trades that bite for a much finer, more even texture — the
     * same total error, simply shared further.
     */
    val SMOOTH_DIFFUSE = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 4 / 32f),
            DiffusionTap(2, 0, 3 / 32f),
            DiffusionTap(3, 0, 2 / 32f),
            DiffusionTap(-3, 1, 1 / 32f),
            DiffusionTap(-2, 1, 2 / 32f),
            DiffusionTap(-1, 1, 3 / 32f),
            DiffusionTap(0, 1, 4 / 32f),
            DiffusionTap(1, 1, 3 / 32f),
            DiffusionTap(2, 1, 2 / 32f),
            DiffusionTap(3, 1, 1 / 32f),
            DiffusionTap(-2, 2, 1 / 32f),
            DiffusionTap(-1, 2, 2 / 32f),
            DiffusionTap(0, 2, 2 / 32f),
            DiffusionTap(1, 2, 2 / 32f),
            DiffusionTap(2, 2, 0 / 32f),
        ),
    )

    /**
     * Axis-dominant diffusion. The four classic kernels all spread the error roughly evenly
     * forward and down, which is what makes their noise look isotropic. Pushing almost all of it
     * along one axis instead makes the error travel in a line, and the result reads as streaks
     * rather than as grain — the "diffuse along Y" look.
     */
    val DIFFUSE_Y = ErrorDiffusion(
        listOf(
            DiffusionTap(0, 1, 11 / 20f),
            DiffusionTap(0, 2, 5 / 20f),
            DiffusionTap(1, 0, 2 / 20f),
            DiffusionTap(-1, 1, 1 / 20f),
            DiffusionTap(1, 1, 1 / 20f),
        ),
    )

    val DIFFUSE_X = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 12 / 20f),
            DiffusionTap(2, 0, 5 / 20f),
            DiffusionTap(0, 1, 2 / 20f),
            DiffusionTap(1, 1, 1 / 20f),
        ),
    )

    /**
     * Ostromoukhov, SIGGRAPH 2001. The weights are looked up per input level rather than fixed —
     * see [Ostromoukhov]. The declared taps are only the shape: they tell the engine that the
     * mode diffuses at all and how deep an error buffer it needs.
     */
    val OSTROMOUKHOV = ErrorDiffusion(
        Ostromoukhov.representative,
        perValue = { Ostromoukhov.kernelFor(it) },
    )

    /**
     * Shiau-Fan. Transcribed from Figure 1 of Ostromoukhov's paper, where it is drawn beside
     * Floyd-Steinberg for comparison. Nearly all of the error goes to the right and straight
     * down; what reaches the row below travels a long way left, which is what breaks up the
     * diagonal worms Floyd-Steinberg leaves behind.
     */
    val SHIAU_FAN = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 8 / 16f),
            DiffusionTap(-3, 1, 1 / 16f),
            DiffusionTap(-2, 1, 1 / 16f),
            DiffusionTap(-1, 1, 2 / 16f),
            DiffusionTap(0, 1, 4 / 16f),
        ),
    )

    /**
     * A Gaussian falling off over a 5x5 window, computed rather than chosen: the weight of each
     * tap is exp(-d²/2σ²) with σ = 1.2, normalised over the forward half. The classic kernels
     * all have a bias built into their integer weights; this one has none, and the grain comes
     * out correspondingly soft and even.
     */
    val GAUSSIAN = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 24 / 128f),
            DiffusionTap(2, 0, 9 / 128f),
            DiffusionTap(-2, 1, 6 / 128f),
            DiffusionTap(-1, 1, 17 / 128f),
            DiffusionTap(0, 1, 24 / 128f),
            DiffusionTap(1, 1, 17 / 128f),
            DiffusionTap(2, 1, 6 / 128f),
            DiffusionTap(-2, 2, 2 / 128f),
            DiffusionTap(-1, 2, 6 / 128f),
            DiffusionTap(0, 2, 9 / 128f),
            DiffusionTap(1, 2, 6 / 128f),
            DiffusionTap(2, 2, 2 / 128f),
        ),
    )

    /**
     * Every neighbour weighted the same. The classic kernels are shaped precisely to avoid this
     * — equal weights let the error pile up in step with the grid and throw off a regular
     * artefact, which here is the point rather than the failure.
     */
    val ARTIFACT_MODULATION = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 1 / 4f),
            DiffusionTap(-1, 1, 1 / 4f),
            DiffusionTap(0, 1, 1 / 4f),
            DiffusionTap(1, 1, 1 / 4f),
        ),
    )

    /**
     * Floyd-Steinberg's footprint with the split between right and down-left decided by the
     * cell's own brightness. Highlights push their error sideways and shadows push it down, so
     * the grain leans one way in the light and the other in the dark. The declared taps are the
     * balanced middle.
     */
    val VARIABLE_ERROR = ErrorDiffusion(
        variableErrorKernel(MIDDLE),
        perValue = byBrightness(::variableErrorKernel),
    )

    /**
     * Error split evenly between right and straight down, with nothing on the diagonal. Denying
     * it the diagonal is what makes the artefacts run in unbroken horizontal and vertical runs
     * that never cross — the cracks the name refers to.
     */
    val CRACKED_DIFFUSE = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 1 / 2f),
            DiffusionTap(0, 1, 1 / 2f),
        ),
    )

    /**
     * Nothing ever reaches the row below, so an error runs to the end of its line and stops.
     * Errors cannot average out across rows, and each line drifts independently — which is what
     * a dropped video line looks like.
     */
    val ARTIFACT_GLITCH = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 8 / 16f),
            DiffusionTap(2, 0, 5 / 16f),
            DiffusionTap(3, 0, 3 / 16f),
        ),
    )

    /**
     * Atkinson smeared sideways. It keeps his eighths — including the quarter he throws away,
     * which is what holds the contrast up — but reaches four cells along the row instead of two,
     * so the loss trails behind bright areas like a tape head catching up.
     */
    val ATKINSON_VHS = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 1 / 8f),
            DiffusionTap(2, 0, 1 / 8f),
            DiffusionTap(3, 0, 1 / 8f),
            DiffusionTap(4, 0, 1 / 8f),
            DiffusionTap(0, 1, 1 / 8f),
            DiffusionTap(1, 1, 1 / 8f),
        ),
    )

    /**
     * Atkinson whose sideways bias follows the brightness. Highlights send their error along the
     * row and shadows send it down, so the smear appears only where the picture is light.
     * Modulation in the literal sense: one thing varying another.
     */
    val ATKINSON_LINE_MOD = ErrorDiffusion(
        atkinsonLineKernel(MIDDLE),
        perValue = byBrightness(::atkinsonLineKernel),
    )

    /**
     * Stucki with its first row weighted far more heavily than its lower ones. The error
     * therefore travels much further along a row than down the image, and the grain stretches
     * into lines without ever becoming purely horizontal.
     */
    val STUCKI_LINES = ErrorDiffusion(
        listOf(
            DiffusionTap(1, 0, 12 / 42f),
            DiffusionTap(2, 0, 8 / 42f),
            DiffusionTap(3, 0, 4 / 42f),
            DiffusionTap(-2, 1, 1 / 42f),
            DiffusionTap(-1, 1, 2 / 42f),
            DiffusionTap(0, 1, 6 / 42f),
            DiffusionTap(1, 1, 4 / 42f),
            DiffusionTap(2, 1, 2 / 42f),
            DiffusionTap(-1, 2, 1 / 42f),
            DiffusionTap(0, 2, 1 / 42f),
            DiffusionTap(1, 2, 1 / 42f),
        ),
    )
}

/** The brightness a value-dependent kernel is represented by: mid-grey, and never a special case. */
private const val MIDDLE = 0.5f

/** How finely a value-dependent kernel is sampled. Finer than either the eye or the ramp. */
private const val STEPS = 64

/**
 * A kernel that changes with the value being quantised, pre-built at [STEPS] brightnesses.
 *
 * This is asked **once per cell**, so it must hand back a cached list rather than build one — a
 * megapixel image is a million allocations if it is answered carelessly. Building the table here
 * rather than at each declaration is what keeps that rule in one place instead of in a comment.
 */
private fun byBrightness(build: (Float) -> List<DiffusionTap>): (Float) -> List<DiffusionTap> {
    val steps = List(STEPS) { build(it / (STEPS - 1f)) }
    return { value -> steps[(value.coerceIn(0f, 1f) * (steps.size - 1)).toInt()] }
}

/**
 * Floyd-Steinberg's four taps, with the share between right and down-left sliding with
 * brightness. The total is always sixteen sixteenths — an intensity-dependent kernel that also
 * leaks error would be two changes at once, and only one of them is the idea.
 */
private fun variableErrorKernel(value: Float): List<DiffusionTap> {
    val lean = (value.coerceIn(0f, 1f) * 3f)
    return listOf(
        DiffusionTap(1, 0, (4f + lean) / 16f),
        DiffusionTap(-1, 1, (4f - lean) / 16f),
        DiffusionTap(0, 1, 5 / 16f),
        DiffusionTap(1, 1, 3 / 16f),
    )
}

/** Atkinson's eighths, with the split between along-the-row and down set by brightness. */
private fun atkinsonLineKernel(value: Float): List<DiffusionTap> {
    val lean = value.coerceIn(0f, 1f)
    return listOf(
        DiffusionTap(1, 0, (1f + lean) / 8f),
        DiffusionTap(2, 0, (1f + lean * 0.5f) / 8f),
        DiffusionTap(-1, 1, (1f - lean * 0.5f) / 8f),
        DiffusionTap(0, 1, (1f - lean * 0.5f) / 8f),
        DiffusionTap(1, 1, (1f - lean * 0.5f) / 8f),
    )
}
