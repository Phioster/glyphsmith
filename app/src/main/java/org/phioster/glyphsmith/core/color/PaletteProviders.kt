package org.phioster.glyphsmith.core.color

import org.phioster.glyphsmith.core.provider.Provider
import org.phioster.glyphsmith.core.provider.ProviderCategory
import org.phioster.glyphsmith.core.provider.Registry

/**
 * A palette, described the way every other internal module is.
 *
 * The two spellings are deliberate and are the same arrangement the enums have. A [Palette]
 * keeps its own short [Palette.id] — `grayscale` — which is what the picker, the favourites and
 * the category listings key on. The *wire id* is `palette.grayscale`, and that is what a saved
 * preset carries, exactly as `DitherMode.FLOYD_STEINBERG` is stored as `dither.floyd-steinberg`.
 *
 * Before this, a preset named its palette with the bare id — the last identity in the format
 * that was not a wire id. Both spellings still resolve, because a file written by any older
 * build contains the bare one.
 */
class PaletteProvider(val palette: Palette) : Provider {
    override val id: String = wireIdOf(palette.id)
    override val displayName: String = palette.name
    override val category = ProviderCategory.PALETTE

    /** The shelf the picker groups it under. Free text — an imported palette brings its own. */
    val family: String = palette.category

    companion object {
        /** The one place a palette's stored spelling is formed. */
        fun wireIdOf(paletteId: String): String =
            if (paletteId.startsWith(PREFIX)) paletteId else "$PREFIX$paletteId"

        /** The bare id inside a wire id, or the input when it is already bare. */
        fun bareIdOf(wireId: String): String = wireId.removePrefix(PREFIX)

        private const val PREFIX = "palette."
    }
}

/** Every palette this build ships. Imported ones are not registered: they live in a file. */
object PaletteProviders : Registry<PaletteProvider>(
    ProviderCategory.PALETTE,
    Palettes.all.map(::PaletteProvider),
) {
    private val byBareId = all.associateBy { it.palette.id }

    fun of(palette: Palette): PaletteProvider = byBareId.getValue(palette.id)
}
