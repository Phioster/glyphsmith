package org.phioster.glyphsmith.ascii

/**
 * The shelf a style is filed under.
 *
 * Sorting exists because the list got long. A flat run of eighty entries is not a menu, it is
 * a haystack — and the sections are not decoration either: a style's category says something
 * true about how it works, so browsing one is a way of asking for a *kind* of look rather
 * than remembering a name.
 */
enum class DitherCategory(val label: String) {
    BASIC("Basic"),
    ERROR_DIFFUSION("Error Diffusion"),
    ORDERED("Ordered"),
    PATTERNED("Patterned"),
    POLYGON("Polygon"),
    GLITCH("Glitch"),
    SPECIAL("Special"),
}

/** How the quantisation error is dealt with when a cell picks its glyph. */
enum class DitherMode {
    NONE,
    FLOYD_STEINBERG,
    ATKINSON,
    JARVIS,
    SIERRA_LITE,
    STUCKI,
    BURKES,
    SIERRA,
    SIERRA_TWO_ROW,
    FALSE_FLOYD,
    STEVENSON_ARCE,
    SMOOTH_DIFFUSE,
    RIEMERSMA,
    DIFFUSE_Y,
    DIFFUSE_X,
    BAYER_2,
    BAYER_4,
    BAYER_8,
    BAYER_16,
    CLUSTER_4,
    CLUSTER_8,
    BLUE_NOISE_16,
    BLUE_NOISE_32,
    MOD_LINES,
    MOD_WAVE,
    MOD_RINGS,
    MOD_ORB,
    BEEHIVE,
    UNIFORM_MODULATION,
    HEART_GRID,
    POP_TONE,
    CHECKERS,
    DIAMOND,
    CROSSHATCH,
    STIPPLING,
    BIT_TONE,
    BLOCK_TONE,
    PRINT_PATTERN,
    GRIDLOCK,
    RANDOM_ORDERED,
    NOISE,
    WAVE,
    RADIAL_BURST,
    SINE_DISTORT,
    MOSAIC,
    SQUARE_MOSAIC,
    CIRCLE_GRID,
    DIAMOND_GRID,
    TRI_POLY,
    HEXA_POLY,
    PENTA_POLY,
    LOW_POLY,
    CAMO,
    OSTROMOUKHOV,
    SHIAU_FAN,
    DOT_DIFFUSION,
    GAUSSIAN,
    ARTIFACT_MODULATION,
    VARIABLE_ERROR,
    FRACTAL_DIFFUSE,
    ;

    val label: String
        get() = when (this) {
            NONE -> "None"
            FLOYD_STEINBERG -> "Floyd-Steinberg"
            ATKINSON -> "Atkinson"
            JARVIS -> "Jarvis"
            SIERRA_LITE -> "Sierra Lite"
            STUCKI -> "Stucki"
            BURKES -> "Burkes"
            SIERRA -> "Sierra"
            SIERRA_TWO_ROW -> "Sierra Two-Row"
            FALSE_FLOYD -> "False Floyd-Steinberg"
            STEVENSON_ARCE -> "Stevenson-Arce"
            SMOOTH_DIFFUSE -> "Smooth Diffuse"
            RIEMERSMA -> "Riemersma"
            DIFFUSE_Y -> "Diffuse Y"
            DIFFUSE_X -> "Diffuse X"
            BAYER_2 -> "Bayer 2×2"
            BAYER_4 -> "Bayer 4×4"
            BAYER_8 -> "Bayer 8×8"
            BAYER_16 -> "Bayer 16×16"
            CLUSTER_4 -> "Clustered Dot 4×4"
            CLUSTER_8 -> "Clustered Dot 8×8"
            BLUE_NOISE_16 -> "Blue Noise 16×16"
            BLUE_NOISE_32 -> "Blue Noise 32×32"
            MOD_LINES -> "Modulation Lines"
            MOD_WAVE -> "Modulation Wave"
            MOD_RINGS -> "Modulation Rings"
            MOD_ORB -> "Orb"
            BEEHIVE -> "Beehive"
            UNIFORM_MODULATION -> "Uniform Modulation"
            HEART_GRID -> "Heart Grid"
            POP_TONE -> "Pop Tone"
            CHECKERS -> "Checkers"
            DIAMOND -> "Diamond"
            CROSSHATCH -> "Crosshatch"
            STIPPLING -> "Stippling"
            BIT_TONE -> "Bit Tone"
            BLOCK_TONE -> "Block Tone"
            PRINT_PATTERN -> "Print Pattern"
            GRIDLOCK -> "Gridlock"
            RANDOM_ORDERED -> "Random Ordered"
            NOISE -> "Noise"
            WAVE -> "Wave"
            RADIAL_BURST -> "Radial Burst"
            SINE_DISTORT -> "Sine Distort"
            MOSAIC -> "Mosaic"
            SQUARE_MOSAIC -> "Square Mosaic"
            CIRCLE_GRID -> "Circle Grid"
            DIAMOND_GRID -> "Diamond Grid"
            TRI_POLY -> "Tri-Poly"
            HEXA_POLY -> "Hexa-Poly"
            PENTA_POLY -> "Penta-Poly"
            LOW_POLY -> "Low-Poly"
            CAMO -> "Camo"
            OSTROMOUKHOV -> "Ostromoukhov"
            SHIAU_FAN -> "Shiau-Fan"
            DOT_DIFFUSION -> "Dot Diffusion"
            GAUSSIAN -> "Gaussian"
            ARTIFACT_MODULATION -> "Artifact Modulation"
            VARIABLE_ERROR -> "Variable Error"
            FRACTAL_DIFFUSE -> "Fractal Diffuse"
        }

    /**
     * Where this style is filed.
     *
     * Deliberately an exhaustive `when` rather than a constructor argument: the compiler then
     * refuses to build once a style is added without saying where it belongs, which is the
     * whole reason it can be trusted to drive a picker.
     */
    val category: DitherCategory
        get() = when (this) {
            NONE -> DitherCategory.BASIC

            FLOYD_STEINBERG, ATKINSON, JARVIS, SIERRA_LITE, STUCKI, BURKES, SIERRA,
            SIERRA_TWO_ROW, FALSE_FLOYD, STEVENSON_ARCE, SMOOTH_DIFFUSE, RIEMERSMA,
            OSTROMOUKHOV, SHIAU_FAN, DOT_DIFFUSION, GAUSSIAN, ARTIFACT_MODULATION,
            VARIABLE_ERROR, FRACTAL_DIFFUSE,
            -> DitherCategory.ERROR_DIFFUSION

            BAYER_2, BAYER_4, BAYER_8, BAYER_16, CLUSTER_4, CLUSTER_8,
            BLUE_NOISE_16, BLUE_NOISE_32,
            -> DitherCategory.ORDERED

            MOD_LINES, UNIFORM_MODULATION, HEART_GRID, POP_TONE,
            CHECKERS, DIAMOND, CROSSHATCH, STIPPLING, BIT_TONE, BLOCK_TONE, PRINT_PATTERN,
            GRIDLOCK,
            // Filed by mechanism rather than by name: a threshold rolled per block is a
            // pattern, whatever "ordered" suggests. ORDERED here means a matrix, and a test
            // holds it to that.
            RANDOM_ORDERED,
            // These flatten an area rather than threshold a cell, but what they produce is a
            // pattern of tiles, and that is where someone looking for one will look.
            MOSAIC, SQUARE_MOSAIC, CIRCLE_GRID, DIAMOND_GRID, CAMO,
            -> DitherCategory.PATTERNED

            // Both push nearly all of the error along one axis, so the grain arrives as
            // streaks rather than as noise — a signal fault rather than a halftone.
            DIFFUSE_Y, DIFFUSE_X -> DitherCategory.GLITCH

            MOD_WAVE, MOD_RINGS, MOD_ORB, BEEHIVE,
            NOISE, WAVE, RADIAL_BURST, SINE_DISTORT,
            -> DitherCategory.SPECIAL

            TRI_POLY, HEXA_POLY, PENTA_POLY, LOW_POLY -> DitherCategory.POLYGON
        }
}
