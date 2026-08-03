package org.phioster.glyphsmith.core.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The identity a saved preset uses for a render mode, a dither algorithm or an effect.
 *
 * A preset names the things it is made of, and until now it named them with Kotlin enum
 * constants. That makes the *source code* the file format: renaming a constant, or reordering
 * anything that serialises by ordinal, silently changes what an existing preset means. A stable
 * id is the same identity written down once and never again derived from something that is
 * free to change — not the enum name, not the class name, not the package, and above all not
 * the label shown in the interface, which is free to be reworded or translated.
 *
 * The shape is `category.name`:
 *
 *     render.pixel-dither    dither.floyd-steinberg    effect.crt-warp
 *
 * lowercase ASCII, words joined by hyphens, one dot between the category and the name. Ids are
 * unique within their category and are not changed again without a migration.
 */
object WireId {

    /** `category.name`, both sides lowercase ASCII words joined by hyphens. */
    private val FORMAT = Regex("[a-z0-9]+(-[a-z0-9]+)*\\.[a-z0-9]+(-[a-z0-9]+)*")

    fun isValid(id: String): Boolean = FORMAT.matches(id)
}

/**
 * A stable id this build has never heard of.
 *
 * Structured on purpose — it carries the category and the offending id — because the one thing
 * that must never happen is quietly resolving it to something else. An unknown dither is not
 * "no dither", and an unknown render mode is not the default one: substituting either would
 * hand back a preset that looks nothing like what its author saved, without saying so. Refusing
 * the value costs the entry that carries it, which the caller can report, and nothing else.
 */
class UnknownWireIdException(val category: String, val id: String) :
    SerializationException("unknown $category id: \"$id\"")

/**
 * Serialises an enum as its stable id, and reads back both the id and the enum-constant name
 * that was written before ids existed.
 *
 * The enums stay exactly where they are and keep doing the work — they are the internal
 * vocabulary, and nothing about the program logic changes. All this adds is one explicit table
 * saying what each constant is *called on disk*, and that table is an exhaustive `when` in the
 * subclass, so the compiler refuses to build once something is added without an id.
 *
 * Legacy names are accepted as aliases rather than translated by the reader: the constant name
 * is what old presets contain, and it identifies exactly the same thing. New writes always use
 * the id.
 */
abstract class WireIdSerializer<T : Enum<T>>(
    /** The category the ids of this enum begin with — `render`, `dither`, `effect`. */
    val category: String,
    values: List<T>,
    idOf: (T) -> String,
) : KSerializer<T> {

    /** Every constant and the id it is written as. Public so a test can hold it to the rules. */
    val ids: Map<T, String> = values.associateWith(idOf)

    private val byId: Map<String, T> = ids.entries.associate { (value, id) -> id to value }

    /** What presets written before the ids contain: the enum constant's own name. */
    private val byLegacyName: Map<String, T> = values.associateBy { it.name }

    init {
        // Two constants sharing an id means one of them cannot be read back, and the loss is
        // silent. A test says the same thing at build time; this says it before a single
        // preset has been written with a broken table.
        require(byId.size == ids.size) { "$category ids are not unique: ${ids.values}" }
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.phioster.glyphsmith.$category", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(ids.getValue(value))

    override fun deserialize(decoder: Decoder): T = of(decoder.decodeString())

    /** The id [value] is written as. */
    fun idOf(value: T): String = ids.getValue(value)

    /**
     * What [raw] names, whether it is a stable id or a legacy enum name.
     *
     * Throws [UnknownWireIdException] rather than returning a fallback, because there is no
     * honest fallback for an identity.
     */
    fun of(raw: String): T = byId[raw] ?: byLegacyName[raw] ?: throw UnknownWireIdException(category, raw)

    /**
     * The id a legacy enum name should become, or null when [raw] is not one — either because
     * it is already an id, or because this build does not know it.
     *
     * Null meaning "leave it alone" is what keeps the schema migration from touching a value it
     * does not understand: an unknown id has to survive intact as far as the reader, which is
     * the only place allowed to refuse it.
     */
    fun migrated(raw: String): String? = byLegacyName[raw]?.let { ids.getValue(it) }
}
