package org.phioster.glyphsmith.ascii

/**
 * A named ramp of glyphs, ordered by *increasing ink coverage*: the first glyph is the
 * emptiest (usually a space), the last one the densest.
 *
 * Everything downstream relies on that ordering — the engine maps a cell's luminance
 * straight onto an index in [glyphs], so a set that isn't sorted by coverage produces
 * noise instead of an image.
 */
data class CharacterSet(
    val id: String,
    val name: String,
    val category: String,
    val glyphs: String,
) {
    val size: Int get() = glyphs.length
}

/**
 * 48 built-in sets across 11 categories, mirroring the shape of the reference app's library.
 *
 * Sets that lean on exotic Unicode (braille, runes, sextant blocks) render only as well as
 * the device font covers them; the picker shows a live preview of every set so a set with
 * missing glyphs is visible before it's used.
 */
object CharacterSets {

    const val CATEGORY_ASCII = "ASCII"
    const val CATEGORY_NUMBERS = "NUMBERS"
    const val CATEGORY_SYMBOLS = "SYMBOLS"
    const val CATEGORY_BLOCKS = "BLOCKS"
    const val CATEGORY_BRAILLE = "BRAILLE"
    const val CATEGORY_GEOMETRIC = "GEOMETRIC"
    const val CATEGORY_LANGUAGES = "LANGUAGES"
    const val CATEGORY_CARDS = "CARDS"
    const val CATEGORY_UNICODE = "UNICODE"
    const val CATEGORY_LINES = "LINES"
    const val CATEGORY_MISC = "MISC"

    val all: List<CharacterSet> = listOf(
        // --- ASCII ---------------------------------------------------------------
        CharacterSet(
            "ascii-standard-70", "Standard 70", CATEGORY_ASCII,
            " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@\$",
        ),
        CharacterSet("ascii-standard-10", "Standard 10", CATEGORY_ASCII, " .:-=+*#%@"),
        CharacterSet("ascii-minimal-5", "Minimal 5", CATEGORY_ASCII, " .*#@"),
        CharacterSet("ascii-typewriter", "Typewriter", CATEGORY_ASCII, " .,:;irsXA253hMHGS#9B&@"),
        CharacterSet("ascii-punctuation", "Punctuation", CATEGORY_ASCII, " .,'`:;!|?*&#@"),
        CharacterSet("ascii-letters", "Letters", CATEGORY_ASCII, " ilcvxzsnuoahkbdpqmwMWB"),

        // --- NUMBERS -------------------------------------------------------------
        CharacterSet("num-digits", "Digits", CATEGORY_NUMBERS, " 1742356908"),
        CharacterSet("num-binary", "Binary", CATEGORY_NUMBERS, " 01"),
        CharacterSet("num-roman", "Roman", CATEGORY_NUMBERS, " IVXLCDM"),
        CharacterSet("num-circled", "Circled", CATEGORY_NUMBERS, " ⓪①②③④⑤⑥⑦⑧⑨"),

        // --- SYMBOLS -------------------------------------------------------------
        CharacterSet("sym-math", "Math", CATEGORY_SYMBOLS, " ·−+±×÷≠≈≡∑∏∫"),
        CharacterSet("sym-currency", "Currency", CATEGORY_SYMBOLS, " ¢£¥€₩₪₫₽\$₿"),
        CharacterSet("sym-arrows", "Arrows", CATEGORY_SYMBOLS, " ·→↑↓←↔↕⇒⇔⇑⇓"),
        CharacterSet("sym-dingbats", "Dingbats", CATEGORY_SYMBOLS, " ·✢✣✤✥✦✧✩✪✫✬✭✮✯"),
        CharacterSet("sym-heavy", "Heavy Punctuation", CATEGORY_SYMBOLS, " ·:;!¡?¿*†‡§¶©®™"),

        // --- BLOCKS --------------------------------------------------------------
        CharacterSet("block-shade", "Shade", CATEGORY_BLOCKS, " ░▒▓█"),
        CharacterSet("block-vertical", "Vertical Eighths", CATEGORY_BLOCKS, " ▁▂▃▄▅▆▇█"),
        CharacterSet("block-horizontal", "Horizontal Eighths", CATEGORY_BLOCKS, " ▏▎▍▌▋▊▉█"),
        CharacterSet("block-quadrant", "Quadrants", CATEGORY_BLOCKS, " ▘▝▖▗▚▀▄▌▐▛▜▙▟█"),
        CharacterSet("block-half", "Halves", CATEGORY_BLOCKS, " ▘▀▌▛█"),
        CharacterSet("block-mixed", "Mixed", CATEGORY_BLOCKS, " ·░▒▓█"),

        // --- BRAILLE -------------------------------------------------------------
        CharacterSet("braille-density", "Density", CATEGORY_BRAILLE, " ⠁⠃⠇⠏⠟⠿⡿⣿"),
        CharacterSet("braille-dots", "Dots", CATEGORY_BRAILLE, " ⠂⠆⠖⠶⠾⡾⣾⣿"),
        CharacterSet("braille-sparse", "Sparse", CATEGORY_BRAILLE, " ⠄⠤⠴⠷⣇⣧⣷⣿"),
        CharacterSet("braille-ramp", "Full Ramp", CATEGORY_BRAILLE, " ⠁⠉⠋⠛⠟⠿⡿⣟⣯⣷⣿"),

        // --- GEOMETRIC -----------------------------------------------------------
        CharacterSet("geo-circles", "Circles", CATEGORY_GEOMETRIC, " ·∘○◌◍◎●"),
        CharacterSet("geo-squares", "Squares", CATEGORY_GEOMETRIC, " ▫▪◻◼◧◨▩■"),
        CharacterSet("geo-triangles", "Triangles", CATEGORY_GEOMETRIC, " ▵▴△▲◣◢◤◥"),
        CharacterSet("geo-diamonds", "Diamonds", CATEGORY_GEOMETRIC, " ⋄◇◈◆"),
        CharacterSet("geo-mixed", "Mixed Shapes", CATEGORY_GEOMETRIC, " ·▫○◇□△◆■●"),

        // --- LANGUAGES -----------------------------------------------------------
        CharacterSet(
            "lang-katakana", "Katakana", CATEGORY_LANGUAGES,
            " ･ｰｦｧｨｩｪｫｯｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾅﾆﾇﾈﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾙﾚﾛﾜﾝ",
        ),
        CharacterSet(
            "lang-hiragana", "Hiragana", CATEGORY_LANGUAGES,
            " ・くしつへのりるろこいたなにはまもをあおぬめ",
        ),
        CharacterSet("lang-greek", "Greek", CATEGORY_LANGUAGES, " ·ιτγνλκζξπμαβθφψΩΞΨ"),
        CharacterSet("lang-cyrillic", "Cyrillic", CATEGORY_LANGUAGES, " ·гтснипкхжэюмбвдфшщЖЩ"),
        CharacterSet("lang-hebrew", "Hebrew", CATEGORY_LANGUAGES, " ·יוזנרכתאבגדהחטםמסעפצקש"),
        CharacterSet("lang-runic", "Runic", CATEGORY_LANGUAGES, " ·ᛁᛂᚨᚱᚲᚷᚹᚺᚾᛃᛈᛊᛏᛒᛖᛗᛚᛜᛟᛞ"),

        // --- CARDS ---------------------------------------------------------------
        CharacterSet("cards-suits", "Suits", CATEGORY_CARDS, " ·♤♧♡♢♠♣♥♦"),
        CharacterSet("cards-chess", "Chess", CATEGORY_CARDS, " ·♙♘♗♖♕♔♟♞♝♜♛♚"),
        CharacterSet("cards-dice", "Dice", CATEGORY_CARDS, " ⚀⚁⚂⚃⚄⚅"),

        // --- UNICODE -------------------------------------------------------------
        CharacterSet("uni-stars", "Stars", CATEGORY_UNICODE, " ·⋅∗✱★✦✧✩✪✵✶✷✸✹✺"),
        CharacterSet("uni-weather", "Weather", CATEGORY_UNICODE, " ·☁☂☃☄★☼☽☾♁♆"),
        CharacterSet("uni-music", "Music", CATEGORY_UNICODE, " ·♩♪♫♬♭♮♯"),
        CharacterSet("uni-misc", "Miscellany", CATEGORY_UNICODE, " ·☺☻☼♀♂♠♣♥♦♪♫►◄§▬"),

        // --- LINES ---------------------------------------------------------------
        CharacterSet("line-box", "Box Drawing", CATEGORY_LINES, " ─│┌┐└┘├┤┼╪╫╬"),
        CharacterSet("line-dashes", "Dashes", CATEGORY_LINES, " ·‥⋯–—━═▬"),
        CharacterSet("line-slashes", "Slashes", CATEGORY_LINES, " ./\\|─+×#▓"),

        // --- MISC ----------------------------------------------------------------
        CharacterSet("misc-dots", "Dot Matrix", CATEGORY_MISC, " ·⁚∴∵⋮⋯⋰⋱⁘⁙"),
        CharacterSet("misc-crosses", "Crosses", CATEGORY_MISC, " ·˖+✛✚✜✠✥"),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    private val byId: Map<String, CharacterSet> = all.associateBy { it.id }

    val default: CharacterSet = byId.getValue("ascii-standard-10")

    fun byId(id: String): CharacterSet = byId[id] ?: default

    fun inCategory(category: String): List<CharacterSet> = all.filter { it.category == category }
}
