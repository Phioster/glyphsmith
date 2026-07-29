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
            -> DitherCategory.ERROR_DIFFUSION

            BAYER_2, BAYER_4, BAYER_8, BAYER_16, CLUSTER_4, CLUSTER_8,
            BLUE_NOISE_16, BLUE_NOISE_32,
            -> DitherCategory.ORDERED

            MOD_LINES, UNIFORM_MODULATION, HEART_GRID, POP_TONE -> DitherCategory.PATTERNED

            // Both push nearly all of the error along one axis, so the grain arrives as
            // streaks rather than as noise — a signal fault rather than a halftone.
            DIFFUSE_Y, DIFFUSE_X -> DitherCategory.GLITCH

            MOD_WAVE, MOD_RINGS, MOD_ORB, BEEHIVE -> DitherCategory.SPECIAL
        }
}
