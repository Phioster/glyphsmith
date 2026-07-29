package org.phioster.glyphsmith.ascii

/**
 * Ostromoukhov's variable-coefficient error diffusion, SIGGRAPH 2001.
 *
 * Every other kernel in this app is a constant: Floyd-Steinberg sends 7/16 to the right
 * whether the cell is nearly white or nearly black. This one looks the weights up by the
 * value it is quantising, and that is the entire algorithm. The pay-off is in the mid-tones
 * and in the near-white and near-black ends, where fixed kernels lay down the visible worm
 * and chain artefacts that error diffusion is otherwise known for.
 *
 * **The table is transcribed from Appendix I of the paper itself**, not from another
 * implementation. That distinction earned its keep: the one open-source implementation
 * checked against it agrees only for the first eight rows and diverges from row 8 onward.
 *
 * The paper tabulates levels 0..127 and states `D(i) == D(255 - i)`, so the upper half is the
 * lower half mirrored rather than missing.
 */
object Ostromoukhov {

    /** `A10, A-11, A01` per input level, from Appendix I. Weights are each over their sum. */
    private val TABLE = arrayOf(
        intArrayOf(13, 0, 5), intArrayOf(13, 0, 5), intArrayOf(21, 0, 10), intArrayOf(7, 0, 4),  // 0..3
        intArrayOf(8, 0, 5), intArrayOf(47, 3, 28), intArrayOf(23, 3, 13), intArrayOf(15, 3, 8),  // 4..7
        intArrayOf(22, 6, 11), intArrayOf(43, 15, 20), intArrayOf(7, 3, 3), intArrayOf(501, 224, 211),  // 8..11
        intArrayOf(249, 116, 103), intArrayOf(165, 80, 67), intArrayOf(123, 62, 49), intArrayOf(489, 256, 191),  // 12..15
        intArrayOf(81, 44, 31), intArrayOf(483, 272, 181), intArrayOf(60, 35, 22), intArrayOf(53, 32, 19),  // 16..19
        intArrayOf(237, 148, 83), intArrayOf(471, 304, 161), intArrayOf(3, 2, 1), intArrayOf(481, 314, 185),  // 20..23
        intArrayOf(354, 226, 155), intArrayOf(1389, 866, 685), intArrayOf(227, 138, 125), intArrayOf(267, 158, 163),  // 24..27
        intArrayOf(327, 188, 220), intArrayOf(61, 34, 45), intArrayOf(627, 338, 505), intArrayOf(1227, 638, 1075),  // 28..31
        intArrayOf(20, 10, 19), intArrayOf(1937, 1000, 1767), intArrayOf(977, 520, 855), intArrayOf(657, 360, 551),  // 32..35
        intArrayOf(71, 40, 57), intArrayOf(2005, 1160, 1539), intArrayOf(337, 200, 247), intArrayOf(2039, 1240, 1425),  // 36..39
        intArrayOf(257, 160, 171), intArrayOf(691, 440, 437), intArrayOf(1045, 680, 627), intArrayOf(301, 200, 171),  // 40..43
        intArrayOf(177, 120, 95), intArrayOf(2141, 1480, 1083), intArrayOf(1079, 760, 513), intArrayOf(725, 520, 323),  // 44..47
        intArrayOf(137, 100, 57), intArrayOf(2209, 1640, 855), intArrayOf(53, 40, 19), intArrayOf(2243, 1720, 741),  // 48..51
        intArrayOf(565, 440, 171), intArrayOf(759, 600, 209), intArrayOf(1147, 920, 285), intArrayOf(2311, 1880, 513),  // 52..55
        intArrayOf(97, 80, 19), intArrayOf(335, 280, 57), intArrayOf(1181, 1000, 171), intArrayOf(793, 680, 95),  // 56..59
        intArrayOf(599, 520, 57), intArrayOf(2413, 2120, 171), intArrayOf(405, 360, 19), intArrayOf(2447, 2200, 57),  // 60..63
        intArrayOf(11, 10, 0), intArrayOf(158, 151, 3), intArrayOf(178, 179, 7), intArrayOf(1030, 1091, 63),  // 64..67
        intArrayOf(248, 277, 21), intArrayOf(318, 375, 35), intArrayOf(458, 571, 63), intArrayOf(878, 1159, 147),  // 68..71
        intArrayOf(5, 7, 1), intArrayOf(172, 181, 37), intArrayOf(97, 76, 22), intArrayOf(72, 41, 17),  // 72..75
        intArrayOf(119, 47, 29), intArrayOf(4, 1, 1), intArrayOf(4, 1, 1), intArrayOf(4, 1, 1),  // 76..79
        intArrayOf(4, 1, 1), intArrayOf(4, 1, 1), intArrayOf(4, 1, 1), intArrayOf(4, 1, 1),  // 80..83
        intArrayOf(4, 1, 1), intArrayOf(4, 1, 1), intArrayOf(65, 18, 17), intArrayOf(95, 29, 26),  // 84..87
        intArrayOf(185, 62, 53), intArrayOf(30, 11, 9), intArrayOf(35, 14, 11), intArrayOf(85, 37, 28),  // 88..91
        intArrayOf(55, 26, 19), intArrayOf(80, 41, 29), intArrayOf(155, 86, 59), intArrayOf(5, 3, 2),  // 92..95
        intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2),  // 96..99
        intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2),  // 100..103
        intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2), intArrayOf(5, 3, 2),  // 104..107
        intArrayOf(305, 176, 119), intArrayOf(155, 86, 59), intArrayOf(105, 56, 39), intArrayOf(80, 41, 29),  // 108..111
        intArrayOf(65, 32, 23), intArrayOf(55, 26, 19), intArrayOf(335, 152, 113), intArrayOf(85, 37, 28),  // 112..115
        intArrayOf(115, 48, 37), intArrayOf(35, 14, 11), intArrayOf(355, 136, 109), intArrayOf(30, 11, 9),  // 116..119
        intArrayOf(365, 128, 107), intArrayOf(185, 62, 53), intArrayOf(25, 8, 7), intArrayOf(95, 29, 26),  // 120..123
        intArrayOf(385, 112, 103), intArrayOf(65, 18, 17), intArrayOf(395, 104, 101), intArrayOf(4, 1, 1),  // 124..127
    )

    /**
     * The kernel for a value in 0..1.
     *
     * Every one of the 128 rows is turned into its tap list once, at class-init, and handed
     * back by reference. Building one per cell would be a million allocations on a large
     * image, which is the difference between an algorithm and a stutter.
     */
    fun kernelFor(value: Float): List<DiffusionTap> =
        KERNELS[level(value)]

    /** Folds a 0..1 value onto the tabulated half. */
    private fun level(value: Float): Int {
        val v = (value.coerceIn(0f, 1f) * 255f).toInt()
        return if (v > 127) 255 - v else v
    }

    /**
     * Where the three coefficients go.
     *
     * The paper's notation is `N`(dx)(dy): `d10` is the cell to the right on the same row,
     * `d-11` the one below and to the left, `d01` the one directly below. The scan is
     * serpentine, and the caller mirrors `dx` on the reversed rows.
     */
    private val KERNELS: List<List<DiffusionTap>> = TABLE.map { row ->
        val sum = (row[0] + row[1] + row[2]).toFloat()
        listOf(
            DiffusionTap(1, 0, row[0] / sum),
            DiffusionTap(-1, 1, row[1] / sum),
            DiffusionTap(0, 1, row[2] / sum),
        )
    }

    /**
     * A stand-in kernel with the right shape but fixed weights.
     *
     * The engine needs one before the loop starts, to size the error buffer and to know that
     * this mode diffuses at all. Mid-grey is as good a representative as any — it is never
     * used to quantise anything.
     */
    val representative: List<DiffusionTap> = KERNELS[127]

    /** Rows in the table. Public so a test can hold the transcription to its length. */
    const val LEVELS = 128
}
